package cn.localledger.merchant

import androidx.room.withTransaction
import cn.localledger.data.LedgerDatabase
import cn.localledger.parser.*
import kotlinx.coroutines.flow.combine

class MerchantRepository(private val db: LedgerDatabase) {
    private val merchantDao = db.merchantDao()
    private val ledgerDao = db.dao()
    val memories = combine(ledgerDao.observeEntries(), merchantDao.observePreferences(), MerchantMemoryEngine::build)

    suspend fun dominantCategory(merchant: String) = MerchantMemoryEngine.dominantCategory(ledgerDao.allEntries(), merchant)

    suspend fun saveManual(name: String, category: String, pinned: Boolean, old: MerchantMemory? = null) = db.withTransaction {
        val display = name.trim()
        require(isLearnableMerchant(display) && category in learnableCategories)
        val normalized = normalizeMerchantName(display)
        if (old != null && (old.normalizedName != normalized || old.category != category)) suppressInternal(old)
        val current = merchantDao.get(normalized, category)
        merchantDao.put(MerchantPreferenceEntity(normalized, category, display, pinned, true, false,
            current?.createdAt ?: System.currentTimeMillis(), System.currentTimeMillis()))
    }

    suspend fun setPinned(memory: MerchantMemory, pinned: Boolean) = db.withTransaction {
        val current = merchantDao.get(memory.normalizedName, memory.category)
        merchantDao.put(MerchantPreferenceEntity(memory.normalizedName, memory.category, memory.name, pinned,
            current?.isManual == true, false, current?.createdAt ?: System.currentTimeMillis(), System.currentTimeMillis()))
    }

    suspend fun suppress(memory: MerchantMemory) = db.withTransaction { suppressInternal(memory) }

    private suspend fun suppressInternal(memory: MerchantMemory) {
        val current = merchantDao.get(memory.normalizedName, memory.category)
        merchantDao.put(MerchantPreferenceEntity(memory.normalizedName, memory.category, memory.name, false,
            false, true, current?.createdAt ?: System.currentTimeMillis(), System.currentTimeMillis()))
    }
}
