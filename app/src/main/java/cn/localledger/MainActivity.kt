package cn.localledger

import android.Manifest
import android.content.*
import android.os.*
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.enableEdgeToEdge
import androidx.activity.SystemBarStyle
import androidx.activity.compose.*
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.*
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.saveable.rememberSaveableStateHolder
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.Alignment
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.core.app.NotificationManagerCompat
import androidx.lifecycle.*
import androidx.lifecycle.compose.*
import androidx.lifecycle.viewmodel.compose.viewModel
import cn.localledger.data.*
import cn.localledger.parser.*
import cn.localledger.ui.LedgerViewModel
import cn.localledger.ui.*
import java.time.*
import java.time.format.DateTimeFormatter
import cn.localledger.ui.design.*
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {
    private var requestedId by mutableStateOf<Long?>(null)
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        requestedId = intent.getLongExtra("entryId", 0).takeIf { it > 0 }
        setContent {
            val mode by (application as LedgerApplication).uiPreferences.theme.collectAsStateWithLifecycle("SYSTEM")
            val dark = mode == "DARK" || (mode == "SYSTEM" && isSystemInDarkTheme())
            SideEffect { enableEdgeToEdge(statusBarStyle = if(dark) SystemBarStyle.dark(android.graphics.Color.TRANSPARENT) else SystemBarStyle.light(android.graphics.Color.TRANSPARENT, android.graphics.Color.TRANSPARENT), navigationBarStyle = if(dark) SystemBarStyle.dark(android.graphics.Color.TRANSPARENT) else SystemBarStyle.light(android.graphics.Color.TRANSPARENT, android.graphics.Color.TRANSPARENT)) }
            AppTheme(dark) {
                LedgerApp(requestedId, consumed = { requestedId = null; intent.removeExtra("entryId") }, themeMode = mode)
            }
        }
    }
    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent); setIntent(intent)
        requestedId = intent.getLongExtra("entryId", 0).takeIf { it > 0 }
    }
}

