package cn.localledger.ui

import android.content.Intent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.saveable.mapSaver
import androidx.compose.runtime.snapshots.SnapshotStateMap
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import cn.localledger.report.*
import cn.localledger.ui.design.*
import cn.localledger.parser.Money
import java.time.*
import java.time.format.DateTimeFormatter

@Composable
fun ReportScreen(vm: ReportViewModel) {
    val busy by vm.busy.collectAsStateWithLifecycle(); val preview by vm.preview.collectAsStateWithLifecycle()
    val share by vm.shareUri.collectAsStateWithLifecycle(); val last by vm.lastReport.collectAsStateWithLifecycle()
    val context = LocalContext.current
    var type by rememberSaveable { mutableStateOf("ANALYSIS") }; var choice by rememberSaveable { mutableStateOf("SINCE") }
    var start by rememberSaveable { mutableStateOf(LocalDate.now().withDayOfMonth(1).toString()) }; var end by rememberSaveable { mutableStateOf(LocalDate.now().toString()) }
    var rangePickerOpen by rememberSaveable { mutableStateOf(false) }
    var name by rememberSaveable { mutableStateOf("") }; var amount by rememberSaveable { mutableStateOf("") }
    val flags = rememberSaveable(saver = mapSaver<SnapshotStateMap<String, Boolean>>(save = { it.toMap() }, restore = { it.map { (k, v) -> k to (v as Boolean) }.toMutableStateMap() })) { mutableStateMapOf("assets" to true, "accounts" to false, "income" to true, "categories" to true,
        "merchants" to true, "large" to true, "periodic" to true, "details" to true, "names" to true,
        "notes" to false, "platforms" to false, "original" to false, "anonymous" to false) }
    fun options() = ReportOptions(flags.getValue("assets"), flags.getValue("accounts"), flags.getValue("income"), flags.getValue("categories"),
        flags.getValue("merchants"), flags.getValue("large"), flags.getValue("periodic"), flags.getValue("details"), flags.getValue("names"),
        flags.getValue("notes"), flags.getValue("platforms"), flags.getValue("original"), flags.getValue("anonymous"))
    val key = listOf(type, choice, start, end, name, amount, flags.toMap()).toString()
    LaunchedEffect(key) { vm.invalidate() }
    val create = rememberLauncherForActivityResult(ReportDocumentContract()) { vm.save(it) }
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).imePadding().padding(16.dp), verticalArrangement = Arrangement.spacedBy(AppDimensions.Medium)) {
        Text("导出给AI：App只整理本地数据，不调用AI、不联网、不自动上传。报告包含个人财务信息，请先检查再分享。")
        Text("报告类型", style = MaterialTheme.typography.titleMedium)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) { listOf("ANALYSIS" to "消费分析", "PURCHASE" to "购买决策").forEach { (v, label) -> FilterChip(type == v, { type = v }, enabled = !busy, label = { Text(label) }) } }
        Text("时间范围", style = MaterialTheme.typography.titleMedium)
        ChoiceMenu("范围", listOf("SINCE" to "上次报告之后", "MONTH" to "本月", "30" to "近30天", "90" to "近90天", "CUSTOM" to "自定义"), choice, !busy) {
            if(it == "CUSTOM") rangePickerOpen = true else choice = it
        }
        Text("上次成功报告：" + (last?.toLongOrNull()?.let { Instant.ofEpochMilli(it).atZone(ZoneId.systemDefault()).format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")) } ?: "尚未生成（默认本月1日至今天）"))
        if(choice == "CUSTOM") OutlinedButton(enabled = !busy, onClick = { rangePickerOpen = true }, modifier = Modifier.testTag("report-custom-range")) {
            Text("$start — $end")
        }
        if(type == "PURCHASE") {
            OutlinedTextField(name, { name = it.take(100) }, label = { Text("计划购买名称（可选）") }, enabled = !busy)
            OutlinedTextField(amount, { amount = it }, label = { Text("计划购买金额（元，必填）") }, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal), enabled = !busy)
        }
        ReportOptionsContent(flags, busy) { id, value -> flags[id] = value }
        Text("隐藏商户名称时也使用一致匿名标签。匿名化不处理备注、自定义收入类别、购买名称或账户名，请自行脱敏。", style = MaterialTheme.typography.bodySmall)
        Button(enabled = !busy, onClick = { vm.prepare(type, choice, LocalDate.parse(start), LocalDate.parse(end), options(), name, Money.parse(amount)) }) { Text("预览报告") }
        preview?.let { report ->
            Text("预计交易${report.transactionCount}条；完整输出，无静默截断。下方内容就是将保存的Markdown，生成时间为这份数据的截止时间。", style = MaterialTheme.typography.bodySmall)
            OutlinedButton(enabled = !busy, onClick = { vm.selectFile()?.let { create.launch(it) } }) { Text("生成 Markdown") }
            if(report.content.length > 12000) Text("页面仅预览前12000字符，导出文件包含完整报告。", style = MaterialTheme.typography.bodySmall)
            Card(Modifier.fillMaxWidth()) { Text(report.content.take(12000), modifier = Modifier.padding(12.dp), style = MaterialTheme.typography.bodySmall) }
        }
        if(preview == null && !busy) Text("还没有生成报告 · 选择内容后预览", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        if(share != null) OutlinedButton(enabled = !busy, onClick = {
            runCatching { context.startActivity(Intent.createChooser(ReportFiles.share(context.contentResolver, share!!), "分享财务报告")) }
                .onFailure { vm.message.value = "无法分享，请从文件管理器分享已保存的.md文件" }
        }) { Text("分享报告") }
        if(busy) { CircularProgressIndicator(); Text("正在整理或保存，请稍候…") }
    }
    if(rangePickerOpen) {
        val today = LocalDate.now()
        CompactDateRangePicker(DateRangeSelection(choice,
            if(choice == "SINCE") null else LocalDate.parse(start),
            if(choice == "SINCE") null else LocalDate.parse(end)),
            listOf(DateRangeOption("SINCE", "上次报告之后", null, null),
                DateRangeOption("MONTH", "本月", today.withDayOfMonth(1), today),
                DateRangeOption("30", "近30天", today.minusDays(29), today),
                DateRangeOption("90", "近90天", today.minusDays(89), today),
                DateRangeOption("CUSTOM", "自定义", null, null)),
            { rangePickerOpen = false }, { selectedRange ->
                choice = selectedRange.preset
                selectedRange.start?.let { start = it.toString() }
                selectedRange.end?.let { end = it.toString() }
                rangePickerOpen = false
            }, today)
    }
}

@Composable
fun ReportOptionsContent(flags: Map<String, Boolean>, busy: Boolean, change: (String, Boolean) -> Unit) {
    SectionHeader("报告内容")
    listOf("assets" to "总资产与资产变化", "income" to "收入汇总", "categories" to "支出分类", "merchants" to "商户汇总",
        "large" to "大额支出", "periodic" to "每日 / 每周支出", "details" to "交易明细", "names" to "商户名称").forEach { (id, label) ->
        ToggleRow(label, flags[id] == true, !busy) { change(id, it) }
    }
    SectionHeader("隐私", "默认不包含账户余额和备注，导出前请检查")
    listOf("accounts" to "各账户余额（含账户名）", "anonymous" to "匿名化商户", "notes" to "交易备注", "platforms" to "支付平台", "original" to "原始支付金额").forEach { (id, label) ->
        ToggleRow(label, flags[id] == true, !busy && (id != "accounts" || flags["assets"] == true)) { change(id, it) }
    }
}
