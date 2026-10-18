package cn.localledger.ui

import cn.localledger.data.LedgerEntry
import cn.localledger.statistics.ExpenseStatistics
import org.junit.Assert.*
import org.junit.Test
import java.time.*

class LedgerFilterTest {
    private val zone = ZoneId.of("Asia/Shanghai")
    private val today = LocalDate.of(2026, 10, 17)
    private fun bill(id: Long, date: LocalDate, cents: Long, category: String = "餐饮", merchant: String = "食堂",
        type: String = "expense", time: LocalTime = LocalTime.NOON) = LedgerEntry(id = id, amount = cents,
        timestamp = date.atTime(time).atZone(zone).toInstant().toEpochMilli(), category = category, merchant = merchant,
        transactionType = type)
    private val sept = LocalDate.of(2026, 9, 29)
    private val bills = listOf(
        bill(1, sept, 1690, merchant = "麦当劳", time = LocalTime.MIDNIGHT),
        bill(2, sept, 2800, merchant = "麦 当 劳", time = LocalTime.of(23, 59, 59)),
        bill(3, sept, 12000, merchant = "餐厅").copy(splitEnabled = true, splitCalculationMode = "MANUAL_RECEIVED", receivedAmountCents = 8000),
        bill(4, sept, 500000, type = "income"), bill(5, sept, 10000, type = "refund"),
        bill(6, today, 3500, "购物", "淘宝"),
        bill(7, today, 1800, "待分类", "未知商户"),
        bill(8, today.minusDays(6), 700, "交通", "公交"),
        bill(9, today.minusDays(7), 900, "餐饮", "食堂"),
        bill(10, LocalDate.of(2025, 12, 31), 1100))

    @Test fun dailyPersonalSpendingMatchesExistingStatisticSemantics() {
        val onDay = LedgerFilterState(LedgerDatePreset.CUSTOM, sept, sept).select(bills, today, zone)
        assertEquals(8490L, personalSpending(onDay))
        assertEquals(8490L, ExpenseStatistics.report(bills, sept, sept, zone = zone).total)
        assertEquals(5300L, personalSpending(LedgerFilterState(LedgerDatePreset.CUSTOM, today, today).select(bills, today, zone)))
        assertEquals(0L, personalSpending(listOf(bill(30, today, 100, type = "income"), bill(31, today, 100, type = "refund"))))
    }

    @Test fun allAndQuickRangesUseInclusiveLocalCalendarDays() {
        assertEquals(10, LedgerFilterState().select(bills, today, zone).size)
        assertEquals(setOf(6L, 7L, 8L), LedgerFilterState(LedgerDatePreset.LAST_7).select(bills, today, zone).map { it.id }.toSet())
        assertEquals(4, LedgerFilterState(LedgerDatePreset.THIS_MONTH).select(bills, today, zone).size)
        assertEquals(5, LedgerFilterState(LedgerDatePreset.PREVIOUS_MONTH).select(bills, today, zone).size)
        assertEquals(9, LedgerFilterState(LedgerDatePreset.LAST_30).select(bills, today, zone).size)
    }

    @Test fun customRangeIncludesBothEndsAcrossMonthsAndYears() {
        val through = LocalDate.of(2026, 10, 1)
        val result = LedgerFilterState(LedgerDatePreset.CUSTOM, sept, through).select(bills, today, zone)
        assertEquals((1L..5L).toList(), result.map { it.id })
        val crossYear = LedgerFilterState(LedgerDatePreset.CUSTOM, LocalDate.of(2025, 12, 30), LocalDate.of(2026, 1, 2))
        assertEquals(listOf(10L), crossYear.select(bills, today, zone).map { it.id })
    }

    @Test fun categoryMerchantAndAllThreeConditionsAreAndNotOr() {
        val food = LedgerFilterState(category = "餐饮")
        assertEquals(setOf(1L, 2L, 3L, 9L, 10L), food.select(bills, today, zone).map { it.id }.toSet())
        assertEquals(setOf(1L, 2L), LedgerFilterState(merchant = "麦当劳").select(bills, today, zone).map { it.id }.toSet())
        assertEquals(setOf(1L, 2L), food.copy(merchant = "麦当劳").select(bills, today, zone).map { it.id }.toSet())
        assertEquals(listOf(1L, 2L), food.copy(preset = LedgerDatePreset.CUSTOM, start = sept, end = sept,
            merchant = "麦当劳").select(bills, today, zone).map { it.id })
        assertTrue(food.copy(preset = LedgerDatePreset.THIS_MONTH, merchant = "麦当劳").select(bills, today, zone).isEmpty())
    }

    @Test fun unknownMerchantCanBeSelectedAndClearingRestoresAll() {
        val selected = LedgerFilterState(merchant = "未知商户")
        assertEquals(listOf(7L), selected.select(bills, today, zone).map { it.id })
        assertEquals(10, LedgerFilterState().select(bills, today, zone).size)
        assertFalse(LedgerFilterState().active)
        assertTrue(selected.active)
    }
}
