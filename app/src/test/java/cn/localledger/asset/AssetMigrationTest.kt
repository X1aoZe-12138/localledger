package cn.localledger.asset

import android.app.Application
import android.content.Context
import android.database.sqlite.SQLiteDatabase
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import cn.localledger.data.*
import kotlinx.coroutines.runBlocking
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], application = Application::class)
class AssetMigrationTest {
    @Test fun actualSchemaFiveUpgradeKeepsEveryExistingTableAndNoAutomaticAssets() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>(); val name = "asset-migration-test.db"
        context.deleteDatabase(name)
        val path = context.getDatabasePath(name).apply { parentFile!!.mkdirs() }
        val schema = javaClass.classLoader!!.getResourceAsStream("cn.localledger.data.LedgerDatabase/5.json")!!.bufferedReader().use { JSONObject(it.readText()).getJSONObject("database") }
        SQLiteDatabase.openOrCreateDatabase(path, null).use { old ->
            val tables = schema.getJSONArray("entities")
            for(i in 0 until tables.length()) { val table = tables.getJSONObject(i); val tableName = table.getString("tableName")
                old.execSQL(table.getString("createSql").replace("\${TABLE_NAME}", tableName))
                table.optJSONArray("indices")?.let { indices ->
                    for(j in 0 until indices.length()) old.execSQL(indices.getJSONObject(j).getString("createSql").replace("\${TABLE_NAME}", tableName))
                }
            }
            val setup = schema.getJSONArray("setupQueries"); for(i in 0 until setup.length()) old.execSQL(setup.getString(i))
            old.execSQL("INSERT INTO income_categories VALUES('other_income','其他收入',1)")
            old.execSQL("INSERT INTO income_categories VALUES('salary','工资',0)")
            old.execSQL("INSERT INTO transactions VALUES(1,1000,20000,'expense','餐饮','餐厅','Alipay','AA备注',1000,1,3,'MANUAL_RECEIVED',NULL,16000,NULL,NULL,NULL)")
            old.execSQL("INSERT INTO transactions VALUES(2,1000,800000,'income','购物','旧公司','Manual','工资',1000,0,3,'NONE',NULL,NULL,'salary','购物','旧公司')")
            old.execSQL("INSERT INTO receipts VALUES('token','key',1000,'hash','Alipay',20000,'expense','餐厅')")
            old.execSQL("INSERT INTO merchant_preferences VALUES('餐厅','餐饮','餐厅',1,0,0,1000,1000)")
            old.execSQL("INSERT INTO ledger_settings VALUES('lastBackupAt','1000')")
            old.execSQL("INSERT INTO unrecognized VALUES(1,1000,'app','标题','正文','','','','未知')")
            old.execSQL("INSERT INTO diagnostic_sessions VALUES('session',1000,301000,300000,301000,300000,'timeout',0)")
            old.execSQL("INSERT INTO diagnostic_notifications VALUES('log','session',1000,1000,'app','应用','标题','正文','','','','','key',0,'')")
            old.version = 5
        }
        val db = Room.databaseBuilder(context, LedgerDatabase::class.java, name).addMigrations(MIGRATION_5_6).allowMainThreadQueries().build()
        try {
            assertEquals(2, db.dao().allEntries().size); assertEquals(4000L, db.dao().get(1)!!.personalExpenseCents)
            assertEquals(20000L, db.dao().get(1)!!.amount); assertEquals("AA备注", db.dao().get(1)!!.note)
            assertEquals("salary", db.dao().get(2)!!.incomeCategoryId); assertEquals("旧公司", db.dao().get(2)!!.legacyMerchant)
            assertEquals(2, db.financialDao().categories().size); assertEquals("1000", db.financialDao().settings().single().value)
            assertEquals(1, db.dao().allReceipts().size); assertEquals(1, db.merchantDao().allPreferences().size)
            db.openHelper.readableDatabase.query("SELECT COUNT(*) FROM unrecognized").use { it.moveToFirst(); assertEquals(1, it.getInt(0)) }
            db.openHelper.readableDatabase.query("SELECT COUNT(*) FROM diagnostic_notifications").use { it.moveToFirst(); assertEquals(1, it.getInt(0)) }
            db.openHelper.readableDatabase.query("SELECT COUNT(*) FROM diagnostic_sessions").use { it.moveToFirst(); assertEquals(1, it.getInt(0)) }
            assertTrue(db.assetDao().accounts().isEmpty()); assertTrue(db.assetDao().snapshots().isEmpty()); assertTrue(db.assetDao().items().isEmpty())
        } finally { db.close() }
        val reopened = Room.databaseBuilder(context, LedgerDatabase::class.java, name).addMigrations(MIGRATION_5_6).allowMainThreadQueries().build()
        try { assertEquals(2, reopened.dao().allEntries().size); assertEquals(4000L, reopened.dao().get(1)!!.personalExpenseCents) }
        finally { reopened.close(); context.deleteDatabase(name) }
    }
}
