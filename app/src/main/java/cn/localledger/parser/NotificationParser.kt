package cn.localledger.parser

import java.math.BigDecimal
import java.security.MessageDigest
import java.text.Normalizer
import java.util.Locale

const val ALIPAY = "com.eg.android.AlipayGphone"
const val WECHAT = "com.tencent.mm"
val categories = listOf("待分类", "餐饮", "交通", "生活", "购物", "娱乐", "数码", "其他")
val learnableCategories get() = categories.filterNot { it == "待分类" }

fun normalizeMerchantName(value: String): String = Normalizer.normalize(value.trim(), Normalizer.Form.NFKC)
    .lowercase(Locale.ROOT).replace(Regex("\\s+"), "")

fun isLearnableMerchant(value: String): Boolean = normalizeMerchantName(value).let { it.isNotBlank() && it != normalizeMerchantName("未知商户") }

data class NotificationInput(
    val packageName: String, val title: String = "", val text: String = "",
    val bigText: String = "", val subText: String = "", val extraText: String = "",
    val timestamp: Long = 0, val key: String = ""
) {
    val content get() = listOf(title, text, bigText, subText, extraText)
        .filter { it.isNotBlank() }.distinct().joinToString("\n")
}
data class ParsedTransaction(val amount: Long, val transactionType: String, val merchant: String,
    val platform: String, val timestamp: Long)

object Money {
    // Cap individual values so aggregate totals remain practical; reject rounding and overflow.
    fun parse(value: String): Long? = runCatching {
        val clean = value.trim()
        if (!Regex("[0-9]{1,9}(\\.[0-9]{1,2})?").matches(clean)) return null
        BigDecimal(clean).movePointRight(2).longValueExact().takeIf { it > 0 }
    }.getOrNull()
    fun format(cents: Long): String = BigDecimal.valueOf(cents, 2).toPlainString()
}

interface PlatformParser { fun parse(input: NotificationInput): ParsedTransaction? }
class AlipayNotificationParser : PlatformParser {
    override fun parse(input: NotificationInput): ParsedTransaction? {
        if (input.packageName != ALIPAY || !TrustedSource.accepts(input)) return null
        val autoDebit = candidateTexts(input).mapNotNull { text ->
            AUTO_DEBIT.matchEntire(text.trim())?.let { match ->
                val merchant = match.groupValues[1].trim()
                val amount = Money.parse(match.groupValues[2].replace(",", ""))
                if (merchant.isBlank() || merchant.length > 100 || amount == null) null else merchant to amount
            }
        }.distinct()
        if (autoDebit.size == 1) {
            val (merchant, amount) = autoDebit.single()
            return ParsedTransaction(amount, "expense", merchant, "Alipay", input.timestamp)
        }
        if (autoDebit.size > 1) return null
        if (input.title == "交易提醒" && input.text.trimStart().startsWith("你有一笔") && input.text.contains("元的支出")) {
            // Bind the amount to its transaction clause, never to the promotional suffix.
            val match = Regex("""^你有一笔\s*((?:\d{1,3}(?:,\d{3})+|\d+)(?:\.\d{1,2})?)\s*元的支出(?:$|[，,。；;\s])""").find(input.text.trim()) ?: return null
            val amount = Money.parse(match.groupValues[1].replace(",", "")) ?: return null
            return ParsedTransaction(amount, "expense", "未知商户", "Alipay", input.timestamp)
        }
        return Rules.parse(input, "Alipay")
    }

    companion object {
        private val AUTO_DEBIT = Regex("""^你在(.+?)有一笔\s*((?:\d{1,3}(?:,\d{3})+|\d+)(?:\.\d{1,2})?)\s*元的免密/自动扣款支付(?:$|[，,。；;\s]).*$""")
    }
}
class WechatNotificationParser : PlatformParser {
    override fun parse(input: NotificationInput): ParsedTransaction? {
        if (input.packageName != WECHAT || !TrustedSource.accepts(input)) return null
        val normalizedCandidates = candidateTexts(input).map(::normalizeWechatWrapper)
        val deductions = normalizedCandidates.mapNotNull { text ->
            DEDUCTION.matchEntire(text)?.groupValues?.get(1)?.let(Money::parse)
        }.distinct()
        if (deductions.size == 1) {
            return ParsedTransaction(deductions.single(), "expense", "未知商户", "WeChat", input.timestamp)
        }
        if (deductions.size > 1 || normalizedCandidates.any { it.startsWith("已扣费") }) return null
        return Rules.parse(input, "WeChat")
    }

    companion object {
        private val DEDUCTION = Regex("""已扣费\s*[¥￥]\s*(\d+(?:\.\d{1,2})?)""")
    }
}
class NotificationParser {
    fun classify(input: NotificationInput): NotificationDecision {
        if (!TrustedSource.accepts(input)) return NotificationDecision.Ignore
        val bodies = candidateTexts(input).map(::normalizeWechatWrapper)
        if (input.packageName == WECHAT && bodies.any { it in setOf("红包退款发起通知", "红包退款到账通知") }) {
            return NotificationDecision.FinancialNotice("refund", "红包退款通知未提供金额")
        }
        if (input.packageName == ALIPAY && input.title == "交易提醒" && input.text.trim() == "你有一笔交易，点此查看详情") {
            return NotificationDecision.FinancialNotice("transaction", "transaction_without_amount")
        }
        val transaction = parseTrusted(input)
        return transaction?.let { NotificationDecision.Transaction(it) }
            ?: NotificationDecision.Unresolved("可信支付通知暂未识别：金额或收支结构不明确")
    }
    fun parse(input: NotificationInput): ParsedTransaction? = (classify(input) as? NotificationDecision.Transaction)?.transaction
    // This predicate means unresolved payment evidence, not arbitrary finance vocabulary.
    fun isPaymentRelated(input: NotificationInput) = classify(input) is NotificationDecision.Unresolved
    private fun parseTrusted(input: NotificationInput): ParsedTransaction? = when(input.packageName) {
        ALIPAY -> AlipayNotificationParser().parse(input)
        WECHAT -> WechatNotificationParser().parse(input)
        else -> null
    }
}

