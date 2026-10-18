package cn.localledger.diagnostic

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.os.PersistableBundle
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

object DiagnosticText {
    fun time(timestamp: Long): String = Instant.ofEpochMilli(timestamp).atZone(ZoneId.systemDefault())
        .format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss.SSS xxx"))
    fun notification(log: NotificationLogEntity) = buildString {
        appendLine("## ${time(log.receivedAt)}")
        appendLine("id: ${log.id}")
        appendLine("receivedAt: ${time(log.receivedAt)} (${log.receivedAt})")
        appendLine("timestamp / postTime: ${time(log.timestamp)} (${log.timestamp})")
        appendLine("package: ${log.packageName}")
        appendLine("app: ${log.appName.ifBlank { "（系统未提供名称）" }}")
        appendLine("notificationKey: ${log.notificationKey}")
        appendLine("groupSummary: ${log.isGroupSummary}")
        appendLine("title:\n${log.title}")
        appendLine("text:\n${log.text}")
        appendLine("bigText:\n${log.bigText}")
        appendLine("subText:\n${log.subText}")
        appendLine("summaryText:\n${log.summaryText}")
        appendLine("otherTextExtras:\n${log.extraText}")
        appendLine("captureNote: ${log.captureNote}")
    }
    fun session(export: DiagnosticExport) = buildString {
        val s = export.session
        appendLine("# Notification Diagnostic Session")
        appendLine("Session: ${s.id}")
        appendLine("Start: ${time(s.startTime)}")
        appendLine("Expected end: ${time(s.expectedEndTime)}")
        appendLine("End: ${s.endTime?.let(::time) ?: "诊断中（当前快照）"}")
        appendLine("Planned duration: ${s.plannedDuration} ms")
        appendLine("Actual duration: ${s.actualDuration?.let { "$it ms" } ?: "诊断中"}")
        appendLine("Stop reason: ${s.stopReason}")
        appendLine("Notification count: ${export.notifications.size}")
        appendLine("按 receivedAt 排序；postTime 为系统发布时间；未分析消费。")
        export.notifications.forEach { appendLine("\n---\n"); append(notification(it)) }
    }

    // Android clipboard IPC has a size limit. Large sessions are copied in explicit lossless parts, never silently truncated.
    fun clipboardParts(text: String, limit: Int = 120_000): List<String> {
        require(limit >= 2)
        if(text.isEmpty()) return listOf("")
        val parts = mutableListOf<String>()
        var start = 0
        while(start < text.length) {
            var end = minOf(start + limit, text.length)
            if(end < text.length && text[end - 1].isHighSurrogate() && text[end].isLowSurrogate()) end--
            parts += text.substring(start, end)
            start = end
        }
        return parts
    }
}

object DiagnosticClipboard {
    fun copy(context: Context, text: String) {
        val clip = ClipData.newPlainText("通知诊断日志", text)
        clip.description.extras = PersistableBundle().apply { putBoolean("android.content.extra.IS_SENSITIVE", true) }
        context.getSystemService(ClipboardManager::class.java).setPrimaryClip(clip)
    }
}
