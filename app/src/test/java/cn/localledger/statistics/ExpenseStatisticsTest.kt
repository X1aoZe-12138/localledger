package cn.localledger.statistics

import cn.localledger.data.*
import cn.localledger.ui.splitEntry
import org.junit.Assert.*
import org.junit.Test
import java.time.*

class ExpenseStatisticsTest {
    private val zone = ZoneId.of("Asia/Shanghai")
    private val start = LocalDate.of(2026, 9, 27)
    private fun bill(amount: Long, day: LocalDate = start, category: String = "餐饮", merchant: String = "食堂") = LedgerEntry(amount = amount, timestamp = day.atStartOfDay(zone).toInstant().toEpochMilli(), category = category, merchant = merchant)
    @Test fun equal100By3() { assertEquals(3334L, SplitExpense.equal(10000, 3)) }
    @Test fun equal20By3() { assertEquals(666L, SplitExpense.equal(2000, 3)) }
    @Test fun equal100By2() { assertEquals(5000L, SplitExpense.equal(10000, 2)) }
    @Test fun received160() { assertEquals(4000L, splitEntry(20000, 3, "", "160")!!.personalExpenseCents) }
    @Test fun self53() { assertEquals(5300L, splitEntry(20000, 3, "53", "")!!.personalExpenseCents) }
    @Test fun offPreservesOriginal() { assertEquals(20000L, bill(20000).personalExpenseCents) }
    @Test fun zeroAndInvalidMoney() {
        assertEquals(0L, splitEntry(20000, 3, "0", "")!!.personalExpenseCents)
        assertEquals(0L, splitEntry(20000, 3, "", "200")!!.personalExpenseCents)
        listOf("-1", "1.234", "NaN", "1e2").forEach { assertNull(splitEntry(20000, 3, it, "")) }
        assertNotNull(SplitExpense.validationError(splitEntry(20000, 3, "201", "")!!))
        assertNotNull(SplitExpense.validationError(splitEntry(20000, 3, "", "201")!!))
        assertNotNull(SplitExpense.validationError(splitEntry(20000, 3, "40", "160")!!))
        assertNotNull(SplitExpense.validationError(splitEntry(2, 4, "", "")!!))
    }
    @Test fun invalidModesCountAndIncome() {
        val valid = splitEntry(20000, 3, "", "")!!
        assertNotNull(SplitExpense.validationError(valid.copy(participantCount = 1)))
        assertNotNull(SplitExpense.validationError(valid.copy(participantCount = 100)))
        assertNotNull(SplitExpense.validationError(valid.copy(splitCalculationMode = "NONE")))
        assertNotNull(SplitExpense.validationError(valid.copy(transactionType = "income")))
    }
    @Test fun personalTotal140Not300() {
        val split = bill(20000).copy(splitEnabled = true, splitCalculationMode = "MANUAL_RECEIVED", receivedAmountCents = 16000)
        val report = ExpenseStatistics.report(listOf(bill(10000), split, bill(99900).copy(transactionType = "income")), start, start, zone = zone)
        assertEquals(14000L, report.total); assertEquals(14000L, report.average); assertEquals(14000L, report.highest)
        assertEquals(20000L, split.amount)
    }
    @Test fun categoryMerchantAndCrossMonthInclusiveDates() {
        val end = LocalDate.of(2026, 10, 2)
        val entries = listOf(bill(10000), bill(5000, end, "生活", "超市"), bill(900, end.plusDays(1)), bill(100, start.minusDays(1)))
        assertEquals(15000L, ExpenseStatistics.report(entries, start, end, zone = zone).total)
        assertEquals(10000L, ExpenseStatistics.report(entries, start, end, StatisticObject.CATEGORY, "餐饮", zone).total)
        assertEquals(5000L, ExpenseStatistics.report(entries, start, end, StatisticObject.MERCHANT, "超 市", zone).total)
        val report = ExpenseStatistics.report(entries, start, end, zone = zone)
        assertEquals(6, report.days.size); assertEquals(0L, report.days[1].total); assertEquals(2500L, report.average)
    }
    @Test fun heatmapBoundaryAndP90Outlier() {
        assertFalse(ExpenseStatistics.report(emptyList(), start, start.plusDays(13)).useHeatmap)
        assertTrue(ExpenseStatistics.report(emptyList(), start, start.plusDays(14)).useHeatmap)
        val normal = (1..19).map { 1000L * it }
        assertEquals(18000L, percentile90(normal + 500000L))
        assertEquals(0, heatLevel(0, 18000)); assertEquals(5, heatLevel(500000, 18000))
        assertEquals(3, heatLevel(9000, 18000)); assertEquals(1, heatLevel(1, 18000))
        assertEquals(0L, percentile90(emptyList())); assertEquals(100L, percentile90(listOf(0, 100)))
    }
}
