package cn.localledger.statistics

import cn.localledger.data.*
import cn.localledger.parser.normalizeMerchantName
import java.time.*
import java.time.temporal.ChronoUnit

enum class StatisticObject { TOTAL, CATEGORY, MERCHANT }
data class DayExpense(val date: LocalDate, val total: Long, val breakdown: Map<String, Long>, val entries: List<LedgerEntry>)
data class ExpenseReport(val days: List<DayExpense>) {
    val total = days.sumOf { it.total }
    val average = SplitExpense.dailyAverage(total, days.size)
    val highest = days.maxOfOrNull { it.total } ?: 0
    val useHeatmap get() = days.size >= 15
    val p90: Long get() = percentile90(days.map { it.total })
}

fun percentile90(values: List<Long>): Long {
    val sorted = values.filter { it > 0 }.sorted()
    if (sorted.isEmpty()) return 0
    // Nearest-rank P90: ceil(0.9 * n), without floating-point money calculations.
    return sorted[((sorted.size.toLong() * 9 + 9) / 10).toInt() - 1]
}
fun heatLevel(amount: Long, p90: Long): Int {
    if (amount <= 0 || p90 <= 0) return 0
    if (amount >= p90) return 5
    return ((amount * 5 + p90 - 1) / p90).toInt().coerceIn(1, 5)
}

object ExpenseStatistics {
    fun report(entries: List<LedgerEntry>, start: LocalDate, end: LocalDate,
        objectType: StatisticObject = StatisticObject.TOTAL, selected: String = "",
        zone: ZoneId = ZoneId.systemDefault()): ExpenseReport {
        require(!end.isBefore(start) && ChronoUnit.DAYS.between(start, end) < 3660) { "日期范围必须为 1–3660 天" }
        val matching = entries.filter { entry ->
            entry.transactionType == "expense" && when (objectType) {
                StatisticObject.TOTAL -> true
                StatisticObject.CATEGORY -> entry.category == selected
                StatisticObject.MERCHANT -> normalizeMerchantName(entry.merchant) == normalizeMerchantName(selected)
            }
        }.groupBy { Instant.ofEpochMilli(it.timestamp).atZone(zone).toLocalDate() }
        val days = generateSequence(start) { it.plusDays(1) }.takeWhile { !it.isAfter(end) }.map { day ->
            val bills = matching[day].orEmpty()
            val breakdown = bills.groupBy { if(objectType == StatisticObject.TOTAL) it.category else it.merchant }
                .mapValues { (_, values) -> values.sumOf { it.personalExpenseCents } }
            DayExpense(day, bills.sumOf { it.personalExpenseCents }, breakdown, bills)
        }.toList()
        return ExpenseReport(days)
    }
}
