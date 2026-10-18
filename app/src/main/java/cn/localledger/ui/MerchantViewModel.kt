package cn.localledger.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import cn.localledger.LedgerApplication
import cn.localledger.merchant.*
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch

class MerchantViewModel(application: Application) : AndroidViewModel(application) {
    private val repository = (application as LedgerApplication).merchantRepository
    val memories = repository.memories.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())
    val busy = MutableStateFlow(false)
    val message = MutableStateFlow<String?>(null)

    suspend fun dominantCategory(merchant: String) = repository.dominantCategory(merchant)
    fun save(name: String, category: String, pinned: Boolean, old: MerchantMemory? = null) = run {
        repository.saveManual(name, category, pinned, old)
    }
    fun pin(memory: MerchantMemory) = run { repository.setPinned(memory, !memory.isPinned) }
    fun delete(memory: MerchantMemory) = run { repository.suppress(memory) }

    private fun run(action: suspend () -> Unit) {
        if (busy.value) return
        busy.value = true
        viewModelScope.launch {
            try { action() }
            catch (e: CancellationException) { throw e }
            catch (_: Exception) { message.value = "商户记忆操作失败，请检查名称和存储空间" }
            finally { busy.value = false }
        }
    }
}
