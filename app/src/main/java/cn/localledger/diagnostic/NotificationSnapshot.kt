package cn.localledger.diagnostic

import android.app.Notification
import android.content.Context
import android.os.Bundle
import android.service.notification.StatusBarNotification
import java.util.UUID

data class NotificationSnapshot(
    val timestamp: Long, val packageName: String, val appName: String = "",
    val title: String = "", val text: String = "", val bigText: String = "", val subText: String = "",
    val summaryText: String = "", val extraText: String = "", val notificationKey: String = "",
    val isGroupSummary: Boolean = false, val captureNote: String = ""
) {
    fun characterCount() = listOf(packageName, appName, title, text, bigText, subText, summaryText, extraText, notificationKey, captureNote).sumOf { it.length.toLong() }
    fun entity(sessionId: String, receivedAt: Long) = NotificationLogEntity(UUID.randomUUID().toString(), sessionId,
        timestamp, receivedAt, packageName, appName, title, text, bigText, subText, summaryText, extraText,
        notificationKey, isGroupSummary, captureNote)
}

object DiagnosticNotificationReader {
    private val primary = setOf(Notification.EXTRA_TITLE, Notification.EXTRA_TEXT, Notification.EXTRA_BIG_TEXT,
        Notification.EXTRA_SUB_TEXT, Notification.EXTRA_SUMMARY_TEXT)

    fun read(context: Context, sbn: StatusBarNotification): NotificationSnapshot {
        val notes = mutableListOf<String>()
        val extras = runCatching { sbn.notification.extras }.getOrElse { notes += "extras 无法读取"; Bundle() }
        var budget = 16_384
        fun bounded(value: String): String {
            val result = value.take(budget)
            if (result.length < value.length && "文本超出每条 16384 字符上限，已截断" !in notes) notes += "文本超出每条 16384 字符上限，已截断"
            budget -= result.length
            return result
        }
        fun field(key: String) = bounded(runCatching { extras.getCharSequence(key)?.toString().orEmpty() }
            .getOrElse { notes += "$key 无法读取"; "" })
        val title = field(Notification.EXTRA_TITLE)
        val text = field(Notification.EXTRA_TEXT)
        val big = field(Notification.EXTRA_BIG_TEXT)
        val sub = field(Notification.EXTRA_SUB_TEXT)
        val summary = field(Notification.EXTRA_SUMMARY_TEXT)
        val additional = StringBuilder()
        // Read only text and small nested bundles (e.g. MessagingStyle messages). Never stringify arbitrary parcelables.
        fun texts(bundle: Bundle, prefix: String, depth: Int) {
            val keys = runCatching { bundle.keySet().sorted() }.getOrElse { notes += "部分 extras 无法枚举"; emptyList() }
            for (key in keys.take(64)) {
                if (depth == 0 && key in primary) continue
                if (budget <= 0) { notes += "其他 extras 可能已截断"; break }
                @Suppress("DEPRECATION")
                val value = runCatching { bundle.get(key) }.getOrElse { notes += "$prefix$key 无法读取"; null }
                when (value) {
                    is CharSequence -> additional.append(bounded("$prefix$key: $value\n"))
                    is Bundle -> if(depth < 2) texts(value, "$prefix$key.", depth + 1)
                    is Array<*> -> value.take(32).forEachIndexed { index, item ->
                        when(item) {
                            is CharSequence -> additional.append(bounded("$prefix$key[$index]: $item\n"))
                            is Bundle -> if(depth < 2) texts(item, "$prefix$key[$index].", depth + 1)
                        }
                    }
                }
            }
        }
        texts(extras, "", 0)
        sbn.notification.tickerText?.let { additional.append(bounded("tickerText: $it\n")) }
        val name = runCatching { context.packageManager.getApplicationLabel(context.packageManager.getApplicationInfo(sbn.packageName, 0)).toString() }.getOrDefault("")
        return NotificationSnapshot(sbn.postTime, sbn.packageName, name, title, text, big, sub, summary,
            additional.toString(), sbn.key, sbn.notification.flags and Notification.FLAG_GROUP_SUMMARY != 0, notes.distinct().joinToString("；"))
    }
}
