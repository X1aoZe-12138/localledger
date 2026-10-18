package cn.localledger.ui

import android.app.Application
import android.net.Uri
import androidx.lifecycle.*
import cn.localledger.LedgerApplication
import cn.localledger.report.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import java.time.LocalDate

class ReportViewModel(application: Application) : AndroidViewModel(application) {
    private val app = application as LedgerApplication
    private val repo = app.reportRepository
    val lastReport = repo.lastReport.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)
    val busy = MutableStateFlow(false)
    val message = MutableStateFlow<String?>(null)
    val preview = MutableStateFlow<PreparedReport?>(null)
    val shareUri = MutableStateFlow<Uri?>(null)
    private var pendingFile: PreparedReport? = null
    fun prepare(type: String, choice: String, start: LocalDate, end: LocalDate, options: ReportOptions, name: String, cents: Long?) = operation {
        preview.value = repo.prepare(type, choice, start, end, options, name, cents)
    }
    fun invalidate() { preview.value = null }
    fun selectFile(): String? { val report = preview.value ?: return null; pendingFile = report; return report.fileName }
    fun save(uri: Uri?) {
        val report = pendingFile; pendingFile = null
        if(uri == null || report == null) return
        operation { shareUri.value = null; repo.save(app.contentResolver, uri, report); shareUri.value = uri
            preview.value = null; message.value = "报告已保存，可以分享到电脑后手动上传给AI" }
    }
    private fun operation(action: suspend () -> Unit) {
        if(busy.value) return
        busy.value = true
        viewModelScope.launch {
            try { withContext(Dispatchers.IO) { action() } }
            catch(e: CancellationException) { throw e }
            catch(e: IllegalArgumentException) { message.value = e.message ?: "报告范围或购买金额不合法" }
            catch(_: Exception) { message.value = "报告未完成：请检查文件权限及可用空间，重新保存；上次报告时间不会因失败而更新。" }
            finally { busy.value = false }
        }
    }
}
