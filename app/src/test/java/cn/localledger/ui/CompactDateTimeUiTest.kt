package cn.localledger.ui

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.unit.Density
import cn.localledger.ui.design.*
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.time.*

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], qualifiers = "zh-rCN-w360dp-h780dp")
class CompactDateTimeUiTest {
    @get:Rule val compose = createComposeRule()
    private val today = LocalDate.of(2026, 10, 18)

    @Test fun dateQuickChoicesAreDraftOnlyAndHistoricalMonthIsInitiallySelected() {
        val historical = LocalDate.of(2026, 8, 20)
        var open by mutableStateOf(true)
        var committed: LocalDate? = null
        compose.setContent { AppTheme { Surface(Modifier.fillMaxSize()) {
            if(open) CompactDatePicker(historical, { open = false }, { committed = it; open = false }, today)
        } } }
        compose.onNodeWithText("2026年8月").assertExists()
        compose.onNodeWithTag("calendar-day:$historical").assertIsSelected()
        compose.onNodeWithTag("date-quick:昨天").performClick()
        compose.onNodeWithTag("calendar-day:${today.minusDays(1)}").assertIsSelected()
        assertNull(committed)
        compose.onNodeWithText("取消").performClick()
        assertNull(committed)
        compose.runOnIdle { open = true }
        compose.onNodeWithTag("calendar-day:$historical").assertIsSelected()
        compose.onNodeWithTag("date-quick:前天").performClick()
        compose.onNodeWithTag("picker-confirm").performClick()
        assertEquals(today.minusDays(2), committed)
    }

    @Test fun timeWheelDefaultsToSavedTimeAndInputRejectsInvalidValues() {
        var open by mutableStateOf(true)
        var committed: LocalTime? = null
        compose.setContent { AppTheme { Surface(Modifier.fillMaxSize()) {
            if(open) CompactTimePicker(LocalTime.of(11, 29), { open = false }, { committed = it; open = false })
        } } }
        compose.onNodeWithTag("time-wheel-selection").assertTextEquals("11:29")
        compose.onNodeWithTag("time-mode-toggle").performClick()
        compose.onNodeWithTag("time-input-hour").assertExists()
        compose.onNodeWithTag("time-input-hour").performSemanticsAction(SemanticsActions.SetText) { it(AnnotatedString("25")) }
        compose.onNodeWithTag("time-input-minute").performSemanticsAction(SemanticsActions.SetText) { it(AnnotatedString("70")) }
        compose.onNodeWithTag("picker-confirm").assertIsNotEnabled()
        compose.onNodeWithTag("time-input-hour").performSemanticsAction(SemanticsActions.SetText) { it(AnnotatedString("08")) }
        compose.onNodeWithTag("time-input-minute").performSemanticsAction(SemanticsActions.SetText) { it(AnnotatedString("05")) }
        compose.onNodeWithTag("picker-confirm").assertIsEnabled()
        compose.onNodeWithText("取消").performClick()
        assertNull(committed)
        compose.runOnIdle { open = true }
        compose.onNodeWithTag("time-wheel-selection").assertTextEquals("11:29")
        compose.onNodeWithTag("time-mode-toggle").performClick()
        compose.onNodeWithTag("time-input-hour").performSemanticsAction(SemanticsActions.SetText) { it(AnnotatedString("23")) }
        compose.onNodeWithTag("time-input-minute").performSemanticsAction(SemanticsActions.SetText) { it(AnnotatedString("59")) }
        compose.onNodeWithTag("picker-confirm").performClick()
        assertEquals(LocalTime.of(23, 59), committed)
    }

    @Test fun wheelSwipeSnapsAndConfirmUsesVisibleCenter() {
        var committed: LocalTime? = null
        compose.setContent { AppTheme { Surface(Modifier.fillMaxSize()) {
            CompactTimePicker(LocalTime.of(14, 35), {}, { committed = it })
        } } }
        compose.onNodeWithTag("time-wheel-selection").assertTextEquals("14:35")
        compose.onNodeWithTag("time-wheel:分钟").performTouchInput { swipeUp() }
        compose.waitForIdle()
        val shown = compose.onNodeWithTag("time-wheel-selection").fetchSemanticsNode()
            .config[androidx.compose.ui.semantics.SemanticsProperties.Text].single().text
        assertTrue(Regex("14:[0-5][0-9]").matches(shown))
        compose.onNodeWithTag("picker-confirm").performClick()
        assertEquals(shown, committed?.toString()?.substring(0, 5))
    }

