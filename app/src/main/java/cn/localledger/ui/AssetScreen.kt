package cn.localledger.ui

import cn.localledger.ui.design.*
import androidx.compose.runtime.saveable.mapSaver
import androidx.compose.runtime.snapshots.SnapshotStateMap
import androidx.compose.ui.Alignment
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.ui.semantics.*
import androidx.compose.ui.text.style.TextOverflow
import kotlin.math.abs
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import cn.localledger.asset.*
import cn.localledger.data.LedgerEntry
import cn.localledger.parser.Money
import java.math.BigDecimal
import java.time.*
import java.time.format.DateTimeFormatter

fun parseAssetBalance(value: String): Long? = runCatching {
    val clean = value.trim()
    if(!Regex("[0-9]{1,9}(\\.[0-9]{1,2})?").matches(clean)) return null
    BigDecimal(clean).movePointRight(2).longValueExact().takeIf { it in 0..MAX_BALANCE }
}.getOrNull()
private fun assetTime(time: Long) = Instant.ofEpochMilli(time).atZone(ZoneId.systemDefault()).format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"))
private fun signed(value: Long) = (if(value > 0) "+" else "") + displayMoney(value)

@Composable
fun AssetScreen(vm: AssetViewModel, entries: List<LedgerEntry>) {
    val accounts by vm.accounts.collectAsStateWithLifecycle()
    val snapshots by vm.snapshots.collectAsStateWithLifecycle()
    val items by vm.items.collectAsStateWithLifecycle()
    val busy by vm.busy.collectAsStateWithLifecycle()
    var mode by rememberSaveable { mutableStateOf("overview") }
    var snapshotId by rememberSaveable { mutableStateOf<String?>(null) }
    var accountId by rememberSaveable { mutableStateOf<String?>(null) }
    var templateName by rememberSaveable { mutableStateOf("") }
    var templateType by rememberSaveable { mutableStateOf("OTHER") }
    var removing by remember { mutableStateOf<AssetAccount?>(null) }
    var showArchived by rememberSaveable { mutableStateOf(false) }
    ScreenBack(mode != "overview") { if(!busy) mode = "overview" }
    val snapshot = snapshots.firstOrNull { it.id == snapshotId }
    val historyItems = items.filter { it.snapshotId == snapshotId }
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).imePadding().padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        when(mode) {
            "account" -> AssetAccountForm(accounts.firstOrNull { it.id == accountId }, templateName, templateType, busy) { name, type, included, note, balance ->
                vm.account(name, type, included, note, balance, accountId) { mode = "overview" }
            }
            "inventory" -> {
                val rows = if(snapshotId == null) accounts.filterNot { it.isArchived }.map { BalanceRow(it.id, it.name, it.currentBalanceCents, it.isIncludedInTotal) }
                    else historyItems.map { BalanceRow(it.accountId, it.accountNameSnapshot, it.balanceCents, it.includedInTotal) }
                if(snapshotId != null && snapshot == null) Text("盘点不存在或已被恢复替换，请返回概览")
                else InventoryForm(snapshotId, rows, snapshot?.note.orEmpty(), snapshot?.timestamp, busy) { balances, note -> vm.inventory(snapshotId, balances, note) { mode = "overview" } }
            }
            "detail" -> if(snapshot == null) Text("盘点不存在或已被恢复替换") else {
                Text(assetTime(snapshot.timestamp), style = MaterialTheme.typography.titleLarge)
                Text("盘点总资产 ¥${displayNumber(snapshot.totalBalanceCents)}", style = MaterialTheme.typography.headlineSmall)
                historyItems.forEach { item -> Text("${item.accountNameSnapshot}  ¥${displayNumber(item.balanceCents)}" + if(!item.includedInTotal) "（未计入）" else "") }
                Text("备注：${snapshot.note.ifBlank { "无" }}")
                Button(enabled = !busy, onClick = { mode = "inventory" }) { Text("编辑这次盘点") }
                Text("历史账户名称与计入范围保留。编辑最新盘点同步当前余额；编辑较老盘点不影响当前余额。", style = MaterialTheme.typography.bodySmall)
                val index = snapshots.indexOfFirst { it.id == snapshot.id }
                snapshots.getOrNull(index + 1)?.let { ComparisonCard(it, snapshot, entries) }
            }
            else -> {
                AssetSummary(accounts, snapshots)
                Button(enabled = !busy && accounts.any { !it.isArchived }, onClick = { snapshotId = null; mode = "inventory" }) { Text("更新资产") }
                Text("账户", style = MaterialTheme.typography.titleLarge)
                Button(enabled = !busy, onClick = { accountId = null; templateName = ""; templateType = "OTHER"; mode = "account" }) { Text("新增账户") }
                if(accounts.isEmpty()) {
                    Text("可选模板（不会自动创建银行账户）")
                    Row { listOf("微信" to "WECHAT", "支付宝" to "ALIPAY", "现金" to "CASH").forEach { (name, type) ->
                        TextButton(enabled = !busy, onClick = { accountId = null; templateName = name; templateType = type; mode = "account" }) { Text(name) }
                    } }
                }
                accounts.filter { !it.isArchived || showArchived }.forEach { a ->
                    var menu by remember(a.id) { mutableStateOf(false) }
                    Row(Modifier.fillMaxWidth().heightIn(min = 64.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(AppDimensions.Medium)) {
                        Icon(if(a.accountType == "BANK") AppIcons.Bank else AppIcons.Wallet, null, tint = MaterialTheme.colorScheme.primary)
                        Column(Modifier.weight(1f)) {
                            Text(a.name + if(a.isArchived) "（已归档）" else "", maxLines = 1, overflow = TextOverflow.Ellipsis)
                            Text("${accountTypes[a.accountType]}" + if(!a.isIncludedInTotal || a.isArchived) " · 未计入总资产" else "", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        Text(displayMoney(a.currentBalanceCents), style = MaterialTheme.typography.titleMedium)
                        Box {
                            IconButton(enabled = !busy, onClick = { menu = true }) { Icon(AppIcons.Chevron, "${a.name}账户操作") }
                            DropdownMenu(menu, { menu = false }) {
                                DropdownMenuItem(text = { Text("编辑") }, onClick = { menu = false; accountId = a.id; mode = "account" })
                                DropdownMenuItem(text = { Text("上移") }, onClick = { menu = false; vm.move(a.id, -1) })
                                DropdownMenuItem(text = { Text("下移") }, onClick = { menu = false; vm.move(a.id, 1) })
                                DropdownMenuItem(text = { Text(if(a.isArchived) "恢复账户" else if(items.any { it.accountId == a.id }) "归档" else "删除", color = if(a.isArchived) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.error) }, onClick = { menu = false; if(a.isArchived) vm.unarchive(a.id) else removing = a })
                            }
                        }
                    }
                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = .4f))
                }
                if(accounts.any { it.isArchived }) TextButton(onClick = { showArchived = !showArchived }) { Text(if(showArchived) "隐藏归档账户" else "显示归档账户") }
                HorizontalDivider()
                Text("资产变化", style = MaterialTheme.typography.titleLarge)
                if(snapshots.size >= 2) {
                    Text("最近盘点 ¥${displayNumber(snapshots[0].totalBalanceCents)} · 上次 ¥${displayNumber(snapshots[1].totalBalanceCents)}")
                    Text("变化 ${signed(snapshots[0].totalBalanceCents - snapshots[1].totalBalanceCents)}")
                    ComparisonCard(snapshots[1], snapshots[0], entries)
                }
                AssetTrend(snapshots)
                snapshots.forEach { s -> OutlinedButton(enabled = !busy, onClick = { snapshotId = s.id; mode = "detail" }, modifier = Modifier.fillMaxWidth()) {
                    Text("${assetTime(s.timestamp)}  ¥${displayNumber(s.totalBalanceCents)}")
                } }
            }
        }
        if(busy) CircularProgressIndicator()
    }
    removing?.let { a -> val hasHistory = items.any { it.accountId == a.id }
        AlertDialog(onDismissRequest = { removing = null }, title = { Text(if(hasHistory) "归档${a.name}？" else "删除${a.name}？") },
            text = { Text(if(hasHistory) "历史盘点完整保留，账户不再显示或计入当前总资产，可以恢复账户。" else "该账户无历史盘点，删除后无法撤销。") },
            confirmButton = { TextButton(enabled = !busy, onClick = { vm.remove(a.id); removing = null }) { Text("确认", color = MaterialTheme.colorScheme.error) } },
            dismissButton = { TextButton(onClick = { removing = null }) { Text("取消") } })
    }
}

