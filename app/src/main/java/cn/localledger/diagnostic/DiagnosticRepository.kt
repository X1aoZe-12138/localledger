package cn.localledger.diagnostic

import androidx.room.withTransaction
import cn.localledger.data.LedgerDatabase
import java.util.UUID

class DiagnosticRepository(private val db: LedgerDatabase) {
    private val dao = db.diagnosticDao()
    val sessions = dao.sessions()
    val active = dao.active()
    fun logs(id: String) = dao.logs(id)

    suspend fun start(minutes: Int, now: Long): DiagnosticSessionEntity = db.withTransaction {
        require(minutes in listOf(1, 5, 10, 15, 30))
        expire(now)
        dao.activeNow()?.let { return@withTransaction it } // Double taps do not create overlapping sessions.
        val duration = minutes * 60_000L
        val session = DiagnosticSessionEntity(UUID.randomUUID().toString(), now, now + duration, duration)
        dao.insert(session)
        dao.retainTen()
        session
    }

    suspend fun stop(now: Long) = db.withTransaction {
        expire(now)
        dao.activeNow()?.let { finish(it, now, "手动停止") }
    }

    suspend fun expire(now: Long) = db.withTransaction {
        dao.activeNow()?.let { if (now >= it.expectedEndTime) finish(it, it.expectedEndTime, "时间到") }
    }

    private suspend fun finish(session: DiagnosticSessionEntity, time: Long, reason: String) {
        val end = time.coerceIn(session.startTime, session.expectedEndTime)
        dao.update(session.copy(endTime = end, actualDuration = end - session.startTime, stopReason = reason))
    }

    // Eligibility uses callback time, not the later disk-write time. Stop/start cannot move a queued event into another session.
    suspend fun record(receivedAt: Long, read: () -> NotificationSnapshot): Boolean = db.withTransaction {
        expire(receivedAt)
        val session = dao.sessionAt(receivedAt) ?: return@withTransaction false
        if (dao.count(session.id) >= MAX_NOTIFICATIONS || session.storedCharacters >= MAX_CHARACTERS) {
            if (session.endTime == null) finish(session, receivedAt, "已达到诊断容量上限")
            return@withTransaction false
        }
        val snapshot = read()
        dao.insert(snapshot.entity(session.id, receivedAt))
        val updated = session.copy(storedCharacters = session.storedCharacters + snapshot.characterCount())
        dao.update(updated)
        if (dao.count(session.id) >= MAX_NOTIFICATIONS || updated.storedCharacters >= MAX_CHARACTERS) {
            if (updated.endTime == null) finish(updated, receivedAt + 1, "已达到诊断容量上限")
        }
        true
    }

    suspend fun clear() = db.withTransaction { dao.clear() }
    suspend fun export(id: String): DiagnosticExport? = db.withTransaction {
        dao.session(id)?.let { DiagnosticExport(it, dao.exportLogs(id)) }
    }

    companion object {
        const val MAX_NOTIFICATIONS = 2000
        const val MAX_CHARACTERS = 2_000_000L
    }
}
