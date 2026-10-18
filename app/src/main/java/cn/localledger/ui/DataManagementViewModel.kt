package cn.localledger.ui

import android.app.Application
import android.net.Uri
import androidx.lifecycle.*
import cn.localledger.LedgerApplication
import cn.localledger.backup.*
import cn.localledger.data.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import java.io.ByteArrayOutputStream
import java.nio.ByteBuffer
import java.nio.charset.CodingErrorAction

class DataManagementViewModel(application: Application) : AndroidViewModel(application) {
    private val app = application as LedgerApplication
    private val backup = app.backupRepository
    private val financial = app.financialRepository
    val categories = financial.categories.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())
    val lastBackup = backup.lastBackup.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)
    val busy = MutableStateFlow(false)
    val message = MutableStateFlow<String?>(null)
    val pending = MutableStateFlow<BackupModelV1?>(null)
    val shareUri = MutableStateFlow<Uri?>(null)
    init { operation { financial.initialize() } }

    fun write(uri: Uri, csv: Boolean = false) = operation {
        if(!csv) shareUri.value = null
        val content = if(csv) backup.csv() else backup.export()
        val resolver = app.contentResolver
        resolver.openOutputStream(uri, "wt")?.use { out -> out.write(content.toByteArray(Charsets.UTF_8)); out.flush() }
            ?: error("无法写入文件")
        if(!csv) {
            require(readDocument(uri) == content) { "保存位置返回的文件内容不完整" }
            BackupImporter.decode(content)
            backup.recordBackupTime(System.currentTimeMillis())
            shareUri.value = uri
        }
        message.value = if(csv) "CSV导出完成（CSV不是完整备份）" else "备份完成，建议分享并保存到电脑"
    }
    fun inspect(uri: Uri) = operation {
        pending.value = null
        try { pending.value = BackupImporter.decode(readDocument(uri)) }
        catch(e: InvalidBackup) { throw e }
        catch(_: Exception) { throw InvalidBackup("备份文件损坏、无法读取或格式不受支持") }
    }
    private fun readDocument(uri: Uri): String {
        val bytes = app.contentResolver.openInputStream(uri)?.use { input ->
            val output = ByteArrayOutputStream()
            val buffer = ByteArray(8192)
            var total = 0
            while(true) {
                val n = input.read(buffer); if(n < 0) break
                total += n; if(total > BackupImporter.MAX_BYTES) throw InvalidBackup("备份超过32MiB，无法恢复")
                output.write(buffer, 0, n)
            }
            output.toByteArray()
        } ?: throw InvalidBackup("备份文件无法读取")
        return Charsets.UTF_8.newDecoder().onMalformedInput(CodingErrorAction.REPORT).onUnmappableCharacter(CodingErrorAction.REPORT).decode(ByteBuffer.wrap(bytes)).toString()
    }
    fun restore() {
        val model = pending.value ?: return
        operation {
            val summary = backup.restore(model)
            pending.value = null
            message.value = "恢复成功\n交易记录：${summary.transactions}条（收入${summary.income}条、AA${summary.split}条）\n商户记录：${summary.merchants}条\n收入类别：${summary.categories}个\n资产账户：${summary.assetAccounts}个，盘点：${summary.assetSnapshots}次"
        }
    }
    fun saveCategory(name: String, id: String? = null) = operation { financial.saveCategory(name, id) }
    fun deleteCategory(id: String) = operation { financial.deleteCategory(id) }
    fun moveCategory(id: String, delta: Int) = operation { financial.moveCategory(id, delta) }
    private fun operation(action: suspend () -> Unit) {
        if(busy.value) return
        busy.value = true
        viewModelScope.launch {
            try { withContext(Dispatchers.IO) { action() } }
            catch(e: CancellationException) { throw e }
            catch(e: InvalidBackup) { message.value = e.message }
            catch(_: Exception) { message.value = "操作失败：请检查文件权限、格式、名称是否重复及剩余存储空间。当前账本未被部分恢复。" }
            finally { busy.value = false }
        }
    }
}
