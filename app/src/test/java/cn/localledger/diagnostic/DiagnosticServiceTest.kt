package cn.localledger.diagnostic

import android.app.Notification
import android.os.Bundle
import android.os.UserHandle
import android.service.notification.StatusBarNotification
import androidx.test.core.app.ApplicationProvider
import cn.localledger.LedgerApplication
import cn.localledger.notification.PaymentNotificationListenerService
import cn.localledger.parser.ALIPAY
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.first
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class DiagnosticServiceTest {
    @Test fun realDeviceExpensesPersistWithoutGuessingCategoryOrMerchant() = runBlocking {
        val app = ApplicationProvider.getApplicationContext<LedgerApplication>()
        val controller = Robolectric.buildService(PaymentNotificationListenerService::class.java).create()
        try {
            val examples = listOf(
                Triple("com.tencent.mm", "微信支付", "已扣费¥13.80"),
                Triple(ALIPAY, "交易提醒", "你有一笔42.50元的支出，点击领0.5元话费券。")
            )
            examples.forEachIndexed { index, (pkg, title, text) ->
                val n = Notification().apply { extras = Bundle().apply {
                    putCharSequence(Notification.EXTRA_TITLE, title)
                    putCharSequence(Notification.EXTRA_TEXT, text)
                } }
                controller.get().onNotificationPosted(StatusBarNotification(pkg, pkg, index + 100, "real", 0, 0, 0, n, UserHandle.getUserHandleForUid(0), System.currentTimeMillis()))
                withTimeout(10000) { app.repository.entries.first { it.size == index + 1 } }
            }
            val bills = app.repository.entries.first()
            assertEquals(setOf(1380L, 4250L), bills.map { it.amount }.toSet())
            assertTrue(bills.all { it.category == "待分类" && it.merchant == "未知商户" && it.transactionType == "expense" })
            assertTrue(app.repository.unknown.first().isEmpty())
        } finally { controller.destroy(); app.scope.cancel() }
    }
    @Test fun sameCallbackRecordsRawNotificationAndPaymentIndependently() = runBlocking {
        val app = ApplicationProvider.getApplicationContext<LedgerApplication>()
        val session = app.diagnosticManager.start(5)
        val controller = Robolectric.buildService(PaymentNotificationListenerService::class.java).create()
        try {
            val notification = Notification().apply { extras = Bundle().apply {
                putCharSequence(Notification.EXTRA_TITLE, "交易提醒")
                putCharSequence(Notification.EXTRA_TEXT, "你有一笔26.80元的支出")
            } }
            controller.get().onNotificationPosted(StatusBarNotification(ALIPAY, ALIPAY, 1, "payment", 0, 0, 0, notification, UserHandle.getUserHandleForUid(0), System.currentTimeMillis()))
            val bills = withTimeout(10000) { app.repository.entries.first { it.isNotEmpty() } }
            val logs = withTimeout(10000) { app.diagnosticManager.repository.logs(session.id).first { it.isNotEmpty() } }
            assertEquals(2680L, bills.single().amount)
            assertEquals("你有一笔26.80元的支出", logs.first().text)
            app.diagnosticManager.repository.clear()
            assertEquals(2680L, app.repository.entries.first().single().amount)
        } finally { controller.destroy(); app.scope.cancel() }
    }
    @Test fun serviceCapturesOtherAppsAndGroupSummariesWithoutCreatingBills() = runBlocking {
        val app = ApplicationProvider.getApplicationContext<LedgerApplication>()
        val session = app.diagnosticManager.start(5)
        val controller = Robolectric.buildService(PaymentNotificationListenerService::class.java).create()
        try {
            val notification = Notification().apply { extras = Bundle(); flags = Notification.FLAG_GROUP_SUMMARY }
            controller.get().onNotificationPosted(StatusBarNotification("campus.app", "campus.app", 3, null, 0, 0, 0, notification, UserHandle.getUserHandleForUid(0), System.currentTimeMillis()))
            val logs = withTimeout(10000) { app.diagnosticManager.repository.logs(session.id).first { it.isNotEmpty() } }
            assertEquals("campus.app", logs.single().packageName)
            assertTrue(logs.single().isGroupSummary)
            assertTrue(app.repository.entries.first().isEmpty())
        } finally { controller.destroy(); app.scope.cancel() }
    }
}
