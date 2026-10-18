package cn.localledger.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.key
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import cn.localledger.data.*
import cn.localledger.merchant.MerchantMemory
import cn.localledger.parser.normalizeMerchantName
import cn.localledger.ui.design.*
import kotlinx.coroutines.launch
import java.time.*
import java.time.format.DateTimeFormatter

@Composable fun BillsContent(entries: List<LedgerEntry>, incomeCategories: List<IncomeCategoryEntity>, connected: Boolean,
    captureError: String?, settings: () -> Unit, manual: () -> Unit, edit: (LedgerEntry) -> Unit,
    merchantMemories: List<MerchantMemory> = emptyList()) {
    val now = LocalDate.now(); val month = YearMonth.from(now)
    fun time(value: Long) = Instant.ofEpochMilli(value).atZone(ZoneId.systemDefault())
    val total = entries.filter { it.transactionType == "expense" && YearMonth.from(time(it.timestamp)) == month }.sumOf { it.personalExpenseCents }
    val income = trueIncome(entries.filter { YearMonth.from(time(it.timestamp)) == month })
    var preset by rememberSaveable { mutableStateOf(LedgerDatePreset.ALL.name) }
    var customStart by rememberSaveable { mutableStateOf("") }
    var customEnd by rememberSaveable { mutableStateOf("") }
    var category by rememberSaveable { mutableStateOf("") }
    var merchant by rememberSaveable { mutableStateOf("") }
    var filterOpen by rememberSaveable { mutableStateOf(false) }
    val filter = LedgerFilterState(LedgerDatePreset.valueOf(preset), customStart.takeIf { it.isNotBlank() }?.let(LocalDate::parse),
        customEnd.takeIf { it.isNotBlank() }?.let(LocalDate::parse), category, merchant)
    val shown = remember(entries, filter, now) { filter.select(entries, now) }
    val listState = rememberLazyListState()
    val scope = rememberCoroutineScope()
    fun applyFilter(next: LedgerFilterState) {
        preset = next.preset.name; customStart = next.start?.toString().orEmpty(); customEnd = next.end?.toString().orEmpty()
        category = next.category; merchant = next.merchant; filterOpen = false
        scope.launch { listState.scrollToItem(0) }
    }
    LazyColumn(Modifier.testTag("bills-list"), state = listState, contentPadding = PaddingValues(AppDimensions.Page, AppDimensions.Small, AppDimensions.Page, AppDimensions.Large * 3), verticalArrangement = Arrangement.spacedBy(AppDimensions.Page)) {
        item(key = "filter-control") {
            Column(verticalArrangement = Arrangement.spacedBy(AppDimensions.Tiny)) {
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    OutlinedButton(onClick = { filterOpen = true }) { Icon(AppIcons.Filter, null, Modifier.size(18.dp)); Spacer(Modifier.width(6.dp)); Text("筛选" + if(filter.active) " · 已启用" else "") }
                    if(filter.active) Text("${shown.size} 笔", Modifier.padding(start = AppDimensions.Medium), style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                if(filter.active) {
                    Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(AppDimensions.Small)) {
                        if(filter.preset != LedgerDatePreset.ALL) FilterChip(true, { applyFilter(filter.copy(preset = LedgerDatePreset.ALL, start = null, end = null)) },
                            label = { Text(filterDateLabel(filter, now) + " ×") })
                        if(category.isNotBlank()) FilterChip(true, { applyFilter(filter.copy(category = "")) }, label = { Text("$category ×") })
                        if(merchant.isNotBlank()) FilterChip(true, { applyFilter(filter.copy(merchant = "")) }, label = { Text("$merchant ×") })
                        TextButton(onClick = { applyFilter(LedgerFilterState()) }) { Text("清除全部") }
                    }
                }
            }
        }
        item { Card(Modifier.fillMaxWidth(), elevation = CardDefaults.cardElevation(defaultElevation = AppElevation.Card), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer)) {
            Column(Modifier.padding(AppDimensions.Section), verticalArrangement = Arrangement.spacedBy(AppDimensions.Small)) {
                Text(if(filter.active) "符合条件的个人支出" else "本月个人支出", style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onPrimaryContainer)
                Text(displayMoney(if(filter.active) personalSpending(shown) else total), Modifier.testTag("filtered-spending"), style = MaterialTheme.typography.headlineLarge, color = MaterialTheme.colorScheme.onPrimaryContainer)
                if(filter.active) Text("共 ${shown.size} 笔 · 仅显示符合筛选条件的账单", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onPrimaryContainer)
                else {
                    Text("本月收入 ${displayMoney(income)}", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onPrimaryContainer)
                    Text("${month.year} 年 ${month.monthValue} 月 · 收入不含退款与回款", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onPrimaryContainer)
                }
            }
        } }
        if(!connected) item { TextButton(onClick = settings) { Text("自动记账尚未连接 · 检查权限") } }
        captureError?.let { item { Text(it, color = MaterialTheme.colorScheme.error) } }
        if(shown.isEmpty()) item {
            if(filter.active) EmptyState("没有找到符合条件的账单", "可以调整日期、类别或商户条件。", "清除筛选") { applyFilter(LedgerFilterState()) }
            else EmptyState("还没有账单", "支付通知识别成功后会自动出现在这里，也可以先记一笔现金消费。", "手动记账", manual)
        }
        shown.groupBy { time(it.timestamp).toLocalDate() }.forEach { (day, rows) ->
            item(key = "date:$day") {
                Column(verticalArrangement = Arrangement.spacedBy(AppDimensions.Small)) {
                    val relative = when(day) { now -> "今天"; now.minusDays(1) -> "昨天"; else -> listOf("周一", "周二", "周三", "周四", "周五", "周六", "周日")[day.dayOfWeek.value - 1] }
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(AppDimensions.Small), verticalAlignment = Alignment.CenterVertically) {
                        Text(day.format(DateTimeFormatter.ofPattern(if(day.year == now.year) "M月d日" else "yyyy年M月d日")) + " · $relative",
                            Modifier.weight(1f), maxLines = 1, overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.titleMedium)
                        Text(displayMoney(personalSpending(rows)), Modifier.testTag("day-total:$day"), maxLines = 1,
                            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold), color = LocalFinanceColors.current.expense)
                    }
                    Card(Modifier.fillMaxWidth().testTag("day-card:$day"), shape = RoundedCornerShape(16.dp),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
                        elevation = CardDefaults.cardElevation(defaultElevation = AppElevation.Card)) {
                        rows.forEachIndexed { index, entry -> key(entry.id) {
                            BillRow(entry, incomeCategories, edit)
                            if(index < rows.lastIndex) HorizontalDivider(
                                Modifier.padding(horizontal = AppDimensions.Page).testTag("bill-divider:${entry.id}"),
                                color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.45f))
                        } }
                    }
                }
            }
        }
    }
    if(filterOpen) BillFilterSheet(entries, merchantMemories, filter, now, { filterOpen = false }, ::applyFilter)
}


