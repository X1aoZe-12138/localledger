package cn.localledger.ui

import androidx.compose.foundation.layout.*
import androidx.compose.material3.Surface
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.unit.dp
import cn.localledger.data.*
import cn.localledger.statistics.*
import cn.localledger.ui.design.*
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.time.*

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], qualifiers = "zh-rCN-w320dp-h780dp")
class BillsAndChartUiTest {
    @get:Rule val compose = createComposeRule()
    private val today = LocalDate.now()
    private fun stamp(day: LocalDate) = day.atTime(12, 0).atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()

    @Test fun threeDailyCardsContainRowsAndOnlyInternalDividers() {
        val categories = defaultIncomeCategories.mapIndexed { i, (id, name) -> IncomeCategoryEntity(id, name, i) }
        val entries = (1..5).map { LedgerEntry(id = it.toLong(), timestamp = stamp(today), amount = 1690,
            merchant = if(it == 5) "未知商户" else "商户$it", category = if(it == 5) "待分类" else "餐饮") } + listOf(
            LedgerEntry(id = 6, timestamp = stamp(today.minusDays(1)), merchant = "餐厅", category = "餐饮", amount = 20000,
                splitEnabled = true, splitCalculationMode = "MANUAL_RECEIVED", receivedAmountCents = 16000),
            LedgerEntry(id = 7, timestamp = stamp(today.minusDays(2)), transactionType = "income", amount = 800000, incomeCategoryId = "salary"),
            LedgerEntry(id = 8, timestamp = stamp(today.minusDays(2)), transactionType = "refund", amount = 10000))
        var clicked: Long? = null
        compose.setContent { AppTheme { Surface(Modifier.fillMaxSize()) { BillsContent(entries, categories, true, null, {}, {}, { clicked = it.id }) } } }
        val list = compose.onNodeWithTag("bills-list")
        for(day in listOf(today, today.minusDays(1), today.minusDays(2))) {
            list.performScrollToNode(hasTestTag("day-card:$day"))
            compose.onNodeWithTag("day-card:$day").assertExists()
        }
        list.performScrollToNode(hasTestTag("bill:5"))
        compose.onNodeWithText("未知商户").assertExists()
        compose.onNodeWithTag("bill-divider:4", useUnmergedTree = true).assertExists()
        compose.onNodeWithTag("bill-divider:5", useUnmergedTree = true).assertDoesNotExist()
        list.performScrollToNode(hasTestTag("bill:6"))
        compose.onNodeWithTag("bill:6").assertTextContains("-¥40.00").performClick()
        compose.runOnIdle { assertEquals(6L, clicked) }
        compose.onNodeWithTag("bill-divider:6", useUnmergedTree = true).assertDoesNotExist()
        list.performScrollToNode(hasTestTag("bill:8"))
        compose.onNodeWithTag("bill:7").assertTextContains("工资").assertTextContains("+¥8,000.00")
        compose.onNodeWithTag("bill:8").assertTextContains("+¥100.00")
    }

    @Test fun sevenTenAndFourteenDaysFitNarrowViewportAndRemainClickable() {
        var count by mutableIntStateOf(7)
        var clicked: LocalDate? = null
        var selected by mutableStateOf<LocalDate?>(null)
        compose.setContent { AppTheme { Surface(Modifier.fillMaxSize()) {
            Column(Modifier.fillMaxWidth().padding(16.dp)) {
                val start = today.minusDays(count - 1L)
                // Include a zero-spend last day; it must remain visible and selectable.
                val entries = (0 until count - 1).map { index -> LedgerEntry(id = index + 1L,
                    timestamp = stamp(start.plusDays(index.toLong())), amount = (index + 1) * 1000L, category = "餐饮") }
                StackedBars(ExpenseStatistics.report(entries, start, today), StatisticObject.TOTAL, "", emptyList(), selected) { selected = it; clicked = it }
            }
        } } }
        for(days in listOf(7, 10, 14)) {
            compose.runOnIdle { count = days }
            val plot = compose.onNodeWithTag("bars-plot").fetchSemanticsNode()
            assertFalse("The short chart must not expose horizontal scrolling", plot.config.contains(SemanticsProperties.HorizontalScrollAxisRange))
            val bounds = plot.boundsInRoot
            for(index in 0 until days) {
                val day = today.minusDays(days - 1L).plusDays(index.toLong())
                val node = compose.onNodeWithTag("bar-date:$day").assertIsDisplayed()
                val bar = node.fetchSemanticsNode().boundsInRoot
                assertTrue("Every day must fit inside plot width", bar.left >= bounds.left - 1 && bar.right <= bounds.right + 1)
                node.performClick()
                compose.runOnIdle { assertEquals(day, clicked) }
                node.assertIsSelected()
            }
            compose.onNodeWithContentDescription("$today 个人支出 0.00 元").assertIsDisplayed()
            compose.runOnIdle { selected = null }
            compose.onNodeWithTag("bar-date:$today").assertIsNotSelected()
        }
    }

    @Test fun filterSheetCombinesDateCategoryMerchantAndUpdatesDailyTotals() {
        val prior = today.minusDays(1)
        val entries = listOf(
            LedgerEntry(id = 1, timestamp = stamp(today), amount = 1000, category = "餐饮", merchant = "食堂"),
            LedgerEntry(id = 2, timestamp = stamp(today), amount = 2000, category = "购物", merchant = "淘宝"),
            LedgerEntry(id = 3, timestamp = stamp(prior), amount = 4000, category = "餐饮", merchant = "食堂"),
            LedgerEntry(id = 4, timestamp = stamp(today), amount = 500000, transactionType = "income", incomeCategoryId = "salary"),
            LedgerEntry(id = 5, timestamp = stamp(today), amount = 300, transactionType = "refund"))
        compose.setContent { AppTheme { Surface(Modifier.fillMaxSize()) {
            BillsContent(entries, emptyList(), true, null, {}, {}, {})
        } } }
        compose.onNodeWithTag("day-total:$today").assertTextEquals("¥30.00")
        compose.onNodeWithText("筛选").performClick()
        compose.onNodeWithTag("filter-preset:LAST_7").performSemanticsAction(SemanticsActions.OnClick) { it() }
        compose.onNodeWithTag("filter-preset:LAST_7").assertIsSelected()
        compose.onNodeWithTag("filter-category:餐饮").performScrollTo().performSemanticsAction(SemanticsActions.OnClick) { it() }
        compose.onNodeWithTag("filter-category:餐饮").assertIsSelected()
        compose.onNodeWithTag("filter-merchant:食堂").performScrollTo().performSemanticsAction(SemanticsActions.OnClick) { it() }
        compose.onNodeWithTag("apply-bill-filter").performScrollTo().assertIsEnabled()
            .performSemanticsAction(SemanticsActions.OnClick) { assertTrue(it()) }
        compose.mainClock.advanceTimeBy(500)
        compose.waitUntil(5000) { compose.onAllNodesWithText("符合条件的个人支出").fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithText("符合条件的个人支出").assertExists()
        compose.onNodeWithTag("filtered-spending").assertTextEquals("¥50.00")
        val list = compose.onNodeWithTag("bills-list")
        list.performScrollToNode(hasTestTag("day-card:$today"))
        compose.onNodeWithTag("day-total:$today").assertTextEquals("¥10.00")
        list.performScrollToNode(hasTestTag("day-card:$prior"))
        compose.onNodeWithTag("day-total:$prior").assertTextEquals("¥40.00")
        compose.onNodeWithText("清除全部").performClick()
        compose.onNodeWithText("本月个人支出").assertExists()
    }
}
