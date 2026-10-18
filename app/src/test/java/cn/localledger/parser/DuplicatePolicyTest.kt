package cn.localledger.parser

import org.junit.Assert.*
import org.junit.Test

class DuplicatePolicyTest {
    private val first = ReceiptIdentity("key1", 10000, "digest", "Alipay", 1000, "expense", "食堂")
    @Test fun identicalReplay() { assertTrue(DuplicatePolicy.isDuplicate(first, first.copy())) }
    @Test fun sameNotificationUpdate() { assertTrue(DuplicatePolicy.isDuplicate(first, first.copy(timestamp = 11000))) }
    @Test fun reverseOrder() { assertTrue(DuplicatePolicy.isDuplicate(first, first.copy(timestamp = 9000))) }
    @Test fun separateNotificationPreserved() { assertFalse(DuplicatePolicy.isDuplicate(first, first.copy(key = "key2"))) }
    @Test fun sameAmountLaterPreserved() { assertFalse(DuplicatePolicy.isDuplicate(first, first.copy(timestamp = 12000))) }
    @Test fun changedContentPreserved() { assertFalse(DuplicatePolicy.isDuplicate(first, first.copy(timestamp = 11000, digest = "new"))) }
    @Test fun differentDirectionPreserved() { assertFalse(DuplicatePolicy.isDuplicate(first, first.copy(type = "income"))) }
    @Test fun differentPlatformPreserved() { assertFalse(DuplicatePolicy.isDuplicate(first, first.copy(platform = "WeChat"))) }
    @Test fun differentMerchantPreserved() { assertFalse(DuplicatePolicy.isDuplicate(first, first.copy(merchant = "超市"))) }
    @Test fun differentAmountPreserved() { assertFalse(DuplicatePolicy.isDuplicate(first, first.copy(amount = 2000))) }
    @Test fun absentIdentityNotSuppressed() { assertFalse(DuplicatePolicy.isDuplicate(first.copy(key = ""), first.copy(key = ""))) }
}
