package cn.localledger.data

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import cn.localledger.notification.*
import cn.localledger.parser.*
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.flow.first
import org.junit.*
import org.junit.Assert.*
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], application = android.app.Application::class)
class RecordedReminderTest {
    private lateinit var db: LedgerDatabase
    private lateinit var repo: LedgerRepository
    private lateinit var manager: NotificationManager
    private lateinit var reminders: RecordedReminderManager
    private val context get() = ApplicationProvider.getApplicationContext<Context>()

    @Before fun setup() {
        db = Room.inMemoryDatabaseBuilder(context, LedgerDatabase::class.java).allowMainThreadQueries().build()
        repo = LedgerRepository(db)
        manager = context.getSystemService(NotificationManager::class.java)
        manager.createNotificationChannel(NotificationChannel(RECORDED_CHANNEL, "记账", NotificationManager.IMPORTANCE_DEFAULT))
        manager.createNotificationChannel(NotificationChannel("other", "其他", NotificationManager.IMPORTANCE_DEFAULT))
        reminders = RecordedReminderManager(context, repo)
    }
    @After fun close() { db.close() }
    private suspend fun auto(key: String, amount: Long): Long = repo.record(
        NotificationInput(WECHAT, "微信支付", "已扣费¥${Money.format(amount)}", timestamp = 10000 + key.hashCode().toLong().and(0xffff), key = key),
        ParsedTransaction(amount, "expense", "未知商户", "WeChat", 10000))!!
    private fun post(id: Long, channel: String = RECORDED_CHANNEL) = manager.notify(id.toString(), REMINDER_NOTIFICATION_ID,
        Notification.Builder(context, channel).setSmallIcon(android.R.drawable.ic_dialog_info).setContentTitle("已自动记账").build())
    private fun active(): Set<Long> = manager.activeNotifications.mapNotNull { it.tag?.toLongOrNull() }.toSet()

    @Test fun appEditCompletesOnlyMatchingReminderEvenForEqualAmounts() = runBlocking {
        val first = auto("a", 1690); val second = auto("b", 1690)
        post(first); post(second)
        repo.save(repo.get(first)!!.copy(category = "餐饮", merchant = "食堂"))
        assertEquals(setOf(first), repo.completedReminderIds())
        assertTrue(reminders.cancel(first))
        assertEquals(setOf(second), active())
        repo.save(repo.get(second)!!.copy(category = "餐饮", merchant = "食堂"))
        reminders.cancel(second)
        assertTrue(active().isEmpty())
        assertEquals(2, repo.entries.first().size)
    }

    @Test fun restartReconcilesCompletedOnlyAndLeavesUnreviewedVisible() = runBlocking {
        val first = auto("c", 1690); val second = auto("d", 2200)
        post(first); post(second)
        repo.save(repo.get(first)!!.copy(category = "餐饮"))
        // Simulate a process ending after DB commit but before NotificationManager.cancel.
        RecordedReminderManager(context, LedgerRepository(db)).reconcile()
        assertEquals(setOf(second), active())
        assertEquals(setOf(first), LedgerRepository(db).completedReminderIds())
    }

    @Test fun failedSaveKeepsNotificationAndNoCompletionMarker() = runBlocking {
        val id = auto("e", 2400); post(id)
        runCatching { repo.save(repo.get(id)!!.copy(amount = -1)) }.onSuccess { fail("Invalid amount must fail") }
        assertTrue(repo.completedReminderIds().isEmpty())
        assertEquals(setOf(id), active())
    }

    @Test fun manuallyAddedSameAmountNeverClaimsCapturedReminder() = runBlocking {
        val recorded = auto("f", 1690); post(recorded)
        val manual = repo.save(LedgerEntry(amount = 1690, merchant = "食堂", category = "餐饮"))
        repo.save(repo.get(manual)!!.copy(note = "补录"))
        assertTrue(repo.completedReminderIds().isEmpty())
        reminders.reconcile()
        assertEquals(setOf(recorded), active())
    }

    @Test fun deleteCapturedBillCompletesItsOwnReminderWithoutTouchingOtherChannels() = runBlocking {
        val first = auto("g", 1300); val second = auto("h", 2200)
        post(first); post(second, "other")
        repo.delete(first)
        reminders.reconcile()
        assertEquals(setOf(second), active())
        assertNull(repo.get(first))
    }
}
