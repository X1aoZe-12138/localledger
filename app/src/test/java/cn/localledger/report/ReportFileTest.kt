package cn.localledger.report

import android.app.Application
import android.content.*
import android.content.pm.ProviderInfo
import android.database.Cursor
import android.net.Uri
import android.os.ParcelFileDescriptor
import androidx.activity.result.contract.ActivityResultContracts
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import cn.localledger.backup.*
import cn.localledger.data.*
import kotlinx.coroutines.runBlocking
import org.json.JSONObject
import org.junit.*
import org.junit.Assert.*
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.shadows.ShadowContentResolver
import java.io.File
import java.io.FileNotFoundException
import java.time.LocalDate

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], application = Application::class)
class ReportFileTest {
    private lateinit var db: LedgerDatabase
    private lateinit var repo: ReportRepository
    private lateinit var context: Context
    private lateinit var file: File
    private val uri = Uri.parse("content://report-test/report.md")
    private var failWrite = false; private var badRead = false
    @Before fun setup() {
        context = ApplicationProvider.getApplicationContext()
        file = File(context.cacheDir, "report-test.md")
        val provider = object : ContentProvider() {
            override fun onCreate() = true
            override fun getType(uri: Uri) = "text/markdown"
            override fun query(uri: Uri, projection: Array<out String>?, selection: String?, selectionArgs: Array<out String>?, sortOrder: String?): Cursor? = null
            override fun insert(uri: Uri, values: ContentValues?): Uri? = null
            override fun delete(uri: Uri, selection: String?, selectionArgs: Array<out String>?) = 0
            override fun update(uri: Uri, values: ContentValues?, selection: String?, selectionArgs: Array<out String>?) = 0
            override fun openFile(uri: Uri, mode: String): ParcelFileDescriptor {
                if(mode.contains("w")) { if(failWrite) throw FileNotFoundException("模拟失败")
                    return ParcelFileDescriptor.open(file, ParcelFileDescriptor.MODE_CREATE or ParcelFileDescriptor.MODE_TRUNCATE or ParcelFileDescriptor.MODE_READ_WRITE) }
                if(badRead) file.writeText("不完整")
                return ParcelFileDescriptor.open(file, ParcelFileDescriptor.MODE_READ_ONLY)
            }
        }
        provider.attachInfo(context, ProviderInfo().apply { authority = "report-test" })
        ShadowContentResolver.registerProviderInternal("report-test", provider)
        db = Room.inMemoryDatabaseBuilder(context, LedgerDatabase::class.java).allowMainThreadQueries().build()
        repo = ReportRepository(db)
    }
    @After fun close() { db.close(); file.delete() }
    private suspend fun preview(now: Long = System.currentTimeMillis()) = repo.prepare("ANALYSIS", "MONTH", LocalDate.now(), LocalDate.now(), ReportOptions(), "", null, now)
    @Test fun previewAndCancelDoNotChangeCutoffSuccessfulSafSaveWritesUtf8AndUpdatesOnlyAfterReadback() = runBlocking {
        LedgerRepository(db).save(LedgerEntry(amount = 10000))
        val report = preview()
        assertTrue(db.financialDao().settings().isEmpty())
        val contract = ReportDocumentContract()
        val intent = contract.createIntent(context, report.fileName)
        assertEquals(Intent.ACTION_CREATE_DOCUMENT, intent.action); assertEquals("text/markdown", intent.type)
        assertTrue(intent.hasCategory(Intent.CATEGORY_OPENABLE)); assertTrue(report.fileName.endsWith(".md"))
        assertNull(contract.parseResult(android.app.Activity.RESULT_CANCELED, null))
        assertTrue(db.financialDao().settings().isEmpty())
        repo.save(context.contentResolver, uri, report)
        assertArrayEquals(report.content.toByteArray(Charsets.UTF_8), file.readBytes())
        assertEquals(report.generatedAt.toString(), db.financialDao().settings().single().value)
    }
    @Test fun failedWriteOrCorruptedReadDoesNotAdvanceLastReportTimestamp() = runBlocking {
        db.financialDao().putSetting(LedgerSetting("lastReportGeneratedAt", "1000"))
        val report = preview()
        failWrite = true; assertTrue(runCatching { repo.save(context.contentResolver, uri, report) }.isFailure)
        assertEquals("1000", db.financialDao().settings().single().value)
        failWrite = false; badRead = true; assertTrue(runCatching { repo.save(context.contentResolver, uri, report) }.isFailure)
        assertEquals("1000", db.financialDao().settings().single().value)
    }
    @Test fun shareSheetUsesOnlyContentUriClipDataAndTemporaryReadGrant() = runBlocking {
        repo.save(context.contentResolver, uri, preview())
        val intent = ReportFiles.share(context.contentResolver, uri)
        assertEquals(Intent.ACTION_SEND, intent.action); assertEquals("text/markdown", intent.type)
        assertEquals(uri, intent.getParcelableExtra<Uri>(Intent.EXTRA_STREAM))
        assertEquals(uri, intent.clipData!!.getItemAt(0).uri)
        assertTrue(intent.flags and Intent.FLAG_GRANT_READ_URI_PERMISSION != 0)
        assertEquals(Intent.ACTION_CHOOSER, Intent.createChooser(intent, "分享报告").action)
        assertTrue(runCatching { ReportFiles.share(context.contentResolver, Uri.parse("file:///private/report.md")) }.isFailure)
    }
    @Test fun backupRetainsCutoffButNoMarkdownAndOldSettingsStillRestoreWithDefaults() = runBlocking {
        repo.save(context.contentResolver, uri, preview())
        val backup = BackupRepository(db); val text = backup.export()
        assertFalse(text.contains("个人财务分析报告")); assertFalse(text.contains("report-test"))
        val model = BackupImporter.decode(text); backup.restore(model)
        assertTrue(db.financialDao().settings().any { it.key == "lastReportGeneratedAt" })
        backup.restore(model.copy(settings = emptyList()))
        assertTrue(db.financialDao().settings().isEmpty())
        assertEquals(2, JSONObject(text).getInt("backupFormatVersion"))
    }
}
