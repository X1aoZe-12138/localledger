package cn.localledger.notification

import android.app.NotificationManager
import android.content.Context
import cn.localledger.data.LedgerRepository

/** Only touches this app's recorded-bill reminders, never payment apps' notifications. */
class RecordedReminderManager(context: Context, private val repository: LedgerRepository) {
    private val notifications = context.getSystemService(NotificationManager::class.java)

    fun cancel(entryId: Long): Boolean = runCatching {
        notifications.cancel(entryId.toString(), REMINDER_NOTIFICATION_ID)
    }.isSuccess

    suspend fun reconcile() {
        val completed = repository.completedReminderIds()
        if (completed.isEmpty()) return
        runCatching {
            notifications.activeNotifications.forEach { posted ->
                if (posted.id == REMINDER_NOTIFICATION_ID && posted.notification.channelId == RECORDED_CHANNEL &&
                    posted.tag?.toLongOrNull() in completed) cancel(posted.tag!!.toLong())
            }
        }
    }
}

const val RECORDED_CHANNEL = "recorded"
const val REMINDER_NOTIFICATION_ID = 1
