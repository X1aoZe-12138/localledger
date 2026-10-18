package cn.localledger.merchant

import android.app.Application
import android.content.Context
import android.database.sqlite.SQLiteDatabase
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import cn.localledger.data.*
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], application = Application::class)
class MerchantMigrationTest {
    @Test fun migrateVersionTwoKeepsBillsAndBuildsRecommendationsDynamically() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val name = "migration-v03-test.db"
        context.deleteDatabase(name)
        val path = context.getDatabasePath(name).apply { parentFile!!.mkdirs() }
        val json = javaClass.classLoader!!.getResourceAsStream("cn.localledger.data.LedgerDatabase/2.json")!!.bufferedReader().use { JSONObject(it.readText()).getJSONObject("database") }
        SQLiteDatabase.openOrCreateDatabase(path, null).use { old ->
            val entities = json.getJSONArray("entities")
            for(i in 0 until entities.length()) {
                val entity = entities.getJSONObject(i)
                old.execSQL(entity.getString("createSql").replace("\${TABLE_NAME}", entity.getString("tableName")))
                entity.optJSONArray("indices")?.let { indexes -> for(j in 0 until indexes.length()) old.execSQL(indexes.getJSONObject(j).getString("createSql").replace("\${TABLE_NAME}", entity.getString("tableName"))) }
            }
            val setup = json.getJSONArray("setupQueries")
            for(i in 0 until setup.length()) old.execSQL(setup.getString(i))
            repeat(3) { index -> old.execSQL("INSERT INTO transactions VALUES(?, ?, 100, 'expense', '餐饮', '食堂', 'Manual', '', ?)", arrayOf<Any>(index + 1L, index + 100L, index + 100L)) }
            old.execSQL("INSERT INTO receipts VALUES('token', 'key', 1000, 'hash', 'Alipay', 100, 'expense', '食堂')")
            old.execSQL("INSERT INTO unrecognized VALUES(1, 1000, 'app', '标题', '文本', '', '', '', '未知')")
            old.version = 2
        }
        val db = Room.databaseBuilder(context, LedgerDatabase::class.java, name).addMigrations(MIGRATION_2_3, MIGRATION_3_4, MIGRATION_4_5, MIGRATION_5_6).allowMainThreadQueries().build()
        try {
            assertEquals(3, db.dao().allEntries().size)
            assertEquals(1, db.dao().receipts("key", 0, 2000).size)
            db.openHelper.readableDatabase.query("SELECT COUNT(*) FROM unrecognized").use { it.moveToFirst(); assertEquals(1, it.getInt(0)) }
            val memory = MerchantRepository(db).memories.first { it.isNotEmpty() }.single()
            assertEquals("食堂", memory.name); assertEquals(3, memory.useCount); assertTrue(memory.isShortcut)
            assertEquals(0, db.openHelper.readableDatabase.query("SELECT COUNT(*) FROM merchant_preferences").use { it.moveToFirst(); it.getInt(0) })
        } finally { db.close(); context.deleteDatabase(name) }
    }
}
