package cn.localledger.data

import androidx.room.*
import kotlinx.coroutines.flow.Flow

const val OTHER_INCOME_ID = "other_income"
val flowTypes = linkedMapOf("expense" to "支出", "income" to "收入", "refund" to "退款", "reimbursement" to "报销", "transfer_adjustment" to "AA回款/资金调整")
val defaultIncomeCategories = listOf("salary" to "工资", "bonus" to "奖金", "side_income" to "副业", "gift" to "红包/赠与", OTHER_INCOME_ID to "其他收入")

@Entity(tableName = "income_categories", indices = [Index(value = ["name"], unique = true)])
data class IncomeCategoryEntity(@PrimaryKey val id: String, val name: String, val sortOrder: Int)

@Entity(tableName = "ledger_settings")
data class LedgerSetting(@PrimaryKey val key: String, val value: String)

@Dao
interface FinancialDao {
    @Query("SELECT * FROM income_categories ORDER BY sortOrder, id") fun observeCategories(): Flow<List<IncomeCategoryEntity>>
    @Query("SELECT * FROM income_categories ORDER BY sortOrder, id") suspend fun categories(): List<IncomeCategoryEntity>
    @Insert(onConflict = OnConflictStrategy.ABORT) suspend fun insertCategory(value: IncomeCategoryEntity)
    @Update suspend fun updateCategory(value: IncomeCategoryEntity)
    @Query("DELETE FROM income_categories WHERE id = :id") suspend fun deleteCategory(id: String)
    @Query("UPDATE transactions SET incomeCategoryId = :replacement WHERE incomeCategoryId = :id") suspend fun remapCategory(id: String, replacement: String)
    @Query("SELECT * FROM ledger_settings") suspend fun settings(): List<LedgerSetting>
    @Query("SELECT value FROM ledger_settings WHERE `key` = :key") fun setting(key: String): Flow<String?>
    @Insert(onConflict = OnConflictStrategy.REPLACE) suspend fun putSetting(value: LedgerSetting)
    @Query("DELETE FROM ledger_settings") suspend fun clearSettings()
    @Query("DELETE FROM income_categories") suspend fun clearCategories()
}

fun trueIncome(entries: List<LedgerEntry>): Long = entries.filter { it.transactionType == "income" }.sumOf { it.amount }

// Called inside the caller's Room transaction. Empty new installations get defaults once.
suspend fun ensureIncomeCategories(db: LedgerDatabase) {
    if(db.financialDao().categories().isEmpty()) defaultIncomeCategories.forEachIndexed { index, (id, name) ->
        db.financialDao().insertCategory(IncomeCategoryEntity(id, name, index))
    }
}
