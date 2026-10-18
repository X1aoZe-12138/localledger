package cn.localledger.diagnostic

import androidx.room.*
import kotlinx.coroutines.flow.Flow

@Entity(tableName = "diagnostic_sessions")
data class DiagnosticSessionEntity(
    @PrimaryKey val id: String,
    val startTime: Long,
    val expectedEndTime: Long,
    val plannedDuration: Long,
    val endTime: Long? = null,
    val actualDuration: Long? = null,
    val stopReason: String = "",
    val storedCharacters: Long = 0
)

@Entity(tableName = "diagnostic_notifications", foreignKeys = [ForeignKey(
    entity = DiagnosticSessionEntity::class, parentColumns = ["id"], childColumns = ["sessionId"], onDelete = ForeignKey.CASCADE
)], indices = [Index("sessionId")])
data class NotificationLogEntity(
    @PrimaryKey val id: String,
    val sessionId: String,
    val timestamp: Long,
    val receivedAt: Long,
    val packageName: String,
    val appName: String,
    val title: String,
    val text: String,
    val bigText: String,
    val subText: String,
    val summaryText: String,
    val extraText: String,
    val notificationKey: String,
    val isGroupSummary: Boolean,
    val captureNote: String
)

data class DiagnosticSessionSummary(@Embedded val session: DiagnosticSessionEntity, val notificationCount: Int)
data class DiagnosticExport(val session: DiagnosticSessionEntity, val notifications: List<NotificationLogEntity>)

@Dao
interface DiagnosticDao {
    @Query("SELECT s.*, (SELECT COUNT(*) FROM diagnostic_notifications n WHERE n.sessionId = s.id) AS notificationCount FROM diagnostic_sessions s ORDER BY startTime DESC, rowid DESC")
    fun sessions(): Flow<List<DiagnosticSessionSummary>>
    @Query("SELECT * FROM diagnostic_sessions WHERE endTime IS NULL LIMIT 1") fun active(): Flow<DiagnosticSessionEntity?>
    @Query("SELECT * FROM diagnostic_sessions WHERE endTime IS NULL LIMIT 1") suspend fun activeNow(): DiagnosticSessionEntity?
    @Query("SELECT * FROM diagnostic_sessions WHERE id = :id") suspend fun session(id: String): DiagnosticSessionEntity?
    @Query("SELECT * FROM diagnostic_sessions WHERE startTime <= :receivedAt AND :receivedAt < COALESCE(endTime, expectedEndTime) ORDER BY startTime DESC, rowid DESC LIMIT 1")
    suspend fun sessionAt(receivedAt: Long): DiagnosticSessionEntity?
    @Insert suspend fun insert(session: DiagnosticSessionEntity)
    @Update suspend fun update(session: DiagnosticSessionEntity)
    @Insert suspend fun insert(log: NotificationLogEntity)
    @Query("SELECT COUNT(*) FROM diagnostic_notifications WHERE sessionId = :id") suspend fun count(id: String): Int
    @Query("SELECT * FROM diagnostic_notifications WHERE sessionId = :id ORDER BY receivedAt, rowid") fun logs(id: String): Flow<List<NotificationLogEntity>>
    @Query("SELECT * FROM diagnostic_notifications WHERE sessionId = :id ORDER BY receivedAt, rowid") suspend fun exportLogs(id: String): List<NotificationLogEntity>
    @Query("DELETE FROM diagnostic_sessions WHERE id NOT IN (SELECT id FROM diagnostic_sessions ORDER BY startTime DESC, rowid DESC LIMIT 10)") suspend fun retainTen()
    @Query("DELETE FROM diagnostic_sessions") suspend fun clear()
}
