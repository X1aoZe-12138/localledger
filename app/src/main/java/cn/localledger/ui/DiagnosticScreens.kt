package cn.localledger.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.lazy.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import cn.localledger.ui.design.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import cn.localledger.diagnostic.*

@Composable
fun DiagnosticControls(vm: DiagnosticViewModel, openHistory: () -> Unit) {
    val active by vm.active.collectAsStateWithLifecycle()
    val now by vm.now.collectAsStateWithLifecycle()
    val busy by vm.busy.collectAsStateWithLifecycle()
    val error by vm.captureError.collectAsStateWithLifecycle()
    var armed by rememberSaveable { mutableStateOf(false) }
    var minutes by rememberSaveable { mutableIntStateOf(5) }
    var clear by remember { mutableStateOf(false) }
    val running = active?.let { now < it.expectedEndTime } == true
    LaunchedEffect(active?.id) { if(active != null) armed = false }
    Text("通知诊断模式", style = MaterialTheme.typography.titleMedium)
    Text("开启后，在选定时间内额外记录所有 App 的系统通知（包括私人消息）。只保存在本机，不影响自动记账。请在分享前检查隐私内容。")
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        Text(if(running) "通知诊断进行中" else "准备通知诊断")
        Switch(checked = running || armed, enabled = !busy, onCheckedChange = {
            if(!it && running) vm.stop()
            armed = it && !running
        })
    }
    if(running) {
        val seconds = ((active!!.expectedEndTime - now).coerceAtLeast(0) + 999) / 1000
        Text("剩余 %02d:%02d".format(seconds / 60, seconds % 60), style = MaterialTheme.typography.headlineSmall)
        Text("开始：${DiagnosticText.time(active!!.startTime)}")
        Button(enabled = !busy, onClick = vm::stop) { Text("提前停止诊断") }
    } else {
        Text("诊断时长")
        Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            listOf(1, 5, 10, 15, 30).forEach { value -> FilterChip(selected = minutes == value,
                enabled = !busy, onClick = { minutes = value }, label = { Text("$value 分钟") }) }
        }
        Button(enabled = armed && !busy, onClick = { vm.start(minutes) }) { Text("开始诊断") }
        Text("打开开关并点击开始才会记录；时间到自动停止。退出页面和锁屏不影响截止时间。", style = MaterialTheme.typography.bodySmall)
    }
    error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
    Text("保留最近 10 次。单次最多 2000 条 / 约 200 万字符，达到上限会停止并标明原因。")
    OutlinedButton(onClick = openHistory) { Text("通知诊断日志") }
    TextButton(enabled = !busy, onClick = { clear = true }) { Text("清空诊断日志", color = MaterialTheme.colorScheme.error) }
    if(clear) ClearDiagnosticDialog({ clear = false }) { vm.clear(); armed = false; clear = false }
}

@Composable
private fun ClearDiagnosticDialog(dismiss: () -> Unit, confirm: () -> Unit) {
    AlertDialog(onDismissRequest = dismiss, title = { Text("清空全部诊断日志？") },
        text = { Text("同时停止当前诊断并删除所有诊断会话。已有账单和未识别支付日志保留。") },
        confirmButton = { TextButton(onClick = confirm) { Text("清空", color = MaterialTheme.colorScheme.error) } },
        dismissButton = { TextButton(onClick = dismiss) { Text("取消") } })
}

@Composable
fun DiagnosticHistory(vm: DiagnosticViewModel, open: (String) -> Unit) {
    val sessions by vm.sessions.collectAsStateWithLifecycle()
    val busy by vm.busy.collectAsStateWithLifecycle()
    var clear by remember { mutableStateOf(false) }
    Column(Modifier.padding(16.dp)) {
        TextButton(enabled = !busy && sessions.isNotEmpty(), onClick = { clear = true }) { Text("清空诊断日志", color = MaterialTheme.colorScheme.error) }
        LazyColumn(verticalArrangement = Arrangement.spacedBy(AppDimensions.Medium)) {
            if(sessions.isEmpty()) item { EmptyState("还没有通知诊断", "到通知诊断页面开启并开始诊断，再进行真实支付。") }
            items(sessions, key = { it.session.id }) { summary ->
                val s = summary.session
                Card(onClick = { open(s.id) }, modifier = Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(AppDimensions.Small)) {
                        Text(DiagnosticText.time(s.startTime), style = MaterialTheme.typography.titleMedium)
                        Text("计划 ${s.plannedDuration / 60000} 分钟 · ${summary.notificationCount} 条通知")
                        Text(s.actualDuration?.let { "实际 ${it / 1000} 秒 · ${s.stopReason}" } ?: "诊断进行中")
                    }
                }
            }
        }
    }
    if(clear) ClearDiagnosticDialog({ clear = false }) { vm.clear(); clear = false }
}

