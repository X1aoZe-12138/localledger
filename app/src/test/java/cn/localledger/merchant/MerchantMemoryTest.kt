package cn.localledger.merchant

import cn.localledger.data.LedgerEntry
import cn.localledger.parser.normalizeMerchantName
import org.junit.Assert.*
import org.junit.Test

class MerchantMemoryTest {
    private fun entry(name: String, category: String, time: Long = 1) = LedgerEntry(id = time, amount = 100, merchant = name, category = category, timestamp = time)

    @Test fun shortcutAppearsOnThirdRealTransaction() {
        assertFalse(MerchantMemoryEngine.build(listOf(entry("食堂", "餐饮")), emptyList()).single().isShortcut)
        assertFalse(MerchantMemoryEngine.build(listOf(entry("食堂", "餐饮", 1), entry("食堂", "餐饮", 2)), emptyList()).single().isShortcut)
        assertTrue(MerchantMemoryEngine.build(listOf(entry("食堂", "餐饮", 1), entry("食堂", "餐饮", 2), entry("食堂", "餐饮", 3)), emptyList()).single().isShortcut)
    }

    @Test fun manualMerchantAppearsWithZeroUsage() {
        val profile = MerchantPreferenceEntity(normalizeMerchantName("星巴克"), "餐饮", "星巴克", isManual = true)
        val memory = MerchantMemoryEngine.build(emptyList(), listOf(profile)).single()
        assertEquals(0, memory.useCount); assertTrue(memory.isShortcut)
    }

    @Test fun unknownBlankAndPendingNeverLearn() {
        val entries = listOf(entry("未知商户", "餐饮"), entry("  ", "餐饮", 2), entry("麦当劳", "待分类", 3))
        assertTrue(MerchantMemoryEngine.build(entries, emptyList()).isEmpty())
        assertNull(MerchantMemoryEngine.dominantCategory(entries, "麦当劳"))
    }

    @Test fun normalizationIsLightweightAndKeepsCompanySuffix() {
        assertEquals(normalizeMerchantName("麦当劳"), normalizeMerchantName("  麦 当 劳  "))
        assertEquals(normalizeMerchantName("McDonald"), normalizeMerchantName(" MC  DONALD "))
        assertTrue(normalizeMerchantName("示例科技有限公司").endsWith("有限公司"))
    }

    @Test fun dominantEightToOneAutoSelects() {
        val entries = (1L..8L).map { entry("麦当劳", "餐饮", it) } + entry("麦 当 劳", "其他", 9)
        assertEquals("餐饮", MerchantMemoryEngine.dominantCategory(entries, " 麦当劳 "))
    }

    @Test fun fourToThreeDoesNotAutoSelect() {
        val entries = (1L..4L).map { entry("淘宝", "生活", it) } + (5L..7L).map { entry("淘宝", "数码", it) }
        assertNull(MerchantMemoryEngine.dominantCategory(entries, "淘宝"))
    }

    @Test fun minimumThreeAndEightyPercentAreBothRequired() {
        assertNull(MerchantMemoryEngine.dominantCategory(listOf(entry("A", "餐饮"), entry("A", "餐饮", 2)), "A"))
        val entries = (1L..3L).map { entry("A", "餐饮", it) } + entry("A", "其他", 4)
        assertNull(MerchantMemoryEngine.dominantCategory(entries, "A"))
    }

    @Test fun shortcutsSortPinnedThenCountThenRecent() {
        val entries = (1L..4L).map { entry("食堂", "餐饮", it) } + (5L..7L).map { entry("麦当劳", "餐饮", it) }
        val pin = MerchantPreferenceEntity(normalizeMerchantName("麦当劳"), "餐饮", "麦当劳", isPinned = true)
        val result = MerchantMemoryEngine.build(entries, listOf(pin))
        assertEquals(listOf("麦当劳", "食堂"), result.map { it.name })
    }
}
