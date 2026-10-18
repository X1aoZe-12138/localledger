package cn.localledger.data

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import cn.localledger.parser.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.first
import org.junit.*
import org.junit.Assert.*
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], application = android.app.Application::class)
class LedgerRepositoryTest {
    private lateinit var db: LedgerDatabase
    private lateinit var repository: LedgerRepository
    private val input = NotificationInput(ALIPAY, "交易提醒", "你有一笔12元的支出", timestamp = 10000, key = "payment1")
    private val parsed = ParsedTransaction(1200, "expense", "未知商户", "Alipay", 10000)
    @Before fun setup() {
        db = Room.inMemoryDatabaseBuilder(ApplicationProvider.getApplicationContext<Context>(), LedgerDatabase::class.java).allowMainThreadQueries().build()
        repository = LedgerRepository(db)
    }
    @After fun close() { db.close() }
    @Test fun automaticInsertAndReplayAfterRepositoryRecreation() = runBlocking {
        assertNotNull(repository.record(input, parsed))
        assertNull(LedgerRepository(db).record(input, parsed))
        assertEquals(1, repository.entries.first().size)
        assertEquals("待分类", repository.entries.first().single().category)
    }
    @Test fun editDeleteDoesNotResurrectOldNotification() = runBlocking {
        val id = repository.record(input, parsed)!!
        repository.save(repository.get(id)!!.copy(amount = 1300, category = "餐饮", note = "修改"))
        assertEquals(1300L, repository.get(id)!!.amount)
        repository.delete(id)
        assertNull(repository.record(input, parsed))
        assertTrue(repository.entries.first().isEmpty())
    }
    @Test fun independentSameAmountPaymentsPersist() = runBlocking {
        repository.record(input, parsed)
        repository.record(input.copy(key = "payment2"), parsed)
        assertEquals(2, repository.entries.first().size)
    }
    @Test fun concurrentCallbacksInsertOnce() = runBlocking {
        coroutineScope { (1..8).map { async(Dispatchers.IO) { repository.record(input, parsed) } }.awaitAll() }
        assertEquals(1, repository.entries.first().size)
    }
    @Test fun manualCrud() = runBlocking {
        val id = repository.save(LedgerEntry(amount = 29, merchant = "现金", platform = "Manual"))
        assertEquals(29L, repository.get(id)!!.amount)
        repository.delete(id)
        assertNull(repository.get(id))
    }
    @Test fun boundedDebugLogsAndClear() = runBlocking {
        repeat(305) { repository.log(input.copy(timestamp = it.toLong()), "未识别") }
        val logs = repository.unknown.first()
        assertEquals(300, logs.size)
        assertEquals(304L, logs.first().timestamp)
        repository.clearUnknown()
        assertTrue(repository.unknown.first().isEmpty())
    }
}