private fun filterDateLabel(filter: LedgerFilterState, today: LocalDate): String = when(filter.preset) {
    LedgerDatePreset.ALL -> "全部时间"
    LedgerDatePreset.LAST_7 -> "近7天"
    LedgerDatePreset.LAST_30 -> "近30天"
    LedgerDatePreset.THIS_MONTH -> "本月"
    LedgerDatePreset.PREVIOUS_MONTH -> "上个月"
    LedgerDatePreset.CUSTOM -> filter.bounds(today).let { "${it.first}–${it.second}" }
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
private fun BillFilterSheet(entries: List<LedgerEntry>, memories: List<MerchantMemory>, applied: LedgerFilterState,
    today: LocalDate, dismiss: () -> Unit, apply: (LedgerFilterState) -> Unit) {
    var preset by rememberSaveable { mutableStateOf(applied.preset.name) }
    var from by rememberSaveable { mutableStateOf((applied.start ?: today.minusDays(6)).toString()) }
    var through by rememberSaveable { mutableStateOf((applied.end ?: today).toString()) }
    var category by rememberSaveable { mutableStateOf(applied.category) }
    var merchant by rememberSaveable { mutableStateOf(applied.merchant) }
    var search by rememberSaveable { mutableStateOf("") }
    var rangePickerOpen by rememberSaveable { mutableStateOf(false) }
    val actualCategories = (entries.asSequence().filter { it.transactionType == "expense" }.map { it.category }.toList() + memories.map { it.category }).distinct()
    val knownNames = (memories.map { it.name } + entries.filter { it.transactionType == "expense" }.map { it.merchant })
        .filter { it.isNotBlank() }.distinctBy(::normalizeMerchantName)
    fun group(name: String): Int {
        val records = memories.filter { it.normalizedName == normalizeMerchantName(name) }
        return when { records.any { it.isPinned } -> 0; records.any { it.isShortcut } -> 1; else -> 2 }
    }
    val merchants = knownNames.filter { it.contains(search, ignoreCase = true) }
        .sortedWith(compareBy<String> { group(it) }.thenBy { it })
    val validDates = preset != LedgerDatePreset.CUSTOM.name || !LocalDate.parse(from).isAfter(LocalDate.parse(through))
    ModalBottomSheet(onDismissRequest = dismiss) {
        Column(Modifier.fillMaxWidth().heightIn(max = 680.dp).verticalScroll(rememberScrollState())
            .padding(horizontal = AppDimensions.Page).padding(bottom = AppDimensions.Section),
            verticalArrangement = Arrangement.spacedBy(AppDimensions.Medium)) {
            Text("筛选账单", style = MaterialTheme.typography.titleLarge)
            Text("时间范围", style = MaterialTheme.typography.titleMedium)
            FlowRow(horizontalArrangement = Arrangement.spacedBy(AppDimensions.Small)) {
                listOf(LedgerDatePreset.ALL to "全部时间", LedgerDatePreset.LAST_7 to "近7天",
                    LedgerDatePreset.LAST_30 to "近30天", LedgerDatePreset.THIS_MONTH to "本月",
                    LedgerDatePreset.PREVIOUS_MONTH to "上个月", LedgerDatePreset.CUSTOM to "自定义").forEach { (option, label) ->
                    FilterChip(selected = preset == option.name, onClick = {
                        if(option == LedgerDatePreset.CUSTOM) rangePickerOpen = true else preset = option.name
                    }, label = { Text(label) },
                        modifier = Modifier.testTag("filter-preset:${option.name}"))
                }
            }
            if(preset == LedgerDatePreset.CUSTOM.name) {
                OutlinedButton(onClick = { rangePickerOpen = true }, modifier = Modifier.testTag("bill-custom-range")) {
                    Text("$from — $through")
                }
                if(!validDates) Text("开始日期不能晚于结束日期", color = MaterialTheme.colorScheme.error)
            }
            Text("支出类别", style = MaterialTheme.typography.titleMedium)
            FlowRow(horizontalArrangement = Arrangement.spacedBy(AppDimensions.Small)) {
                FilterChip(category.isBlank(), { category = "" }, label = { Text("全部类别") })
                actualCategories.forEach { name -> FilterChip(category == name, { category = name }, label = { Text(name) },
                    modifier = Modifier.testTag("filter-category:$name")) }
            }
            Text("商户", style = MaterialTheme.typography.titleMedium)
            OutlinedTextField(search, { search = it }, Modifier.fillMaxWidth(), label = { Text("搜索历史商户") }, singleLine = true)
            TextButton(onClick = { merchant = "" }) { Text(if(merchant.isBlank()) "✓ 全部商户" else "全部商户") }
            Column(Modifier.fillMaxWidth().heightIn(max = 200.dp).verticalScroll(rememberScrollState())) {
                var lastGroup = -1
                merchants.forEach { name ->
                    val currentGroup = group(name)
                    if(currentGroup != lastGroup) {
                        Text(listOf("置顶商户", "常用商户", "其他历史商户")[currentGroup],
                            style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        lastGroup = currentGroup
                    }
                    Row(Modifier.fillMaxWidth().testTag("filter-merchant:${normalizeMerchantName(name)}").heightIn(min = AppDimensions.Touch).clickable { merchant = name }
                        .padding(horizontal = AppDimensions.Small), verticalAlignment = Alignment.CenterVertically) {
                        Text(name, Modifier.weight(1f), maxLines = 1, overflow = TextOverflow.Ellipsis)
                        if(normalizeMerchantName(name) == normalizeMerchantName(merchant)) Text("✓", color = MaterialTheme.colorScheme.primary)
                    }
                }
            }
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                TextButton(onClick = { apply(LedgerFilterState()) }) { Text("清除全部") }
                Button(enabled = validDates, modifier = Modifier.testTag("apply-bill-filter"), onClick = {
                    apply(LedgerFilterState(LedgerDatePreset.valueOf(preset),
                        if(preset == LedgerDatePreset.CUSTOM.name) LocalDate.parse(from) else null,
                        if(preset == LedgerDatePreset.CUSTOM.name) LocalDate.parse(through) else null,
                        category, merchant))
                }) { Text("应用筛选") }
            }
        }
    }
    if(rangePickerOpen) {
        val options = listOf(
            DateRangeOption(LedgerDatePreset.ALL.name, "全部时间", null, null),
            DateRangeOption(LedgerDatePreset.LAST_7.name, "近7天", today.minusDays(6), today),
            DateRangeOption(LedgerDatePreset.LAST_30.name, "近30天", today.minusDays(29), today),
            DateRangeOption(LedgerDatePreset.THIS_MONTH.name, "本月", today.withDayOfMonth(1), today),
            DateRangeOption(LedgerDatePreset.PREVIOUS_MONTH.name, "上个月",
                YearMonth.from(today).minusMonths(1).atDay(1), YearMonth.from(today).minusMonths(1).atEndOfMonth()),
            DateRangeOption(LedgerDatePreset.CUSTOM.name, "自定义", null, null))
        val currentOption = options.first { it.id == preset }
        CompactDateRangePicker(DateRangeSelection(preset,
            if(preset == LedgerDatePreset.CUSTOM.name) LocalDate.parse(from) else currentOption.start,
            if(preset == LedgerDatePreset.CUSTOM.name) LocalDate.parse(through) else currentOption.end), options,
            { rangePickerOpen = false }, { chosen ->
                preset = chosen.preset
                chosen.start?.let { from = it.toString() }
                chosen.end?.let { through = it.toString() }
                rangePickerOpen = false
            }, today)
    }
}

@Composable
private fun BillRow(entry: LedgerEntry, incomeCategories: List<IncomeCategoryEntity>, edit: (LedgerEntry) -> Unit) {
    fun time(value: Long) = Instant.ofEpochMilli(value).atZone(ZoneId.systemDefault())
    val expense = entry.transactionType == "expense"
    val title = when(entry.transactionType) { "expense" -> entry.merchant; "income" -> incomeCategories.firstOrNull { it.id == entry.incomeCategoryId }?.name ?: "其他收入"; else -> flowTypes[entry.transactionType].orEmpty() }
    val platform = when(entry.platform) { "Alipay" -> "支付宝"; "WeChat" -> "微信"; else -> "手动" }
    val amountColor = when(entry.transactionType) { "expense" -> LocalFinanceColors.current.expense; "income" -> LocalFinanceColors.current.income; else -> LocalFinanceColors.current.refund }
    Row(Modifier.fillMaxWidth().testTag("bill:${entry.id}").heightIn(min = AppDimensions.Touch).clickable { edit(entry) }.padding(horizontal = AppDimensions.Page, vertical = AppDimensions.Medium), horizontalArrangement = Arrangement.spacedBy(AppDimensions.Medium), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(AppDimensions.Tiny)) {
            Text(title, maxLines = 1, overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.bodyLarge,
                color = if(title == "未知商户") MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.onSurface)
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.size(6.dp).background(if(expense) CategoryColors.category(entry.category, LocalAppDark.current) else amountColor, CircleShape))
                Text("${if(expense) entry.category else flowTypes[entry.transactionType]} · $platform" + if(entry.splitEnabled) " · AA" else "",
                    style = MaterialTheme.typography.bodySmall, color = if(expense && entry.category == "待分类") LocalFinanceColors.current.warning else MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        Column(horizontalAlignment = Alignment.End, verticalArrangement = Arrangement.spacedBy(AppDimensions.Tiny)) {
            Text((if(expense) "-" else "+") + displayMoney(if(expense) entry.personalExpenseCents else entry.amount), style = MaterialTheme.typography.titleMedium, color = amountColor)
            Text(time(entry.timestamp).format(DateTimeFormatter.ofPattern("HH:mm")), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}
