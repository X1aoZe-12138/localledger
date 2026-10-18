package cn.localledger.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import cn.localledger.LedgerApplication
import cn.localledger.diagnostic.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*

class DiagnosticViewModel(application: Application) : AndroidViewModel(application) {
    private val manager = (application as LedgerApplication).diagnosticManager
    val active = manager.active
    val captureError = manager.error
    val sessions = manager.repository.sessions.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())
    val now = MutableStateFlow(System.currentTimeMillis())
    val busy = MutableStateFlow(false)
    val message = MutableStateFlow<String?>(null)
    val exportParts = MutableStateFlow<List<String>>(emptyList())
    fun logs(id: String) = manager.repository.logs(id)
    init {
        viewModelScope.launch {
            while(isActive) {
                now.value = System.currentTimeMillis()
                try { manager.refresh() } catch (e: CancellationException) { throw e }
                catch (_: Exception) { message.value = "无法读取诊断状态，请检查存储空间" }
                delay(1000)
            }
        }
    }
    fun start(minutes: Int) = run { manager.start(minutes); manager.error.value = null }
    fun stop() = run { manager.stop() }
    fun clear() = run { manager.repository.clear(); exportParts.value = emptyList(); manager.error.value = null }
    fun export(id: String) = run {
        manager.refresh()
        val data = manager.repository.export(id)
        if(data == null) message.value = "会话已被清空或自动清理"
        else exportParts.value = withContext(Dispatchers.Default) { DiagnosticText.clipboardParts(DiagnosticText.session(data)) }
    }
    private fun run(action: suspend () -> Unit) {
        if(busy.value) return
        busy.value = true
        viewModelScope.launch {
            try { action() }
            catch(e: CancellationException) { throw e }
            catch (_: Exception) { message.value = "诊断操作失败，请检查存储空间后重试" }
            finally { busy.value = false }
        }
    }
}
