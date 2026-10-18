package cn.localledger.data

import androidx.room.withTransaction
import cn.localledger.parser.*

class LedgerRepository(private val db: LedgerDatabase) {
    private val dao = db.dao()
    val entries = dao.observeEntries()
    val unknown = dao.observeUnknown()
    suspend fun get(id: Long) = dao.get(id)
    suspend fun save(entry: LedgerEntry): Long = db.withTransaction {
        ensureIncomeCategories(db)
        require(entry.amount > 0 && entry.transactionType in flowTypes)
        require(entry.timestamp in 0..7_258_118_400_000L)
        if(entry.transactionType == "expense") require(entry.category in categories)
        require(SplitExpense.validationError(entry) == null)
        val canonical = if(entry.transactionType == "income") entry.copy(incomeCategoryId = entry.incomeCategoryId ?: OTHER_INCOME_ID) else entry
        if(canonical.incomeCategoryId != null) require(db.financialDao().categories().any { it.id == canonical.incomeCategoryId })
        if (entry.id == 0L) dao.insert(canonical) else {
            val before = requireNotNull(dao.get(entry.id)) { "账单已不存在，请刷新后重试" }
            dao.update(canonical)
            // A captured bill is already stored. Saving it is the user's review/completion.
            // Persist this marker in the same transaction, before the OS notification is cancelled.
            if (before.platform in setOf("WeChat", "Alipay"))
                db.financialDao().putSetting(LedgerSetting(reminderCompletionKey(entry.id), "1"))
            entry.id
        }
    }
    suspend fun delete(id: Long) = db.withTransaction {
        val before = dao.get(id)
        if (before?.platform in setOf("WeChat", "Alipay"))
            db.financialDao().putSetting(LedgerSetting(reminderCompletionKey(id), "1"))
        dao.delete(id)
    }
    suspend fun completedReminderIds(): Set<Long> = db.financialDao().settings().mapNotNull {
        it.key.removePrefix(REMINDER_COMPLETION_PREFIX).takeIf { suffix -> suffix != it.key }?.toLongOrNull()
    }.toSet()
    suspend fun clearUnknown() = dao.clearUnknown()
    suspend fun log(input: NotificationInput, reason: String) = db.withTransaction {
        dao.unknown(UnknownNotification(timestamp = input.timestamp, packageName = input.packageName,
            title = input.title.take(2000), text = input.text.take(4000), bigText = input.bigText.take(8000),
            subText = input.subText.take(2000), extraText = input.extraText.take(8000), reason = reason))
        dao.trimUnknown()
    }
    suspend fun record(input: NotificationInput, parsed: ParsedTransaction): Long? = db.withTransaction {
        ensureIncomeCategories(db)
        val identity = ReceiptIdentity(input.key, input.timestamp, fingerprint(input.content), parsed.platform,
            parsed.amount, parsed.transactionType, parsed.merchant)
        val existing = dao.receipts(input.key, input.timestamp - 1500, input.timestamp + 1500)
        if (existing.any {
            val compatibleType = if(parsed.transactionType == "refund" && it.type == "income") "refund" else it.type
            DuplicatePolicy.isDuplicate(ReceiptIdentity(it.key,it.timestamp,it.digest,it.platform,it.amount,compatibleType,it.merchant), identity)
        }) return@withTransaction null
        val token = fingerprint("${identity.key}|${identity.timestamp}|${identity.platform}|${identity.amount}|${identity.type}|${identity.merchant}")
        if (dao.receipt(Receipt(token,identity.key,identity.timestamp,identity.digest,identity.platform,identity.amount,identity.type,identity.merchant)) == -1L) return@withTransaction null
        dao.insert(LedgerEntry(timestamp = parsed.timestamp, amount = parsed.amount, transactionType = parsed.transactionType,
            merchant = parsed.merchant, platform = parsed.platform,
            incomeCategoryId = if(parsed.transactionType == "income") OTHER_INCOME_ID else null))
    }
}

const val REMINDER_COMPLETION_PREFIX = "recorded_reminder_completed:"
fun reminderCompletionKey(entryId: Long) = "$REMINDER_COMPLETION_PREFIX$entryId"
