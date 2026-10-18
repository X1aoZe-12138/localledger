package cn.localledger.backup

import androidx.room.withTransaction
import cn.localledger.data.*
import java.time.*
import cn.localledger.parser.Money

class BackupRepository(private val db: LedgerDatabase) {
    suspend fun snapshot(): BackupModelV1 = db.withTransaction {
        ensureIncomeCategories(db)
        // OS reminder completion is device-local notification state, not financial data.
        // The backup validator intentionally accepts only long-lived ledger settings.
        val ledgerSettings = db.financialDao().settings().filterNot { it.key.startsWith(REMINDER_COMPLETION_PREFIX) }
        BackupModelV1(db.dao().allEntries(), db.merchantDao().allPreferences(), db.financialDao().categories(), db.dao().allReceipts(), ledgerSettings, db.assetDao().accounts(), db.assetDao().snapshots(), db.assetDao().items())
    }
    suspend fun export(): String = BackupExporter.encode(snapshot())

    // Validation happens before any write, and is repeated inside restore's caller boundary.
    suspend fun restore(model: BackupModelV1, beforeCommit: suspend () -> Unit = {}): RestoreSummary {
        BackupValidator.validate(model)
        return db.withTransaction {
            db.assetDao().clearItems(); db.assetDao().clearSnapshots(); db.assetDao().clearAccounts()
            db.dao().clearEntries(); db.dao().clearReceipts()
            db.merchantDao().clearPreferences(); db.financialDao().clearCategories(); db.financialDao().clearSettings()
            model.incomeCategories.forEach { db.financialDao().insertCategory(it) }
            model.transactions.forEach { db.dao().insert(it) }
            model.merchantPreferences.forEach { db.merchantDao().put(it) }
            model.receipts.forEach { require(db.dao().receipt(it) != -1L) }
            model.settings.forEach { db.financialDao().putSetting(it) }
            model.assetAccounts.forEach { db.assetDao().insertAccount(it) }
            model.assetSnapshots.forEach { db.assetDao().insertSnapshot(it) }
            model.assetSnapshotItems.forEach { db.assetDao().insertItem(it) }
            // Read back every durable field, not only counts. Any discrepancy rolls back everything.
            val restored = snapshot()
            require(restored.transactions.sortedBy { it.id } == model.transactions.sortedBy { it.id })
            require(restored.merchantPreferences.toSet() == model.merchantPreferences.toSet())
            require(restored.incomeCategories.toSet() == model.incomeCategories.toSet())
            require(restored.receipts.toSet() == model.receipts.toSet() && restored.settings.toSet() == model.settings.toSet())
            require(restored.assetAccounts.toSet() == model.assetAccounts.toSet() && restored.assetSnapshots.toSet() == model.assetSnapshots.toSet() && restored.assetSnapshotItems.toSet() == model.assetSnapshotItems.toSet())
            beforeCommit()
            RestoreSummary(model.transactions.size, model.transactions.count { it.transactionType == "income" }, model.merchantPreferences.size,
                model.transactions.count { it.splitEnabled }, model.incomeCategories.size, model.assetAccounts.size, model.assetSnapshots.size)
        }
    }
    suspend fun recordBackupTime(time: Long) = db.financialDao().putSetting(LedgerSetting("lastBackupAt", time.toString()))
    val lastBackup = db.financialDao().setting("lastBackupAt")

    suspend fun csv(): String {
        val model = snapshot()
        val names = model.incomeCategories.associate { it.id to it.name }
        fun cell(value: String): String {
            // Prevent spreadsheet formula execution in user-controlled text cells.
            val safe = if(value.trimStart().firstOrNull() in listOf('=', '+', '-', '@', '\t', '\r')) "'$value" else value
            return "\"${safe.replace("\"", "\"\"")}\""
        }
        val header = "date,time,transactionType,originalAmount,personalExpense,category,merchant,platform,note,incomeCategory,splitMode,participantCount,manualSelf,received,legacyCategory,legacyMerchant"
        return "\uFEFF" + header + "\r\n" + model.transactions.sortedBy { it.timestamp }.joinToString("\r\n") { e ->
            val date = Instant.ofEpochMilli(e.timestamp).atZone(ZoneId.systemDefault())
            listOf(date.toLocalDate().toString(), date.toLocalTime().toString(), e.transactionType, Money.format(e.amount), Money.format(e.personalExpenseCents),
                e.category, e.merchant, e.platform, e.note, names[e.incomeCategoryId].orEmpty(), e.splitCalculationMode,
                e.participantCount.toString(), e.manualSelfAmountCents?.let(Money::format).orEmpty(), e.receivedAmountCents?.let(Money::format).orEmpty(),
                e.legacyCategory.orEmpty(), e.legacyMerchant.orEmpty()).joinToString(",", transform = ::cell)
        }
    }
}
