package cn.localledger.data

import androidx.room.*
import kotlinx.coroutines.flow.Flow
import cn.localledger.diagnostic.*
import cn.localledger.merchant.*
import cn.localledger.asset.*

@Entity(tableName = "transactions", indices = [Index("timestamp")])
data class LedgerEntry(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val timestamp: Long = System.currentTimeMillis(), val amount: Long,
    val transactionType: String = "expense", val category: String = "待分类",
    val merchant: String = "未知商户", val platform: String = "Manual",
    val note: String = "", val createdAt: Long = System.currentTimeMillis(),
    @ColumnInfo(defaultValue = "0") val splitEnabled: Boolean = false,
    @ColumnInfo(defaultValue = "3") val participantCount: Int = 3,
    @ColumnInfo(defaultValue = "'NONE'") val splitCalculationMode: String = "NONE",
    val manualSelfAmountCents: Long? = null,
    val receivedAmountCents: Long? = null,
    val incomeCategoryId: String? = null,
    val legacyCategory: String? = null,
    val legacyMerchant: String? = null
)
@Entity(tableName = "receipts", indices = [Index("timestamp")])
data class Receipt(@PrimaryKey val token: String, val key: String, val timestamp: Long,
    val digest: String, val platform: String, val amount: Long, val type: String, val merchant: String)
@Entity(tableName = "unrecognized")
data class UnknownNotification(@PrimaryKey(autoGenerate = true) val id: Long = 0,
    val timestamp: Long, val packageName: String, val title: String, val text: String,
    val bigText: String, val subText: String, val extraText: String, val reason: String)

@Dao
interface LedgerDao {
    @Query("SELECT * FROM transactions ORDER BY timestamp DESC, id DESC") fun observeEntries(): Flow<List<LedgerEntry>>
    @Query("SELECT * FROM transactions") suspend fun allEntries(): List<LedgerEntry>
    @Query("SELECT * FROM transactions WHERE id = :id") suspend fun get(id: Long): LedgerEntry?
    @Insert suspend fun insert(entry: LedgerEntry): Long
    @Update suspend fun update(entry: LedgerEntry)
    @Query("DELETE FROM transactions WHERE id = :id") suspend fun delete(id: Long)
    @Query("DELETE FROM transactions") suspend fun clearEntries()
    @Query("SELECT * FROM receipts") suspend fun allReceipts(): List<Receipt>
    @Query("DELETE FROM receipts") suspend fun clearReceipts()
    @Query("SELECT * FROM receipts WHERE `key` = :key AND timestamp BETWEEN :start AND :end")
    suspend fun receipts(key: String, start: Long, end: Long): List<Receipt>
    @Insert(onConflict = OnConflictStrategy.IGNORE) suspend fun receipt(receipt: Receipt): Long
    @Query("SELECT * FROM unrecognized ORDER BY id DESC") fun observeUnknown(): Flow<List<UnknownNotification>>
    @Insert suspend fun unknown(log: UnknownNotification)
    @Query("DELETE FROM unrecognized WHERE id NOT IN (SELECT id FROM unrecognized ORDER BY id DESC LIMIT 300)") suspend fun trimUnknown()
    @Query("DELETE FROM unrecognized") suspend fun clearUnknown()
}
@Database(entities = [LedgerEntry::class, Receipt::class, UnknownNotification::class, DiagnosticSessionEntity::class, NotificationLogEntity::class, MerchantPreferenceEntity::class, IncomeCategoryEntity::class, LedgerSetting::class, AssetAccount::class, AssetSnapshot::class, AssetSnapshotItem::class], version = 6, exportSchema = true)
abstract class LedgerDatabase : RoomDatabase() {
    abstract fun dao(): LedgerDao
    abstract fun diagnosticDao(): DiagnosticDao
    abstract fun merchantDao(): MerchantDao
    abstract fun financialDao(): FinancialDao
    abstract fun assetDao(): AssetDao
}
