package cn.localledger.data

import android.app.Application
import android.content.Context
import android.database.sqlite.SQLiteDatabase
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import kotlinx.coroutines.runBlocking
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], application = Application::class)
class FinancialMigrationTest {
    @Test fun migrateActualSchemaFourPreservesAAAndLegacyIncomeAndAllExistingTables() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val name = "migration-v05-test.db"
        context.deleteDatabase(name)
        val path = context.getDatabasePath(name).apply { parentFile!!.mkdirs() }
        val json = javaClass.classLoader!!.getResourceAsStream("cn.localledger.data.LedgerDatabase/4.json")!!.bufferedReader().use { JSONObject(it.readText()).getJSONObject("database") }
        SQLiteDatabase.openOrCreateDatabase(path, null).use { old ->
            val entities = json.getJSONArray("entities")
            for(i in 0 until entities.length()) {
                val entity = entities.getJSONObject(i)
                old.execSQL(entity.getString("createSql").replace("\${TABLE_NAME}", entity.getString("tableName")))
                entity.optJSONArray("indices")?.let { indexes -> for(j in 0 until indexes.length()) old.execSQL(indexes.getJSONObject(j).getString("createSql").replace("\${TABLE_NAME}", entity.getString("tableName"))) }
            }
            val setup = json.getJSONArray("setupQueries")
            for(i in 0 until setup.length()) old.execSQL(setup.getString(i))
            old.execSQL("INSERT INTO transactions VALUES(1,1000,20000,'expense','餐饮','原商户','Alipay','AA原备注',1000,1,3,'MANUAL_RECEIVED',NULL,16000)")
            old.execSQL("INSERT INTO transactions VALUES(2,1000,10000,'income','购物','朋友','WeChat','退款还是赠与无法可靠判断',1000,0,3,'NONE',NULL,NULL)")
            old.execSQL("INSERT INTO transactions VALUES(3,1000,800000,'income','工资','公司','Manual','旧工资',1000,0,3,'NONE',NULL,NULL)")
            old.execSQL("INSERT INTO receipts VALUES('token','key',1000,'hash','Alipay',20000,'expense','原商户')")
            old.execSQL("INSERT INTO unrecognized VALUES(1,1000,'app','标题','正文','','','','未知')")
            old.execSQL("INSERT INTO merchant_preferences VALUES('原商户','餐饮','原商户',1,0,0,1000,1000)")
            old.execSQL("INSERT INTO diagnostic_sessions VALUES('session',1000,301000,300000,301000,300000,'timeout',0)")
            old.execSQL("INSERT INTO diagnostic_notifications VALUES('log','session',1000,1000,'app','应用','标题','正文','','','','','key',0,'')")
            old.version = 4
        }
        val db = Room.databaseBuilder(context, LedgerDatabase::class.java, name).addMigrations(MIGRATION_4_5, MIGRATION_5_6).allowMainThreadQueries().build()
        try {
            val aa = db.dao().get(1)!!
            assertEquals(20000L, aa.amount); assertEquals(4000L, aa.personalExpenseCents); assertEquals("AA原备注", aa.note)
            val income = db.dao().get(2)!!
            assertEquals("income", income.transactionType); assertEquals(OTHER_INCOME_ID, income.incomeCategoryId)
            assertEquals("购物", income.legacyCategory); assertEquals("朋友", income.legacyMerchant)
            assertEquals("购物", income.category); assertEquals("朋友", income.merchant)
            assertEquals("退款还是赠与无法可靠判断", income.note)
            assertEquals("salary", db.dao().get(3)!!.incomeCategoryId)
            assertEquals(5, db.financialDao().categories().size)
            assertEquals(3, db.dao().allEntries().size)
            listOf("receipts", "unrecognized", "merchant_preferences", "diagnostic_sessions", "diagnostic_notifications").forEach { table ->
                db.openHelper.readableDatabase.query("SELECT COUNT(*) FROM $table").use { it.moveToFirst(); assertEquals(table, 1, it.getInt(0)) }
            }
        } finally { db.close() }
        val reopened = Room.databaseBuilder(context, LedgerDatabase::class.java, name).addMigrations(MIGRATION_4_5, MIGRATION_5_6).allowMainThreadQueries().build()
        try { assertEquals(4000L, reopened.dao().get(1)!!.personalExpenseCents); assertEquals("朋友", reopened.dao().get(2)!!.legacyMerchant) }
        finally { reopened.close(); context.deleteDatabase(name) }
    }
}
