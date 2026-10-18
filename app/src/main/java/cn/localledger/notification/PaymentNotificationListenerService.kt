package cn.localledger.notification

import android.app.*
import android.content.*
import android.service.notification.*
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import cn.localledger.*
import cn.localledger.parser.*
import cn.localledger.diagnostic.DiagnosticNotificationReader
import kotlinx.coroutines.*
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

class PaymentNotificationListenerService : NotificationListenerService() {
    private val app get() = application as LedgerApplication
    private val parser = NotificationParser()
    private val mutex = Mutex()
    override fun onListenerConnected() {
        app.listenerConnected.value = true
        // Do not reinterpret old visible notifications after a parser upgrade. Only new posted callbacks enter parsing.
    }
    override fun onListenerDisconnected() {
        app.listenerConnected.value = false
        runCatching { requestRebind(ComponentName(this, javaClass)) }
    }
    override fun onDestroy() { app.listenerConnected.value = false; super.onDestroy() }
    override fun onNotificationPosted(sbn: StatusBarNotification) {
        val receivedAt = System.currentTimeMillis()
        // Diagnostic capture is independent and precedes payment-only filters. Reconnection replay is excluded.
        app.diagnosticManager.capture(receivedAt) { DiagnosticNotificationReader.read(this, sbn) }
        processPayment(sbn)
    }
    private fun processPayment(sbn: StatusBarNotification) {
        if (sbn.packageName !in setOf(ALIPAY, WECHAT)) return
        if (sbn.notification.flags and Notification.FLAG_GROUP_SUMMARY != 0) return
        val input = runCatching {
            val e = sbn.notification.extras
            fun field(key: String) = e.getCharSequence(key)?.toString().orEmpty()
            val additional = listOf(field(Notification.EXTRA_SUMMARY_TEXT), field(Notification.EXTRA_INFO_TEXT),
                e.getCharSequenceArray(Notification.EXTRA_TEXT_LINES)?.joinToString("\n").orEmpty(),
                sbn.notification.tickerText?.toString().orEmpty()).filter { it.isNotBlank() }.joinToString("\n")
            NotificationInput(sbn.packageName, field(Notification.EXTRA_TITLE), field(Notification.EXTRA_TEXT),
                field(Notification.EXTRA_BIG_TEXT), field(Notification.EXTRA_SUB_TEXT), additional,
                sbn.postTime.takeIf { it > 0 } ?: System.currentTimeMillis(), sbn.key)
        }.getOrElse { app.listenerError.value = "读取通知失败，请检查权限并手动补录"; return }
        app.scope.launch {
            mutex.withLock {
                try {
                    when (val decision = parser.classify(input)) {
                        is NotificationDecision.Transaction -> {
                            val id = app.repository.record(input, decision.transaction)
                            if (id != null) runCatching { notifyRecorded(id, decision.transaction) }
                        }
                        is NotificationDecision.Unresolved -> app.repository.log(input, decision.reason)
                        // Known amount-free notices are not errors or bills. Raw text remains available in opted-in diagnostics.
                        is NotificationDecision.FinancialNotice, NotificationDecision.Ignore -> Unit
                    }
                    app.listenerError.value = null
                } catch (e: CancellationException) { throw e }
                catch (_: Exception) { app.listenerError.value = "自动入账失败，请检查手机存储空间并手动补录" }
            }
        }
    }
    private fun notifyRecorded(id: Long, parsed: ParsedTransaction) {
        val manager = getSystemService(NotificationManager::class.java)
        manager.createNotificationChannel(NotificationChannel(RECORDED_CHANNEL, "自动记账提醒", NotificationManager.IMPORTANCE_DEFAULT))
        if (!NotificationManagerCompat.from(this).areNotificationsEnabled()) return
        val intent = Intent(this, MainActivity::class.java).putExtra("entryId", id)
            .setData(android.net.Uri.parse("localledger://entry/$id"))
        val pending = PendingIntent.getActivity(this, 0, intent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        val label = if(parsed.platform == "Alipay") "支付宝" else "微信"
        manager.notify(id.toString(), REMINDER_NOTIFICATION_ID, NotificationCompat.Builder(this, RECORDED_CHANNEL)
            .setSmallIcon(R.drawable.ic_ledger).setContentTitle("已自动记账")
            .setContentText("¥${Money.format(parsed.amount)} · $label · ${if(parsed.transactionType == "expense") "待分类" else if(parsed.transactionType == "income") "其他收入" else "退款（非收入）"}")
            // A tap only opens the bill; keep the reminder until its edit is committed.
            .setVisibility(NotificationCompat.VISIBILITY_PRIVATE).setAutoCancel(false).setContentIntent(pending).build())
    }
}
