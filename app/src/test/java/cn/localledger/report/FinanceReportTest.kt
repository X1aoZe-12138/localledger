package cn.localledger.report

import android.app.Application
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import cn.localledger.asset.*
import cn.localledger.data.*
import java.time.*

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], application = Application::class)
class FinanceReportTest {
    private val zone = ZoneId.of("UTC")
    private fun time(value: String) = Instant.parse(value).toEpochMilli()
    private val now = time("2026-09-27T20:00:00Z")
    private val start = time("2026-09-01T00:00:00Z")
    private val defaults = defaultIncomeCategories.mapIndexed { i, (id, name) -> IncomeCategoryEntity(id, name, i) }
    private fun entry(id: Long, amount: Long, type: String = "expense", merchant: String = "食堂", category: String = "餐饮") = LedgerEntry(
        id = id, timestamp = start + id * 1000, createdAt = start, amount = amount, transactionType = type,
        merchant = merchant, category = category, incomeCategoryId = if(type == "income") "salary" else null,
        platform = "WeChat", note = "notification raw text extras com.tencent.mm TEST_ACCOUNT_ID SECRET_NOTE")
    private fun data(entries: List<LedgerEntry>) = ReportData(entries, defaults,
        listOf(AssetAccount("bank", "测试账户A", "BANK", 2260000, createdAt = start, updatedAt = start)), emptyList())
    private fun generate(data: ReportData, options: ReportOptions = ReportOptions(), type: String = "ANALYSIS", purchase: Long? = null): PreparedReport = FinanceReport.generate(data,
        ReportRequest(type, ReportRange(start, now + 1), options, "显示器", purchase), now, zone)

