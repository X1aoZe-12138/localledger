package cn.localledger.backup

import cn.localledger.BuildConfig
import cn.localledger.data.*
import cn.localledger.merchant.MerchantPreferenceEntity
import cn.localledger.parser.*
import cn.localledger.asset.*
import org.json.*
import java.time.OffsetDateTime
import java.time.Instant

private fun json(vararg fields: Pair<String, Any?>) = JSONObject().apply { fields.forEach { (key, value) -> put(key, value ?: JSONObject.NULL) } }
private fun array(values: List<JSONObject>) = JSONArray().apply { values.forEach(::put) }
private fun time(value: Long): String = Instant.ofEpochMilli(value).toString()

object BackupExporter {
    fun encode(model: BackupModelV1, now: Instant = Instant.now()): String {
        BackupValidator.validate(model)
        val data = json(
            "transactions" to array(model.transactions.map { e -> json(
                "id" to e.id, "timestamp" to time(e.timestamp), "originalAmountCents" to e.amount,
                "personalExpenseCents" to e.personalExpenseCents, "transactionType" to e.transactionType,
                "category" to e.category, "merchant" to e.merchant, "platform" to e.platform, "note" to e.note,
                "createdAt" to time(e.createdAt), "splitEnabled" to e.splitEnabled,
                "participantCount" to e.participantCount, "splitCalculationMode" to e.splitCalculationMode,
                "manualSelfAmountCents" to e.manualSelfAmountCents, "receivedAmountCents" to e.receivedAmountCents,
                "incomeCategoryId" to e.incomeCategoryId, "legacyCategory" to e.legacyCategory, "legacyMerchant" to e.legacyMerchant) }),
            "merchantPreferences" to array(model.merchantPreferences.map { p -> json(
                "normalizedName" to p.normalizedName, "category" to p.category, "displayName" to p.displayName,
                "isPinned" to p.isPinned, "isManual" to p.isManual, "isHidden" to p.isHidden,
                "createdAt" to time(p.createdAt), "updatedAt" to time(p.updatedAt)) }),
            "incomeCategories" to array(model.incomeCategories.map { c -> json("id" to c.id, "name" to c.name, "sortOrder" to c.sortOrder) }),
            "deduplicationReceipts" to array(model.receipts.map { r -> json("token" to r.token, "key" to r.key, "timestamp" to time(r.timestamp), "digest" to r.digest,
                "platform" to r.platform, "amount" to r.amount, "type" to r.type, "merchant" to r.merchant) }),
            "ledgerSettings" to array(model.settings.map { s -> json("key" to s.key, "value" to s.value) }),
            "assetAccounts" to array(model.assetAccounts.map { a -> json("id" to a.id, "name" to a.name,
                "accountType" to a.accountType, "currentBalanceCents" to a.currentBalanceCents,
                "isIncludedInTotal" to a.isIncludedInTotal, "isArchived" to a.isArchived, "displayOrder" to a.displayOrder,
                "note" to a.note, "createdAt" to time(a.createdAt), "updatedAt" to time(a.updatedAt)) }),
            "assetSnapshots" to array(model.assetSnapshots.map { s -> json("id" to s.id, "timestamp" to time(s.timestamp),
                "totalBalanceCents" to s.totalBalanceCents, "note" to s.note, "createdAt" to time(s.createdAt)) }),
            "assetSnapshotItems" to array(model.assetSnapshotItems.map { i -> json("id" to i.id, "snapshotId" to i.snapshotId,
                "accountId" to i.accountId, "accountNameSnapshot" to i.accountNameSnapshot,
                "balanceCents" to i.balanceCents, "includedInTotal" to i.includedInTotal) }))
        val root = json("backupFormatVersion" to 2, "appVersionName" to BuildConfig.VERSION_NAME,
            "appVersionCode" to BuildConfig.VERSION_CODE, "databaseSchemaVersion" to 6,
            "exportedAt" to now.toString(), "data" to data)
        root.put("checksum", fingerprint(canonical(root)))
        return root.toString(2).also { require(it.toByteArray(Charsets.UTF_8).size <= BackupImporter.MAX_BYTES) { "备份超过32MiB，未写入文件" } }
    }
}

// Canonical checksum ignores formatting and object key order. It is integrity detection, not encryption/authentication.
internal fun canonical(value: Any?): String = when(value) {
    null, JSONObject.NULL -> "null"
    is JSONObject -> value.keys().asSequence().toList().sorted().joinToString(",", "{", "}") { JSONObject.quote(it) + ":" + canonical(value.get(it)) }
    is JSONArray -> (0 until value.length()).joinToString(",", "[", "]") { canonical(value.get(it)) }
    is String -> JSONObject.quote(value)
    else -> value.toString()
}