@Composable
fun DiagnosticDetail(vm: DiagnosticViewModel, id: String) {
    val sessions by vm.sessions.collectAsStateWithLifecycle()
    val flow = remember(id) { vm.logs(id) }
    val logs by flow.collectAsStateWithLifecycle(emptyList())
    val busy by vm.busy.collectAsStateWithLifecycle()
    val summary = sessions.firstOrNull { it.session.id == id }
    LazyColumn(contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        item {
            Text(summary?.session?.let { "开始：${DiagnosticText.time(it.startTime)}\n结束：${it.endTime?.let(DiagnosticText::time) ?: "诊断中"}\n${logs.size} 条通知 · ${it.stopReason}" } ?: "正在读取，或会话已被清理")
            Button(enabled = !busy && summary != null, onClick = { vm.export(id) }) { Text("复制诊断日志") }
            Text("按接收时间排序；每次通知更新也单独记录。复制诊断中会话得到当前快照。")
        }
        if(logs.isEmpty()) item { EmptyState("还没有收到通知", "诊断记录系统通知，部分支付可能不产生通知。") }
        items(logs, key = { it.id }) { log -> Card(Modifier.fillMaxWidth()) {
            Text(DiagnosticText.notification(log), Modifier.padding(12.dp), style = MaterialTheme.typography.bodySmall)
        } }
    }
}

@Composable
fun DiagnosticExportDialog(vm: DiagnosticViewModel) {
    val parts by vm.exportParts.collectAsStateWithLifecycle()
    val context = LocalContext.current
    var index by remember(parts) { mutableIntStateOf(0) }
    var copied by remember(parts) { mutableStateOf(false) }
    LaunchedEffect(parts) {
        if(parts.size == 1) {
            runCatching { DiagnosticClipboard.copy(context, parts.single()) }
                .onSuccess { vm.message.value = "已复制完整诊断日志" }
                .onFailure { vm.message.value = "系统剪贴板复制失败，请稍后重试" }
            vm.exportParts.value = emptyList()
        }
    }
    if(parts.size > 1) AlertDialog(onDismissRequest = { vm.exportParts.value = emptyList() },
        title = { Text(if(parts.size == 1) "复制完整诊断日志" else "日志较大 · 第 ${index + 1}/${parts.size} 段") },
        text = { Text(if(parts.size == 1) "完整会话已准备好。日志可能含私人消息，请检查后再分享。" else "受 Android 剪贴板容量限制，请逐段复制并粘贴到目标位置，再复制下一段。所有内容均保留，未静默截断。${if(copied) " 本段已复制。" else ""}") },
        confirmButton = { TextButton(onClick = {
            runCatching { DiagnosticClipboard.copy(context, parts[index]) }.onSuccess {
                copied = true
                if(parts.size == 1) { vm.exportParts.value = emptyList(); vm.message.value = "已复制完整诊断日志" }
            }.onFailure { vm.message.value = "系统剪贴板复制失败，请稍后重试" }
        }) { Text("复制${if(parts.size == 1) "全部" else "本段"}") } },
        dismissButton = {
            Row {
                if(index > 0) TextButton(onClick = { index--; copied = false }) { Text("上一段") }
                if(index < parts.lastIndex) TextButton(enabled = copied, onClick = { index++; copied = false }) { Text("下一段") }
                else TextButton(onClick = { vm.exportParts.value = emptyList() }) { Text("关闭") }
            }
        })
}
