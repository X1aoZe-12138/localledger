package cn.localledger.data

import java.math.BigDecimal
import java.math.RoundingMode

object SplitExpense {
    fun parseNonNegative(text: String): Long? = runCatching {
        if (!Regex("[0-9]{1,9}(\\.[0-9]{1,2})?").matches(text.trim())) return null
        BigDecimal(text.trim()).movePointRight(2).longValueExact()
    }.getOrNull()

    fun validationError(entry: LedgerEntry): String? {
        if (!entry.splitEnabled) return if (entry.splitCalculationMode != "NONE" || entry.manualSelfAmountCents != null || entry.receivedAmountCents != null) "关闭 AA 后请清空分摊参数" else null
        if (entry.transactionType != "expense") return "AA 仅适用于支出账单"
        if (entry.participantCount !in 2..99) return "人数必须为 2–99 人"
        if (entry.manualSelfAmountCents != null && entry.receivedAmountCents != null) return "实际承担和已收回只能填写一种"
        val self = entry.manualSelfAmountCents
        val received = entry.receivedAmountCents
        return when (entry.splitCalculationMode) {
            "MANUAL_SELF" -> if (self == null || self !in 0..entry.amount || received != null) "我实际承担必须在 0 与原始支付金额之间" else null
            "MANUAL_RECEIVED" -> if (received == null || received !in 0..entry.amount || self != null) "我已收回必须在 0 与原始支付金额之间" else null
            "EQUAL" -> if (self != null || received != null) "平均分摊不能同时填写手动金额" else if (equal(entry.amount, entry.participantCount) < 0) "金额过小，平均分摊会产生负数，请填写实际承担" else null
            else -> "请选择有效的 AA 计算方式"
        }
    }

    // Integer half-up rounding, matching the user's observed group collection behavior.
    fun equal(amount: Long, count: Int): Long {
        require(amount >= 0 && count in 2..99)
        val share = amount / count + if ((amount % count) * 2 >= count) 1 else 0
        return amount - share * (count - 1)
    }

    fun personal(entry: LedgerEntry): Long {
        if (entry.transactionType != "expense") return 0
        if (!entry.splitEnabled) return entry.amount
        require(validationError(entry) == null) { validationError(entry).orEmpty() }
        return when (entry.splitCalculationMode) {
            "MANUAL_SELF" -> entry.manualSelfAmountCents!!
            "MANUAL_RECEIVED" -> entry.amount - entry.receivedAmountCents!!
            else -> equal(entry.amount, entry.participantCount)
        }
    }

    fun dailyAverage(total: Long, days: Int): Long = BigDecimal.valueOf(total)
        .divide(BigDecimal.valueOf(days.toLong()), 0, RoundingMode.HALF_UP).longValueExact()
}

val LedgerEntry.personalExpenseCents: Long get() = SplitExpense.personal(this)
