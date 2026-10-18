package cn.localledger.ui.design

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.border
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.gestures.snapping.SnapPosition
import androidx.compose.foundation.gestures.snapping.rememberSnapFlingBehavior
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.DialogProperties
import kotlinx.coroutines.launch
import java.time.*

data class DateRangeOption(val id: String, val label: String, val start: LocalDate?, val end: LocalDate?)
data class DateRangeSelection(val preset: String, val start: LocalDate?, val end: LocalDate?)

/** Local calendar dates never pass through DatePicker's UTC-millis representation. */
fun calendarDays(month: YearMonth): List<LocalDate?> {
    val leading = month.atDay(1).dayOfWeek.value - 1 // Monday = 0.
    return List(42) { position -> (position - leading + 1).takeIf { it in 1..month.lengthOfMonth() }?.let(month::atDay) }
}

fun replaceLocalDate(timestamp: Long, day: LocalDate, zone: ZoneId = ZoneId.systemDefault()): Long =
    LocalDateTime.of(day, Instant.ofEpochMilli(timestamp).atZone(zone).toLocalTime()).atZone(zone).toInstant().toEpochMilli()

fun replaceLocalTime(timestamp: Long, time: LocalTime, zone: ZoneId = ZoneId.systemDefault()): Long =
    LocalDateTime.of(Instant.ofEpochMilli(timestamp).atZone(zone).toLocalDate(), time.withSecond(0).withNano(0))
        .atZone(zone).toInstant().toEpochMilli()

fun validClock(hour: String, minute: String): LocalTime? {
    if(hour.isBlank() || minute.isBlank() || hour.length > 2 || minute.length > 2 ||
        !hour.all(Char::isDigit) || !minute.all(Char::isDigit)) return null
    val h = hour.toIntOrNull() ?: return null
    val m = minute.toIntOrNull() ?: return null
    return if(h in 0..23 && m in 0..59) LocalTime.of(h, m) else null
}

@Composable
private fun PickerSurface(title: String, dismiss: () -> Unit, confirm: () -> Unit, enabled: Boolean,
    scrollable: Boolean = true,
    content: @Composable ColumnScope.() -> Unit) {
    AlertDialog(onDismissRequest = dismiss,
        properties = DialogProperties(dismissOnBackPress = true, dismissOnClickOutside = true),
        shape = RoundedCornerShape(20.dp),
        title = { Text(title, style = MaterialTheme.typography.titleLarge) },
        text = { Column(Modifier.fillMaxWidth().heightIn(max = 540.dp)
            .then(if(scrollable) Modifier.verticalScroll(rememberScrollState()) else Modifier),
            verticalArrangement = Arrangement.spacedBy(AppDimensions.Small), content = content) },
        confirmButton = { Button(onClick = confirm, enabled = enabled,
            modifier = Modifier.heightIn(min = AppDimensions.Touch).testTag("picker-confirm")) { Text("确定") } },
        dismissButton = { TextButton(onClick = dismiss, modifier = Modifier.heightIn(min = AppDimensions.Touch)) { Text("取消") } })
}

@Composable
fun CompactDatePicker(initial: LocalDate, dismiss: () -> Unit, confirm: (LocalDate) -> Unit,
    today: LocalDate = LocalDate.now(), minDate: LocalDate = LocalDate.of(1970, 1, 1)) {
    var draft by rememberSaveable { mutableStateOf(initial.toString()) }
    var month by rememberSaveable { mutableStateOf(YearMonth.from(initial).toString()) }
    val selected = LocalDate.parse(draft)
    PickerSurface("选择日期", dismiss, { confirm(selected) }, !selected.isBefore(minDate)) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(AppDimensions.Small)) {
            listOf("今天" to today, "昨天" to today.minusDays(1), "前天" to today.minusDays(2)).forEach { (label, day) ->
                FilterChip(selected == day, onClick = { draft = day.toString(); month = YearMonth.from(day).toString() },
                    enabled = !day.isBefore(minDate), label = { Text(label) }, modifier = Modifier.testTag("date-quick:$label"))
            }
        }
        CalendarMonth(YearMonth.parse(month), selected, null, today, minDate,
            { month = it.toString() }, { draft = it.toString() })
    }
}

