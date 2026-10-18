package cn.localledger.report

import cn.localledger.asset.*
import cn.localledger.data.*
import cn.localledger.parser.*
import java.math.BigDecimal
import java.math.RoundingMode
import java.time.*
import java.time.format.DateTimeFormatter

data class ReportOptions(val assets: Boolean = true, val accounts: Boolean = false,
    val income: Boolean = true, val categories: Boolean = true, val merchants: Boolean = true,
    val largeExpenses: Boolean = true, val periodic: Boolean = true, val details: Boolean = true,
    val merchantNames: Boolean = true, val notes: Boolean = false, val platforms: Boolean = false,
    val originalAmounts: Boolean = false, val anonymous: Boolean = false)
data class ReportRange(val startInclusive: Long, val endExclusive: Long)
data class ReportRequest(val type: String, val range: ReportRange, val options: ReportOptions = ReportOptions(),
    val purchaseName: String = "", val purchaseCents: Long? = null)
data class ReportData(val entries: List<LedgerEntry>, val incomeCategories: List<IncomeCategoryEntity>,
    val accounts: List<AssetAccount>, val snapshots: List<AssetSnapshot>)
data class PreparedReport(val content: String, val fileName: String, val generatedAt: Long,
    val transactionCount: Int, val range: ReportRange)

fun reportRange(choice: String, now: Long, lastReport: Long?, start: LocalDate, end: LocalDate,
    zone: ZoneId = ZoneId.systemDefault()): ReportRange {
    val today = Instant.ofEpochMilli(now).atZone(zone).toLocalDate()
    val first = when(choice) { "SINCE" -> today.withDayOfMonth(1); "MONTH" -> today.withDayOfMonth(1);
        "30" -> today.minusDays(29); "90" -> today.minusDays(89); else -> start }
    val last = if(choice == "CUSTOM") end else today
    require(!last.isBefore(first) && java.time.temporal.ChronoUnit.DAYS.between(first, last) <= 3660) { "日期范围不合法（最多3661天）" }
    val lower = if(choice == "SINCE" && lastReport != null) Math.addExact(lastReport, 1) else first.atStartOfDay(zone).toInstant().toEpochMilli()
    val upper = minOf(last.plusDays(1).atStartOfDay(zone).toInstant().toEpochMilli(), now + 1)
    require(lower >= 0 && lower < upper) { "当前范围没有新的时间区间，请选择本月或自定义" }
    return ReportRange(lower, upper)
}