    @Test fun rangeRequiresTwoDatesAndCancelPreservesOldSelection() {
        val initial = DateRangeSelection("CUSTOM", LocalDate.of(2026, 9, 1), LocalDate.of(2026, 9, 30))
        var open by mutableStateOf(true)
        var committed: DateRangeSelection? = null
        val options = listOf(DateRangeOption("LAST_7", "近7天", today.minusDays(6), today),
            DateRangeOption("CUSTOM", "自定义", null, null))
        compose.setContent { AppTheme { Surface(Modifier.fillMaxSize()) {
            if(open) CompactDateRangePicker(initial, options, { open = false }, { committed = it; open = false }, today)
        } } }
        compose.onNodeWithText("2026年9月").assertExists()
        compose.onNodeWithTag("range-quick:LAST_7").performClick()
        assertNull(committed)
        compose.onNodeWithText("取消").performClick()
        compose.runOnIdle { open = true }
        compose.onNodeWithTag("calendar-day:2026-09-01").performClick()
        compose.onNodeWithTag("picker-confirm").assertIsNotEnabled()
        compose.onNodeWithTag("calendar-day:2026-09-01").performClick()
        compose.onNodeWithTag("picker-confirm").performClick()
        assertEquals(DateRangeSelection("CUSTOM", LocalDate.of(2026, 9, 1), LocalDate.of(2026, 9, 1)), committed)
    }

    @Test @Config(qualifiers = "zh-rCN-w320dp-h640dp")
    fun darkThemeAndLargeFontKeepDateAndTimeActionsVisible() {
        var showDate by mutableStateOf(true)
        var showTime by mutableStateOf(false)
        var showRange by mutableStateOf(false)
        compose.setContent {
            val density = LocalDensity.current
            CompositionLocalProvider(LocalDensity provides Density(density.density, 1.3f)) {
                AppTheme(dark = true) { Surface(Modifier.fillMaxSize()) {
                    if(showDate) CompactDatePicker(today, { showDate = false }, { showDate = false }, today)
                    if(showTime) CompactTimePicker(LocalTime.of(8, 5), { showTime = false }, { showTime = false })
                    if(showRange) CompactDateRangePicker(
                        DateRangeSelection("CUSTOM", today.minusDays(6), today),
                        listOf(DateRangeOption("ALL", "全部时间", null, null),
                            DateRangeOption("LAST_7", "近7天", today.minusDays(6), today),
                            DateRangeOption("LAST_30", "近30天", today.minusDays(29), today),
                            DateRangeOption("THIS_MONTH", "本月", today.withDayOfMonth(1), today),
                            DateRangeOption("PREVIOUS_MONTH", "上个月", today.minusMonths(1).withDayOfMonth(1), today.withDayOfMonth(1).minusDays(1)),
                            DateRangeOption("CUSTOM", "自定义", null, null)),
                        { showRange = false }, { showRange = false }, today)
                } }
            }
        }
        compose.onNodeWithTag("picker-confirm").assertIsDisplayed()
        compose.onNodeWithText("取消").performClick()
        compose.runOnIdle { showTime = true }
        compose.onNodeWithTag("time-wheel-selection").assertTextEquals("08:05")
        compose.onNodeWithTag("picker-confirm").assertIsDisplayed()
        compose.onNodeWithTag("time-mode-toggle").performClick()
        compose.onNodeWithTag("picker-confirm").assertIsDisplayed()
        compose.onNodeWithText("取消").performClick()
        compose.runOnIdle { showRange = true }
        compose.onNodeWithTag("picker-confirm").assertIsDisplayed()
        compose.onNodeWithTag("calendar-day:2026-10-31").performScrollTo().assertIsDisplayed()
    }
}
