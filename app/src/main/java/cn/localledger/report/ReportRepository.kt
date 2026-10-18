package cn.localledger.report

import android.content.*
import android.net.Uri
import androidx.room.withTransaction
import androidx.activity.result.contract.ActivityResultContracts
import cn.localledger.data.*
import java.time.*

class ReportDocumentContract : ActivityResultContracts.CreateDocument("text/markdown") {
    override fun createIntent(context: Context, input: String): Intent = super.createIntent(context, input).addCategory(Intent.CATEGORY_OPENABLE)
}

class ReportRepository(private val db: LedgerDatabase) {
    val lastReport = db.financialDao().setting("lastReportGeneratedAt")
    suspend fun prepare(type: String, choice: String, start: LocalDate, end: LocalDate, options: ReportOptions,
        purchaseName: String, purchaseCents: Long?, now: Long = System.currentTimeMillis()): PreparedReport {
        val (data, last) = db.withTransaction {
            ensureIncomeCategories(db)
            ReportData(db.dao().allEntries(), db.financialDao().categories(), db.assetDao().accounts(), db.assetDao().snapshots()) to
                db.financialDao().settings().firstOrNull { it.key == "lastReportGeneratedAt" }?.value?.toLongOrNull()
        }
        val range = reportRange(choice, now, last, start, end)
        // Release the database snapshot before formatting long text, so notification writes can proceed.
        return FinanceReport.generate(data,
            ReportRequest(type, range, options, purchaseName, purchaseCents), now)
    }
    suspend fun save(resolver: ContentResolver, uri: Uri, report: PreparedReport) {
        ReportFiles.writeVerified(resolver, uri, report.content)
        // This is the report's data cutoff, recorded only after successful output verification.
        db.financialDao().putSetting(LedgerSetting("lastReportGeneratedAt", report.generatedAt.toString()))
    }
}

object ReportFiles {
    fun writeVerified(resolver: ContentResolver, uri: Uri, content: String) {
        require(uri.scheme == "content")
        val bytes = content.toByteArray(Charsets.UTF_8)
        resolver.openOutputStream(uri, "wt")?.use { it.write(bytes); it.flush() } ?: error("无法写入报告")
        resolver.openInputStream(uri)?.use { input ->
            var cursor = 0; val buffer = ByteArray(8192)
            while(true) { val n = input.read(buffer); if(n < 0) break
                require(cursor + n <= bytes.size && (0 until n).all { buffer[it] == bytes[cursor + it] }) { "报告文件内容不完整" }
                cursor += n
            }
            require(cursor == bytes.size)
        } ?: error("无法验证报告")
    }
    fun share(resolver: ContentResolver, uri: Uri): Intent {
        require(uri.scheme == "content")
        return Intent(Intent.ACTION_SEND).setType("text/markdown").putExtra(Intent.EXTRA_STREAM, uri).apply {
            clipData = ClipData.newUri(resolver, "财务报告", uri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
    }
}
