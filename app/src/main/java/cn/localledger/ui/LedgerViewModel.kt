package cn.localledger.ui

import android.app.Application
import androidx.lifecycle.*
import cn.localledger.LedgerApplication
import cn.localledger.data.LedgerEntry
import cn.localledger.notification.RecordedReminderManager
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch

class LedgerViewModel(application: Application) : AndroidViewModel(application) {
    private val repo = (application as LedgerApplication).repository
    private val reminders = RecordedReminderManager(application, repo)
    val entries = repo.entries.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())
    val unknown = repo.unknown.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())
    val error = MutableStateFlow<String?>(null)
    val busy = MutableStateFlow(false)
    suspend fun get(id: Long) = repo.get(id)
    init { viewModelScope.launch { runCatching { reminders.reconcile() } } }
    fun save(entry: LedgerEntry, done: () -> Unit) = perform(done) {
        val id = repo.save(entry)
        if(entry.id > 0) reminders.cancel(id)
    }
    fun delete(id: Long, done: () -> Unit) = perform(done) {
        repo.delete(id)
        reminders.cancel(id)
    }
    fun clearLogs(done: () -> Unit) = perform(done) { repo.clearUnknown() }
    private fun perform(done: () -> Unit, action: suspend () -> Unit) {
        if (busy.value) return
        busy.value = true
        viewModelScope.launch {
            try { action(); error.value = null; done() }
            catch (_: Exception) { error.value = "保存失败，请检查存储空间后重试" }
            finally { busy.value = false }
        }
    }
}
