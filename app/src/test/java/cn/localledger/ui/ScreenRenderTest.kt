package cn.localledger.ui

import android.content.res.Configuration
import android.graphics.Bitmap
import android.graphics.Canvas
import android.view.View
import androidx.compose.runtime.*
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Density
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import cn.localledger.ui.design.*
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.File

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35], qualifiers = "zh-rCN-w360dp-h780dp")
class ScreenRenderTest {
    @get:Rule val compose = createComposeRule()
    @Test fun majorScreensRenderInLightAndDark() {
        var page by mutableIntStateOf(0)
        var dark by mutableStateOf(false)
        var view: View? = null
        compose.setContent {
            view = LocalView.current
            val config = Configuration(LocalConfiguration.current).apply { uiMode = if(dark) Configuration.UI_MODE_NIGHT_YES else Configuration.UI_MODE_NIGHT_NO }
            CompositionLocalProvider(LocalConfiguration provides config) {
                key(page, dark) { when(page) {
                    0 -> BillsPreview(); 1 -> EditorPreview(); 2 -> StatisticsPreview()
                    3 -> AssetsPreview(); 4 -> ReportPreview(); 5 -> MyPreview(); 6 -> HeatmapPreview(); 7 -> Bars14Preview(); else -> BillsPreview()
                } }
            }
        }
        val directory = File("build/ui-captures").apply { mkdirs() }
        listOf("bills", "editor", "statistics", "assets", "report", "my", "heatmap", "bars14", "bills-history").forEachIndexed { index, name ->
            listOf(false, true).forEach { night ->
                compose.runOnIdle { page = index; dark = night }
                compose.waitForIdle()
                if(index == 8) compose.onNodeWithTag("bills-list").performScrollToNode(hasTestTag("bill:8"))
                compose.mainClock.advanceTimeByFrame()
                // Draw the real Compose view into native Skia. PixelCopy's hardware
                // redraw callback is unavailable in this Windows Robolectric host.
                lateinit var bitmap: Bitmap
                compose.runOnUiThread {
                    val root = view!!
                    bitmap = Bitmap.createBitmap(root.width, root.height, Bitmap.Config.ARGB_8888)
                    root.draw(Canvas(bitmap))
                }
                assertTrue(bitmap.width > 0 && bitmap.height > 0)
                assertEquals("The captured theme must match its filename", (if(night) AppDarkColors else AppLightColors).surface.toArgb(), bitmap.getPixel(0, 0))
                File(directory, "$name-${if(night) "dark" else "light"}.png").outputStream().use { assertTrue(bitmap.compress(Bitmap.CompressFormat.PNG, 100, it)) }
            }
        }
    }

    @Test
    @Config(qualifiers = "zh-rCN-w320dp-h640dp")
    fun narrowScreenLargeFontAndMaximumBillRender() {
        var view: View? = null
        compose.setContent {
            view = LocalView.current
            val density = LocalDensity.current
            CompositionLocalProvider(LocalDensity provides Density(density.density, 1.3f)) { LargeBillPreview() }
        }
        compose.onNodeWithText("-¥999,999,999.99").assertExists()
        compose.waitForIdle()
        compose.mainClock.advanceTimeByFrame()
        compose.runOnUiThread {
            val root = view!!
            val bitmap = Bitmap.createBitmap(root.width, root.height, Bitmap.Config.ARGB_8888)
            root.draw(Canvas(bitmap))
            val file = File("build/ui-captures/bills-narrow-large-font.png").apply { parentFile!!.mkdirs() }
            file.outputStream().use { assertTrue(bitmap.compress(Bitmap.CompressFormat.PNG, 100, it)) }
        }
    }
}