object BackupImporter {
    const val MAX_BYTES = 32 * 1024 * 1024
    fun decode(text: String): BackupModelV1 {
        try {
            require(text.toByteArray(Charsets.UTF_8).size <= MAX_BYTES)
            val tokener = JSONTokener(text)
            val root = tokener.nextValue() as? JSONObject ?: throw InvalidBackup()
            require(tokener.nextClean() == '\u0000')
            val version = root.integer("backupFormatVersion")
            if(version !in 1L..2L) throw InvalidBackup("不支持的 backupFormatVersion：$version")
            require(root.string("appVersionName").isNotBlank())
            require(root.integer("appVersionCode") > 0 && root.integer("databaseSchemaVersion") > 0)
            root.time("exportedAt")
            val checksum = root.string("checksum")
            require(Regex("[0-9a-f]{64}").matches(checksum))
            root.remove("checksum")
            require(fingerprint(canonical(root)) == checksum)
            val data = root.getJSONObject("data")
            val base = decodeV1(data)
            return if(version == 1L) base else decodeV2(data, base).also(BackupValidator::validate)
        } catch(e: InvalidBackup) { throw e }
        catch(_: Exception) { throw InvalidBackup() }
    }

    private fun decodeV2(data: JSONObject, base: BackupModelV1): BackupModelV1 = base.copy(
        assetAccounts = data.objects("assetAccounts").map { a -> AssetAccount(a.string("id"), a.string("name"), a.string("accountType"),
            a.integer("currentBalanceCents"), a.bool("isIncludedInTotal"), a.bool("isArchived"), a.int("displayOrder"),
            a.string("note"), a.time("createdAt"), a.time("updatedAt")) },
        assetSnapshots = data.objects("assetSnapshots").map { s -> AssetSnapshot(s.string("id"), s.time("timestamp"),
            s.integer("totalBalanceCents"), s.string("note"), s.time("createdAt")) },
        assetSnapshotItems = data.objects("assetSnapshotItems").map { i -> AssetSnapshotItem(i.string("id"), i.string("snapshotId"),
            i.string("accountId"), i.string("accountNameSnapshot"), i.integer("balanceCents"), i.bool("includedInTotal")) })

    private fun decodeV1(data: JSONObject): BackupModelV1 {
        val entries = data.objects("transactions").map { e ->
            val bill = LedgerEntry(e.integer("id"), e.time("timestamp"), e.integer("originalAmountCents"),
                e.string("transactionType"), e.string("category"), e.string("merchant"), e.string("platform"),
                e.string("note"), e.time("createdAt"), e.bool("splitEnabled"), e.int("participantCount"),
                e.string("splitCalculationMode"), e.nullableLong("manualSelfAmountCents"), e.nullableLong("receivedAmountCents"),
                e.nullableString("incomeCategoryId"), e.nullableString("legacyCategory"), e.nullableString("legacyMerchant"))
            require(SplitExpense.validationError(bill) == null)
            require(e.integer("personalExpenseCents") == bill.personalExpenseCents)
            bill
        }
        val prefs = data.objects("merchantPreferences").map { p -> MerchantPreferenceEntity(p.string("normalizedName"), p.string("category"), p.string("displayName"),
            p.bool("isPinned"), p.bool("isManual"), p.bool("isHidden"), p.time("createdAt"), p.time("updatedAt")) }
        val cats = data.objects("incomeCategories").map { c -> IncomeCategoryEntity(c.string("id"), c.string("name"), c.int("sortOrder")) }
        val receipts = data.objects("deduplicationReceipts").map { r -> Receipt(r.string("token"), r.string("key"), r.time("timestamp"), r.string("digest"), r.string("platform"), r.integer("amount"), r.string("type"), r.string("merchant")) }
        val settings = data.objects("ledgerSettings").map { s -> LedgerSetting(s.string("key"), s.string("value")) }
        return BackupModelV1(entries, prefs, cats, receipts, settings).also(BackupValidator::validate)
    }
}

private fun JSONObject.string(key: String): String = (get(key) as? String ?: throw InvalidBackup()).also { require(it.length <= 100_000) }
private fun JSONObject.nullableString(key: String): String? { require(has(key)); return if(isNull(key)) null else string(key) }
private fun JSONObject.integer(key: String): Long { val value = get(key); require(value is Int || value is Long); return (value as Number).toLong() }
private fun JSONObject.int(key: String): Int = integer(key).also { require(it in Int.MIN_VALUE..Int.MAX_VALUE) }.toInt()
private fun JSONObject.nullableLong(key: String): Long? { require(has(key)); return if(isNull(key)) null else integer(key) }
private fun JSONObject.bool(key: String): Boolean = get(key) as? Boolean ?: throw InvalidBackup()
private fun JSONObject.time(key: String): Long = OffsetDateTime.parse(string(key)).toInstant().toEpochMilli().also { require(it in 0..7_258_118_400_000L) }
private fun JSONObject.objects(key: String): List<JSONObject> { val values = getJSONArray(key); require(values.length() <= 100_000); return (0 until values.length()).map { values.getJSONObject(it) } }