    @Test fun financialSemanticsUse8000Income400ExpenseWithRefundAndReimbursementSeparate() {
        val d = data(listOf(entry(1, 800000, "income"), entry(2, 10000), entry(3, 20000, category = "数码"),
            entry(4, 30000).copy(splitEnabled = true, splitCalculationMode = "MANUAL_SELF", manualSelfAmountCents = 10000),
            entry(5, 5000, "refund"), entry(6, 8000, "reimbursement")))
        val report = generate(d).content
        assertTrue(report.contains("期间真正收入：¥8000.00")); assertTrue(report.contains("期间个人实际支出：¥400.00"))
        assertTrue(report.contains("期间净结余（收入−个人支出）：¥7600.00"))
        assertTrue(report.contains("退款：¥50.00；报销：¥80.00"))
        assertTrue(report.contains("| 数码 | ¥200.00 | 50.0% | 1 |"))
        assertFalse(report.contains("期间个人实际支出：¥600.00"))
        assertFalse(report.contains("原始支付")); assertTrue(report.contains("AA使用个人承担"))
    }
    @Test fun merchantsAggregateCountsAndUnknownDoesNotOccupyTop10() {
        val report = generate(data(listOf(entry(1, 2000), entry(2, 3000), entry(3, 4000), entry(4, 5000, merchant = "麦当劳"), entry(5, 10000, merchant = "未知商户")))).content
        assertTrue(report.contains("| 食堂 | ¥90.00 | 3 |")); assertTrue(report.contains("| 麦当劳 | ¥50.00 | 1 |"))
        assertTrue(report.contains("未知商户支出：¥100.00 / 1次")); assertFalse(report.contains("| 未知商户 |"))
    }
    @Test fun defaultPrivacyNeverIncludesNotesPlatformsBankDetailsOrLegacyFields() {
        val report = generate(data(listOf(entry(1, 10000), entry(2, 800000, "income").copy(legacyMerchant = "私密工资单位", legacyCategory = "订单号666")))).content
        listOf("notification raw text", "extras", "com.tencent.mm", "TEST_ACCOUNT_ID", "SECRET_NOTE", "私密工资单位", "订单号666", "WeChat").forEach { assertFalse(it, report.contains(it)) }
        assertFalse(report.contains("各账户余额")); assertTrue(report.contains("当前总可流动资产：¥22600.00"))
    }
    @Test fun explicitOptionsIncludeNotesAccountsPlatformsAndOriginalAmounts() {
        val report = generate(data(listOf(entry(1, 30000).copy(splitEnabled = true, splitCalculationMode = "MANUAL_SELF", manualSelfAmountCents = 10000))),
            ReportOptions(accounts = true, notes = true, platforms = true, originalAmounts = true)).content
        assertTrue(report.contains("测试账户A")); assertTrue(report.contains("SECRET")); assertTrue(report.contains("| 微信"))
        assertTrue(report.contains("原始支付 ¥300.00")); assertTrue(report.contains("支出 ¥100.00"))
    }
    @Test fun anonymousAliasesAreConsistentAcrossRankingsLargeExpensesAndDetailsAndNormalizedNames() {
        val report = generate(data(listOf(entry(1, 2000, merchant = "麦当劳"), entry(2, 3000, merchant = "食堂"), entry(3, 4000, merchant = "麦 当 劳"))), ReportOptions(anonymous = true)).content
        assertFalse(report.contains("麦当劳")); assertFalse(report.contains("麦 当 劳")); assertFalse(report.contains("食堂"))
        assertTrue(report.contains("| 商户A | ¥60.00 | 2 |")); assertTrue(report.contains("| 商户B | ¥30.00 | 1 |"))
        assertTrue(report.lines().count { it.contains("| 商户A") } >= 5)
        val hidden = generate(data(listOf(entry(1, 10000, merchant = "秘密公司"))), ReportOptions(merchantNames = false)).content
        assertFalse(hidden.contains("秘密公司")); assertTrue(hidden.contains("商户A"))
    }
    @Test fun incomeAndAssetSwitchesSuppressDerivedMetricsAndIncomeDetailsEverywhere() {
        val d = data(listOf(entry(1, 800000, "income"), entry(2, 10000))).copy(snapshots = listOf(
            AssetSnapshot("a", start - 1000, 2000000, createdAt = start - 1000), AssetSnapshot("b", now, 2260000, createdAt = now)))
        val report = generate(d, ReportOptions(assets = false, accounts = true, income = false), "PURCHASE", 500000).content
        listOf("¥8000.00", "¥22600.00", "¥7900.00", "测试账户A", "资产变化\n", "计划金额占当前资产", "收入汇总\n").forEach { assertFalse(it, report.contains(it)) }
        assertFalse(report.contains("| 收入 ¥"))
        val withAssets = generate(d, ReportOptions(income = false)).content
        assertFalse(withAssets.contains("账本推算变化：")); assertFalse(withAssets.contains("账本与资产差额："))
    }
    @Test fun assetsUseNearestSnapshotBeforeStartAndLatestInsideRangeNotFuture() {
        val d = data(emptyList()).copy(snapshots = listOf(
            AssetSnapshot("old", start - 86400000, 1900000, createdAt = start - 86400000),
            AssetSnapshot("near", start - 1000, 2000000, createdAt = start - 1000),
            AssetSnapshot("latest", now, 2260000, createdAt = now),
            AssetSnapshot("future", now + 1000, 9999999, createdAt = now + 1000)))
        val report = generate(d).content
        assertTrue(report.contains("期初附近资产：¥20000.00")); assertTrue(report.contains("真实资产变化：¥2600.00"))
        assertFalse(report.contains("¥99999.99")); assertFalse(report.contains("期初附近资产：¥19000.00"))
        assertTrue(generate(d.copy(snapshots = d.snapshots.filter { it.id == "latest" })).content.contains("缺少足够的资产快照"))
        val noBefore = d.copy(snapshots = listOf(AssetSnapshot("first", start + 1, 2000000, createdAt = start), AssetSnapshot("last", now, 2260000, createdAt = now)))
        assertTrue(generate(noBefore).content.contains("真实资产变化：¥2600.00"))
    }
    @Test fun shortRangeIncludesZeroDaysAndLongRangeUsesWeeklyWithoutTruncating1001Rows() {
        val small = generate(data(listOf(entry(1, 1000)))).content
        assertTrue(small.contains("2026-09-02 | ¥0.00"))
        val longStart = time("2026-01-01T00:00:00Z")
        val entries = (1L..1001L).map { entry(it, 100).copy(timestamp = longStart + it * 1000) }
        val report = FinanceReport.generate(data(entries), ReportRequest("ANALYSIS", ReportRange(longStart, now + 1)), now, zone)
        assertEquals(1001, report.transactionCount); assertTrue(report.content.contains("## 每周支出")); assertFalse(report.content.contains("## 每日支出"))
        assertTrue(report.content.contains("期间个人实际支出：¥1001.00")); assertEquals(1001, report.content.substringAfter("## 交易明细").substringBefore("## 给 AI").lines().count { it.startsWith("- ") })
    }
    @Test fun purchaseMetricsUseExactMoneyAndHandleZeroBasesWithoutConclusions() {
        val d = data(listOf(entry(1, 270000))).copy(accounts = listOf(AssetAccount("a", "现金", "CASH", 2000000, createdAt = start, updatedAt = start)))
        val report = generate(d, type = "PURCHASE", purchase = 500000).content
        assertTrue(report.contains("计划金额占当前资产：25.0%")); assertTrue(report.contains("日均个人支出：¥100.00"))
        assertTrue(report.contains("平均支出天数：50.0天"))
        listOf("可以买", "不能买", "财务健康", "财务危险").forEach { assertFalse(report.contains(it)) }
        val empty = generate(data(emptyList()).copy(accounts = emptyList()), type = "PURCHASE", purchase = 100).content
        assertTrue(empty.contains("无法计算（基数为0）")); assertTrue(empty.contains("无法计算（支出为0）"))
        assertTrue(runCatching { generate(d, type = "PURCHASE", purchase = 0) }.isFailure)
    }
    @Test fun rangeDefaultsSinceCutoffAndCustomRespectExactBoundariesAndDst() {
        val today = LocalDate.of(2026, 9, 27)
        assertEquals(start, reportRange("SINCE", now, null, today, today, zone).startInclusive)
        assertEquals(start + 1001, reportRange("SINCE", now, start + 1000, today, today, zone).startInclusive)
        assertEquals(time("2026-08-29T00:00:00Z"), reportRange("30", now, null, today, today, zone).startInclusive)
        val custom = reportRange("CUSTOM", now, null, today.minusDays(1), today.minusDays(1), zone)
        assertEquals(time("2026-09-26T00:00:00Z"), custom.startInclusive); assertEquals(time("2026-09-27T00:00:00Z"), custom.endExclusive)
        val d = data(listOf(entry(1, 100).copy(timestamp = custom.startInclusive), entry(2, 200).copy(timestamp = custom.endExclusive)))
        val report = FinanceReport.generate(d, ReportRequest("ANALYSIS", custom), now, zone)
        assertEquals(1, report.transactionCount)
        assertTrue(runCatching { reportRange("CUSTOM", now, null, today, today.minusDays(1), zone) }.isFailure)
        val dstDate = LocalDate.of(2026, 3, 8)
        val dst = reportRange("CUSTOM", now, null, dstDate, dstDate, ZoneId.of("America/New_York"))
        assertEquals(23L * 60 * 60 * 1000, dst.endExclusive - dst.startInclusive)
    }
    @Test fun userTextCannotInjectMarkdownSectionsAndDetailToggleSuppressesAllRows() {
        val report = generate(data(listOf(entry(1, 1000, merchant = "店|<script>\n#标题"))), ReportOptions(details = false, largeExpenses = false)).content
        assertFalse(report.contains("<script>")); assertFalse(report.contains("\n#标题")); assertFalse(report.contains("## 交易明细"))
        assertTrue(report.contains("&lt;script&gt;")); assertTrue(report.contains("店\\|"))
    }
}
