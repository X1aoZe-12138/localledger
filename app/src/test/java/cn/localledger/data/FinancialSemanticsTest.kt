package cn.localledger.data

import android.app.Application
import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import cn.localledger.parser.*
import cn.localledger.merchant.MerchantMemoryEngine
import kotlinx.coroutines.runBlocking
import org.junit.*
import org.junit.Assert.*
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], application = Application::class)
class FinancialSemanticsTest {
    @Test fun trueIncomeExcludesEveryReturnAndAA() {
        val entries = listOf(LedgerEntry(amount = 800000, transactionType = "income", incomeCategoryId = "salary"),
            LedgerEntry(amount = 10000, transactionType = "refund"), LedgerEntry(amount = 20000, transactionType = "reimbursement"),
            LedgerEntry(amount = 16000, transactionType = "transfer_adjustment"),
            LedgerEntry(amount = 20000, splitEnabled = true, splitCalculationMode = "MANUAL_RECEIVED", receivedAmountCents = 16000))
        assertEquals(800000L, trueIncome(entries))
        assertEquals(4000L, entries.sumOf { it.personalExpenseCents })
        assertTrue(MerchantMemoryEngine.build(entries.map { it.copy(category = "餐饮", merchant = "食堂") }.filter { it.transactionType != "expense" }, emptyList()).isEmpty())
    }
    @Test fun incomeCategoryCrudAndDeletingUsedCategoryPreservesTransaction() = runBlocking {
        val db = Room.inMemoryDatabaseBuilder(ApplicationProvider.getApplicationContext<Context>(), LedgerDatabase::class.java).allowMainThreadQueries().build()
        try {
            val repo = FinancialRepository(db); repo.initialize()
            repo.saveCategory("稿费"); val category = db.financialDao().categories().last()
            val id = LedgerRepository(db).save(LedgerEntry(amount = 30000, transactionType = "income", incomeCategoryId = category.id))
            repo.saveCategory("稿费收入", category.id); repo.moveCategory(category.id, -1)
            assertEquals("稿费收入", db.financialDao().categories().first { it.id == category.id }.name)
            assertTrue(db.financialDao().categories().indexOfFirst { it.id == category.id } < 5)
            repo.deleteCategory(category.id)
            assertEquals(OTHER_INCOME_ID, db.dao().get(id)!!.incomeCategoryId); assertEquals(30000L, db.dao().get(id)!!.amount)
            assertTrue(runCatching { repo.deleteCategory(OTHER_INCOME_ID) }.isFailure)
            assertTrue(runCatching { repo.saveCategory("工资") }.isFailure)
        } finally { db.close() }
    }
    @Test fun refundParserAndLegacyDedupDoNotRecreateOldIncomeBills() = runBlocking {
        val db = Room.inMemoryDatabaseBuilder(ApplicationProvider.getApplicationContext<Context>(), LedgerDatabase::class.java).allowMainThreadQueries().build()
        try {
            val input = NotificationInput(ALIPAY, "交易提醒", "收到退款￥100", timestamp = 1000, key = "key")
            val repo = LedgerRepository(db)
            repo.record(input, ParsedTransaction(10000, "income", "未知商户", "Alipay", 1000))
            val refund = NotificationParser().parse(input)!!
            assertEquals("refund", refund.transactionType)
            assertNull(repo.record(input, refund)); assertEquals(1, db.dao().allEntries().size)
            val newInput = input.copy(timestamp = 5000, key = "new")
            repo.record(newInput, refund.copy(timestamp = 5000))
            assertEquals(2, db.dao().allEntries().size); assertEquals(10000L, trueIncome(db.dao().allEntries()))
        } finally { db.close() }
    }
}
