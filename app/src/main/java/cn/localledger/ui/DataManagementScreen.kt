package cn.localledger.ui

import android.content.*
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import cn.localledger.ui.design.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import cn.localledger.data.*
import java.time.*
import java.time.format.DateTimeFormatter

@Composable
fun DataManagementScreen(vm: DataManagementViewModel) {
    val busy by vm.busy.collectAsStateWithLifecycle()
    val pending by vm.pending.collectAsStateWithLifecycle()
    val lastBackup by vm.lastBackup.collectAsStateWithLifecycle()
    val share by vm.shareUri.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val create = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/json")) { uri -> uri?.let { vm.write(it) } }
    val restore = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri -> uri?.let { vm.inspect(it) } }
    val csv = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("text/csv")) { uri -> uri?.let { vm.write(it, csv = true) } }
    val stamp = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd_HHmm"))
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(AppDimensions.Page), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text("备份与恢复", style = MaterialTheme.typography.titleMedium)
        Text("备份文件包含个人财务信息，请妥善保管。JSON可复制到电脑长期保存；不包含通知诊断和聊天原文。")
        Button(enabled = !busy, onClick = { create.launch("Bookkeeping_Backup_$stamp.json") }) { Text("备份全部数据") }
        if(share != null) OutlinedButton(enabled = !busy, onClick = {
            val uri = share!!
            val intent = Intent(Intent.ACTION_SEND).setType("application/json").putExtra(Intent.EXTRA_STREAM, uri).apply {
                clipData = ClipData.newUri(context.contentResolver, "账本备份", uri)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            runCatching { context.startActivity(Intent.createChooser(intent, "分享备份")) }.onFailure { vm.message.value = "无法分享，请从保存位置的文件管理器分享备份" }
        }) { Text("分享备份") }
        OutlinedButton(enabled = !busy, onClick = { restore.launch(arrayOf("application/json", "application/octet-stream", "text/plain", "*/*")) }) { Text("恢复备份", color = MaterialTheme.colorScheme.error) }
        Text("恢复前完整校验。恢复只支持替换全部账本，不合并；建议先备份当前数据。", style = MaterialTheme.typography.bodySmall)
        HorizontalDivider()
        Text("数据导出", style = MaterialTheme.typography.titleMedium)
        OutlinedButton(enabled = !busy, onClick = { csv.launch("Bookkeeping_Transactions_$stamp.csv") }) { Text("导出交易 CSV") }
        Text("CSV用于查看，不是完整备份。原始金额和个人承担均独立导出。")
        HorizontalDivider()
        Text("上次备份：" + (lastBackup?.toLongOrNull()?.let { Instant.ofEpochMilli(it).atZone(ZoneId.systemDefault()).format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm")) } ?: "尚未备份"))
        if(lastBackup?.toLongOrNull()?.let { System.currentTimeMillis() - it > 30L * 24 * 60 * 60 * 1000 } != false) Text("建议备份并保存到电脑", color = MaterialTheme.colorScheme.primary)
        if(busy) { CircularProgressIndicator(); Text("处理中，请稍候…") }
    }
    pending?.let { model -> AlertDialog(onDismissRequest = { if(!busy) vm.pending.value = null }, title = { Text("确认替换当前全部账本？") },
        text = { Text("备份已通过校验：${model.transactions.size}条交易、${model.merchantPreferences.size}条商户偏好、${model.assetAccounts.size}个资产账户、${model.assetSnapshots.size}次盘点。\n\n恢复将覆盖当前账本及全部资产数据。旧V1备份的资产为空，恢复会清空当前资产。建议先备份当前数据。\n诊断日志不在备份中，也不会被恢复覆盖。") },
        confirmButton = { TextButton(enabled = !busy, onClick = { vm.restore() }) { Text("继续恢复", color = MaterialTheme.colorScheme.error) } },
        dismissButton = { TextButton(enabled = !busy, onClick = { vm.pending.value = null }) { Text("取消") } }) }
}

@Composable
fun IncomeCategoriesScreen(vm: DataManagementViewModel) {
    val categories by vm.categories.collectAsStateWithLifecycle()
    val busy by vm.busy.collectAsStateWithLifecycle()
    var editingId by rememberSaveable { mutableStateOf<String?>(null) }
    val editing = categories.firstOrNull { it.id == editingId }
    var adding by androidx.compose.runtime.saveable.rememberSaveable { mutableStateOf(false) }
    var deleting by remember { mutableStateOf<IncomeCategoryEntity?>(null) }
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text("收入使用独立一级类别；退款、报销和AA回款不属于真正收入。")
        Button(enabled = !busy, onClick = { adding = true }) { Text("新增收入类别") }
        categories.forEachIndexed { index, c -> Column(Modifier.fillMaxWidth().padding(vertical = AppDimensions.Small)) {
            Text(c.name, style = MaterialTheme.typography.titleMedium)
            Row {
                TextButton(enabled = !busy, onClick = { editingId = c.id }) { Text("编辑") }
                TextButton(enabled = !busy && index > 0, onClick = { vm.moveCategory(c.id, -1) }) { Text("上移") }
                TextButton(enabled = !busy && index < categories.lastIndex, onClick = { vm.moveCategory(c.id, 1) }) { Text("下移") }
                TextButton(enabled = !busy && c.id != OTHER_INCOME_ID, onClick = { deleting = c }) { Text("删除", color = MaterialTheme.colorScheme.error) }
            }
        }
        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = .4f))
        }
        Text("其他收入是恢复兜底类别，不能删除；可改名和排序。删除类别会把对应收入归到此类别，不删除账单。", style = MaterialTheme.typography.bodySmall)
    }
    if(adding || editing != null) {
        var name by rememberSaveable(editing?.id) { mutableStateOf(editing?.name.orEmpty()) }
        AlertDialog(onDismissRequest = { adding = false; editingId = null }, title = { Text("收入类别") }, text = { OutlinedTextField(name, { name = it.take(100) }, label = { Text("名称") }) },
            confirmButton = { TextButton(enabled = !busy && name.isNotBlank(), onClick = { vm.saveCategory(name, editing?.id); adding = false; editingId = null }) { Text("保存") } },
            dismissButton = { TextButton(onClick = { adding = false; editingId = null }) { Text("取消") } })
    }
    deleting?.let { c -> AlertDialog(onDismissRequest = { deleting = null }, title = { Text("删除${c.name}？") }, text = { Text("账单保留，收入类别改为其他收入。") },
        confirmButton = { TextButton(enabled = !busy, onClick = { vm.deleteCategory(c.id); deleting = null }) { Text("删除", color = MaterialTheme.colorScheme.error) } },
        dismissButton = { TextButton(onClick = { deleting = null }) { Text("取消") } }) }
}
