package cn.localledger.asset

import android.app.Application
import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import cn.localledger.backup.*
import cn.localledger.data.*
import cn.localledger.parser.fingerprint
import cn.localledger.ui.parseAssetBalance
import kotlinx.coroutines.runBlocking
import org.json.JSONObject
import org.junit.*
import org.junit.Assert.*
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], application = Application::class)
class AssetTest {
    private lateinit var db: LedgerDatabase
    private lateinit var repo: AssetRepository
    private lateinit var backup: BackupRepository
    @Before fun setup() {
        db = Room.inMemoryDatabaseBuilder(ApplicationProvider.getApplicationContext<Context>(), LedgerDatabase::class.java).allowMainThreadQueries().build()
        repo = AssetRepository(db); backup = BackupRepository(db)
    }
    @After fun close() { db.close() }
    private suspend fun add(name: String, cents: Long, type: String = "OTHER"): AssetAccount {
        repo.saveAccount(name, type, true, "备注", cents, now = 1000)
        return db.assetDao().accounts().first { it.name == name }
    }
    private fun snapshot(id: String, time: Long, cents: Long) = AssetSnapshot(id, time, cents, createdAt = time)

    @Test fun totalsExcludeUncheckedAndArchivedAccountsAndInitialBalancesDoNotCreateSnapshots() = runBlocking {
        val wx = add("微信", 10000, "WECHAT"); add("支付宝", 20000, "ALIPAY"); val cash = add("现金", 5000, "CASH")
        assertEquals(35000L, totalLiquidAssets(db.assetDao().accounts()))
        assertTrue(db.assetDao().snapshots().isEmpty())
        repo.saveAccount(cash.name, cash.accountType, false, cash.note, id = cash.id, now = 2000)
        assertEquals(30000L, totalLiquidAssets(db.assetDao().accounts()))
        repo.move(wx.id, 1); assertEquals(wx.id, db.assetDao().accounts()[1].id)
        repo.archiveOrDelete(cash.id); assertEquals(2, db.assetDao().accounts().size)
        repo.archiveOrDelete(db.assetDao().accounts().first().id)
        val last = add("新账户", 0)
        assertEquals(last.id, db.assetDao().accounts().last().id)
    }
    @Test fun snapshotsKeepIndependentBalancesAndRenameAndArchiveKeepHistoricalNames() = runBlocking {
        val a = add("微信", 100000, "WECHAT")
        val first = repo.saveSnapshot(mapOf(a.id to 100000L), "首次", 2000)
        val second = repo.saveSnapshot(mapOf(a.id to 120000L), "第二次", 3000)
        assertEquals(100000L, db.assetDao().snapshots().first { it.id == first }.totalBalanceCents)
        assertEquals(120000L, db.assetDao().snapshots().first { it.id == second }.totalBalanceCents)
        assertEquals(20000L, compareAssets(snapshot(first, 2000, 100000), snapshot(second, 3000, 120000), emptyList()).actualChange)
        repo.saveAccount("微信零钱", "WECHAT", false, "", id = a.id, now = 4000)
        assertTrue(db.assetDao().items().all { it.accountNameSnapshot == "微信" && it.includedInTotal })
        repo.archiveOrDelete(a.id)
        assertEquals(0L, totalLiquidAssets(db.assetDao().accounts()))
        assertTrue(db.assetDao().accounts().single().isArchived)
        assertEquals(2, db.assetDao().items().size)
        repo.unarchive(a.id); assertFalse(db.assetDao().accounts().single().isArchived)
    }
    @Test fun editingOldSnapshotDoesNotChangeCurrentAndEditingLatestDoesIncludingNewAccounts() = runBlocking {
        val a = add("现金", 10000)
        val old = repo.saveSnapshot(mapOf(a.id to 10000L), "旧", 2000)
        val latest = repo.saveSnapshot(mapOf(a.id to 20000L), "新", 3000)
        val newAccount = add("银行卡", 50000, "BANK")
        repo.editSnapshot(old, mapOf(a.id to 15000L), "修正旧盘点")
        assertEquals(20000L, db.assetDao().accounts().first { it.id == a.id }.currentBalanceCents)
        repo.editSnapshot(latest, mapOf(a.id to 25000L), "修正最新")
        assertEquals(25000L, db.assetDao().accounts().first { it.id == a.id }.currentBalanceCents)
        assertEquals(50000L, db.assetDao().accounts().first { it.id == newAccount.id }.currentBalanceCents)
        assertEquals(15000L, db.assetDao().snapshots().first { it.id == old }.totalBalanceCents)
        assertEquals(25000L, db.assetDao().snapshots().first { it.id == latest }.totalBalanceCents)
    }
    @Test fun invalidOrStaleInventoriesAndNegativeAmountsLeaveEveryFieldUnchanged() = runBlocking {
        val a = add("微信", 10000); val b = add("现金", 5000)
        repo.saveSnapshot(mapOf(a.id to 10000L, b.id to 5000L), "", 2000)
        val original = backup.snapshot()
        assertTrue(runCatching { repo.saveSnapshot(mapOf(a.id to 12000L), "", 3000) }.isFailure)
        assertTrue(runCatching { repo.saveSnapshot(mapOf(a.id to 12000L, b.id to -1L), "", 3000) }.isFailure)
        assertTrue(runCatching { repo.saveSnapshot(mapOf(a.id to 12000L, b.id to 100L), "", 1000) }.isFailure)
        assertTrue(runCatching { repo.editSnapshot(original.assetSnapshots.single().id, mapOf(a.id to -1L, b.id to 100L), "") }.isFailure)
        assertEquals(original, backup.snapshot())
    }
    @Test fun amountInputUsesExactCentsAllowsZeroAndRejectsInvalidValues() {
        assertEquals(0L, parseAssetBalance("0.00")); assertEquals(42853L, parseAssetBalance("428.53"))
        listOf("-1", "1.001", "NaN", "1e3", "1000000000", "").forEach { assertNull(parseAssetBalance(it)) }
    }
    @Test fun differenceUsesIncomePersonalExpenseRefundReimbursementWithExactTimeBoundaries() {
        val entries = listOf(
            LedgerEntry(timestamp = 1500, amount = 800000, transactionType = "income"),
            LedgerEntry(timestamp = 1500, amount = 500000),
            LedgerEntry(timestamp = 1500, amount = 300000, splitEnabled = true, splitCalculationMode = "MANUAL_SELF", manualSelfAmountCents = 100000),
            LedgerEntry(timestamp = 1500, amount = 20000, transactionType = "refund"),
            LedgerEntry(timestamp = 2000, amount = 10000, transactionType = "reimbursement"),
            LedgerEntry(timestamp = 1000, amount = 999999, transactionType = "income"),
            LedgerEntry(timestamp = 2001, amount = 999999),
            LedgerEntry(timestamp = 1500, amount = 200000, transactionType = "transfer_adjustment"))
        val result = compareAssets(snapshot("old", 1000, 1000000), snapshot("new", 2000, 1100000), entries)
        assertEquals(100000L, result.actualChange); assertEquals(800000L, result.income)
        assertEquals(600000L, result.expense); assertEquals(230000L, result.recordedNetChange)
        assertEquals(-130000L, result.difference); assertEquals(1, result.skippedAdjustments)
        // A boundary entry is counted once across adjacent intervals.
        assertEquals(0L, compareAssets(snapshot("new", 2000, 1100000), snapshot("next", 2001, 1100000), entries).reimbursements)
    }
    @Test fun aa200Personal40DoesNotCount160ReturnAsTrueIncomeOrTwice() {
        val entries = listOf(LedgerEntry(timestamp = 1500, amount = 20000, splitEnabled = true, splitCalculationMode = "MANUAL_RECEIVED", receivedAmountCents = 16000),
            LedgerEntry(timestamp = 1500, amount = 16000, transactionType = "transfer_adjustment"))
        val result = compareAssets(snapshot("old", 1000, 20000), snapshot("new", 2000, 16000), entries)
        assertEquals(4000L, result.expense); assertEquals(0L, result.income)
        assertEquals(-4000L, result.recordedNetChange); assertEquals(0L, result.difference)
    }
    @Test fun assetsNeverModifyLedgerOrExpenseStatistics() = runBlocking {
        LedgerRepository(db).save(LedgerEntry(amount = 20000, splitEnabled = true, splitCalculationMode = "MANUAL_RECEIVED", receivedAmountCents = 16000))
        val before = db.dao().allEntries()
        val a = add("微信", 10000); repo.saveSnapshot(mapOf(a.id to 5000L), "", 2000)
        assertEquals(before, db.dao().allEntries()); assertEquals(4000L, db.dao().allEntries().single().personalExpenseCents)
    }
    @Test fun v2BackupRoundTripPreservesAllAccountAndHistoryFieldsAndRollback() = runBlocking {
        val wx = add("微信", 10000, "WECHAT"); val ali = add("支付宝", 20000, "ALIPAY")
        val bank = add("招商银行", 30000, "BANK"); val cash = add("现金", 5000, "CASH")
        LedgerRepository(db).save(LedgerEntry(amount = 20000, splitEnabled = true, splitCalculationMode = "MANUAL_RECEIVED", receivedAmountCents = 16000))
        val balances = mapOf(wx.id to 10000L, ali.id to 20000L, bank.id to 30000L, cash.id to 5000L)
        repo.saveSnapshot(balances, "首次", 2000); repo.saveSnapshot(balances.mapValues { it.value + 1000 }, "第二次", 3000)
        repo.archiveOrDelete(cash.id); repo.saveAccount("工资卡", "BANK", false, "账户新名", id = bank.id, now = 4000)
        val original = backup.snapshot(); val exported = backup.export()
        assertEquals(2, JSONObject(exported).getInt("backupFormatVersion"))
        val model = BackupImporter.decode(exported)
        assertEquals(original, model)
        assertTrue(runCatching { backup.restore(model.copy(assetAccounts = emptyList())) }.isFailure)
        assertEquals(original, backup.snapshot())
        val alternate = model.copy(assetAccounts = model.assetAccounts.map { it.copy(currentBalanceCents = 0) })
        assertTrue(runCatching { backup.restore(alternate) { error("模拟恢复失败") } }.isFailure)
        assertEquals(original, backup.snapshot())
        db.assetDao().clearItems(); db.assetDao().clearSnapshots(); db.assetDao().clearAccounts(); db.dao().clearEntries()
        backup.restore(model); assertEquals(original, backup.snapshot())
        backup.restore(model); assertEquals(4, db.assetDao().accounts().size); assertEquals(2, db.assetDao().snapshots().size)
    }
    @Test fun realV1ShapeWithoutAssetsRestoresLedgerAndClearsCurrentAssets() = runBlocking {
        LedgerRepository(db).save(LedgerEntry(amount = 1234, note = "旧账单"))
        val oldEntries = db.dao().allEntries()
        val root = JSONObject(backup.export())
        root.put("backupFormatVersion", 1).put("databaseSchemaVersion", 5).put("appVersionName", "0.5.0").put("appVersionCode", 6)
        val data = root.getJSONObject("data")
        listOf("assetAccounts", "assetSnapshots", "assetSnapshotItems").forEach(data::remove)
        resign(root)
        val account = add("现金", 10000); repo.saveSnapshot(mapOf(account.id to 10000L), "", 2000)
        val model = BackupImporter.decode(root.toString())
        assertTrue(model.assetAccounts.isEmpty()); backup.restore(model)
        assertEquals(oldEntries, db.dao().allEntries()); assertTrue(db.assetDao().accounts().isEmpty())
        assertTrue(db.assetDao().snapshots().isEmpty()); assertTrue(db.assetDao().items().isEmpty())
    }
    @Test fun v2InvalidAssetReferencesDuplicateIdsAndTotalsAreRejectedBeforeRestore() = runBlocking {
        val a = add("微信", 10000); repo.saveSnapshot(mapOf(a.id to 10000L), "", 2000)
        val original = backup.snapshot(); val text = backup.export()
        val changes: List<(JSONObject) -> Unit> = listOf(
            { it.getJSONArray("assetSnapshotItems").getJSONObject(0).put("accountId", "missing") },
            { it.getJSONArray("assetSnapshotItems").getJSONObject(0).put("snapshotId", "missing") },
            { it.getJSONArray("assetAccounts").getJSONObject(0).put("currentBalanceCents", -1) },
            { it.getJSONArray("assetSnapshots").getJSONObject(0).put("totalBalanceCents", 999) },
            { it.remove("assetAccounts") },
            { it.getJSONArray("assetAccounts").put(it.getJSONArray("assetAccounts").getJSONObject(0)) })
        changes.forEach { change -> val root = JSONObject(text); change(root.getJSONObject("data")); resign(root)
            assertTrue(runCatching { BackupImporter.decode(root.toString()) }.exceptionOrNull() is InvalidBackup)
            assertEquals(original, backup.snapshot())
        }
    }
    private fun resign(root: JSONObject) { root.remove("checksum"); root.put("checksum", fingerprint(canonical(root))) }
}