private fun date(timestamp: Long) = Instant.ofEpochMilli(timestamp).atZone(ZoneId.systemDefault())
private fun platform(value: String) = when(value) { "Alipay" -> "支付宝"; "WeChat" -> "微信"; else -> "手动" }

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LedgerApp(requestedId: Long?, consumed: () -> Unit, vm: LedgerViewModel = viewModel(), themeMode: String = "SYSTEM") {
    val entries by vm.entries.collectAsStateWithLifecycle()
    val logs by vm.unknown.collectAsStateWithLifecycle()
    val error by vm.error.collectAsStateWithLifecycle()
    val busy by vm.busy.collectAsStateWithLifecycle()
    val diagnostic: DiagnosticViewModel = viewModel()
    val merchantVm: MerchantViewModel = viewModel()
    val dataVm: DataManagementViewModel = viewModel()
    val assetVm: AssetViewModel = viewModel()
    val assetMessage by assetVm.message.collectAsStateWithLifecycle()
    val assetBusy by assetVm.busy.collectAsStateWithLifecycle()
    val reportVm: ReportViewModel = viewModel()
    val reportBusy by reportVm.busy.collectAsStateWithLifecycle()
    val reportMessage by reportVm.message.collectAsStateWithLifecycle()
    val dataMessage by dataVm.message.collectAsStateWithLifecycle()
    val dataBusy by dataVm.busy.collectAsStateWithLifecycle()
    val incomeCategories by dataVm.categories.collectAsStateWithLifecycle()
    val diagnosticMessage by diagnostic.message.collectAsStateWithLifecycle()
    val merchantMessage by merchantVm.message.collectAsStateWithLifecycle()
    var screen by rememberSaveable { mutableStateOf("home") }
    var sessionId by rememberSaveable { mutableStateOf("") }
    var editingId by rememberSaveable { mutableStateOf(0L) }
    var editing by remember { mutableStateOf<LedgerEntry?>(null) }
    var editorOrigin by rememberSaveable { mutableStateOf("home") }
    var childBack by remember { mutableStateOf<(() -> Unit)?>(null) }
    val registerBack = remember { { action: (() -> Unit)? -> childBack = action } }
    val stateHolder = rememberSaveableStateHolder()
    val roots = listOf("home", "statistics", "assets", "settings")
    val snackbar = remember { SnackbarHostState() }
    LaunchedEffect(error) { error?.let { snackbar.showSnackbar(it); vm.error.value = null } }
    LaunchedEffect(diagnosticMessage) { diagnosticMessage?.let { snackbar.showSnackbar(it); diagnostic.message.value = null } }
    LaunchedEffect(merchantMessage) { merchantMessage?.let { snackbar.showSnackbar(it); merchantVm.message.value = null } }
    LaunchedEffect(dataMessage) { dataMessage?.let { snackbar.showSnackbar(it); dataVm.message.value = null } }
    LaunchedEffect(assetMessage) { assetMessage?.let { snackbar.showSnackbar(it); assetVm.message.value = null } }
    LaunchedEffect(reportMessage) { reportMessage?.let { snackbar.showSnackbar(it); reportVm.message.value = null } }
    LaunchedEffect(requestedId) {
        requestedId?.let { id ->
            runCatching { vm.get(id) }.onSuccess {
                editing = it
                editingId = it?.id ?: 0L
                if(it != null) screen = "edit" else snackbar.showSnackbar("该账单已删除")
            }.onFailure { vm.error.value = "无法读取账单" }
            consumed()
        }
    }
    LaunchedEffect(screen, editingId) {
        if(screen == "edit" && editingId > 0 && editing?.id != editingId) {
            runCatching { vm.get(editingId) }.onSuccess {
                editing = it
                if(it == null) { screen = "home"; snackbar.showSnackbar("该账单已删除") }
            }.onFailure { screen = "home"; vm.error.value = "无法读取账单" }
        }
    }
    fun back() { screen = when(screen) { "diagnosticDetail" -> "diagnostics"; "diagnostics" -> "diagnosticSetup"; "report", "logs", "diagnosticSetup", "merchants", "data", "incomeCategories" -> "settings"; else -> "home" } }
    fun navigateBack() { if(childBack != null) childBack!!() else if(screen == "edit") screen = editorOrigin else back() }
    val working = busy || dataBusy || assetBusy || reportBusy
    BackHandler(screen !in roots || screen != "home" || childBack != null) { if(!working) navigateBack() }
    Scaffold(snackbarHost = { SnackbarHost(snackbar) }, topBar = {
        TopAppBar(title = { Text(when(screen) { "report" -> "财务报告"; "assets" -> "资产"; "data" -> "数据管理"; "incomeCategories" -> "收入类别管理"; "statistics" -> "支出统计"; "merchants" -> "商户管理"; "diagnosticSetup" -> "通知诊断"; "diagnostics" -> "通知诊断日志"; "diagnosticDetail" -> "诊断会话详情"; "settings" -> "我的"; "logs" -> "未识别通知日志"; "edit" -> if(editing == null) "手动记账" else "编辑账单"; else -> "账单" }) },
            navigationIcon = { if(screen !in roots || childBack != null) IconButton(enabled = !working, onClick = ::navigateBack) { Icon(AppIcons.Back, "返回") } })
    }, bottomBar = { if(screen in roots && childBack == null) NavigationBar {
        roots.zip(listOf("账单", "统计", "资产", "我的")).forEachIndexed { index, (target, label) -> NavigationBarItem(selected = screen == target, enabled = !working,
            onClick = { screen = target }, icon = { Icon(listOf(AppIcons.Ledger, AppIcons.Chart, AppIcons.Wallet, AppIcons.Person)[index], null) }, label = { Text(label) }) }
    } }, floatingActionButton = { if(screen == "home") FloatingActionButton(onClick = { stateHolder.removeState("edit:0"); editorOrigin = "home"; editing = null; editingId = 0; screen = "edit" }) { Icon(AppIcons.Add, "手动记账") } }) { padding ->
        Column(Modifier.padding(padding).consumeWindowInsets(padding).fillMaxSize()) {
          CompositionLocalProvider(LocalBackRegistration provides registerBack) {
           stateHolder.SaveableStateProvider(if(screen == "edit") "edit:$editingId" else screen) {
            when(screen) {
                "home" -> Home(entries, incomeCategories, merchantVm, { screen = "settings" }, { stateHolder.removeState("edit:0"); editorOrigin = "home"; editing = null; editingId = 0; screen = "edit" }) { stateHolder.removeState("edit:${it.id}"); editorOrigin = "home"; editing = it; editingId = it.id; screen = "edit" }
                "settings" -> SettingsPage({ screen = "diagnosticSetup" }, { screen = "merchants" }, { screen = "data" }, { screen = "incomeCategories" }, { screen = "report" }, themeMode) { screen = "logs" }
                "diagnosticSetup" -> Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(AppDimensions.Page)) { DiagnosticControls(diagnostic) { screen = "diagnostics" } }
                "report" -> ReportScreen(reportVm)
                "data" -> DataManagementScreen(dataVm)
                "assets" -> AssetScreen(assetVm, entries)
                "incomeCategories" -> IncomeCategoriesScreen(dataVm)
                "merchants" -> MerchantManagement(merchantVm)
                "statistics" -> StatisticsScreen(entries, merchantVm) { stateHolder.removeState("edit:${it.id}"); editorOrigin = "statistics"; editing = it; editingId = it.id; screen = "edit" }
                "diagnostics" -> DiagnosticHistory(diagnostic) { sessionId = it; screen = "diagnosticDetail" }
                "diagnosticDetail" -> DiagnosticDetail(diagnostic, sessionId)
                "logs" -> LogsPage(logs, busy) { vm.clearLogs {} }
                "edit" -> if(editingId > 0 && editing?.id != editingId) CircularProgressIndicator() else key(editingId) { Editor(editing, busy, merchantVm, incomeCategories, { screen = editorOrigin }, { vm.save(it) { stateHolder.removeState("edit:$editingId"); screen = editorOrigin } }, { editing?.let { vm.delete(it.id) { stateHolder.removeState("edit:$editingId"); screen = editorOrigin } } }) }
            }
           }
          }
        }
    }
    DiagnosticExportDialog(diagnostic)
}

