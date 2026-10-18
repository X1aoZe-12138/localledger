package cn.localledger.ui.design

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.*
import androidx.compose.foundation.shape.RoundedCornerShape
import java.math.BigDecimal
import java.text.DecimalFormat
import java.text.DecimalFormatSymbols
import java.util.Locale

object AppDimensions {
    val Tiny = 4.dp; val Small = 8.dp; val Medium = 12.dp; val Page = 16.dp; val Section = 24.dp; val Large = 32.dp
    val Touch = 48.dp
}
object AppElevation { val Card = 0.dp }
data class FinanceColors(val expense: Color, val income: Color, val refund: Color, val warning: Color)
val LightFinanceColors = FinanceColors(Color(0xFF8C4535), Color(0xFF28654B), Color(0xFF27677D), Color(0xFF875C13))
val DarkFinanceColors = FinanceColors(Color(0xFFFFB4A5), Color(0xFF88D4AD), Color(0xFF89D0E6), Color(0xFFE8C27C))
val LocalFinanceColors = staticCompositionLocalOf { LightFinanceColors }
val LocalAppDark = staticCompositionLocalOf { false }
val AppLightColors = lightColorScheme(primary = Color(0xFF456579), onPrimary = Color.White,
    primaryContainer = Color(0xFFDFEBF3), onPrimaryContainer = Color(0xFF16384D),
    background = Color(0xFFF7F8FA), surface = Color(0xFFF7F8FA), onSurface = Color(0xFF1C2832),
    onSurfaceVariant = Color(0xFF4E5D68), surfaceVariant = Color(0xFFE4EAF0), outline = Color(0xFF74818B),
    secondary = Color(0xFF456579), secondaryContainer = Color(0xFFDCE8F0), onSecondaryContainer = Color(0xFF16384D),
    surfaceContainerLow = Color(0xFFEEF2F6), surfaceContainer = Color(0xFFE8EEF3), surfaceContainerHigh = Color(0xFFE5EBF0), surfaceContainerHighest = Color(0xFFDDE5EC), outlineVariant = Color(0xFFC3CDD6))
val AppDarkColors = darkColorScheme(primary = Color(0xFFAACBDD), onPrimary = Color(0xFF123344),
    primaryContainer = Color(0xFF2B4B60), onPrimaryContainer = Color(0xFFDFEBF3),
    background = Color(0xFF111820), surface = Color(0xFF111820), onSurface = Color(0xFFE5EAF0),
    onSurfaceVariant = Color(0xFFB6C3CE), surfaceVariant = Color(0xFF34414D), outline = Color(0xFF85939F),
    secondary = Color(0xFFAACBDD), secondaryContainer = Color(0xFF334C60), onSecondaryContainer = Color(0xFFDFEBF3),
    surfaceContainerLow = Color(0xFF19232D), surfaceContainer = Color(0xFF202C37), surfaceContainerHigh = Color(0xFF27343F), surfaceContainerHighest = Color(0xFF2D3A46), outlineVariant = Color(0xFF45535F))
val AppTypography = Typography(
    headlineLarge = TextStyle(fontFamily = FontFamily.SansSerif, fontWeight = FontWeight.SemiBold, fontSize = 32.sp, lineHeight = 40.sp, fontFeatureSettings = "tnum"),
    headlineMedium = TextStyle(fontWeight = FontWeight.SemiBold, fontSize = 28.sp, lineHeight = 36.sp, fontFeatureSettings = "tnum"),
    titleLarge = TextStyle(fontWeight = FontWeight.Medium, fontSize = 22.sp, lineHeight = 28.sp),
    titleMedium = TextStyle(fontWeight = FontWeight.Medium, fontSize = 16.sp, lineHeight = 24.sp, fontFeatureSettings = "tnum"),
    bodyLarge = TextStyle(fontSize = 16.sp, lineHeight = 24.sp), bodyMedium = TextStyle(fontSize = 14.sp, lineHeight = 22.sp),
    bodySmall = TextStyle(fontSize = 12.sp, lineHeight = 18.sp), labelLarge = TextStyle(fontWeight = FontWeight.Medium, fontSize = 14.sp, lineHeight = 20.sp),
    labelSmall = TextStyle(fontSize = 11.sp, lineHeight = 16.sp, fontFeatureSettings = "tnum"))

@Composable fun AppTheme(dark: Boolean = isSystemInDarkTheme(), content: @Composable () -> Unit) {
    CompositionLocalProvider(LocalFinanceColors provides if(dark) DarkFinanceColors else LightFinanceColors, LocalAppDark provides dark) {
        MaterialTheme(colorScheme = if(dark) AppDarkColors else AppLightColors, typography = AppTypography,
            shapes = Shapes(small = RoundedCornerShape(8.dp), medium = RoundedCornerShape(12.dp), large = RoundedCornerShape(20.dp), extraLarge = RoundedCornerShape(24.dp)), content = content)
    }
}
// Presentation only: parser, storage, CSV, backup and report money remain unchanged.
fun displayNumber(cents: Long): String = DecimalFormat("#,##0.00", DecimalFormatSymbols(Locale.US)).format(BigDecimal.valueOf(cents, 2))
fun displayMoney(cents: Long): String = (if(cents < 0) "-" else "") + "¥" + displayNumber(cents).removePrefix("-")

@Composable fun SectionHeader(title: String, subtitle: String? = null) {
    Column(Modifier.padding(top = AppDimensions.Small), verticalArrangement = Arrangement.spacedBy(AppDimensions.Tiny)) {
        Text(title, style = MaterialTheme.typography.titleMedium)
        subtitle?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
    }
}
@Composable fun EmptyState(title: String, message: String, action: String? = null, onAction: () -> Unit = {}) {
    Column(Modifier.fillMaxWidth().padding(vertical = AppDimensions.Large), verticalArrangement = Arrangement.spacedBy(AppDimensions.Medium)) {
        Text(title, style = MaterialTheme.typography.titleLarge)
        Text(message, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        action?.let { FilledTonalButton(onClick = onAction) { Text(it) } }
    }
}
