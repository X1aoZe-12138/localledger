package cn.localledger.asset

import androidx.room.*
import cn.localledger.data.*
import kotlinx.coroutines.flow.Flow

val accountTypes = linkedMapOf("WECHAT" to "微信", "ALIPAY" to "支付宝", "BANK" to "银行卡", "CASH" to "现金", "OTHER" to "其他")
const val MAX_BALANCE = 99_999_999_999L
const val MAX_TIME = 7_258_118_400_000L

@Entity(tableName = "asset_accounts")
data class AssetAccount(@PrimaryKey val id: String, val name: String, val accountType: String,
    val currentBalanceCents: Long, val isIncludedInTotal: Boolean = true, val isArchived: Boolean = false,
    val displayOrder: Int = 0, val note: String = "", val createdAt: Long, val updatedAt: Long)

@Entity(tableName = "asset_snapshots", indices = [Index("timestamp")])
data class AssetSnapshot(@PrimaryKey val id: String, val timestamp: Long, val totalBalanceCents: Long,
    val note: String = "", val createdAt: Long)

// No account foreign key: historical names and balances must remain readable independently.
@Entity(tableName = "asset_snapshot_items", foreignKeys = [ForeignKey(entity = AssetSnapshot::class,
    parentColumns = ["id"], childColumns = ["snapshotId"], onDelete = ForeignKey.CASCADE)],
    indices = [Index("snapshotId"), Index(value = ["snapshotId", "accountId"], unique = true)])
data class AssetSnapshotItem(@PrimaryKey val id: String, val snapshotId: String, val accountId: String,
    val accountNameSnapshot: String, val balanceCents: Long, val includedInTotal: Boolean)

@Dao
interface AssetDao {
    @Query("SELECT * FROM asset_accounts ORDER BY displayOrder, id") fun observeAccounts(): Flow<List<AssetAccount>>
    @Query("SELECT * FROM asset_snapshots ORDER BY timestamp DESC, createdAt DESC, id DESC") fun observeSnapshots(): Flow<List<AssetSnapshot>>
    @Query("SELECT * FROM asset_snapshot_items") fun observeItems(): Flow<List<AssetSnapshotItem>>
    @Query("SELECT * FROM asset_accounts ORDER BY displayOrder, id") suspend fun accounts(): List<AssetAccount>
    @Query("SELECT * FROM asset_snapshots ORDER BY timestamp DESC, createdAt DESC, id DESC") suspend fun snapshots(): List<AssetSnapshot>
    @Query("SELECT * FROM asset_snapshot_items") suspend fun items(): List<AssetSnapshotItem>
    @Insert(onConflict = OnConflictStrategy.ABORT) suspend fun insertAccount(value: AssetAccount)
    @Update suspend fun updateAccount(value: AssetAccount)
    @Query("DELETE FROM asset_accounts WHERE id = :id") suspend fun deleteAccount(id: String)
    @Insert(onConflict = OnConflictStrategy.ABORT) suspend fun insertSnapshot(value: AssetSnapshot)
    @Update suspend fun updateSnapshot(value: AssetSnapshot)
    @Insert(onConflict = OnConflictStrategy.ABORT) suspend fun insertItem(value: AssetSnapshotItem)
    @Update suspend fun updateItem(value: AssetSnapshotItem)
    @Query("DELETE FROM asset_snapshot_items") suspend fun clearItems()
    @Query("DELETE FROM asset_snapshots") suspend fun clearSnapshots()
    @Query("DELETE FROM asset_accounts") suspend fun clearAccounts()
}

fun totalLiquidAssets(accounts: List<AssetAccount>): Long = accounts.filter { it.isIncludedInTotal && !it.isArchived }.sumOf { it.currentBalanceCents }

data class AssetComparison(val actualChange: Long, val income: Long, val expense: Long,
    val refunds: Long, val reimbursements: Long, val skippedAdjustments: Int) {
    val recordedNetChange get() = income - expense + refunds + reimbursements
    val difference get() = actualChange - recordedNetChange
}

// Open start / closed end prevents transactions at a snapshot boundary being counted twice.
fun compareAssets(previous: AssetSnapshot, current: AssetSnapshot, entries: List<LedgerEntry>): AssetComparison {
    require(current.timestamp >= previous.timestamp)
    val interval = entries.filter { it.timestamp > previous.timestamp && it.timestamp <= current.timestamp }
    return AssetComparison(current.totalBalanceCents - previous.totalBalanceCents,
        trueIncome(interval), interval.filter { it.transactionType == "expense" }.sumOf { it.personalExpenseCents },
        interval.filter { it.transactionType == "refund" }.sumOf { it.amount },
        interval.filter { it.transactionType == "reimbursement" }.sumOf { it.amount },
        interval.count { it.transactionType == "transfer_adjustment" })
}
