package cn.localledger.data

import android.app.Application
import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import cn.localledger.statistics.ExpenseStatistics
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.time.*

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], application = Application::class)
class SplitRepositoryTest {
    @Test fun historicalEnableDisableCountAmountDeleteRecalculatesWithoutDrift() = runBlocking {
        val db = Room.inMemoryDatabaseBuilder(ApplicationProvider.getApplicationContext<Context>(), LedgerDatabase::class.java).allowMainThreadQueries().build()
        try {
            val repo = LedgerRepository(db)
            val date = LocalDate.of(2026, 9, 27)
            val id = repo.save(LedgerEntry(amount = 10000, timestamp = date.atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli()))
            suspend fun total() = ExpenseStatistics.report(repo.entries.first(), date, date).total
            assertEquals(10000L, total())
            val original = repo.get(id)!!
            repo.save(original.copy(splitEnabled = true, splitCalculationMode = "EQUAL"))
            assertEquals(3334L, total()); assertEquals(10000L, repo.get(id)!!.amount)
            repo.save(repo.get(id)!!.copy(participantCount = 2)); assertEquals(5000L, total())
            repo.save(repo.get(id)!!.copy(amount = 20000)); assertEquals(10000L, total())
            repo.save(repo.get(id)!!.copy(splitCalculationMode = "MANUAL_RECEIVED", receivedAmountCents = 16000)); assertEquals(4000L, total())
            repo.save(repo.get(id)!!.copy(splitEnabled = false, splitCalculationMode = "NONE", receivedAmountCents = null)); assertEquals(20000L, total())
            repo.delete(id); assertEquals(0L, total())
        } finally { db.close() }
    }
    @Test fun invalidSplitCannotPersist() = runBlocking {
        val db = Room.inMemoryDatabaseBuilder(ApplicationProvider.getApplicationContext<Context>(), LedgerDatabase::class.java).allowMainThreadQueries().build()
        try {
            val repo = LedgerRepository(db)
            assertTrue(runCatching { repo.save(LedgerEntry(amount = 100, splitEnabled = true, splitCalculationMode = "MANUAL_SELF", manualSelfAmountCents = 101)) }.isFailure)
            assertEquals(0, db.dao().allEntries().size)
        } finally { db.close() }
    }
}
