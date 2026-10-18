package cn.localledger.parser

import org.junit.Assert.*
import org.junit.Test

/** Synthetic fixtures preserving notification structure without personal notification content. */
class NotificationFormatRegressionTest {
    private val parser = NotificationParser()
    private fun wx(text: String, title: String = "微信支付", extra: String = "") = NotificationInput(WECHAT, title, text, extraText = extra, timestamp = 1234)
    private fun ali(text: String, title: String = "交易提醒") = NotificationInput(ALIPAY, title, text, timestamp = 1234)
    private fun expense(input: NotificationInput, cents: Long, platform: String) {
        val transaction = parser.parse(input)!!
        assertEquals(cents, transaction.amount)
        assertEquals("expense", transaction.transactionType)
        assertEquals(platform, transaction.platform)
        assertEquals("未知商户", transaction.merchant)
        assertEquals(1234L, transaction.timestamp)
        assertFalse(parser.isPaymentRelated(input))
    }
    private fun ignored(input: NotificationInput) {
        assertEquals(NotificationDecision.Ignore, parser.classify(input))
        assertNull(parser.parse(input))
        assertFalse(parser.isPaymentRelated(input))
    }
    @Test fun wechatDeduction1763() { expense(wx("已扣费¥17.63", extra = "微信支付: 已扣费¥17.63"), 1763, "WeChat") }
    @Test fun wechatSmallDeduction063() { expense(wx("已扣费¥0.63"), 63, "WeChat") }
    @Test fun wechatAggregatedDeduction2340() { expense(wx("[2条]微信支付: 已扣费¥23.40"), 2340, "WeChat") }
    @Test fun wechatAggregatedDeduction1870() { expense(wx("[2条]微信支付: 已扣费¥18.70"), 1870, "WeChat") }
    @Test fun wechatAggregatedTenFullWidthColon() { expense(wx("[10条]微信支付：已扣费 ￥1.56"), 156, "WeChat") }
    @Test fun wechatWrappedWithoutCounterAndCandidateFields() {
        expense(wx("微信支付: 已扣费¥23.40"), 2340, "WeChat")
        expense(wx("无可用正文").copy(bigText = "[3条] 微信支付： 已扣费￥23.40"), 2340, "WeChat")
        expense(wx("无可用正文").copy(extraText = "微信支付: 已扣费¥23.40"), 2340, "WeChat")
        expense(wx("无可用正文").copy(extraText = "其他文本\n[10条]微信支付：已扣费 ￥23.40"), 2340, "WeChat")
    }
    @Test fun conflictingWechatCandidateAmountsStayUnresolved() {
        val input = wx("已扣费¥23.40").copy(bigText = "微信支付: 已扣费¥18.70")
        assertNull(parser.parse(input)); assertTrue(parser.classify(input) is NotificationDecision.Unresolved)
    }
    @Test fun variousDeductionsNeverInferMerchant() {
        listOf("1.56" to 156L, "18.70" to 1870L, "1.73" to 173L, "1.93" to 193L, "1.59" to 159L).forEach {
            expense(wx("已扣费¥${it.first}"), it.second, "WeChat")
        }
    }
    @Test fun deductionCurrencyVariantsAndWhitespace() {
        expense(wx("已扣费 ￥ 17.6"), 1760, "WeChat")
        expense(wx("已扣费 ¥ 1"), 100, "WeChat")
    }
    @Test fun deductionMalformedAmountsCannotFallBackToAnotherAmount() {
        listOf("0", "-1", "1.234", "99999999999999", "1,23").forEach {
            val input = wx("已扣费¥$it", extra = "支付成功¥5")
            assertNull(parser.parse(input))
            assertTrue(parser.classify(input) is NotificationDecision.Unresolved)
        }
    }
    @Test fun groupChatIsIgnored() {
        ignored(wx("[3条]成员甲: @我 收款啊", "示例交流群", "成员甲: @我 收款啊"))
    }
    @Test fun chatKeywordsAndQuotedTransactionAreNotEvidence() {
        listOf("收款", "付款", "红包", "转账", "钱", "已扣费¥17.63", "付款成功￥20").forEach { ignored(wx(it, "普通聊天")) }
    }
    @Test fun titleSubstringDoesNotImpersonatePaymentSource() {
        listOf("微信支付交流群", "朋友的收款通知", "支付成功", "微信收款助手").forEach { ignored(wx("已扣费¥17.63", it)) }
    }
    @Test fun refundInitiatedWithoutAmountIsNotAnUnresolvedError() {
        val input = wx("红包退款发起通知")
        assertEquals(NotificationDecision.FinancialNotice("refund", "红包退款通知未提供金额"), parser.classify(input))
        assertNull(parser.parse(input)); assertFalse(parser.isPaymentRelated(input))
    }
    @Test fun refundArrivedWithoutAmountIsNotAnUnresolvedError() {
        val input = wx("[2条]微信支付: 红包退款到账通知")
        assertTrue(parser.classify(input) is NotificationDecision.FinancialNotice)
        assertNull(parser.parse(input)); assertFalse(parser.isPaymentRelated(input))
    }
    @Test fun refundWordsInChatStayIgnored() { ignored(wx("红包退款发起通知", "同学")) }
    @Test fun alipay3725WithCoupon050() { expense(ali("你有一笔37.25元的支出，点击领0.5元话费券。"), 3725, "Alipay") }
    @Test fun alipayPasswordFreeAutoDebit() {
        val result = parser.parse(ali("你在示例科技有限公司有一笔14.75元的免密/自动扣款支付，点击领取8个支付宝积分。"))!!
        assertEquals(1475L, result.amount)
        assertEquals("expense", result.transactionType)
        assertEquals("示例科技有限公司", result.merchant)
        assertEquals("Alipay", result.platform)
    }
    @Test fun autoDebitCandidateFieldsAndWhitespace() {
        val input = ali("其他说明").copy(bigText = "你在 示例科技有限公司 有一笔 14.75 元的免密/自动扣款支付，点击领取8个支付宝积分。")
        val result = parser.parse(input)!!
        assertEquals(1475L, result.amount)
        assertEquals("示例科技有限公司", result.merchant)
    }
    @Test fun autoDebitInvalidMainAmountNeverUsesPoints() {
        val input = ali("你在示例科技有限公司有一笔14.751元的免密/自动扣款支付，点击领取8个支付宝积分。")
        assertNull(parser.parse(input)); assertTrue(parser.classify(input) is NotificationDecision.Unresolved)
    }
    @Test fun alipay1234WithRedPacket300() { expense(ali("你有一笔12.34元的支出，领3元小荷包支付红包。"), 1234, "Alipay") }
    @Test fun marketingExclusionWordDoesNotDiscardRealExpenseClause() { expense(ali("你有一笔37.25元的支出，点击领0.5元优惠券。"), 3725, "Alipay") }
    @Test fun extraFieldsCannotReplaceStrongAmount() {
        expense(ali("你有一笔12.34元的支出，领3元小荷包支付红包。").copy(bigText = "红包3元", extraText = "话费券0.5元"), 1234, "Alipay")
    }
    @Test fun alipayStrongAmountWhitespace() { expense(ali("你有一笔 8.4 元的支出"), 840, "Alipay") }
    @Test fun existingThousandsAndIncomeClauseRemainSupported() {
        expense(ali("你有一笔1,234.56元的支出，领2元红包。"), 123456, "Alipay")
        assertEquals("income", parser.parse(ali("你有一笔10元的收入"))?.transactionType)
    }
    @Test fun malformedStrongClauseNeverUsesCouponAmount() {
        listOf("1.234", "-2", "0", "999999999999999").forEach { assertNull(parser.parse(ali("你有一笔${it}元的支出，领2元红包。"))) }
    }
    @Test fun alipayAmountFreeNoticeDoesNotBecomeZeroBillOrError() {
        val input = ali("你有一笔交易，点此查看详情")
        assertEquals(NotificationDecision.FinancialNotice("transaction", "transaction_without_amount"), parser.classify(input))
        assertNull(parser.parse(input)); assertFalse(parser.isPaymentRelated(input))
    }
    @Test fun alipayEnergyGameIgnored() { ignored(ali("游戏好友：我帮你找回了25g已过期的能量，快来收", "支付宝")) }
    @Test fun alipayTapAdNeverCreatesBill() { ignored(ali("碰一下线下支付通用，立即查看>>", "最高6元碰一下红包")) }
    @Test fun alipayBareAppTitleAndChatFinancialWordsIgnored() { ignored(ali("聊天联系人：付款成功￥25，收到红包了吗", "支付宝")) }
    @Test fun completionTitleMustBeAnEntireStructure() {
        ignored(ali("支付成功￥20", "支付成功￥20交流群"))
        ignored(ali("收款成功1元", "恭喜最高收款成功1元"))
    }
    @Test fun officialUnknownFormatStillLogged() {
        listOf(wx("扣费详情暂不可用"), ali("请打开查看交易详情")).forEach {
            assertTrue(parser.classify(it) is NotificationDecision.Unresolved)
            assertTrue(parser.isPaymentRelated(it)); assertNull(parser.parse(it))
        }
    }
    @Test fun wrongPackageCannotUseOfficialTitle() { ignored(wx("已扣费¥17.63").copy(packageName = "other.app")) }
    @Test fun existingCompletionTitlesAndRefundAmountsStillWork() {
        assertEquals(250L, parser.parse(ali("收到退款￥2.50"))?.amount)
        assertEquals("refund", parser.parse(wx("收到退款￥2.50"))?.transactionType)
        assertEquals(1000L, parser.parse(ali("已转入余额", "成功收款10.00元"))?.amount)
        assertEquals(320L, parser.parse(ali("付款成功￥3.20", "付款成功"))?.amount)
    }
}