@Composable
private fun Home(entries: List<LedgerEntry>, incomeCategories: List<IncomeCategoryEntity>, merchantVm: MerchantViewModel, settings: () -> Unit, manual: () -> Unit, edit: (LedgerEntry) -> Unit) {
    val app = LocalContext.current.applicationContext as LedgerApplication
    val connected by app.listenerConnected.collectAsStateWithLifecycle()
    val captureError by app.listenerError.collectAsStateWithLifecycle()
    val memories by merchantVm.memories.collectAsStateWithLifecycle()
    BillsContent(entries, incomeCategories, connected, captureError, settings, manual, edit, memories)
}
@Composable
private fun Editor(entry: LedgerEntry?, busy: Boolean, merchantVm: MerchantViewModel, incomeCategories: List<IncomeCategoryEntity>, close: () -> Unit, save: (LedgerEntry) -> Unit, delete: () -> Unit) {
    val memories by merchantVm.memories.collectAsStateWithLifecycle()
    EditorContent(entry, busy, memories, incomeCategories, { merchantVm.dominantCategory(it) }, close, save, delete)
}

@Composable
fun EditorContent(entry: LedgerEntry?, busy: Boolean, memories: List<cn.localledger.merchant.MerchantMemory>, incomeCategories: List<IncomeCategoryEntity>, suggest: suspend (String) -> String?, close: () -> Unit, save: (LedgerEntry) -> Unit, delete: () -> Unit) {
    var amount by rememberSaveable { mutableStateOf(entry?.let { Money.format(it.amount) }.orEmpty()) }
    var merchant by rememberSaveable { mutableStateOf(entry?.merchant.orEmpty()) }
    var note by rememberSaveable { mutableStateOf(entry?.note.orEmpty()) }
    var category by rememberSaveable { mutableStateOf(entry?.category ?: "待分类") }
    var type by rememberSaveable { mutableStateOf(entry?.transactionType ?: "expense") }
    var incomeCategoryId by rememberSaveable { mutableStateOf(entry?.incomeCategoryId ?: OTHER_INCOME_ID) }
    var timestamp by rememberSaveable { mutableStateOf(entry?.timestamp ?: System.currentTimeMillis()) }
    var datePickerOpen by rememberSaveable { mutableStateOf(false) }
    var timePickerOpen by rememberSaveable { mutableStateOf(false) }
    var invalid by rememberSaveable { mutableStateOf(false) }
    var confirm by remember { mutableStateOf(false) }
    var splitEnabled by rememberSaveable { mutableStateOf(entry?.splitEnabled ?: false) }
    var participantCount by rememberSaveable { mutableStateOf(entry?.participantCount ?: 3) }
    var manualSelf by rememberSaveable { mutableStateOf(entry?.manualSelfAmountCents?.let(Money::format).orEmpty()) }
    var received by rememberSaveable { mutableStateOf(entry?.receivedAmountCents?.let(Money::format).orEmpty()) }
    var splitError by rememberSaveable { mutableStateOf<String?>(null) }
    var edited by rememberSaveable { mutableStateOf(false) }
    var abandon by remember { mutableStateOf(false) }
    val focus = LocalFocusManager.current
    ScreenBack(edited) { abandon = true }
    LaunchedEffect(entry?.id, entry?.merchant, entry?.category) {
        if(entry != null && entry.transactionType == "expense" && entry.category == "待分类" && isLearnableMerchant(entry.merchant)) {
            val suggested = suggest(entry.merchant)
            if(category == "待分类" && suggested != null) category = suggested
        }
    }
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).imePadding().padding(AppDimensions.Page), verticalArrangement = Arrangement.spacedBy(AppDimensions.Medium)) {
        OutlinedTextField(amount, { amount = it; invalid = false; edited = true }, label = { Text("金额（元）") }, prefix = { Text("¥") }, textStyle = MaterialTheme.typography.headlineMedium, singleLine = true, isError = invalid, supportingText = { if(invalid) Text("请输入大于零的金额，最多两位小数、九位整数") }, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal, imeAction = ImeAction.Done), keyboardActions = KeyboardActions(onDone = { focus.clearFocus() }), modifier = Modifier.fillMaxWidth())
        Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(AppDimensions.Small)) { flowTypes.forEach { (value, label) -> FilterChip(selected = type == value, onClick = { type = value; edited = true }, label = { Text(label) }) } }
        if(type == "expense") {
        Text("支出分类")
        if(category == "待分类") Text("请选择真实分类", color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
        @OptIn(ExperimentalLayoutApi::class)
        FlowRow(horizontalArrangement = Arrangement.spacedBy(AppDimensions.Small)) { learnableCategories.forEach { value -> FilterChip(category == value, { category = value; edited = true }, label = { Text(value) }) } }
        val normalizedMerchant = normalizeMerchantName(merchant)
        val merchantHistory = memories.filter { it.normalizedName == normalizedMerchant && it.useCount > 0 }
        if(merchantHistory.isNotEmpty()) Text("该商户历史：" + merchantHistory.joinToString(" · ") { "${it.category} ${it.useCount}次" }, style = MaterialTheme.typography.bodySmall)
        val shortcuts = memories.filter { it.category == category && it.isShortcut }
        if(shortcuts.isNotEmpty()) {
            Text("常用商户")
            Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                shortcuts.forEach { memory -> AssistChip(onClick = { merchant = memory.name; edited = true }, label = { Text(memory.name) }) }
            }
        }
        OutlinedTextField(merchant, { merchant = it.take(100); edited = true }, label = { Text("商户名称（可留空）") }, singleLine = true, keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done), keyboardActions = KeyboardActions(onDone = { focus.clearFocus() }), modifier = Modifier.fillMaxWidth())
        } else if(type == "income") {
            Text("收入来源类别")
            Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(AppDimensions.Small)) {
                incomeCategories.forEach { c -> FilterChip(incomeCategoryId == c.id, { incomeCategoryId = c.id; edited = true }, label = { Text(c.name) }) }
            }
            if(entry?.legacyCategory != null || entry?.legacyMerchant != null) Text("历史信息：${entry?.legacyCategory.orEmpty()} · ${entry?.legacyMerchant.orEmpty()}", style = MaterialTheme.typography.bodySmall)
        } else Text("资金回流单独记录，不计入真正收入；当前不自动关联或抵扣原支出。", style = MaterialTheme.typography.bodySmall)
        Row {
            TextButton(onClick = { datePickerOpen = true }, modifier = Modifier.testTag("editor-pick-date")) { Text(date(timestamp).toLocalDate().toString()) }
            TextButton(onClick = { timePickerOpen = true }, modifier = Modifier.testTag("editor-pick-time")) { Text(date(timestamp).format(DateTimeFormatter.ofPattern("HH:mm"))) }
        }
        if(type == "expense") SplitControls(splitEnabled, participantCount, manualSelf, received, Money.parse(amount),
            { splitEnabled = it; manualSelf = ""; received = ""; splitError = null; edited = true }, { participantCount = it; edited = true },
            { manualSelf = it; if(it.isNotBlank()) received = ""; splitError = null; edited = true },
            { received = it; if(it.isNotBlank()) manualSelf = ""; splitError = null; edited = true })
        splitError?.let { Text(it, color = MaterialTheme.colorScheme.error) }
        OutlinedTextField(note, { note = it.take(1000); edited = true }, label = { Text("备注（可选）") }, modifier = Modifier.fillMaxWidth(), minLines = 2, keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done), keyboardActions = KeyboardActions(onDone = { focus.clearFocus() }))
        Button(enabled = !busy, modifier = Modifier.fillMaxWidth(), onClick = {
            val cents = Money.parse(amount)
            if(cents == null) invalid = true else {
                val split = if(splitEnabled && type == "expense") splitEntry(cents, participantCount, manualSelf, received) else LedgerEntry(amount = cents)
                if(split == null) splitError = "AA 金额格式不合法，请填写非负金额，最多两位小数"
                else {
                    val candidate = (entry ?: LedgerEntry(amount = cents)).copy(amount = cents, merchant = merchant.trim().ifBlank { "未知商户" }, category = category, transactionType = type, note = note, timestamp = timestamp,
                        incomeCategoryId = if(type == "income") incomeCategoryId else null,
                        splitEnabled = split.splitEnabled, participantCount = split.participantCount, splitCalculationMode = split.splitCalculationMode,
                        manualSelfAmountCents = split.manualSelfAmountCents, receivedAmountCents = split.receivedAmountCents)
                    splitError = SplitExpense.validationError(candidate)
                    if(splitError == null) save(candidate)
                }
            }
        }) { Text(if(busy) "正在保存…" else "保存") }
        if(entry != null) TextButton(enabled = !busy, onClick = { confirm = true }) { Text("删除账单", color = MaterialTheme.colorScheme.error) }
    }
    if(confirm) AlertDialog(onDismissRequest = { confirm = false }, title = { Text("删除这条账单？") }, text = { Text("删除后无法恢复。") }, confirmButton = { TextButton(onClick = { confirm = false; delete() }) { Text("删除", color = MaterialTheme.colorScheme.error) } }, dismissButton = { TextButton(onClick = { confirm = false }) { Text("取消") } })
    if(abandon) AlertDialog(onDismissRequest = { abandon = false }, title = { Text("放弃修改？") }, text = { Text("尚未保存的内容会丢失，原账单保持不变。") }, confirmButton = { TextButton(onClick = { abandon = false; close() }) { Text("放弃修改", color = MaterialTheme.colorScheme.error) } }, dismissButton = { TextButton(onClick = { abandon = false }) { Text("继续编辑") } })
    if(datePickerOpen) CompactDatePicker(date(timestamp).toLocalDate(), { datePickerOpen = false }, { day ->
        timestamp = replaceLocalDate(timestamp, day); edited = true; datePickerOpen = false
    })
    if(timePickerOpen) CompactTimePicker(date(timestamp).toLocalTime(), { timePickerOpen = false }, { time ->
        timestamp = replaceLocalTime(timestamp, time); edited = true; timePickerOpen = false
    })
}

