package cn.localledger.backup

import android.app.Application
import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import cn.localledger.data.*
import cn.localledger.merchant.MerchantPreferenceEntity
import cn.localledger.parser.*
import cn.localledger.diagnostic.*
import kotlinx.coroutines.runBlocking
import org.json.*
import org.junit.*
import org.junit.Assert.*
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], application = Application::class)
class BackupTest {
    private lateinit var db: LedgerDatabase
    private lateinit var ledger: LedgerRepository
    private lateinit var backup: BackupRepository
    @Before fun setup() { db = Room.inMemoryDatabaseBuilder(ApplicationProvider.getApplicationContext<Context>(), LedgerDatabase::class.java).allowMainThreadQueries().build(); ledger = LedgerRepository(db); backup = BackupRepository(db) }
    @After fun close() { db.close() }
    private suspend fun seed() {
        ledger.save(LedgerEntry(amount = 10000, category = "餐饮", merchant = "食堂", note = "逗号,引号\"及换行\n备注"))
        ledger.save(LedgerEntry(amount = 20000, category = "购物", merchant = "商场", splitEnabled = true, splitCalculationMode = "MANUAL_RECEIVED", receivedAmountCents = 16000))
        ledger.save(LedgerEntry(amount = 800000, transactionType = "income", incomeCategoryId = "salary", note = "工资"))
        ledger.save(LedgerEntry(amount = 5000, transactionType = "income", incomeCategoryId = "gift"))
        ledger.save(LedgerEntry(amount = 10000, transactionType = "refund"))
        ledger.save(LedgerEntry(amount = 20000, transactionType = "reimbursement"))
        db.merchantDao().put(MerchantPreferenceEntity(normalizeMerchantName("食堂"), "餐饮", "食堂", isPinned = true))
        db.merchantDao().put(MerchantPreferenceEntity(normalizeMerchantName("商场"), "购物", "商场", isManual = true, isHidden = true))
        FinancialRepository(db).saveCategory("稿费")
        db.dao().receipt(Receipt("token", "key", 1000, "hash", "WeChat", 10000, "expense", "食堂"))
        backup.recordBackupTime(1000)
    }
    @Test fun roundTripPreservesEveryDurableFieldAndRelationships() = runBlocking {
        seed()
        val before = backup.snapshot()
        val encoded = backup.export()
        val imported = BackupImporter.decode(encoded)
        db.dao().clearEntries(); db.merchantDao().clearPreferences(); db.dao().clearReceipts(); db.financialDao().clearCategories(); db.financialDao().clearSettings()
        val summary = backup.restore(imported)
        assertEquals(before, backup.snapshot())
        assertEquals(6, summary.transactions); assertEquals(2, summary.income); assertEquals(1, summary.split); assertEquals(2, summary.merchants)
        assertEquals(20000L, db.dao().allEntries().first { it.splitEnabled }.amount)
        assertEquals(4000L, db.dao().allEntries().first { it.splitEnabled }.personalExpenseCents)
    }
    @Test fun malformedJsonNeverWritesAnything() = runBlocking {
        seed(); val before = backup.snapshot()
        listOf("not JSON", "{", "[]", "{}", backup.export() + " trailing").forEach { text -> assertTrue(runCatching { backup.restore(BackupImporter.decode(text)) }.isFailure); assertEquals(before, backup.snapshot()) }
    }
    @Test fun unsupportedVersionHasClearError() = runBlocking {
        seed(); val root = JSONObject(backup.export()).put("backupFormatVersion", 99)
        val error = runCatching { BackupImporter.decode(root.toString()) }.exceptionOrNull()
        assertTrue(error is InvalidBackup); assertTrue(error!!.message!!.contains("backupFormatVersion"))
    }
    @Test fun backupIsNotBoundToAnInstallationPackageOrAppVersion() = runBlocking {
        seed()
        val original = backup.snapshot()
        val root = JSONObject(backup.export())
        assertFalse(root.has("applicationId"))
        root.put("appVersionName", "0.9.1")
        root.put("appVersionCode", 12)
        root.remove("checksum")
        root.put("checksum", fingerprint(canonical(root)))
        assertEquals(original, BackupImporter.decode(root.toString()))
    }
    @Test fun injectedFailureRollsBackAllTablesAndSettings() = runBlocking {
        seed(); val before = backup.snapshot()
        val incoming = before.copy(transactions = before.transactions.take(1).map { it.copy(note = "替换后的内容") }, merchantPreferences = emptyList(), settings = emptyList())
        assertTrue(runCatching { backup.restore(incoming) { error("模拟导入后磁盘失败") } }.isFailure)
        assertEquals(before, backup.snapshot())
    }
    @Test fun restoreReplacesRatherThanAppendsAndIsRepeatable() = runBlocking {
        seed(); val model = BackupImporter.decode(backup.export())
        ledger.save(LedgerEntry(amount = 999)); assertEquals(7, db.dao().allEntries().size)
        backup.restore(model); backup.restore(model)
        assertEquals(model, backup.snapshot())
        val next = ledger.save(LedgerEntry(amount = 100)); assertTrue(next > model.transactions.maxOf { it.id })
    }
    @Test fun checksumAndValidationRejectBrokenFieldsBeforeWriting() = runBlocking {
        seed(); val before = backup.snapshot(); val original = backup.export()
        val corrupt = JSONObject(original); corrupt.getJSONObject("data").getJSONArray("transactions").getJSONObject(0).put("note", "传输损坏")
        assertTrue(runCatching { BackupImporter.decode(corrupt.toString()) }.isFailure)
        fun invalid(change: (JSONObject) -> Unit) {
            val root = JSONObject(original); change(root); root.remove("checksum"); root.put("checksum", fingerprint(canonical(root)))
            assertTrue(runCatching { BackupImporter.decode(root.toString()) }.isFailure)
        }
        invalid { it.getJSONObject("data").getJSONArray("transactions").getJSONObject(0).put("originalAmountCents", "100") }
        invalid { it.getJSONObject("data").getJSONArray("transactions").getJSONObject(0).put("originalAmountCents", -1) }
        invalid { it.getJSONObject("data").getJSONArray("transactions").getJSONObject(0).put("timestamp", "invalid time") }
        invalid { it.getJSONObject("data").getJSONArray("transactions").getJSONObject(2).put("incomeCategoryId", "missing") }
        invalid { val values = it.getJSONObject("data").getJSONArray("transactions"); values.put(values.getJSONObject(0)) }
        invalid { it.getJSONObject("data").getJSONArray("transactions").getJSONObject(1).put("personalExpenseCents", 20000) }
        invalid { it.getJSONObject("data").remove("incomeCategories") }
        assertEquals(before, backup.snapshot())
        assertEquals(before, BackupImporter.decode(JSONObject(original).toString())) // Formatting-independent checksum.
    }
    @Test fun notificationTextIsExcludedAndRestoreLeavesLocalDebugStateAlone() = runBlocking {
        seed()
        ledger.log(NotificationInput(WECHAT, "私人聊天", "SECRET_CHAT"), "诊断")
        val repo = DiagnosticRepository(db); val session = repo.start(5, 1000)
        repo.record(1001) { NotificationSnapshot(1001, "chat", text = "SECRET_OTP") }
        val text = backup.export()
        assertFalse(text.contains("SECRET_CHAT")); assertFalse(text.contains("SECRET_OTP")); assertFalse(text.contains("diagnostic_notifications"))
        backup.restore(BackupImporter.decode(text))
        assertEquals(1, repo.export(session.id)!!.notifications.size)
        db.openHelper.readableDatabase.query("SELECT COUNT(*) FROM unrecognized").use { it.moveToFirst(); assertEquals(1, it.getInt(0)) }
    }
    @Test fun completedReminderStateDoesNotBreakOrEnterFinancialBackup() = runBlocking {
        val captured = ledger.record(NotificationInput(WECHAT, "微信支付", "已扣费¥16.90", key = "reminder-backup"),
            ParsedTransaction(1690, "expense", "未知商户", "WeChat", System.currentTimeMillis()))!!
        ledger.save(ledger.get(captured)!!.copy(category = "餐饮", merchant = "食堂"))
        assertEquals(setOf(captured), ledger.completedReminderIds())
        val json = backup.export()
        assertFalse(json.contains(REMINDER_COMPLETION_PREFIX))
        val model = BackupImporter.decode(json)
        backup.restore(model)
        assertEquals("食堂", ledger.get(captured)!!.merchant)
        assertTrue(ledger.completedReminderIds().isEmpty())
    }
    @Test fun csvRetainsMoneyTypesAndEscapesFormulaAndText() = runBlocking {
        seed(); ledger.save(LedgerEntry(amount = 100, merchant = "=HYPERLINK(\"bad\")", note = "注释"))
        val csv = backup.csv()
        assertTrue(csv.startsWith("\uFEFFdate,time")); assertTrue(csv.contains("\"200.00\",\"40.00\""))
        assertTrue(csv.contains("'=")); assertTrue(csv.contains("引号\"\"及换行")); assertTrue(csv.contains("\"refund\""))
    }
}
