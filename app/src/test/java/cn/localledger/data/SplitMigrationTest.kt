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
class SplitMigrationTest {
    @Test fun versionThreeMigrationPreservesAllTablesAndSplitDefaults() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val name = "migration-v04-test.db"
        context.deleteDatabase(name)
        val path = context.getDatabasePath(name).apply { parentFile!!.mkdirs() }
        val json = javaClass.classLoader!!.getResourceAsStream("cn.localledger.data.LedgerDatabase/3.json")!!.bufferedReader().use { JSONObject(it.readText()).getJSONObject("database") }
        SQLiteDatabase.openOrCreateDatabase(path, null).use { old ->
            val entities = json.getJSONArray("entities")
            for(i in 0 until entities.length()) {
                val entity = entities.getJSONObject(i)
                old.execSQL(entity.getString("createSql").replace("\${TABLE_NAME}", entity.getString("tableName")))
                entity.optJSONArray("indices")?.let { indexes -> for(j in 0 until indexes.length()) old.execSQL(indexes.getJSONObject(j).getString("createSql").replace("\${TABLE_NAME}", entity.getString("tableName"))) }
            }
            val setup = json.getJSONArray("setupQueries")
            for(i in 0 until setup.length()) old.execSQL(setup.getString(i))
            old.execSQL("INSERT INTO transactions VALUES(1, 1000, 20000, 'expense', '餐饮', '旧餐厅', 'Alipay', '原始备注', 1000)")
            old.execSQL("INSERT INTO receipts VALUES('token', 'key', 1000, 'hash', 'Alipay', 20000, 'expense', '旧餐厅')")
            old.execSQL("INSERT INTO unrecognized VALUES(1, 1000, 'app', '标题', '文本', '', '', '', '未知')")
            old.execSQL("INSERT INTO merchant_preferences VALUES('旧餐厅','餐饮','旧餐厅',1,1,0,1000,1000)")
            old.execSQL("INSERT INTO diagnostic_sessions VALUES('session',1000,301000,300000,301000,300000,'timeout',0)")
            old.execSQL("INSERT INTO diagnostic_notifications VALUES('log','session',1000,1000,'app','应用','标题','正文','','','','','key',0,'')")
            old.version = 3
        }
        val db = Room.databaseBuilder(context, LedgerDatabase::class.java, name).addMigrations(MIGRATION_3_4, MIGRATION_4_5, MIGRATION_5_6).allowMainThreadQueries().build()
        try {
            val bill = db.dao().get(1)!!
            assertEquals(20000L, bill.amount); assertEquals(20000L, bill.personalExpenseCents)
            assertFalse(bill.splitEnabled); assertEquals("NONE", bill.splitCalculationMode)
            assertEquals(3, bill.participantCount); assertNull(bill.manualSelfAmountCents); assertNull(bill.receivedAmountCents)
            assertEquals("原始备注", bill.note)
            listOf("transactions", "receipts", "unrecognized", "merchant_preferences", "diagnostic_sessions", "diagnostic_notifications").forEach { table ->
                db.openHelper.readableDatabase.query("SELECT COUNT(*) FROM $table").use { it.moveToFirst(); assertEquals(table, 1, it.getInt(0)) }
            }
            LedgerRepository(db).save(bill.copy(splitEnabled = true, splitCalculationMode = "MANUAL_RECEIVED", receivedAmountCents = 16000))
            assertEquals(4000L, db.dao().get(1)!!.personalExpenseCents)
        } finally { db.close() }
        val reopened = Room.databaseBuilder(context, LedgerDatabase::class.java, name).addMigrations(MIGRATION_3_4, MIGRATION_4_5, MIGRATION_5_6).allowMainThreadQueries().build()
        try { assertEquals(4000L, reopened.dao().get(1)!!.personalExpenseCents); assertEquals(20000L, reopened.dao().get(1)!!.amount) }
        finally { reopened.close(); context.deleteDatabase(name) }
    }
}