object FinanceReport {
    fun generate(data: ReportData, request: ReportRequest, generatedAt: Long,
        zone: ZoneId = ZoneId.systemDefault()): PreparedReport {
        require(request.type in setOf("ANALYSIS", "PURCHASE"))
        require(request.range.startInclusive >= 0 && request.range.endExclusive > request.range.startInclusive && request.range.endExclusive <= generatedAt + 1)
        if(request.type == "PURCHASE") require(request.purchaseCents != null && request.purchaseCents in 1..MAX_BALANCE) { "请输入合法计划购买金额" }
        val o = request.options
        val range = request.range
        val rows = data.entries.filter { it.timestamp >= range.startInclusive && it.timestamp < range.endExclusive }.sortedWith(compareBy({ it.timestamp }, { it.id }))
        val expenses = rows.filter { it.transactionType == "expense" }
        val spending = expenses.sumOf { it.personalExpenseCents }; val income = trueIncome(rows)
        val assets = totalLiquidAssets(data.accounts)
        fun date(time: Long) = Instant.ofEpochMilli(time).atZone(zone).toLocalDate()
        fun stamp(time: Long) = Instant.ofEpochMilli(time).atZone(zone).format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss.SSS XXX"))
        val first = date(range.startInclusive); val last = date(range.endExclusive - 1)
        val days = java.time.temporal.ChronoUnit.DAYS.between(first, last) + 1
        fun money(cents: Long) = "¥${Money.format(cents)}"
        fun ratio(n: Long, denominator: Long, scale: Long = 1): String = if(denominator <= 0) "无法计算（基数为0）" else BigDecimal.valueOf(n).multiply(BigDecimal.valueOf(scale)).divide(BigDecimal.valueOf(denominator), 1, RoundingMode.HALF_UP).toPlainString()
        fun safe(value: String): String {
            var text = value.replace("\\", "\\\\").replace("\r", " ").replace("\n", " / ").replace("<", "&lt;").replace(">", "&gt;")
            listOf("|", "*", "_", "[", "]", "#", "`").forEach { text = text.replace(it, "\\$it") }
            return text
        }
        val aliases = linkedMapOf<String, String>()
        fun letter(index: Int): String { var n = index + 1; var s = ""; while(n > 0) { n--; s = ('A' + n % 26) + s; n /= 26 }; return s }
        expenses.filter { isLearnableMerchant(it.merchant) }.forEach { e -> val name = normalizeMerchantName(e.merchant)
            if(name !in aliases) aliases[name] = "商户${letter(aliases.size)}"
        }
        fun merchant(e: LedgerEntry) = if(!isLearnableMerchant(e.merchant)) "未知商户" else if(o.anonymous || !o.merchantNames) aliases.getValue(normalizeMerchantName(e.merchant)) else safe(e.merchant)
        val incomeNames = data.incomeCategories.associate { it.id to it.name }
        val text = buildString {
            appendLine(if(request.type == "PURCHASE") "# 购买决策财务报告" else "# 个人财务分析报告")
            appendLine("\n报告生成时间：${stamp(generatedAt)}")
            appendLine("统计区间：${stamp(range.startInclusive)} ～ ${stamp(range.endExclusive - 1)}（含首尾，时区 $zone）")
            appendLine("\n## 财务概览\n")
            if(o.assets) { appendLine("当前总可流动资产：${money(assets)}"); appendLine("最近盘点：${data.snapshots.filter { it.timestamp <= generatedAt }.maxByOrNull { it.timestamp }?.let { stamp(it.timestamp) } ?: "尚未盘点"}（当前余额可能含之后新增账户，不通过账本推算）") }
            if(o.income) appendLine("期间真正收入：${money(income)}")
            appendLine("期间个人实际支出：${money(spending)}")
            if(o.income) appendLine("期间净结余（收入−个人支出）：${money(income - spending)}（退款/报销另列）")
            appendLine("统计记录：${rows.size}条；支出${expenses.size}条")
            if(request.type == "PURCHASE") {
                val planned = request.purchaseCents!!
                appendLine("\n## 计划购买\n")
                appendLine("项目：${safe(request.purchaseName.ifBlank { "未填写" })}")
                appendLine("计划购买金额：${money(planned)}")
                if(o.assets) appendLine("计划金额占当前资产：${ratio(planned, assets, 100)}" + if(assets > 0) "%" else "")
                val avg = BigDecimal.valueOf(spending, 2).divide(BigDecimal.valueOf(days), 2, RoundingMode.HALF_UP)
                appendLine("日均个人支出：¥$avg（按范围内${days}个日历日，无消费日计0）")
                appendLine("计划金额相当于平均支出天数：" + if(spending > 0) ratio(planned, spending, days) + "天" else "无法计算（支出为0）")
            }
            if(o.income) {
                appendLine("\n## 收入汇总\n\n| 类别 | 金额 |\n| --- | ---: |")
                val grouped = rows.filter { it.transactionType == "income" }.groupBy { it.incomeCategoryId }
                data.incomeCategories.sortedBy { it.sortOrder }.forEach { c -> appendLine("| ${safe(c.name)} | ${money(grouped[c.id].orEmpty().sumOf { it.amount })} |") }
            }
            val refunds = rows.filter { it.transactionType == "refund" }.sumOf { it.amount }
            val reimbursements = rows.filter { it.transactionType == "reimbursement" }.sumOf { it.amount }
            val adjustments = rows.count { it.transactionType == "transfer_adjustment" }
            if(refunds > 0 || reimbursements > 0 || adjustments > 0) {
                appendLine("\n## 账务调整\n")
                appendLine("退款：${money(refunds)}；报销：${money(reimbursements)}（不属于真正收入，尚未关联抵扣原消费）")
                if(adjustments > 0) appendLine("AA回款/资金调整：${adjustments}条，不额外计入净变化，避免与AA已收回重复")
            }
            if(o.assets && o.accounts) {
                appendLine("\n## 各账户余额\n\n| 账户 | 类型 | 余额 | 是否计入当前总额 |\n| --- | --- | ---: | --- |")
                data.accounts.filterNot { it.isArchived }.sortedBy { it.displayOrder }.forEach { a -> appendLine("| ${safe(a.name)} | ${accountTypes[a.accountType]} | ${money(a.currentBalanceCents)} | ${if(a.isIncludedInTotal) "是" else "否"} |") }
            }
            if(o.categories) {
                appendLine("\n## 支出分类\n\n| 分类 | 金额 | 占比 | 次数 |\n| --- | ---: | ---: | ---: |")
                expenses.groupBy { it.category }.entries.sortedByDescending { it.value.sumOf { e -> e.personalExpenseCents } }.forEach { (category, values) ->
                    val sum = values.sumOf { it.personalExpenseCents }
                    appendLine("| ${safe(category)} | ${money(sum)} | ${if(spending > 0) ratio(sum, spending, 100) + "%" else "0.0%"} | ${values.size} |")
                }
            }
            if(o.merchants) {
                appendLine("\n## 主要商户（Top 10）\n\n| 商户 | 个人支出 | 次数 |\n| --- | ---: | ---: |")
                expenses.filter { isLearnableMerchant(it.merchant) }.groupBy { normalizeMerchantName(it.merchant) }.entries
                    .sortedWith(compareByDescending<Map.Entry<String, List<LedgerEntry>>> { it.value.sumOf { e -> e.personalExpenseCents } }.thenBy { it.key }).take(10).forEach { (_, values) ->
                        appendLine("| ${merchant(values.first())} | ${money(values.sumOf { it.personalExpenseCents })} | ${values.size} |")
                    }
                val unknown = expenses.filterNot { isLearnableMerchant(it.merchant) }
                appendLine("\n未知商户支出：${money(unknown.sumOf { it.personalExpenseCents })} / ${unknown.size}次（单独统计，不占排行）")
            }
            fun line(e: LedgerEntry): String {
                val amount = if(e.transactionType == "expense") e.personalExpenseCents else e.amount
                val category = if(e.transactionType == "income") incomeNames[e.incomeCategoryId] ?: "其他收入" else if(e.transactionType == "expense") e.category else flowTypes[e.transactionType].orEmpty()
                return "${stamp(e.timestamp)} | ${flowTypes[e.transactionType]} ${money(amount)} | ${safe(category)}" +
                    (if(e.transactionType == "expense") " | ${merchant(e)}" else "") +
                    (if(o.originalAmounts && e.transactionType == "expense") " | 原始支付 ${money(e.amount)}" else "") +
                    (if(o.platforms) " | ${when(e.platform) { "WeChat" -> "微信"; "Alipay" -> "支付宝"; else -> "手动" }}" else "") +
                    (if(o.notes && e.note.isNotBlank()) " | 备注 ${safe(e.note)}" else "")
            }
            if(o.largeExpenses) { appendLine("\n## 大额支出（个人承担 Top 10）\n"); expenses.sortedByDescending { it.personalExpenseCents }.take(10).forEach { appendLine("- ${line(it)}") } }
            if(o.periodic) {
                val daily = expenses.groupBy { date(it.timestamp) }.mapValues { (_, e) -> e.sumOf { it.personalExpenseCents } }
                appendLine(if(days <= 90) "\n## 每日支出\n" else "\n## 每周支出（从范围开始按7天分段）\n")
                var cursor = first
                while(!cursor.isAfter(last)) {
                    val finish = if(days <= 90) cursor else minOf(cursor.plusDays(6), last)
                    var sum = 0L; var d = cursor
                    while(!d.isAfter(finish)) { sum += daily[d] ?: 0; d = d.plusDays(1) }
                    appendLine("- $cursor" + if(finish != cursor) " ～ $finish | ${money(sum)}" else " | ${money(sum)}")
                    cursor = finish.plusDays(1)
                }
            }
            if(o.assets) {
                appendLine("\n## 资产变化\n")
                val sorted = data.snapshots.filter { it.timestamp < range.endExclusive }.sortedWith(compareBy({ it.timestamp }, { it.createdAt }, { it.id }))
                val before = sorted.lastOrNull { it.timestamp < range.startInclusive }
                val inRange = sorted.filter { it.timestamp >= range.startInclusive }
                val beginning = before ?: inRange.firstOrNull(); val ending = inRange.lastOrNull()
                if(beginning == null || ending == null || beginning.id == ending.id || ending.timestamp <= beginning.timestamp) appendLine("该时间段缺少足够的资产快照，无法可靠计算资产变化。")
                else {
                    val result = compareAssets(beginning, ending, data.entries)
                    appendLine("期初附近资产：${money(beginning.totalBalanceCents)}（${stamp(beginning.timestamp)}）")
                    appendLine("范围内最新盘点：${money(ending.totalBalanceCents)}（${stamp(ending.timestamp)}）")
                    appendLine("真实资产变化：${money(result.actualChange)}")
                    // Income privacy must also hold for the snapshot comparison, which can span outside report dates.
                    if(o.income) { appendLine("账本推算变化：${money(result.recordedNetChange)}"); appendLine("账本与资产差额：${money(result.difference)}") }
                    else appendLine("收入汇总已关闭，隐藏账本推算和差额，避免透露收入。")
                    appendLine("差额采用上述实际两次快照之间(previous,current]的账本记录，可能与报告区间不同；包含退款/报销，不额外加AA回款。账户范围变化和回款时间差也可能造成差额。")
                }
            }
            if(o.details) { appendLine("\n## 交易明细\n"); rows.filter { o.income || it.transactionType != "income" }.forEach { appendLine("- ${line(it)}") } }
            appendLine("\n## 给 AI 的分析目标\n")
            appendLine(if(request.type == "PURCHASE") "请结合这些记录分析计划支出的财务影响、风险及需关注因素，不替用户决定是否购买。" else "请分析消费结构、频率、大额记录和资产变化，指出数据局限及需要进一步核对的因素，不把差额直接认定为漏记。")
            appendLine("\n## 数据说明\n")
            appendLine("- 本报告由本地记账App整理生成，App不调用AI、不联网、不自动上传。")
            appendLine("- 支出使用个人实际消费金额，AA使用个人承担；净结余是收入−个人支出，不是完整现金流。")
            appendLine("- 退款、报销和AA回款不属于真正收入。")
            appendLine("- 部分支付可能因通知权限或支付渠道限制未被自动记录，历史收入可能需人工复核。")
            appendLine("- 资产来自手动盘点/初始余额，可能未及时更新；账本与资产差额不应直接解释为漏记金额。")
            appendLine("- 商户匿名仅替换商户字段；自定义类别、购买名称、主动包含的备注及账户名请自行脱敏。")
        }
        val fileName = (if(request.type == "PURCHASE") "Purchase_Report" else "Finance_Report") + "_${first}_to_${last}.md"
        return PreparedReport(text, fileName, generatedAt, rows.size, range)
    }
}
