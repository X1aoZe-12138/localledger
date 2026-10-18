package cn.localledger.ui.design

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import cn.localledger.parser.normalizeMerchantName
import java.math.BigDecimal
import java.math.RoundingMode

/** Presentation colors only. Financial amount colors remain in FinanceColors. */
object CategoryColors {
    private val named = linkedMapOf(
        "餐饮" to Color(0xFFFF8A34), "交通" to Color(0xFF4F7CFF),
        "生活" to Color(0xFF30C48D), "购物" to Color(0xFF9B6DFF),
        "娱乐" to Color(0xFFFF5B8D), "数码" to Color(0xFF00AFC7),
        "其他" to Color(0xFF8A93A5), "待分类" to Color(0xFF8A93A5)
    )
    // Fixed hashes, never random. New names need neither database fields nor backup changes.
    private val extension = named.values.distinct()
    private val merchants = listOf(Color(0xFF4F7CFF), Color(0xFFFF8A34),
        Color(0xFF30C48D), Color(0xFF9B6DFF), Color(0xFFFF5B8D), Color(0xFF00AFC7))

    fun category(name: String, dark: Boolean = false): Color = forTheme(
        named[name] ?: extension[Math.floorMod(name.hashCode(), extension.size)], dark)

    fun merchant(name: String, dark: Boolean = false): Color = forTheme(
        merchants[Math.floorMod(normalizeMerchantName(name).hashCode(), merchants.size)], dark)

    fun forTheme(color: Color, dark: Boolean): Color = if(dark) lerp(color, Color.White, 0.18f) else color
}

/** Short axis labels only; full precise money is still shown in summaries and day details. */
internal fun chartAxisMoney(cents: Long): String {
    val (scale, suffix) = when {
        cents >= 100_000_000L -> 8 to "M"
        cents >= 100_000L -> 5 to "k"
        else -> 2 to ""
    }
    return "¥" + BigDecimal.valueOf(cents, scale).setScale(1, RoundingMode.HALF_UP)
        .stripTrailingZeros().toPlainString() + suffix
}