@Composable
private fun AssetAccountForm(account: AssetAccount?, templateName: String, templateType: String, busy: Boolean,
    save: (String, String, Boolean, String, Long) -> Unit) {
    var name by rememberSaveable(account?.id) { mutableStateOf(account?.name ?: templateName) }
    var type by rememberSaveable(account?.id) { mutableStateOf(account?.accountType ?: templateType) }
    var included by rememberSaveable(account?.id) { mutableStateOf(account?.isIncludedInTotal ?: true) }
    var note by rememberSaveable(account?.id) { mutableStateOf(account?.note.orEmpty()) }
    var balance by rememberSaveable(account?.id) { mutableStateOf("0.00") }
    var error by remember { mutableStateOf(false) }
    Text(if(account == null) "新增账户" else "编辑账户", style = MaterialTheme.typography.titleLarge)
    OutlinedTextField(name, { name = it.take(100) }, label = { Text("账户名称") }, singleLine = true, modifier = Modifier.fillMaxWidth())
    Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(AppDimensions.Small)) {
        accountTypes.forEach { (key, label) -> FilterChip(type == key, { type = key }, label = { Text(label) }) }
    }
    Row { Checkbox(included, { included = it }); Text("计入总可流动资产", modifier = Modifier.padding(top = 12.dp)) }
    OutlinedTextField(note, { note = it.take(1000) }, label = { Text("备注") }, modifier = Modifier.fillMaxWidth())
    if(account == null) OutlinedTextField(balance, { balance = it; error = false }, label = { Text("当前余额（元，可为0）") }, isError = error, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal))
    else Text("当前余额 ¥${displayNumber(account.currentBalanceCents)}；余额请在资产盘点中修改。")
    if(error) Text("请填写名称及合法非负余额，最多两位小数、九位整数", color = MaterialTheme.colorScheme.error)
    Button(enabled = !busy, onClick = { val cents = if(account == null) parseAssetBalance(balance) else account.currentBalanceCents
        if(name.isBlank() || cents == null) error = true else save(name, type, included, note, cents)
    }) { Text("保存账户") }
    if(account == null) Text("创建账户不会生成全局盘点；请随后点击更新资产。", style = MaterialTheme.typography.bodySmall)
}

