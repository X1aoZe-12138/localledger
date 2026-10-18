package cn.localledger.data

import androidx.room.withTransaction
import java.util.UUID

class FinancialRepository(private val db: LedgerDatabase) {
    val categories = db.financialDao().observeCategories()
    suspend fun initialize() = db.withTransaction { ensureIncomeCategories(db) }
    suspend fun saveCategory(name: String, id: String? = null) = db.withTransaction {
        ensureIncomeCategories(db)
        val clean = name.trim()
        require(clean.isNotBlank() && clean.length <= 100)
        val existing = db.financialDao().categories()
        if(id == null) db.financialDao().insertCategory(IncomeCategoryEntity(UUID.randomUUID().toString(), clean, (existing.maxOfOrNull { it.sortOrder } ?: -1) + 1))
        else db.financialDao().updateCategory(existing.first { it.id == id }.copy(name = clean))
    }
    suspend fun deleteCategory(id: String) = db.withTransaction {
        require(id != OTHER_INCOME_ID) { "其他收入作为恢复兜底类别需要保留" }
        db.financialDao().remapCategory(id, OTHER_INCOME_ID)
        db.financialDao().deleteCategory(id)
    }
    suspend fun moveCategory(id: String, delta: Int) = db.withTransaction {
        val existing = db.financialDao().categories().toMutableList()
        val from = existing.indexOfFirst { it.id == id }
        val to = from + delta
        if(from >= 0 && to in existing.indices) {
            val item = existing.removeAt(from); existing.add(to, item)
            existing.forEachIndexed { index, c -> db.financialDao().updateCategory(c.copy(sortOrder = index)) }
        }
    }
}
