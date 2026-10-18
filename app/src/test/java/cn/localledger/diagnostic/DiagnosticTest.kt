package cn.localledger.diagnostic

import android.app.Application
import android.app.Notification
import android.content.ClipboardManager
import android.content.Context
import android.os.Bundle
import android.os.UserHandle
import android.service.notification.StatusBarNotification
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import cn.localledger.data.LedgerDatabase
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.first
import org.junit.*
import org.junit.Assert.*
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], application = Application::class)
class DiagnosticTest {
    private lateinit var db: LedgerDatabase
    private lateinit var repo: DiagnosticRepository
    private val now = 1_800_000_000_000L
    private val context get() = ApplicationProvider.getApplicationContext<Context>()
    @Before fun setup() {
        db = Room.inMemoryDatabaseBuilder(context, LedgerDatabase::class.java).allowMainThreadQueries().build()
        repo = DiagnosticRepository(db)
    }
    @After fun close() { db.close() }
    private fun sample(pkg: String = "ordinary.app") = NotificationSnapshot(now - 123, pkg, text = "普通通知")
    @Test fun fiveMinuteSessionAndOrdinaryNotification() = runBlocking {
        val s = repo.start(5, now)
        assertEquals(now + 300000, s.expectedEndTime)
        assertTrue(repo.record(now + 1) { sample() })
        val log = repo.export(s.id)!!.notifications.single()
        assertEquals("ordinary.app", log.packageName)
        assertEquals(now - 123, log.timestamp)
        assertEquals(now + 1, log.receivedAt)
    }
    @Test fun manyAppsAndUniqueIds() = runBlocking {
        val s = repo.start(5, now)
        listOf("com.tencent.mm", "com.eg.android.AlipayGphone", "com.xiaomi.wallet", "com.taobao.taobao").forEach { pkg -> repo.record(now + 1) { sample(pkg) } }
        val logs = repo.export(s.id)!!.notifications
        assertEquals(4, logs.map { it.packageName }.toSet().size)
        assertEquals(4, logs.map { it.id }.toSet().size)
    }
    @Test fun inactiveDoesNotEvenReadNotificationContents() = runBlocking {
        var reads = 0
        assertFalse(repo.record(now) { reads++; sample() })
        assertEquals(0, reads)
    }
    @Test fun manualStop() = runBlocking {
        val s = repo.start(5, now)
        repo.stop(now + 10000)
        assertFalse(repo.record(now + 10000) { sample() })
        val ended = repo.export(s.id)!!.session
        assertEquals(10000L, ended.actualDuration)
        assertEquals("手动停止", ended.stopReason)
    }
    @Test fun deadlineIsExclusiveAndEndsAtPlannedTime() = runBlocking {
        val s = repo.start(5, now)
        assertTrue(repo.record(now + 299999) { sample() })
        assertFalse(repo.record(now + 300000) { sample() })
        assertEquals(now + 300000, repo.export(s.id)!!.session.endTime)
        assertEquals(300000L, repo.export(s.id)!!.session.actualDuration)
    }
    @Test fun processRecreationUsesSavedDeadline() = runBlocking {
        val s = repo.start(5, now)
        val restored = DiagnosticRepository(db)
        assertTrue(restored.record(now + 1000) { sample() })
        restored.expire(now + 900000)
        assertEquals(now + 300000, restored.export(s.id)!!.session.endTime)
        assertFalse(restored.record(now + 900001) { sample() })
    }
    @Test fun independentSessionsAndQueuedNotificationAfterStop() = runBlocking {
        val first = repo.start(1, now)
        repo.stop(now + 100)
        val second = repo.start(1, now + 200)
        repo.record(now + 50) { sample("old") }
        repo.record(now + 250) { sample("new") }
        assertEquals("old", repo.export(first.id)!!.notifications.single().packageName)
        assertEquals("new", repo.export(second.id)!!.notifications.single().packageName)
    }
    @Test fun doubleStartDoesNotOverlap() = runBlocking {
        val first = repo.start(5, now)
        assertEquals(first.id, repo.start(10, now + 1).id)
    }
    @Test fun retentionAndCascadeDelete() = runBlocking {
        val ids = (0..10).map { index ->
            val start = now + index * 100000L
            val s = repo.start(1, start)
            repo.record(start + 1) { sample() }
            repo.stop(start + 2)
            s.id
        }
        assertEquals(10, repo.sessions.first().size)
        assertNull(repo.export(ids.first()))
        assertTrue(repo.logs(ids.first()).first().isEmpty())
    }
    @Test fun clearStopsAndPendingCaptureCannotRecreateDeletedSessions() = runBlocking {
        val s = repo.start(5, now)
        repo.record(now + 1) { sample() }
        repo.clear()
        assertFalse(repo.record(now + 2) { sample() })
        assertTrue(repo.sessions.first().isEmpty())
        assertTrue(repo.logs(s.id).first().isEmpty())
        assertNull(repo.active.first())
    }
    @Test fun capacityStopsExplicitly() = runBlocking {
        val s = repo.start(5, now)
        db.diagnosticDao().update(s.copy(storedCharacters = DiagnosticRepository.MAX_CHARACTERS - 1))
        repo.record(now + 1) { sample() }
        assertEquals("已达到诊断容量上限", repo.export(s.id)!!.session.stopReason)
        assertFalse(repo.record(now + 3) { sample() })
    }
    @Test fun readerPreservesEmptyTitleAndTextAndBigTextAndSummary() = runBlocking {
        val s = repo.start(5, now)
        val n = Notification().apply { extras = Bundle().apply {
            putCharSequence(Notification.EXTRA_BIG_TEXT, "长文本金额 26.80")
            putCharSequence(Notification.EXTRA_SUMMARY_TEXT, "摘要")
            putCharSequence(Notification.EXTRA_SUB_TEXT, "副标题")
            putCharSequenceArray(Notification.EXTRA_TEXT_LINES, arrayOf("行一", "行二"))
            putString("custom.description", "自定义文本")
        }; flags = Notification.FLAG_GROUP_SUMMARY }
        val sbn = StatusBarNotification("com.xiaomi.wallet", "com.xiaomi.wallet", 7, "tag", 0, 0, 0, n, UserHandle.getUserHandleForUid(0), now)
        repo.record(now + 1) { DiagnosticNotificationReader.read(context, sbn) }
        val log = repo.export(s.id)!!.notifications.single()
        assertEquals("", log.title)
        assertEquals("", log.text)
        assertEquals("长文本金额 26.80", log.bigText)
        assertEquals("摘要", log.summaryText)
        assertTrue(log.extraText.contains("自定义文本"))
        assertTrue(log.extraText.contains("行二"))
        assertTrue(log.isGroupSummary)
    }
    @Test fun completelyEmptyNotificationIsNotDropped() = runBlocking {
        val s = repo.start(5, now)
        repo.record(now + 1) { NotificationSnapshot(now, "system") }
        assertEquals(1, repo.export(s.id)!!.notifications.size)
    }
    @Test fun exportAndClipboardPreserveSessionAndFields() = runBlocking {
        val s = repo.start(5, now)
        repo.record(now + 1) { sample().copy(title = "标题", bigText = "原始大文本", summaryText = "摘要") }
        repo.stop(now + 10)
        val output = DiagnosticText.session(repo.export(s.id)!!)
        assertTrue(output.contains(s.id))
        assertTrue(output.contains("原始大文本"))
        assertTrue(output.contains("ordinary.app"))
        assertTrue(output.contains("End:"))
        DiagnosticClipboard.copy(context, output)
        assertEquals(output, context.getSystemService(ClipboardManager::class.java).primaryClip!!.getItemAt(0).text.toString())
    }
    @Test fun largeExportIsLosslessAndDoesNotSplitSurrogates() {
        val text = "通知😀完整".repeat(50000)
        val parts = DiagnosticText.clipboardParts(text)
        assertTrue(parts.size > 1)
        assertEquals(text, parts.joinToString(""))
        assertTrue(parts.all { it.length <= 120000 && !it.last().isHighSurrogate() })
    }
    @Test fun backgroundDeadlineWithoutActivityOrNewNotification() = runBlocking {
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
        try {
            val start = System.currentTimeMillis()
            db.diagnosticDao().insert(DiagnosticSessionEntity("timer", start, start + 100, 100))
            DiagnosticManager(repo, scope)
            val result = withTimeout(5000) { repo.sessions.first { it.single().session.endTime != null } }
            assertEquals(start + 100, result.single().session.endTime)
        } finally { scope.cancel() }
    }
}