@Composable
fun CompactDateRangePicker(initial: DateRangeSelection, options: List<DateRangeOption>, dismiss: () -> Unit,
    confirm: (DateRangeSelection) -> Unit, today: LocalDate = LocalDate.now(),
    minDate: LocalDate = LocalDate.of(1970, 1, 1)) {
    var preset by rememberSaveable { mutableStateOf(initial.preset) }
    var startText by rememberSaveable { mutableStateOf(initial.start?.toString().orEmpty()) }
    var endText by rememberSaveable { mutableStateOf(initial.end?.toString().orEmpty()) }
    var month by rememberSaveable { mutableStateOf(YearMonth.from(initial.start ?: today).toString()) }
    val start = startText.takeIf(String::isNotBlank)?.let(LocalDate::parse)
    val end = endText.takeIf(String::isNotBlank)?.let(LocalDate::parse)
    val custom = preset == "CUSTOM"
    val valid = !custom || start != null && end != null && !start.isAfter(end)
    PickerSurface("选择时间范围", dismiss, { confirm(DateRangeSelection(preset, start, end)) }, valid) {
        @OptIn(ExperimentalLayoutApi::class)
        FlowRow(horizontalArrangement = Arrangement.spacedBy(AppDimensions.Small)) {
            options.forEach { option ->
                FilterChip(preset == option.id, onClick = {
                    preset = option.id
                    if(option.id != "CUSTOM") {
                        startText = option.start?.toString().orEmpty(); endText = option.end?.toString().orEmpty()
                    } else if(start == null || end == null) {
                        startText = today.minusDays(6).toString(); endText = today.toString()
                    }
                    month = YearMonth.from(startText.takeIf(String::isNotBlank)?.let(LocalDate::parse) ?: today).toString()
                }, label = { Text(option.label) }, modifier = Modifier.testTag("range-quick:${option.id}"))
            }
        }
        if(custom) {
            Text("开始：${start ?: "请选择"}    结束：${end ?: "请选择"}",
                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.testTag("range-selection"))
            CalendarMonth(YearMonth.parse(month), start, end, today, minDate,
                { month = it.toString() }, { day ->
                    if(start == null || end != null || day.isBefore(start)) { startText = day.toString(); endText = "" }
                    else endText = day.toString()
                })
            Text("先选择开始日期，再选择结束日期；再次点击同一天可选单日。",
                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        } else Text(if(start == null) options.firstOrNull { it.id == preset }?.label ?: "全部时间"
            else "$start${end?.let { " — $it" }.orEmpty()}",
            style = MaterialTheme.typography.bodyMedium, modifier = Modifier.testTag("range-selection"))
    }
}

@Composable
private fun CalendarMonth(month: YearMonth, start: LocalDate?, end: LocalDate?, today: LocalDate,
    minDate: LocalDate, changeMonth: (YearMonth) -> Unit, choose: (LocalDate) -> Unit) {
    var jumping by rememberSaveable { mutableStateOf(false) }
    if(jumping) {
        var year by rememberSaveable(month) { mutableStateOf(month.year.toString()) }
        var selectedMonth by rememberSaveable(month) { mutableIntStateOf(month.monthValue) }
        Text("跳转到年月", style = MaterialTheme.typography.titleMedium)
        OutlinedTextField(year, { if(it.length <= 4 && it.all(Char::isDigit)) year = it },
            label = { Text("年份") }, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
            singleLine = true, modifier = Modifier.fillMaxWidth().testTag("picker-year"))
        @OptIn(ExperimentalLayoutApi::class)
        FlowRow(horizontalArrangement = Arrangement.spacedBy(AppDimensions.Tiny)) {
            (1..12).forEach { value -> FilterChip(selectedMonth == value, { selectedMonth = value },
                label = { Text("${value}月") }, modifier = Modifier.testTag("picker-month:$value")) }
        }
        TextButton(enabled = year.toIntOrNull() in 1970..2100, onClick = {
            changeMonth(YearMonth.of(year.toInt(), selectedMonth)); jumping = false
        }, modifier = Modifier.testTag("picker-jump")) { Text("跳转到该月") }
    } else {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween) {
            TextButton(onClick = { changeMonth(month.minusMonths(1)) }, enabled = month > YearMonth.from(minDate),
                modifier = Modifier.widthIn(min = AppDimensions.Touch).testTag("picker-prev-month")) { Text("‹") }
            TextButton(onClick = { jumping = true }, modifier = Modifier.testTag("picker-month-heading")) {
                Text("${month.year}年${month.monthValue}月", style = MaterialTheme.typography.titleMedium)
            }
            TextButton(onClick = { changeMonth(month.plusMonths(1)) }, enabled = month.year < 2100 || month.monthValue < 12,
                modifier = Modifier.widthIn(min = AppDimensions.Touch).testTag("picker-next-month")) { Text("›") }
        }
        Row(Modifier.fillMaxWidth()) {
            listOf("一", "二", "三", "四", "五", "六", "日").forEach { day ->
                Text(day, Modifier.weight(1f), textAlign = TextAlign.Center,
                    style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        calendarDays(month).chunked(7).forEach { week -> Row(Modifier.fillMaxWidth()) {
            week.forEach { day ->
                val chosen = day != null && (day == start || day == end)
                val between = day != null && start != null && end != null && day.isAfter(start) && day.isBefore(end)
                val enabled = day != null && !day.isBefore(minDate)
                Box(Modifier.weight(1f).height(AppDimensions.Touch)
                    .then(if(day != null) Modifier.testTag("calendar-day:$day") else Modifier)
                    .background(if(chosen) MaterialTheme.colorScheme.primaryContainer else if(between) MaterialTheme.colorScheme.secondaryContainer.copy(alpha = .6f) else Color.Transparent,
                        RoundedCornerShape(12.dp))
                    .then(if(enabled) Modifier.clickable { choose(day!!) } else Modifier)
                    .semantics { if(day != null) { contentDescription = "$day${if(day == today) "，今天" else ""}${if(chosen) "，已选中" else ""}"; selected = chosen } },
                    contentAlignment = Alignment.Center) {
                    if(day != null) Text(day.dayOfMonth.toString(),
                        color = if(!enabled) MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = .4f)
                            else if(chosen) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurface,
                        fontWeight = if(chosen) FontWeight.SemiBold else FontWeight.Normal)
                }
            }
        } }
    }
}

@Composable
fun CompactTimePicker(initial: LocalTime, dismiss: () -> Unit, confirm: (LocalTime) -> Unit) {
    var inputMode by rememberSaveable { mutableStateOf(false) }
    var hourText by rememberSaveable { mutableStateOf(initial.hour.toString().padStart(2, '0')) }
    var minuteText by rememberSaveable { mutableStateOf(initial.minute.toString().padStart(2, '0')) }
    val hourState = rememberLazyListState(initialFirstVisibleItemIndex = initial.hour)
    val minuteState = rememberLazyListState(initialFirstVisibleItemIndex = initial.minute)
    val hour = centeredValue(hourState, 24)
    val minute = centeredValue(minuteState, 60)
    val selected = if(inputMode) validClock(hourText, minuteText) else LocalTime.of(hour, minute)
    PickerSurface("选择时间", dismiss, { selected?.let(confirm) },
        selected != null && (inputMode || !hourState.isScrollInProgress && !minuteState.isScrollInProgress),
        scrollable = false) {
        Text("24 小时制", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        if(inputMode) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(AppDimensions.Small, Alignment.CenterHorizontally)) {
                ClockNumberField("小时", hourText, { hourText = it }, hourText.toIntOrNull() !in 0..23,
                    "time-input-hour", Modifier.width(96.dp))
                Text(":", style = MaterialTheme.typography.titleLarge)
                ClockNumberField("分钟", minuteText, { minuteText = it }, minuteText.toIntOrNull() !in 0..59,
                    "time-input-minute", Modifier.width(96.dp))
            }
            if(selected == null) Text("请输入 00–23 小时、00–59 分钟", color = MaterialTheme.colorScheme.error,
                style = MaterialTheme.typography.bodySmall)
        } else {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
                Text("小时", style = MaterialTheme.typography.bodySmall)
                Text("分钟", style = MaterialTheme.typography.bodySmall)
            }
            Box(Modifier.fillMaxWidth().height(240.dp)) {
                Box(Modifier.fillMaxWidth().height(48.dp).align(Alignment.Center)
                    .background(MaterialTheme.colorScheme.primaryContainer.copy(alpha = .55f), RoundedCornerShape(12.dp)))
                Row(Modifier.fillMaxSize(), verticalAlignment = Alignment.CenterVertically) {
                    NumberWheel(24, hourState, hour, "小时", Modifier.weight(1f))
                    Text(":", style = MaterialTheme.typography.titleLarge)
                    NumberWheel(60, minuteState, minute, "分钟", Modifier.weight(1f))
                }
            }
            Text("${hour.toString().padStart(2, '0')}:${minute.toString().padStart(2, '0')}",
                Modifier.fillMaxWidth().testTag("time-wheel-selection"), textAlign = TextAlign.Center,
                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        TextButton(onClick = {
            if(!inputMode) {
                hourText = hour.toString().padStart(2, '0'); minuteText = minute.toString().padStart(2, '0')
            }
            inputMode = !inputMode
        }, modifier = Modifier.align(Alignment.CenterHorizontally).testTag("time-mode-toggle")) {
            Text(if(inputMode) "返回滚轮" else "键盘输入")
        }
    }
}

@Composable
private fun ClockNumberField(label: String, value: String, change: (String) -> Unit, invalid: Boolean, tag: String,
    modifier: Modifier = Modifier) {
    Column(modifier, horizontalAlignment = Alignment.CenterHorizontally) {
        Text(label, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        BasicTextField(value, { if(it.length <= 2 && it.all(Char::isDigit)) change(it) },
            singleLine = true, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
            textStyle = MaterialTheme.typography.titleLarge.copy(
                color = if(invalid) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurface,
                textAlign = TextAlign.Center),
            modifier = Modifier.fillMaxWidth().height(56.dp).testTag(tag)
                .border(1.dp, if(invalid) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.outline,
                    RoundedCornerShape(12.dp)),
            decorationBox = { inner -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { inner() } })
    }
}

@Composable
private fun centeredValue(state: androidx.compose.foundation.lazy.LazyListState, count: Int): Int {
    val halfItem = with(LocalDensity.current) { 24.dp.roundToPx() }
    return remember(state, halfItem) { derivedStateOf {
        // Content padding places firstVisibleItemIndex in the center row. The
        // half-row threshold picks whichever number is visually nearest.
        (state.firstVisibleItemIndex + if(state.firstVisibleItemScrollOffset >= halfItem) 1 else 0)
            .coerceIn(0, count - 1)
    } }.value
}

@Composable
private fun NumberWheel(count: Int, state: androidx.compose.foundation.lazy.LazyListState, selected: Int,
    label: String, modifier: Modifier = Modifier) {
    val scope = rememberCoroutineScope()
    LazyColumn(modifier.height(240.dp).testTag("time-wheel:$label"), state = state,
        contentPadding = PaddingValues(vertical = 96.dp), flingBehavior = rememberSnapFlingBehavior(state, SnapPosition.Center),
        horizontalAlignment = Alignment.CenterHorizontally) {
        items((0 until count).toList()) { value ->
            val active = value == selected
            Box(Modifier.fillMaxWidth().height(48.dp).clickable { scope.launch { state.animateScrollToItem(value) } }
                .testTag("time-$label:$value")
                .semantics { contentDescription = "$label ${value.toString().padStart(2, '0')}${if(active) "，已选中" else ""}"; this.selected = active },
                contentAlignment = Alignment.Center) {
                Text(value.toString().padStart(2, '0'), style = if(active) MaterialTheme.typography.titleLarge else MaterialTheme.typography.bodyLarge,
                    fontWeight = if(active) FontWeight.SemiBold else FontWeight.Normal,
                    color = if(active) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = .65f))
            }
        }
    }
}
