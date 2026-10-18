package cn.localledger.ui

import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.ui.geometry.*
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.semantics.selected
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import cn.localledger.data.*
import cn.localledger.ui.design.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.text.style.TextOverflow
import cn.localledger.merchant.MerchantMemory
import cn.localledger.parser.*
import cn.localledger.statistics.*
import java.time.*
import java.time.format.DateTimeFormatter
import java.time.temporal.ChronoUnit

@Composable
fun StatisticsScreen(entries: List<LedgerEntry>, merchantVm: MerchantViewModel, openEntry: (LedgerEntry) -> Unit) {
    val memories by merchantVm.memories.collectAsStateWithLifecycle()
    StatisticsContent(entries, memories, openEntry)
}
@Composable
fun StatisticsContent(entries: List<LedgerEntry>, memories: List<MerchantMemory>, openEntry: (LedgerEntry) -> Unit) {
    val today = LocalDate.now()
    var preset by rememberSaveable { mutableStateOf("近7天") }
    var startText by rememberSaveable { mutableStateOf(today.minusDays(6).toString()) }
    var endText by rememberSaveable { mutableStateOf(today.toString()) }
    var objectName by rememberSaveable { mutableStateOf("TOTAL") }
    var selectedCategory by rememberSaveable { mutableStateOf("餐饮") }
    var selectedMerchant by rememberSaveable { mutableStateOf("") }
    var merchantDialog by remember { mutableStateOf(false) }
    var daySelected by rememberSaveable { mutableStateOf<String?>(null) }
    var rangePickerOpen by rememberSaveable { mutableStateOf(false) }
    val start = LocalDate.parse(startText); val end = LocalDate.parse(endText)
    val type = StatisticObject.valueOf(objectName)
    val selected = when(type) { StatisticObject.CATEGORY -> selectedCategory; StatisticObject.MERCHANT -> selectedMerchant; else -> "" }
    val valid = !end.isBefore(start) && ChronoUnit.DAYS.between(start, end) < 3660
    val report = remember(entries, start, end, type, selected) { if(valid) ExpenseStatistics.report(entries, start, end, type, selected) else null }
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(AppDimensions.Small)) {
            ChoiceMenu("范围", listOf("近7天", "近30天", "今年", "自定义").map { it to it }, preset) { label ->
                if(label == "自定义") rangePickerOpen = true else {
                    preset = label; endText = today.toString()
                    startText = when(label) { "近7天" -> today.minusDays(6); "近30天" -> today.minusDays(29); "今年" -> today.withDayOfYear(1); else -> start }.toString()
                }
            }
            ChoiceMenu("对象", listOf("TOTAL" to "总支出", "CATEGORY" to "分类", "MERCHANT" to "商户"), objectName) { objectName = it }
        }
        if(preset == "自定义") OutlinedButton(onClick = { rangePickerOpen = true }, modifier = Modifier.testTag("statistics-custom-range")) {
            Text("$startText — $endText")
        } else Text("$startText — $endText", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        if(type == StatisticObject.CATEGORY) ChoiceMenu("分类", (categories + entries.map { it.category }).distinct().map { it to it }, selectedCategory) { selectedCategory = it }
        if(type == StatisticObject.MERCHANT) OutlinedButton(onClick = { merchantDialog = true }) { Text(selectedMerchant.ifBlank { "选择商户 / 搜索" }) }
        if(report == null) Text("结束日期不能早于开始日期，范围最多 3660 天", color = MaterialTheme.colorScheme.error)
        else {
            Card(Modifier.fillMaxWidth(), elevation = CardDefaults.cardElevation(defaultElevation = AppElevation.Card), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer)) { Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(AppDimensions.Small)) {
                Text("$preset · ${if(type == StatisticObject.TOTAL) "总支出" else selected.ifBlank { "请选择商户" }}")
                Text("¥${displayNumber(report.total)}", style = MaterialTheme.typography.headlineMedium)
                Text("日均 ¥${displayNumber(report.average)}", style = MaterialTheme.typography.bodyMedium)
                Text("最高单日 ¥${displayNumber(report.highest)}", style = MaterialTheme.typography.bodyMedium)
                Text("按个人承担统计 · 不包含收入", style = MaterialTheme.typography.bodySmall)
            } }
            if(report.total == 0L) EmptyState("这段时间还没有支出", "可调整日期范围；收入和资金回流不计入支出。")
            if(report.useHeatmap) Heatmap(report, when(type) { StatisticObject.CATEGORY -> CategoryColors.category(selected); StatisticObject.MERCHANT -> CategoryColors.merchant(selected); else -> MaterialTheme.colorScheme.primary }) { daySelected = it.toString() }
            else StackedBars(report, type, selectedCategory, memories, daySelected?.let(LocalDate::parse)) { daySelected = it.toString() }
            Text("点击日期查看金额明细和账单", style = MaterialTheme.typography.bodySmall)
        }
    }
    if(merchantDialog) MerchantSelector(entries, memories, { merchantDialog = false }) { selectedMerchant = it; merchantDialog = false }
    if(rangePickerOpen) CompactDateRangePicker(DateRangeSelection(if(preset == "自定义") "CUSTOM" else preset, start, end),
        listOf(DateRangeOption("近7天", "近7天", today.minusDays(6), today),
            DateRangeOption("近30天", "近30天", today.minusDays(29), today),
            DateRangeOption("今年", "今年", today.withDayOfYear(1), today),
            DateRangeOption("CUSTOM", "自定义", null, null)),
        { rangePickerOpen = false }, { selectedRange ->
            preset = if(selectedRange.preset == "CUSTOM") "自定义" else selectedRange.preset
            selectedRange.start?.let { startText = it.toString() }
            selectedRange.end?.let { endText = it.toString() }
            rangePickerOpen = false
        }, today)
    daySelected?.let { day -> report?.days?.firstOrNull { it.date.toString() == day }?.let { detail ->
        DayExpenseDialog(detail, type, { daySelected = null }, { entry -> daySelected = null; openEntry(entry) })
    } }
}

