package cn.localledger.merchant

import android.app.Application
import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import cn.localledger.data.*
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.*
import org.junit.Assert.*
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], application = Application::class)
class MerchantRepositoryTest {
    private lateinit var db: LedgerDatabase
    private lateinit var ledger: LedgerRepository
    private lateinit var merchants: MerchantRepository
    @Before fun setup() {
        db = Room.inMemoryDatabaseBuilder(ApplicationProvider.getApplicationContext<Context>(), LedgerDatabase::class.java).allowMainThreadQueries().build()
        ledger = LedgerRepository(db); merchants = MerchantRepository(db)
    }
    @After fun close() { db.close() }

    @Test fun repeatedSaveOfSameBillNeverInflatesUsage() = runBlocking {
        val id = ledger.save(LedgerEntry(amount = 100, merchant = "食堂", category = "餐饮"))
        repeat(5) { ledger.save(ledger.get(id)!!.copy(note = "编辑 $it")) }
        val memory = merchants.memories.first { it.isNotEmpty() }.single()
        assertEquals(1, memory.useCount); assertFalse(memory.isShortcut)
    }

    @Test fun editingAndDeletingBillRecomputesStatistics() = runBlocking {
        val ids = (1..3).map { ledger.save(LedgerEntry(amount = 100, merchant = "食堂", category = "餐饮", timestamp = it.toLong())) }
        assertEquals(3, merchants.memories.first { it.singleOrNull()?.useCount == 3 }.single().useCount)
        ledger.save(ledger.get(ids.first())!!.copy(category = "其他"))
        val changed = merchants.memories.first { list -> list.sumOf { it.useCount } == 3 && list.size == 2 }
        assertEquals(2, changed.single { it.category == "餐饮" }.useCount)
        ledger.delete(ids.last())
        val afterDelete = merchants.memories.first { list -> list.sumOf { it.useCount } == 2 }
        assertEquals(1, afterDelete.single { it.category == "餐饮" }.useCount)
    }

    @Test fun oldTransactionsImmediatelyProvideShortcutsWithoutBackfill() = runBlocking {
        repeat(3) { ledger.save(LedgerEntry(amount = 100, merchant = "肯德基", category = "餐饮", timestamp = it.toLong())) }
        assertTrue(merchants.memories.first { it.isNotEmpty() }.single().isShortcut)
        assertEquals(0, db.openHelper.readableDatabase.query("SELECT COUNT(*) FROM merchant_preferences").use { it.moveToFirst(); it.getInt(0) })
    }

    @Test fun manualCreatePinEditAndDeleteOnlyAffectMemory() = runBlocking {
        val billId = ledger.save(LedgerEntry(amount = 100, merchant = "星巴克", category = "餐饮"))
        merchants.saveManual("星巴克", "餐饮", false)
        var memory = merchants.memories.first { it.singleOrNull()?.isManual == true }.single()
        assertTrue(memory.isShortcut); assertEquals(1, memory.useCount)
        merchants.setPinned(memory, true)
        memory = merchants.memories.first { it.singleOrNull()?.isPinned == true }.single()
        merchants.saveManual("Starbucks", "其他", true, memory)
        val renamed = merchants.memories.first { list -> list.any { it.name == "Starbucks" } }
        assertEquals("其他", renamed.single { it.name == "Starbucks" }.category)
        merchants.suppress(renamed.single { it.name == "Starbucks" })
        assertTrue(merchants.memories.first { list -> list.none { it.name == "Starbucks" } }.none { it.name == "Starbucks" })
        assertEquals("星巴克", ledger.get(billId)!!.merchant)
    }

    @Test fun unknownMerchantAndPendingCategoryAreIgnoredInDatabaseFlow() = runBlocking {
        ledger.save(LedgerEntry(amount = 100, merchant = "未知商户", category = "餐饮"))
        ledger.save(LedgerEntry(amount = 100, merchant = "麦当劳", category = "待分类"))
        assertTrue(merchants.memories.first().isEmpty())
    }
}