@Composable
private fun SettingsPage(diagnostic: () -> Unit, merchants: () -> Unit, data: () -> Unit, incomeCategories: () -> Unit, report: () -> Unit, themeMode: String, logs: () -> Unit) {
    val context = LocalContext.current
    val owner = LocalLifecycleOwner.current
    val scope = rememberCoroutineScope()
    var message by remember { mutableStateOf<String?>(null) }
    var granted by remember { mutableStateOf(false) }
    var notifications by remember { mutableStateOf(false) }
    val app = context.applicationContext as LedgerApplication
    val connected by app.listenerConnected.collectAsStateWithLifecycle()
    fun refresh() { granted = NotificationManagerCompat.getEnabledListenerPackages(context).contains(context.packageName); notifications = NotificationManagerCompat.from(context).areNotificationsEnabled() }
    DisposableEffect(owner) { val observer = LifecycleEventObserver { _, event -> if(event == Lifecycle.Event.ON_RESUME) refresh() }; owner.lifecycle.addObserver(observer); refresh(); onDispose { owner.lifecycle.removeObserver(observer) } }
    val permission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { refresh() }
    fun open(intent: Intent) { runCatching { context.startActivity(intent) }.onFailure { message = "系统未提供此入口，请到手机设置中手动开启" } }
    MyContent(granted, connected, notifications, BuildConfig.VERSION_NAME, themeMode,
        { mode -> scope.launch { runCatching { app.uiPreferences.setTheme(mode) }.onFailure { message = "无法保存主题，请重试" } } },
        { open(Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS)) },
        { if(Build.VERSION.SDK_INT >= 33 && !notifications) permission.launch(Manifest.permission.POST_NOTIFICATIONS) else open(Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName)) },
        diagnostic, merchants, incomeCategories, report, data, logs,
        { open(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, android.net.Uri.parse("package:${context.packageName}"))) })
    message?.let { AlertDialog(onDismissRequest = { message = null }, title = { Text("操作未完成") }, text = { Text(it) }, confirmButton = { TextButton(onClick = { message = null }) { Text("知道了") } }) }
}

