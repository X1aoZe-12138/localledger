package cn.localledger.diagnostic

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
class DiagnosticMigrationTest {
    @Test fun migrateRealVersionOneDatabaseAndKeepAllOldTables() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val name = "migration-v02-test.db"
        context.deleteDatabase(name)
        val path = context.getDatabasePath(name)
        path.parentFile!!.mkdirs()
        val json = javaClass.classLoader!!.getResourceAsStream("cn.localledger.data.LedgerDatabase/1.json")!!.bufferedReader().use { JSONObject(it.readText()).getJSONObject("database") }
        SQLiteDatabase.openOrCreateDatabase(path, null).use { old ->
            val entities = json.getJSONArray("entities")
            for(i in 0 until entities.length()) {
                val entity = entities.getJSONObject(i)
                old.execSQL(entity.getString("createSql").replace("\${TABLE_NAME}", entity.getString("tableName")))
                entity.optJSONArray("indices")?.let { indexes -> for(j in 0 until indexes.length()) old.execSQL(indexes.getJSONObject(j).getString("createSql").replace("\${TABLE_NAME}", entity.getString("tableName"))) }
            }
            val setup = json.getJSONArray("setupQueries")
            for(i in 0 until setup.length()) old.execSQL(setup.getString(i))
            old.execSQL("INSERT INTO transactions VALUES(1, 1000, 2680, 'expense', '餐饮', '旧账单', 'Alipay', '保留备注', 1000)")
            old.execSQL("INSERT INTO receipts VALUES('token', 'key', 1000, 'hash', 'Alipay', 2680, 'expense', '旧账单')")
            old.execSQL("INSERT INTO unrecognized VALUES(1, 1000, 'app', '标题', '文本', '', '', '', '未知')")
            old.version = 1
        }
        val db = Room.databaseBuilder(context, LedgerDatabase::class.java, name).addMigrations(MIGRATION_1_2, MIGRATION_2_3, MIGRATION_3_4, MIGRATION_4_5, MIGRATION_5_6).allowMainThreadQueries().build()
        try {
            assertEquals("保留备注", db.dao().get(1)!!.note) // Opens, migrates and validates schema.
            assertEquals(2680L, db.dao().get(1)!!.amount)
            assertEquals(1, db.dao().receipts("key", 0, 2000).size)
            db.openHelper.readableDatabase.query("SELECT COUNT(*) FROM unrecognized").use { it.moveToFirst(); assertEquals(1, it.getInt(0)) }
            val repository = DiagnosticRepository(db)
            val session = repository.start(5, 2000)
            repository.record(2001) { NotificationSnapshot(2001, "test") }
            assertEquals(1, repository.export(session.id)!!.notifications.size)
        } finally { db.close() }
        val reopened = Room.databaseBuilder(context, LedgerDatabase::class.java, name).addMigrations(MIGRATION_1_2, MIGRATION_2_3, MIGRATION_3_4, MIGRATION_4_5, MIGRATION_5_6).allowMainThreadQueries().build()
        try {
            assertNotNull(reopened.diagnosticDao().activeNow())
            assertEquals(2680L, reopened.dao().get(1)!!.amount)
        } finally { reopened.close(); context.deleteDatabase(name) }
    }
}
