package cn.localledger.ui

import androidx.compose.material3.Surface
import androidx.compose.ui.Modifier
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.junit4.StateRestorationTester
import androidx.compose.ui.semantics.SemanticsActions
import cn.localledger.*
import cn.localledger.data.*
import cn.localledger.ui.design.*
import org.junit.Assert.*
import org.junit.Rule
import org.junit.After
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

private val incomeCategories = defaultIncomeCategories.mapIndexed { i, (id, name) -> IncomeCategoryEntity(id, name, i) }

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class EditorUiTest {
    @get:Rule val compose = createComposeRule()

    @Test fun manualExpenseAaAndStateRestorationKeepOriginalPayment() {
        var saved: LedgerEntry? = null
        val restoration = StateRestorationTester(compose)
        restoration.setContent { AppTheme { Surface(Modifier.fillMaxSize()) {
            EditorContent(null, false, emptyList(), incomeCategories, { null }, {}, { saved = it }, {})
        } } }
        compose.onNode(hasSetTextAction() and hasText("金额（元）")).performTextInput("100")
        compose.onNodeWithText("餐饮").performClick()
        compose.onNode(isToggleable()).performScrollTo().performClick()
        compose.onNodeWithText("我已收回（元）").performScrollTo()
        compose.onNodeWithText("个人承担 ¥33.34", substring = true).assertExists()
        restoration.emulateSavedInstanceStateRestore()
        compose.onNode(hasSetTextAction() and hasText("金额（元）")).assertTextContains("100")
        compose.onNodeWithText("保存", useUnmergedTree = true).performScrollTo().performClick()
        compose.runOnIdle {
            assertNotNull(saved)
            assertEquals(10000L, saved!!.amount)
            assertEquals(3334L, saved!!.personalExpenseCents)
            assertEquals("餐饮", saved!!.category)
        }
    }

    @Test fun incomeDoesNotShowExpenseMerchantOrAa() {
        compose.setContent { AppTheme(true) { Surface {
            EditorContent(null, false, emptyList(), incomeCategories, { null }, {}, {}, {})
        } } }
        compose.onNodeWithText("收入", useUnmergedTree = true).performClick()
        compose.onNodeWithText("收入来源类别").assertExists()
        compose.onNodeWithText("工资").assertExists()
        compose.onNodeWithText("商户名称（可留空）").assertDoesNotExist()
        compose.onNodeWithText("AA / 分摊").assertDoesNotExist()
    }

    @Test fun aaManualFieldsRemainMutuallyExclusive() {
        compose.setContent { AppTheme { Surface { EditorContent(LedgerEntry(amount = 20000, splitEnabled = true, splitCalculationMode = "EQUAL"), false, emptyList(), incomeCategories, { null }, {}, {}, {}) } } }
        val self = hasSetTextAction() and hasText("我实际承担（元）")
        val received = hasSetTextAction() and hasText("我已收回（元）")
        compose.onNode(self).performScrollTo().performTextInput("53")
        compose.onNode(received).performScrollTo().performTextInput("160")
        compose.onNode(self).assertTextEquals("我实际承担（元）", "")
        compose.onNodeWithText("个人承担 ¥40.00", substring = true).assertExists()
    }
}

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class NavigationUiTest {
    @get:Rule val compose = createAndroidComposeRule<MainActivity>()
    @After fun releaseProcessResources() {
        compose.runOnUiThread { compose.activity.viewModelStore.clear(); compose.activity.finish() }
        closeTestApplication()
    }

    @Test fun allTabsAndSecondaryEntrancesOpenAndReturn() {
        compose.onNodeWithText("本月个人支出").assertExists()
        compose.onNodeWithText("统计", useUnmergedTree = true).performClick()
        compose.onNodeWithText("按个人承担统计 · 不包含收入").assertExists()
        compose.onNodeWithText("范围 · 近7天 ▾").performClick()
        compose.onNodeWithText("近30天", useUnmergedTree = true).performClick()
        compose.onNodeWithText("每日支出热力图", substring = true).assertExists()
        compose.onNodeWithText("资产", useUnmergedTree = true).performClick()
        compose.onNodeWithText("总可流动资产").assertExists()
        // Check the accessible action after scrolling. Raw pointer dispatch for
        // this transition is unstable in Windows Robolectric's reused sandbox.
        compose.onNodeWithText("新增账户").performScrollTo().assertIsEnabled().assertIsDisplayed()
            .performSemanticsAction(SemanticsActions.OnClick) { it() }
        compose.waitUntil(5000) { compose.onAllNodesWithText("账户名称").fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithText("账户名称").assertExists()
        compose.onNodeWithContentDescription("返回").performClick()
        compose.onNodeWithText("统计", useUnmergedTree = true).performClick()
        compose.onNodeWithText("范围 · 近30天 ▾").assertExists()
        compose.onNodeWithText("我的", useUnmergedTree = true).performClick()
        // Use the same accessible click as the asset entrance: Windows Robolectric's
        // reused sandbox can lose raw pointer transitions after scrolling.
        compose.onNodeWithText("AI 财务报告").performScrollTo().assertIsDisplayed().assertIsEnabled()
            .performSemanticsAction(SemanticsActions.OnClick) { assertTrue(it()) }
        compose.onNodeWithText("报告类型").assertExists()
        compose.onNodeWithContentDescription("返回").performClick()
        compose.onNodeWithText("数据管理").performScrollTo().assertIsDisplayed().assertIsEnabled()
            .performSemanticsAction(SemanticsActions.OnClick) { assertTrue(it()) }
        compose.onNodeWithText("备份全部数据").assertExists()
        compose.onNodeWithText("恢复备份").assertExists()
        compose.onNodeWithContentDescription("返回").performClick()
        compose.onNodeWithText("商户管理").performScrollTo().assertIsDisplayed().assertIsEnabled()
            .performSemanticsAction(SemanticsActions.OnClick) { assertTrue(it()) }
        compose.onNodeWithText("新增商户").assertExists()
        compose.onNodeWithContentDescription("返回").performClick()
        compose.onNodeWithText("通知诊断").performScrollTo().assertIsDisplayed().assertIsEnabled()
            .performSemanticsAction(SemanticsActions.OnClick) { assertTrue(it()) }
        compose.onNodeWithText("开始诊断").assertExists()
        compose.onNodeWithContentDescription("返回").performClick()
        compose.onNodeWithText("账单", useUnmergedTree = true).performClick()
        compose.onNodeWithContentDescription("手动记账").performClick()
        compose.onNodeWithText("金额（元）").assertExists()
    }

    @Test fun dirtyEditorBackMatchesToolbarAndSystemBack() {
        compose.onNodeWithContentDescription("手动记账").performClick()
        compose.onNode(hasSetTextAction() and hasText("金额（元）")).performTextInput("12.50")
        compose.onNodeWithContentDescription("返回").performClick()
        compose.onNodeWithText("放弃修改？").assertExists()
        compose.onNodeWithText("继续编辑").performClick()
        compose.runOnUiThread { compose.activity.onBackPressedDispatcher.onBackPressed() }
        compose.onNodeWithText("放弃修改？").assertExists()
        compose.onNodeWithText("放弃修改", useUnmergedTree = true).performClick()
        compose.onNodeWithText("本月个人支出").assertExists()
    }
}