private data class BalanceRow(val id: String, val name: String, val balance: Long, val included: Boolean)
@Composable
private fun InventoryForm(id: String?, rows: List<BalanceRow>, originalNote: String, timestamp: Long?, busy: Boolean, save: (Map<String, Long>, String) -> Unit) {
    val inputs = rememberSaveable(id, rows.map { it.id }, saver = mapSaver<SnapshotStateMap<String, String>>(save = { it.toMap() }, restore = { it.map { (k, v) -> k to (v as String) }.toMutableStateMap() })) { mutableStateMapOf<String, String>().apply { rows.forEach { put(it.id, Money.format(it.balance)) } } }
    var note by rememberSaveable(id) { mutableStateOf(originalNote) }
    var error by remember { mutableStateOf(false) }
    val amounts = rows.associate { it.id to parseAssetBalance(inputs[it.id].orEmpty()) }
    Text(if(id == null) "资产盘点" else "编辑历史盘点", style = MaterialTheme.typography.titleLarge)
    Text(timestamp?.let(::assetTime) ?: "保存时记录当前时间")
    rows.forEach { row -> OutlinedTextField(inputs[row.id].orEmpty(), { inputs[row.id] = it; error = false },
        label = { Text(row.name + if(!row.included) "（未计入总资产）" else "") },
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal), singleLine = true,
        isError = error && amounts[row.id] == null, modifier = Modifier.fillMaxWidth()) }
    Text("总资产：" + if(amounts.values.any { it == null }) "金额尚未填完整" else "¥${displayNumber(rows.filter { it.included }.sumOf { amounts[it.id]!! })}")
    OutlinedTextField(note, { note = it.take(1000) }, label = { Text("盘点备注") }, modifier = Modifier.fillMaxWidth())
    if(error) Text("所有账户请填写非负余额（含未计入账户），最多两位小数、九位整数", color = MaterialTheme.colorScheme.error)
    Button(enabled = !busy && rows.isNotEmpty(), onClick = {
        if(amounts.values.any { it == null }) error = true else save(amounts.mapValues { it.value!! }, note)
    }) { Text(if(id == null) "保存本次盘点" else "保存修改") }
}