private fun candidateTexts(input: NotificationInput): List<String> =
    listOf(input.text, input.bigText, input.extraText, input.subText)
        .flatMap { it.lineSequence().toList() }.map(String::trim).filter { it.isNotBlank() }.distinct()

private fun normalizeWechatWrapper(value: String): String {
    var result = value.trim().replace(Regex("""^\[\d+条]\s*"""), "")
    while (true) {
        val unwrapped = result.replaceFirst(Regex("""^微信支付\s*[:：]\s*"""), "")
        if (unwrapped == result) return result.trim()
        result = unwrapped.trim()
    }
}

sealed interface NotificationDecision {
    data class Transaction(val transaction: ParsedTransaction) : NotificationDecision
    data class FinancialNotice(val type: String, val reason: String) : NotificationDecision
    data class Unresolved(val reason: String) : NotificationDecision
    data object Ignore : NotificationDecision
}

private object TrustedSource {
    private val amount = "(?:[0-9]{1,3}(?:,[0-9]{3})+|[0-9]+)(?:\\.[0-9]{1,2})?"
    private val completionTitle = Regex("(?:付款成功|支付成功|成功收款|收款成功)\\s*(?:[¥￥]\\s*$amount\\s*元?|$amount\\s*元)")
    fun accepts(input: NotificationInput): Boolean = when(input.packageName) {
        WECHAT -> input.title == "微信支付"
        // Preserve existing completion-title formats, but never use a title substring or a bare app name as evidence.
        ALIPAY -> input.title in setOf("交易提醒", "付款成功", "支付成功", "成功收款", "收款成功") ||
            completionTitle.matches(input.title)
        else -> false
    }
}

private object Rules {
    private val number = "(?:[0-9]{1,3}(?:,[0-9]{3})+|[0-9]+)(?:\\.[0-9]{1,2})?"
    private val money = Regex("(?<![0-9.,])(?:[¥￥]\\s*($number)(?![0-9.]|,[0-9])|($number)\\s*元)")
    private val income = Regex("成功收款|收款成功|已收款|收到.*?转账|向[你您]付款|到账|到帐|收到退款|退款成功|的收入")
    private val expense = Regex("付款成功|支付成功|成功付款|已付款|已支付|扣款成功|已扣款|的支出|支出[:：]|向.{1,60}?付款")
    fun parse(input: NotificationInput, platform: String): ParsedTransaction? {
        val content = input.content.replace('，', ',')
        if (Regex("失败|待支付|等待付款|待收款|待领取|未支付|未付款|未到账|退款中|退款申请|申请退款|将扣款|即将|优惠券|红包待领").containsMatchIn(content)) return null
        val isIncome = income.containsMatchIn(content)
        val isExpense = expense.containsMatchIn(content) && !Regex("向[你您]付款").containsMatchIn(content)
        if (isIncome == isExpense) return null // Ambiguous direction goes to local debug log.
        val matches = money.findAll(content).toList()
        val candidates = matches.mapNotNull { m ->
            // Ignore explicitly labelled balances/discounts, never blindly take the first number.
            val prefix = content.substring(maxOf(0, m.range.first - 10), m.range.first)
            if (Regex("(?:余额|优惠|立减|原价|积分|累计)[^\n:：]{0,3}[:：]?\\s*$").containsMatchIn(prefix)) null
            else Money.parse(m.groupValues[1].ifEmpty { m.groupValues[2] }.replace(",", ""))
        }.distinct()
        if (candidates.size != 1) return null
        val merchant = listOf(
            Regex("(?:商户|商家|收款方)[:：]\\s*([^\n,。;；]{1,60})"),
            Regex("向\\s*(?![你您])([^\n]{1,60}?)\\s*付款"),
            Regex("收到\\s*([^\n]{1,60}?)\\s*的转账")
        ).firstNotNullOfOrNull { it.find(content)?.groupValues?.get(1)?.trim() } ?: "未知商户"
        val type = if(isIncome && Regex("收到退款|退款成功|退款到账|退款到帐").containsMatchIn(content)) "refund" else if(isIncome) "income" else "expense"
        return ParsedTransaction(candidates.single(), type, merchant, platform, input.timestamp)
    }
}

fun fingerprint(text: String): String = MessageDigest.getInstance("SHA-256")
    .digest(text.toByteArray(Charsets.UTF_8)).joinToString("") { "%02x".format(it) }

data class ReceiptIdentity(val key: String, val timestamp: Long, val digest: String,
    val platform: String, val amount: Long, val type: String, val merchant: String)
object DuplicatePolicy {
    fun isDuplicate(old: ReceiptIdentity, new: ReceiptIdentity): Boolean {
        if (old.key.isBlank() || old.key != new.key || old.platform != new.platform || old.amount != new.amount || old.type != new.type) return false
        val gap = new.timestamp - old.timestamp
        // Exact replay survives process death. Brief same-key updates require identical text.
        return old.timestamp == new.timestamp && old.merchant == new.merchant ||
            gap in -1500L..1500L && old.digest == new.digest && old.merchant == new.merchant
    }
}