object BackupValidator {
    fun validate(model: BackupModelV1) {
        require(listOf(model.transactions.size, model.merchantPreferences.size, model.incomeCategories.size, model.receipts.size, model.settings.size).all { it <= 100_000 })
        require(model.incomeCategories.isNotEmpty() && model.incomeCategories.any { it.id == OTHER_INCOME_ID })
        require(model.incomeCategories.map { it.id }.distinct().size == model.incomeCategories.size)
        require(model.incomeCategories.map { it.name }.distinct().size == model.incomeCategories.size)
        model.incomeCategories.forEach { require(it.id.isNotBlank() && it.id.length <= 100 && it.name.isNotBlank() && it.name.length <= 100 && it.sortOrder >= 0) }
        val ids = model.incomeCategories.map { it.id }.toSet()
        require(model.transactions.map { it.id }.distinct().size == model.transactions.size)
        model.transactions.forEach { e ->
            require(e.id in 1 until Long.MAX_VALUE && e.amount in 1..99_999_999_999L && e.transactionType in flowTypes)
            require(e.timestamp in 0..7_258_118_400_000L && e.createdAt in 0..7_258_118_400_000L)
            require(e.platform in setOf("Manual", "Alipay", "WeChat"))
            require(listOf(e.category, e.merchant, e.note, e.platform, e.legacyCategory.orEmpty(), e.legacyMerchant.orEmpty()).all { it.length <= 100_000 })
            if(e.transactionType == "expense") require(e.category in categories)
            if(e.transactionType == "income") require(e.incomeCategoryId in ids)
            if(e.incomeCategoryId != null) require(e.incomeCategoryId in ids)
            require(SplitExpense.validationError(e) == null)
        }
        require(model.merchantPreferences.map { it.normalizedName to it.category }.distinct().size == model.merchantPreferences.size)
        model.merchantPreferences.forEach { require(isLearnableMerchant(it.displayName) && normalizeMerchantName(it.displayName) == it.normalizedName && it.category in learnableCategories && it.createdAt in 0..7_258_118_400_000L && it.updatedAt in 0..7_258_118_400_000L && it.displayName.length <= 100_000 && it.normalizedName.length <= 100_000) }
        require(model.receipts.map { it.token }.distinct().size == model.receipts.size)
        model.receipts.forEach { require(it.token.isNotBlank() && it.digest.isNotBlank() && it.amount in 1..99_999_999_999L && it.type in flowTypes && it.platform in setOf("Manual", "WeChat", "Alipay") && it.timestamp in 0..7_258_118_400_000L && listOf(it.token, it.digest, it.key, it.merchant).all { value -> value.length <= 100_000 }) }
        require(model.settings.map { it.key }.distinct().size == model.settings.size)
        model.settings.forEach { require(it.key in setOf("lastBackupAt", "lastReportGeneratedAt") && it.value.toLongOrNull()?.let { value -> value in 0..MAX_TIME } == true) }
        require(listOf(model.assetAccounts.size, model.assetSnapshots.size, model.assetSnapshotItems.size).all { it <= 100_000 })
        require(model.assetAccounts.map { it.id }.distinct().size == model.assetAccounts.size)
        require(model.assetSnapshots.map { it.id }.distinct().size == model.assetSnapshots.size)
        require(model.assetSnapshotItems.map { it.id }.distinct().size == model.assetSnapshotItems.size)
        fun validId(value: String) = value.isNotBlank() && value.length <= 100
        model.assetAccounts.forEach { a -> require(validId(a.id) && a.name.isNotBlank() && a.name.length <= 100 && a.accountType in accountTypes && a.currentBalanceCents in 0..MAX_BALANCE && a.displayOrder >= 0 && a.note.length <= 1000 && a.createdAt in 0..MAX_TIME && a.updatedAt in 0..MAX_TIME) }
        model.assetSnapshots.forEach { s -> require(validId(s.id) && s.timestamp in 0..MAX_TIME && s.createdAt in 0..MAX_TIME && s.totalBalanceCents >= 0 && s.note.length <= 1000) }
        val snapshotIds = model.assetSnapshots.map { it.id }.toSet()
        val accountIds = model.assetAccounts.map { it.id }.toSet()
        require(model.assetSnapshotItems.map { it.snapshotId to it.accountId }.distinct().size == model.assetSnapshotItems.size)
        model.assetSnapshotItems.forEach { i -> require(validId(i.id) && i.snapshotId in snapshotIds && i.accountId in accountIds && i.accountNameSnapshot.isNotBlank() && i.accountNameSnapshot.length <= 100 && i.balanceCents in 0..MAX_BALANCE) }
        val bySnapshot = model.assetSnapshotItems.groupBy { it.snapshotId }
        model.assetSnapshots.forEach { s ->
            val items = bySnapshot[s.id].orEmpty()
            require(items.isNotEmpty() && s.totalBalanceCents == items.filter { it.includedInTotal }.sumOf { it.balanceCents })
        }
    }
}