@Composable
private fun ComparisonCard(previous: AssetSnapshot, current: AssetSnapshot, entries: List<LedgerEntry>) {
    val result = compareAssets(previous, current, entries)
    Card(Modifier.fillMaxWidth(), elevation = CardDefaults.cardElevation(defaultElevation = AppElevation.Card)) { Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(AppDimensions.Small)) {
        Text("账本与资产变化对比", style = MaterialTheme.typography.titleMedium)
        Text("${assetTime(previous.timestamp)} ～ ${assetTime(current.timestamp)}", style = MaterialTheme.typography.bodySmall)
        Text("真实变化 ${signed(result.actualChange)}")
        Text("真正收入 ¥${displayNumber(result.income)} · 个人支出 ¥${displayNumber(result.expense)}")
        Text("退款 ¥${displayNumber(result.refunds)} · 报销 ¥${displayNumber(result.reimbursements)}")
        Text("账本净变化 ${signed(result.recordedNetChange)}")
        Text("账本与资产变化差额 ${signed(result.difference)}", style = MaterialTheme.typography.titleMedium)
        Text("可能来自未记录交易、账户转账、统计范围变化或余额调整。AA使用个人承担，回款未到账时也可能产生时间差。", style = MaterialTheme.typography.bodySmall)
        if(result.skippedAdjustments > 0) Text("区间有${result.skippedAdjustments}条AA回款/资金调整未额外计入，避免与AA已收回重复；请人工核对。", style = MaterialTheme.typography.bodySmall)
    } }
}

