package cn.localledger.data

import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

val MIGRATION_5_6 = object : Migration(5, 6) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("CREATE TABLE IF NOT EXISTS asset_accounts (id TEXT NOT NULL PRIMARY KEY, name TEXT NOT NULL, accountType TEXT NOT NULL, currentBalanceCents INTEGER NOT NULL, isIncludedInTotal INTEGER NOT NULL, isArchived INTEGER NOT NULL, displayOrder INTEGER NOT NULL, note TEXT NOT NULL, createdAt INTEGER NOT NULL, updatedAt INTEGER NOT NULL)")
        db.execSQL("CREATE TABLE IF NOT EXISTS asset_snapshots (id TEXT NOT NULL PRIMARY KEY, timestamp INTEGER NOT NULL, totalBalanceCents INTEGER NOT NULL, note TEXT NOT NULL, createdAt INTEGER NOT NULL)")
        db.execSQL("CREATE INDEX IF NOT EXISTS index_asset_snapshots_timestamp ON asset_snapshots(timestamp)")
        db.execSQL("CREATE TABLE IF NOT EXISTS asset_snapshot_items (id TEXT NOT NULL PRIMARY KEY, snapshotId TEXT NOT NULL, accountId TEXT NOT NULL, accountNameSnapshot TEXT NOT NULL, balanceCents INTEGER NOT NULL, includedInTotal INTEGER NOT NULL, FOREIGN KEY(snapshotId) REFERENCES asset_snapshots(id) ON UPDATE NO ACTION ON DELETE CASCADE)")
        db.execSQL("CREATE INDEX IF NOT EXISTS index_asset_snapshot_items_snapshotId ON asset_snapshot_items(snapshotId)")
        db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS index_asset_snapshot_items_snapshotId_accountId ON asset_snapshot_items(snapshotId, accountId)")
    }
}

val MIGRATION_4_5 = object : Migration(4, 5) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE transactions ADD COLUMN incomeCategoryId TEXT")
        db.execSQL("ALTER TABLE transactions ADD COLUMN legacyCategory TEXT")
        db.execSQL("ALTER TABLE transactions ADD COLUMN legacyMerchant TEXT")
        db.execSQL("CREATE TABLE IF NOT EXISTS income_categories (id TEXT NOT NULL PRIMARY KEY, name TEXT NOT NULL, sortOrder INTEGER NOT NULL)")
        db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS index_income_categories_name ON income_categories(name)")
        db.execSQL("CREATE TABLE IF NOT EXISTS ledger_settings (`key` TEXT NOT NULL PRIMARY KEY, value TEXT NOT NULL)")
        defaultIncomeCategories.forEachIndexed { index, (id, name) -> db.execSQL("INSERT INTO income_categories VALUES(?, ?, ?)", arrayOf<Any>(id, name, index)) }
        // Do not infer refunds from arbitrary merchant/note keywords. Preserve all legacy data.
        db.execSQL("UPDATE transactions SET legacyCategory = category, legacyMerchant = merchant, incomeCategoryId = 'other_income' WHERE transactionType = 'income'")
        defaultIncomeCategories.forEach { (id, name) -> db.execSQL("UPDATE transactions SET incomeCategoryId = ? WHERE transactionType = 'income' AND category = ?", arrayOf(id, name)) }
    }
}

val MIGRATION_3_4 = object : Migration(3, 4) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE transactions ADD COLUMN splitEnabled INTEGER NOT NULL DEFAULT 0")
        db.execSQL("ALTER TABLE transactions ADD COLUMN participantCount INTEGER NOT NULL DEFAULT 3")
        db.execSQL("ALTER TABLE transactions ADD COLUMN splitCalculationMode TEXT NOT NULL DEFAULT 'NONE'")
        db.execSQL("ALTER TABLE transactions ADD COLUMN manualSelfAmountCents INTEGER")
        db.execSQL("ALTER TABLE transactions ADD COLUMN receivedAmountCents INTEGER")
    }
}

val MIGRATION_1_2 = object : Migration(1, 2) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("""CREATE TABLE IF NOT EXISTS diagnostic_sessions (
            id TEXT NOT NULL PRIMARY KEY, startTime INTEGER NOT NULL, expectedEndTime INTEGER NOT NULL,
            plannedDuration INTEGER NOT NULL, endTime INTEGER, actualDuration INTEGER,
            stopReason TEXT NOT NULL, storedCharacters INTEGER NOT NULL)""")
        db.execSQL("""CREATE TABLE IF NOT EXISTS diagnostic_notifications (
            id TEXT NOT NULL PRIMARY KEY, sessionId TEXT NOT NULL, timestamp INTEGER NOT NULL,
            receivedAt INTEGER NOT NULL, packageName TEXT NOT NULL, appName TEXT NOT NULL,
            title TEXT NOT NULL, text TEXT NOT NULL, bigText TEXT NOT NULL, subText TEXT NOT NULL,
            summaryText TEXT NOT NULL, extraText TEXT NOT NULL, notificationKey TEXT NOT NULL,
            isGroupSummary INTEGER NOT NULL, captureNote TEXT NOT NULL,
            FOREIGN KEY(sessionId) REFERENCES diagnostic_sessions(id) ON UPDATE NO ACTION ON DELETE CASCADE)""")
        db.execSQL("CREATE INDEX IF NOT EXISTS index_diagnostic_notifications_sessionId ON diagnostic_notifications(sessionId)")
    }
}

val MIGRATION_2_3 = object : Migration(2, 3) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("""CREATE TABLE IF NOT EXISTS merchant_preferences (
            normalizedName TEXT NOT NULL, category TEXT NOT NULL, displayName TEXT NOT NULL,
            isPinned INTEGER NOT NULL, isManual INTEGER NOT NULL, isHidden INTEGER NOT NULL,
            createdAt INTEGER NOT NULL, updatedAt INTEGER NOT NULL,
            PRIMARY KEY(normalizedName, category))""")
        db.execSQL("CREATE INDEX IF NOT EXISTS index_merchant_preferences_category ON merchant_preferences(category)")
    }
}