@Composable
internal fun DayExpenseDialog(detail: DayExpense, type: StatisticObject, close: () -> Unit, openEntry: (LedgerEntry) -> Unit) {
    val scroll = rememberScrollState()
    var viewportPx by remember { mutableIntStateOf(0) }
    val maxHeight = with(LocalDensity.current) { LocalWindowInfo.current.containerSize.height.toDp() * 0.65f }
    val dark = LocalAppDark.current
    AlertDialog(onDismissRequest = close,
        properties = androidx.compose.ui.window.DialogProperties(dismissOnBackPress = true, dismissOnClickOutside = true),
        title = { Text("${detail.date.monthValue}月${detail.date.dayOfMonth}日 · 个人支出 ${displayMoney(detail.total)}",
            style = MaterialTheme.typography.titleMedium) },
        text = {
            Box(Modifier.fillMaxWidth().heightIn(max = maxHeight).onSizeChanged { viewportPx = it.height }) {
                Column(Modifier.fillMaxWidth().padding(end = 12.dp).verticalScroll(scroll).testTag("day-detail-content"),
                    verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    if(detail.breakdown.isNotEmpty()) {
                        Text(if(type == StatisticObject.TOTAL) "分类汇总" else "商户汇总", style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant)
                        detail.breakdown.toList().sortedByDescending { it.second }.forEach { (label, amount) ->
                            val dotCategory = if(type == StatisticObject.TOTAL) label else detail.entries.firstOrNull()?.category ?: "其他"
                            Row(Modifier.fillMaxWidth().padding(vertical = 2.dp), verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                Box(Modifier.size(7.dp).background(CategoryColors.category(dotCategory, dark), CircleShape))
                                Text(label, Modifier.weight(1f), maxLines = 1, overflow = TextOverflow.Ellipsis,
                                    style = MaterialTheme.typography.bodyMedium)
                                Text(displayMoney(amount), style = MaterialTheme.typography.bodyMedium)
                            }
                        }
                        Spacer(Modifier.height(4.dp))
                        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                    }
                    if(detail.entries.isEmpty()) Text("当天无支出账单", style = MaterialTheme.typography.bodyMedium)
                    else {
                        Text("账单 · ${detail.entries.size}笔", style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant)
                        detail.entries.forEach { entry ->
                            Row(Modifier.fillMaxWidth().heightIn(min = AppDimensions.Touch).clickable { openEntry(entry) }
                                .testTag("day-detail-entry:${entry.id}").padding(vertical = 6.dp),
                                verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                Box(Modifier.size(7.dp).testTag("day-detail-category-dot:${entry.id}")
                                    .background(CategoryColors.category(entry.category, dark), CircleShape))
                                Column(Modifier.weight(1f)) {
                                    Text(entry.merchant, maxLines = 1, overflow = TextOverflow.Ellipsis,
                                        style = MaterialTheme.typography.bodyMedium)
                                    Text(entry.category + if(entry.splitEnabled) " · AA 实付 ${displayMoney(entry.amount)}" else "",
                                        maxLines = 1, overflow = TextOverflow.Ellipsis,
                                        style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                }
                                Text(displayMoney(entry.personalExpenseCents), style = MaterialTheme.typography.bodyMedium,
                                    color = LocalFinanceColors.current.expense)
                            }
                        }
                    }
                }
                if(scroll.maxValue > 0 && viewportPx > 0) {
                    val tint = MaterialTheme.colorScheme.outline
                    Canvas(Modifier.align(Alignment.CenterEnd).width(4.dp).fillMaxHeight().testTag("day-detail-scrollbar")) {
                        val visible = viewportPx.toFloat()
                        val content = visible + scroll.maxValue
                        val thumb = (visible * visible / content).coerceAtLeast(28.dp.toPx()).coerceAtMost(size.height)
                        val travel = (size.height - thumb).coerceAtLeast(0f)
                        val top = if(scroll.maxValue == 0) 0f else travel * scroll.value / scroll.maxValue
                        drawRoundRect(tint, Offset(0f, top), Size(size.width, thumb), CornerRadius(size.width / 2))
                    }
                }
            }
        }, confirmButton = { TextButton(onClick = close) { Text("关闭") } })
}

@Composable
private fun MerchantSelector(entries: List<LedgerEntry>, memories: List<MerchantMemory>, dismiss: () -> Unit, choose: (String) -> Unit) {
    var search by remember { mutableStateOf("") }
    val names = (memories.map { it.name } + entries.filter { it.transactionType == "expense" }.map { it.merchant }).distinctBy(::normalizeMerchantName)
        .filter { it.contains(search, ignoreCase = true) }.sortedWith(compareBy<String> { name ->
            val history = memories.filter { it.normalizedName == normalizeMerchantName(name) }
            when { history.any { it.isPinned } -> 0; history.any { it.isShortcut } -> 1; else -> 2 }
        }.thenBy { it })
    AlertDialog(onDismissRequest = dismiss, title = { Text("选择商户") }, text = { Column {
        OutlinedTextField(search, { search = it }, label = { Text("搜索商户") }, singleLine = true)
        Column(Modifier.heightIn(max = 350.dp).verticalScroll(rememberScrollState())) {
            var lastGroup = ""
            names.forEach { name ->
                val history = memories.filter { it.normalizedName == normalizeMerchantName(name) }
                val group = when { history.any { it.isPinned } -> "置顶商户"; history.any { it.isShortcut } -> "常用商户"; else -> "其他商户" }
                if(group != lastGroup) { Text(group, style = MaterialTheme.typography.titleSmall); lastGroup = group }
                TextButton(onClick = { choose(name) }) { Text(name) }
            }
        }
    } }, confirmButton = { TextButton(onClick = dismiss) { Text("关闭") } })
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun StackedBars(report: ExpenseReport, type: StatisticObject, category: String, memories: List<MerchantMemory>,
    selectedDay: LocalDate? = null, click: (LocalDate) -> Unit) {
    val dark = LocalAppDark.current
    val primary = MaterialTheme.colorScheme.primary
    val baseline = MaterialTheme.colorScheme.outlineVariant
    val important = memories.filter { it.category == category && (it.isPinned || it.useCount >= 3) }
        .take(5).map { it.normalizedName }.toSet()
    val segments = report.days.map { day ->
        if(type == StatisticObject.CATEGORY) day.entries.groupBy { if(normalizeMerchantName(it.merchant) in important) it.merchant else "其他" }.mapValues { (_, bills) -> bills.sumOf { it.personalExpenseCents } }
        else day.breakdown
    }
    fun color(label: String) = when(type) {
        StatisticObject.TOTAL -> CategoryColors.category(label, dark)
        StatisticObject.CATEGORY -> if(label == "其他") CategoryColors.category("其他", dark) else CategoryColors.merchant(label, dark)
        StatisticObject.MERCHANT -> CategoryColors.merchant(label, dark)
    }
    Text("每日支出（元） · 最高 ¥${displayNumber(report.highest)}", style = MaterialTheme.typography.bodySmall)
    Row(Modifier.fillMaxWidth()) {
        Column(Modifier.width(44.dp).height(170.dp), verticalArrangement = Arrangement.SpaceBetween) {
            listOf(report.highest, report.highest / 2, 0L).forEach {
                Text(chartAxisMoney(it), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
        }
        // Equal-width day slots keep all dates (including zero days) inside the viewport.
        // The painted bar uses the slot width minus its gap, with a slim upper limit.
        Row(Modifier.weight(1f).testTag("bars-plot")) {
            val labelStep = (report.days.size + 6) / 7
            report.days.forEachIndexed { index, day ->
                val isSelected = selectedDay == day.date
                Column(Modifier.weight(1f).testTag("bar-date:${day.date}")
                    .background(if(isSelected) primary.copy(alpha = 0.08f) else Color.Transparent, RoundedCornerShape(8.dp))
                    .clickable { click(day.date) }
                    .semantics { contentDescription = "${day.date} 个人支出 ${displayNumber(day.total)} 元"; selected = isSelected },
                    horizontalAlignment = Alignment.CenterHorizontally) {
                    Canvas(Modifier.fillMaxWidth().height(170.dp)) {
                        val gap = (if(report.days.size <= 7) 8.dp else 4.dp).toPx()
                        val width = (size.width - gap).coerceAtLeast(1f).coerceAtMost(28.dp.toPx())
                        val left = (size.width - width) / 2
                        val totalHeight = if(report.highest == 0L) 0f else (day.total.toDouble() / report.highest * size.height).toFloat()
                        if(totalHeight > 0f) {
                            val topRadius = minOf(6.dp.toPx(), width / 2, totalHeight / 2)
                            val bottomRadius = minOf(2.dp.toPx(), width / 2, totalHeight / 2)
                            val outline = Path().apply { addRoundRect(RoundRect(
                                left, size.height - totalHeight, left + width, size.height,
                                CornerRadius(topRadius), CornerRadius(topRadius), CornerRadius(bottomRadius), CornerRadius(bottomRadius))) }
                            // Clip the whole silhouette; internal segments meet with square edges.
                            clipPath(outline) {
                                var bottom = size.height
                                segments[index].toSortedMap().forEach { (label, amount) ->
                                    val height = (amount.toDouble() / report.highest * size.height).toFloat()
                                    drawRect(color(label), Offset(left, bottom - height), Size(width, height))
                                    bottom -= height
                                }
                            }
                        }
                        drawLine(baseline, Offset(0f, size.height), Offset(size.width, size.height))
                    }
                    Text(if(index % labelStep == 0 || index == report.days.lastIndex) day.date.dayOfMonth.toString() else "",
                        Modifier.heightIn(min = 28.dp).padding(top = 4.dp), style = MaterialTheme.typography.labelSmall,
                        color = if(isSelected || day.date == LocalDate.now()) primary else MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1)
                }
            }
        }
    }
    FlowRow(horizontalArrangement = Arrangement.spacedBy(AppDimensions.Page), verticalArrangement = Arrangement.spacedBy(AppDimensions.Small)) {
        segments.flatMap { it.keys }.distinct().forEach { label ->
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.size(6.dp).background(color(label), CircleShape))
                Text(label, style = MaterialTheme.typography.bodySmall)
            }
        }
    }
}

@Composable
internal fun Heatmap(report: ExpenseReport, base: Color, click: (LocalDate) -> Unit) {
    val tinted = CategoryColors.forTheme(base, LocalAppDark.current)
    val empty = MaterialTheme.colorScheme.surfaceContainerHighest
    Text("每日支出热力图 · 色阶参考 ¥${displayNumber(report.p90)}")
    Text("周一至周日，从上到下；左右滑动查看更多日期", style = MaterialTheme.typography.bodySmall)
    val first = report.days.first().date
    val offset = first.dayOfWeek.value - 1
    val count = (offset + report.days.size + 6) / 7
    Row {
    Column(Modifier.padding(top = 20.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
        listOf("一", "二", "三", "四", "五", "六", "日").forEach { Text(it, Modifier.height(48.dp).width(20.dp), style = MaterialTheme.typography.labelSmall) }
    }
    Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
        repeat(count) { week -> Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            val anchor = first.plusDays((week * 7L - offset).coerceAtLeast(0))
            Text(if(week == 0 || anchor.dayOfMonth <= 7) "${anchor.monthValue}月" else "", style = MaterialTheme.typography.labelSmall, modifier = Modifier.width(48.dp))
            repeat(7) { weekday ->
                val day = report.days.getOrNull(week * 7 + weekday - offset)
                if(day == null) Box(Modifier.size(48.dp))
                else {
                    val level = heatLevel(day.total, report.p90)
                    val background = if(level == 0) empty else lerp(empty, tinted, 0.16f + level * 0.168f)
                    Box(Modifier.size(48.dp).clickable { click(day.date) }
                        .semantics { contentDescription = "${day.date} 个人支出 ${displayNumber(day.total)} 元" }, contentAlignment = Alignment.Center) {
                        Box(Modifier.size(32.dp).background(background, MaterialTheme.shapes.small))
                    }
                }
            }
        } }
    }
    }
    Row(horizontalArrangement = Arrangement.spacedBy(5.dp)) {
        Text("少", style = MaterialTheme.typography.bodySmall)
        repeat(6) { level -> Box(Modifier.size(18.dp).background(if(level == 0) empty else lerp(empty, tinted, 0.16f + level * 0.168f), RoundedCornerShape(4.dp))) }
        Text("多", style = MaterialTheme.typography.bodySmall)
    }
}
