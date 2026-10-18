package cn.localledger.ui

import cn.localledger.data.LedgerEntry
import cn.localledger.data.personalExpenseCents
import cn.localledger.parser.normalizeMerchantName
import java.time.Instant
import java.time.LocalDate
import java.time.YearMonth
import java.time.ZoneId

enum class LedgerDatePreset { ALL, LAST_7, LAST_30, THIS_MONTH, PREVIOUS_MONTH, CUSTOM }

data class LedgerFilterState(
    val preset: LedgerDatePreset = LedgerDatePreset.ALL,
    val start: LocalDate? = null,
    val end: LocalDate? = null,
    val category: String = "",
    val merchant: String = ""
) {
    val active get() = preset != LedgerDatePreset.ALL || category.isNotBlank() || merchant.isNotBlank()

    fun bounds(today: LocalDate): Pair<LocalDate?, LocalDate?> = when(preset) {
        LedgerDatePreset.ALL -> null to null
        LedgerDatePreset.LAST_7 -> today.minusDays(6) to today
        LedgerDatePreset.LAST_30 -> today.minusDays(29) to today
        LedgerDatePreset.THIS_MONTH -> today.withDayOfMonth(1) to today
        LedgerDatePreset.PREVIOUS_MONTH -> YearMonth.from(today).minusMonths(1).let { it.atDay(1) to it.atEndOfMonth() }
        LedgerDatePreset.CUSTOM -> start to end
    }

    fun select(entries: List<LedgerEntry>, today: LocalDate = LocalDate.now(), zone: ZoneId = ZoneId.systemDefault()): List<LedgerEntry> {
        val (from, through) = bounds(today)
        val merchantKey = normalizeMerchantName(merchant)
        return entries.filter { entry ->
            val date = Instant.ofEpochMilli(entry.timestamp).atZone(zone).toLocalDate()
            (from == null || !date.isBefore(from)) && (through == null || !date.isAfter(through)) &&
                (category.isBlank() || entry.transactionType == "expense" && entry.category == category) &&
                (merchantKey.isBlank() || entry.transactionType == "expense" && normalizeMerchantName(entry.merchant) == merchantKey)
        }
    }
}

/** Same personalExpenseCents used by ExpenseStatistics; income and return flows are excluded. */
fun personalSpending(entries: List<LedgerEntry>): Long = entries.asSequence()
    .filter { it.transactionType == "expense" }.sumOf { it.personalExpenseCents }
