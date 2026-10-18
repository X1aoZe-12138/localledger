package cn.localledger.parser

import org.junit.Assert.*
import org.junit.Test

class NotificationParserTest {
    private val parser = NotificationParser()
    private fun alipay(text: String, title: String = "交易提醒") = parser.parse(NotificationInput(ALIPAY, title, text, timestamp = 123))
    private fun wechat(text: String) = parser.parse(NotificationInput(WECHAT, "微信支付", text))
    @Test fun alipayExpense() { assertEquals(2350L, alipay("你有一笔23.50元的支出")?.amount) }
    @Test fun alipayIncome() { assertEquals("income", alipay("成功收款10.00元")?.transactionType) }
    @Test fun wechatExpense() { val result = wechat("向 示例食堂 付款 12.00 元"); assertEquals(1200L, result?.amount); assertEquals("示例食堂", result?.merchant); assertEquals("expense", result?.transactionType) }
    @Test fun wechatIncome() { assertEquals("income", wechat("张三向你付款100元")?.transactionType) }
    @Test fun transferIncome() { assertEquals("张三", wechat("收到 张三 的转账 100.00元")?.merchant) }
    @Test fun refundIsSeparateFromIncome() { assertEquals("refund", alipay("收到退款￥2.50")?.transactionType) }
    @Test fun symbols() { listOf("¥", "￥").forEach { assertEquals(501L, alipay("付款成功${it}5.01")?.amount) } }
    @Test fun yuanInteger() { assertEquals(1000L, alipay("你有一笔10元的支出")?.amount) }
    @Test fun yuanSingleDecimal() { assertEquals(1050L, alipay("你有一笔10.5元的支出")?.amount) }
    @Test fun commaThousands() { assertEquals(123456L, alipay("支付成功￥1,234.56")?.amount) }
    @Test fun bigTextFallback() { assertEquals(320L, parser.parse(NotificationInput(ALIPAY, "付款成功", bigText = "￥3.20", subText = "商户：小店"))?.amount) }
    @Test fun extrasFallback() { assertEquals(320L, parser.parse(NotificationInput(ALIPAY, "付款成功", extraText = "￥3.20"))?.amount) }
    @Test fun missingMerchantPreserved() { assertEquals("未知商户", alipay("支付成功￥9.99")?.merchant) }
    @Test fun timePreserved() { assertEquals(123L, alipay("支付成功￥9.99")?.timestamp) }
    @Test fun zeroAndMalformed() { listOf("￥0", "￥1.234", "￥99999999999999999999", "￥-10", "￥1,23").forEach { assertNull(it, alipay("付款成功$it")) } }
    @Test fun empty() { assertNull(alipay("", "")); assertNull(wechat("")) }
    @Test fun unrelated() { assertNull(alipay("今日优惠￥20")); assertNull(parser.parse(NotificationInput("other", "支付成功", "￥20"))) }
    @Test fun failedPending() { listOf("支付失败￥20", "待支付￥20", "退款申请￥20", "退款中￥20", "未到账￥20").forEach { assertNull(alipay(it)) } }
    @Test fun ordinaryChatNotTransaction() { assertNull(parser.parse(NotificationInput(WECHAT, "朋友", "付款成功￥20"))) }
    @Test fun multipleConflictingAmounts() { assertNull(alipay("支付成功￥20，实付￥18")) }
    @Test fun punctuationAfterSymbolAmount() { assertEquals(2000L, alipay("支付成功￥20，点击查看详情")?.amount) }
    @Test fun ignoreBalanceAndDiscount() { assertEquals(2000L, alipay("支付成功￥20\n余额：￥100\n优惠：￥2")?.amount) }
    @Test fun repeatedFieldsAreOneAmount() { assertEquals(2000L, parser.parse(NotificationInput(ALIPAY, "付款成功￥20", "付款成功￥20", "付款成功￥20"))?.amount) }
    @Test fun unknownPaymentLoggedCandidate() { assertTrue(parser.isPaymentRelated(NotificationInput(WECHAT, "微信支付", "请查看收款详情"))) }
    @Test fun moneyNeverRounds() { assertEquals(29L, Money.parse("0.29")); assertNull(Money.parse("1.001")); assertNull(Money.parse("1e3")); assertEquals("0.29", Money.format(29)) }
}
