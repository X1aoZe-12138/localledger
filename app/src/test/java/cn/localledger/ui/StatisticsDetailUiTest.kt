package cn.localledger.ui

import androidx.compose.foundation.layout.*
import androidx.compose.material3.Surface
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.unit.dp
import cn.localledger.data.LedgerEntry
import cn.localledger.statistics.*
import cn.localledger.ui.design.AppTheme
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.time.*

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], qualifiers = "zh-rCN-w360dp-h780dp")
class StatisticsDetailUiTest {
    @get:Rule val compose = createComposeRule()
    private val day = LocalDate.of(2026, 10, 17)
    private val time = day.atTime(12, 0).atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()
    private fun report(count: Int) = ExpenseStatistics.report((1..count).map {
        LedgerEntry(id = it.toLong(), timestamp = time, amount = it * 100L,
            merchant = "商户$it", category = listOf("餐饮", "娱乐", "生活")[it % 3])
    }, day, day)

    @Test fun oneEntryIsCompactWithoutScrollbarAndTenEntriesRemainReadable() {
        var count by mutableIntStateOf(1)
        compose.setContent { AppTheme { Surface(Modifier.fillMaxSize()) {
            DayExpenseDialog(report(count).days.single(), StatisticObject.TOTAL, {}, {})
        } } }
        compose.onNodeWithText("10月17日 · 个人支出 ¥1.00").assertExists()
        compose.onNodeWithTag("day-detail-entry:1").assertIsDisplayed()
        compose.onNodeWithTag("day-detail-category-dot:1", useUnmergedTree = true).assertExists()
        compose.onNodeWithTag("day-detail-scrollbar", useUnmergedTree = true).assertDoesNotExist()
        compose.runOnIdle { count = 10 }
        compose.onNodeWithText("账单 · 10笔").assertExists()
        compose.onNodeWithTag("day-detail-entry:10", useUnmergedTree = true).assertExists()
    }

    @Test fun thirtyEntriesShowScrollbarAndScrollingReachesLastBill() {
        compose.setContent { AppTheme { Surface(Modifier.fillMaxSize()) {
            DayExpenseDialog(report(30).days.single(), StatisticObject.TOTAL, {}, {})
        } } }
        compose.waitUntil(5000) { compose.onAllNodesWithTag("day-detail-scrollbar", useUnmergedTree = true).fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithTag("day-detail-scrollbar", useUnmergedTree = true).assertIsDisplayed()
        val scroll = compose.onNodeWithTag("day-detail-content").fetchSemanticsNode().config[SemanticsProperties.VerticalScrollAxisRange]
        assertTrue(scroll.maxValue() > 0f)
        compose.onNodeWithTag("day-detail-entry:30", useUnmergedTree = true).performScrollTo().assertIsDisplayed()
        assertTrue(compose.onNodeWithTag("day-detail-content").fetchSemanticsNode().config[SemanticsProperties.VerticalScrollAxisRange].value() > 0f)
        compose.onNodeWithTag("day-detail-scrollbar", useUnmergedTree = true).assertExists()
    }

    @Test fun closingDetailClearsBarSelectionAndAllowsReselection() {
        var selected by mutableStateOf<LocalDate?>(null)
        val report = report(2)
        compose.setContent { AppTheme { Surface(Modifier.fillMaxSize()) {
            Column(Modifier.padding(16.dp)) {
                StackedBars(report, StatisticObject.TOTAL, "", emptyList(), selected) { selected = it }
                selected?.let { DayExpenseDialog(report.days.single(), StatisticObject.TOTAL, { selected = null }, {}) }
            }
        } } }
        val bar = compose.onNodeWithTag("bar-date:$day")
        bar.performClick(); bar.assertIsSelected()
        compose.onNodeWithText("关闭").performClick()
        bar.assertIsNotSelected()
        bar.performClick(); bar.assertIsSelected()
        compose.onNodeWithText("关闭").performClick()
        bar.assertIsNotSelected()
    }
}