@Composable
fun AssetTrend(snapshots: List<AssetSnapshot>) {
    Text("资产趋势", style = MaterialTheme.typography.titleMedium)
    if(snapshots.isEmpty()) { EmptyState("还没有资产盘点", "添加账户后点「更新资产」，记录余额即可查看变化。"); return }
    val ordered = snapshots.reversed()
    val low = ordered.minOf { it.totalBalanceCents }; val high = ordered.maxOf { it.totalBalanceCents }
    Text("最高 ¥${displayNumber(high)} · 最低 ¥${displayNumber(low)}", style = MaterialTheme.typography.bodySmall)
    var selectedId by rememberSaveable { mutableStateOf<String?>(null) }
    val selected = ordered.firstOrNull { it.id == selectedId } ?: ordered.last()
    Text("${assetTime(selected.timestamp)} · ${displayMoney(selected.totalBalanceCents)}", style = MaterialTheme.typography.titleSmall)
    Row(Modifier.horizontalScroll(rememberScrollState())) { ordered.forEach { point ->
        TextButton(onClick = { selectedId = point.id }) { Text(Instant.ofEpochMilli(point.timestamp).atZone(ZoneId.systemDefault()).format(DateTimeFormatter.ofPattern("MM-dd"))) }
    } }
    val color = MaterialTheme.colorScheme.primary
    Canvas(Modifier.fillMaxWidth().height(160.dp).semantics { contentDescription = "资产趋势，最近余额 ${displayMoney(ordered.last().totalBalanceCents)}。可通过日期按钮选择盘点。" }.pointerInput(ordered) {
        detectTapGestures { tap ->
            val pad = 12.dp.toPx(); val width = size.width - pad * 2
            val start = ordered.first().timestamp; val span = ordered.last().timestamp - start
            selectedId = ordered.minBy { point -> abs(tap.x - (pad + width * (if(span > 0) (point.timestamp - start).toDouble() / span else .5).toFloat())) }.id
        }
    }) {
        val pad = 12.dp.toPx(); val width = size.width - pad * 2; val height = size.height - pad * 2
        val start = ordered.first().timestamp; val span = ordered.last().timestamp - start
        val points = ordered.mapIndexed { index, s -> Offset(pad + width * (if(span > 0) (s.timestamp - start).toDouble() / span else if(ordered.size > 1) index.toDouble() / (ordered.size - 1) else 0.5).toFloat(),
            pad + height * (if(high > low) 1.0 - (s.totalBalanceCents - low).toDouble() / (high - low) else 0.5).toFloat()) }
        points.zipWithNext().forEach { (a, b) -> drawLine(color, a, b, strokeWidth = 2.dp.toPx()) }
        val index = ordered.indexOf(selected)
        points.getOrNull(index)?.let { drawCircle(color, 4.dp.toPx(), it) }
    }
    Text("${assetTime(ordered.first().timestamp)} ～ ${assetTime(ordered.last().timestamp)}", style = MaterialTheme.typography.bodySmall)
    Text("仅显示实际盘点点位，连接线不代表中间日期真实余额。点击下面历史记录查看详情。", style = MaterialTheme.typography.bodySmall)
}

@Composable
fun AssetSummary(accounts: List<AssetAccount>, snapshots: List<AssetSnapshot>) {
    Card(Modifier.fillMaxWidth(), elevation = CardDefaults.cardElevation(defaultElevation = AppElevation.Card), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer)) {
        Column(Modifier.padding(AppDimensions.Page), verticalArrangement = Arrangement.spacedBy(AppDimensions.Small)) {
            Text("总可流动资产", style = MaterialTheme.typography.titleMedium)
            Text(displayMoney(totalLiquidAssets(accounts)), style = MaterialTheme.typography.headlineLarge)
            if(snapshots.size >= 2) Text("较上次盘点 " + signed(snapshots[0].totalBalanceCents - snapshots[1].totalBalanceCents), style = MaterialTheme.typography.bodyMedium)
            Text(snapshots.firstOrNull()?.let { "${assetTime(it.timestamp)} 更新" } ?: "还没有资产盘点", style = MaterialTheme.typography.bodySmall)
            snapshots.firstOrNull()?.let { latest ->
                val days = ((System.currentTimeMillis() - latest.timestamp) / 86_400_000L).coerceAtLeast(0)
                if(days >= 30) Text("已${days}天未盘点 · 建议更新资产", style = MaterialTheme.typography.bodySmall, color = LocalFinanceColors.current.warning)
            }
            Text("余额以手动确认为准", style = MaterialTheme.typography.bodySmall)
        }
    }
    if(accounts.isEmpty()) EmptyState("还没有资产账户", "添加账户并记录余额，就可以查看总可流动资产。")
}