@Composable
private fun LogsPage(logs: List<UnknownNotification>, busy: Boolean, clear: () -> Unit) {
    val context = LocalContext.current
    var confirm by remember { mutableStateOf(false) }
    var copied by remember { mutableStateOf(false) }
    fun render(log: UnknownNotification) = "时间: ${date(log.timestamp)}\n来源: ${log.packageName}\n原因: ${log.reason}\ntitle: ${log.title}\ntext: ${log.text}\nbigText: ${log.bigText}\nsubText: ${log.subText}\nextras: ${log.extraText}"
    Column(Modifier.padding(16.dp)) {
        Text("只保留最近 300 条疑似支付但未识别的通知。复制内容可能含姓名和金额，请先检查再分享。")
        Row {
            TextButton(enabled = logs.isNotEmpty(), onClick = {
                val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as android.content.ClipboardManager
                // Bound binder payload; newest entries first.
                val data = logs.joinToString("\n\n", transform = ::render).take(100_000)
                val clip = ClipData.newPlainText("未识别通知日志（最多 10 万字符）", data)
                if(Build.VERSION.SDK_INT >= 33) clip.description.extras = PersistableBundle().apply { putBoolean("android.content.extra.IS_SENSITIVE", true) }
                clipboard.setPrimaryClip(clip)
                copied = true
            }) { Text("复制日志") }
            TextButton(enabled = logs.isNotEmpty() && !busy, onClick = { confirm = true }) { Text("清空日志", color = MaterialTheme.colorScheme.error) }
        }
        if(copied) Text("已复制最近日志（最多 10 万字符）", style = MaterialTheme.typography.bodySmall)
        LazyColumn(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            if(logs.isEmpty()) item { EmptyState("没有未识别通知", "没有系统通知的支付不会出现在这里，可手动补录。") }
            items(logs, key = { it.id }) { Card(Modifier.fillMaxWidth()) { Text(render(it), Modifier.padding(12.dp), style = MaterialTheme.typography.bodySmall) } }
        }
    }
    if(confirm) AlertDialog(onDismissRequest = { confirm = false }, title = { Text("清空全部未识别日志？") }, confirmButton = { TextButton(onClick = { confirm = false; clear() }) { Text("清空", color = MaterialTheme.colorScheme.error) } }, dismissButton = { TextButton(onClick = { confirm = false }) { Text("取消") } })
}
