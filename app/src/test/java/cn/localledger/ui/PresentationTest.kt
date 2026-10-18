package cn.localledger.ui

import cn.localledger.ui.design.*
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.Color
import org.junit.Assert.*
import org.junit.Test

class PresentationTest {
    @Test fun categoryPaletteIsStableAndSeparateFromFinancialAmounts() {
        val expected = mapOf("餐饮" to Color(0xFFFF8A34), "交通" to Color(0xFF4F7CFF), "生活" to Color(0xFF30C48D),
            "购物" to Color(0xFF9B6DFF), "娱乐" to Color(0xFFFF5B8D), "数码" to Color(0xFF00AFC7), "其他" to Color(0xFF8A93A5))
        expected.forEach { (name, color) ->
            assertEquals(color, CategoryColors.category(name))
            assertTrue(CategoryColors.category(name, true).luminance() > color.luminance())
        }
        assertEquals(CategoryColors.category("自定义运动"), CategoryColors.category("自定义运动"))
        assertEquals(CategoryColors.merchant("麦当劳"), CategoryColors.merchant(" 麦 当 劳 "))
        assertNotEquals(CategoryColors.category("餐饮"), LightFinanceColors.expense)
    }
    @Test fun compactAxisDoesNotChangeFullAmountFormatting() {
        assertEquals("¥0", chartAxisMoney(0))
        assertEquals("¥100", chartAxisMoney(10000))
        assertEquals("¥1.3k", chartAxisMoney(128000))
        assertEquals("¥5k", chartAxisMoney(500000))
        assertEquals("¥1,280.00", displayMoney(128000))
    }
    @Test fun moneyFormattingKeepsCentsAndGroupsThousands() {
        assertEquals("¥0.48", displayMoney(48))
        assertEquals("¥1,280.00", displayMoney(128000))
        assertEquals("-¥16.90", displayMoney(-1690))
        assertEquals("¥999,999,999.99", displayMoney(99999999999))
        assertEquals("-¥92,233,720,368,547,758.08", displayMoney(Long.MIN_VALUE))
    }
    @Test fun semanticTextHasReadableContrastInBothThemes() {
        listOf(LightFinanceColors to AppLightColors, DarkFinanceColors to AppDarkColors).forEach { (finance, scheme) ->
            listOf(finance.expense, finance.income, finance.refund, finance.warning, scheme.onSurfaceVariant).forEach { color ->
                val a = color.luminance(); val b = scheme.surface.luminance()
                assertTrue("Contrast must reach 4.5:1", (maxOf(a, b) + .05f) / (minOf(a, b) + .05f) >= 4.5f)
            }
        }
    }
}
