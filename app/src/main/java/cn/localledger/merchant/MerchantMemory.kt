package cn.localledger.merchant

import androidx.room.*
import cn.localledger.data.LedgerEntry
import cn.localledger.parser.*
import kotlinx.coroutines.flow.Flow

@Entity(
    tableName = "merchant_preferences",
    primaryKeys = ["normalizedName", "category"],
    indices = [Index("category")]
)
data class MerchantPreferenceEntity(
    val normalizedName: String,
    val category: String,
    val displayName: String,
    val isPinned: Boolean = false,
    val isManual: Boolean = false,
    val isHidden: Boolean = false,
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis()
)

@Dao
interface MerchantDao {
    @Query("SELECT * FROM merchant_preferences") fun observePreferences(): Flow<List<MerchantPreferenceEntity>>
    @Query("SELECT * FROM merchant_preferences WHERE normalizedName = :name AND category = :category")
    suspend fun get(name: String, category: String): MerchantPreferenceEntity?
    @Query("SELECT * FROM merchant_preferences") suspend fun allPreferences(): List<MerchantPreferenceEntity>
    @Query("DELETE FROM merchant_preferences") suspend fun clearPreferences()
    @Insert(onConflict = OnConflictStrategy.REPLACE) suspend fun put(value: MerchantPreferenceEntity)
}

data class MerchantMemory(
    val name: String,
    val normalizedName: String,
    val category: String,
    val useCount: Int,
    val lastUsedAt: Long,
    val isPinned: Boolean,
    val isManual: Boolean
) {
    val isShortcut get() = isPinned || isManual || useCount >= MerchantMemoryEngine.SHORTCUT_MIN_COUNT
}

object MerchantMemoryEngine {
    const val SHORTCUT_MIN_COUNT = 3
    const val AUTO_CATEGORY_MIN_COUNT = 3
    const val AUTO_CATEGORY_MIN_RATIO = 0.8

    fun build(entries: List<LedgerEntry>, preferences: List<MerchantPreferenceEntity>): List<MerchantMemory> {
        data class MutableUsage(var name: String, var count: Int, var last: Long)
        val usage = linkedMapOf<Pair<String, String>, MutableUsage>()
        entries.asSequence().filter(::isLearnable).forEach { entry ->
            val key = normalizeMerchantName(entry.merchant) to entry.category
            val current = usage[key]
            if (current == null) usage[key] = MutableUsage(entry.merchant.trim(), 1, entry.timestamp)
            else {
                current.count++
                if (entry.timestamp >= current.last) { current.last = entry.timestamp; current.name = entry.merchant.trim() }
            }
        }
        val prefs = preferences.associateBy { it.normalizedName to it.category }
        val keys = usage.keys + prefs.keys
        return keys.distinct().mapNotNull { key ->
            val pref = prefs[key]
            if (pref?.isHidden == true) return@mapNotNull null
            val found = usage[key]
            if (found == null && pref == null) return@mapNotNull null
            MerchantMemory(
                name = pref?.displayName?.takeIf { it.isNotBlank() } ?: found!!.name,
                normalizedName = key.first,
                category = key.second,
                useCount = found?.count ?: 0,
                lastUsedAt = found?.last ?: 0,
                isPinned = pref?.isPinned == true,
                isManual = pref?.isManual == true
            )
        }.sortedWith(compareByDescending<MerchantMemory> { it.isPinned }
            .thenByDescending { it.useCount }.thenByDescending { it.lastUsedAt }.thenBy { it.name })
    }

    fun dominantCategory(entries: List<LedgerEntry>, merchant: String): String? {
        if (!isLearnableMerchant(merchant)) return null
        val normalized = normalizeMerchantName(merchant)
        val counts = entries.asSequence().filter(::isLearnable)
            .filter { normalizeMerchantName(it.merchant) == normalized }
            .groupingBy { it.category }.eachCount()
        val total = counts.values.sum()
        val dominant = counts.maxByOrNull { it.value } ?: return null
        val tied = counts.values.count { it == dominant.value } > 1
        return dominant.key.takeIf {
            !tied && dominant.value >= AUTO_CATEGORY_MIN_COUNT && total > 0 && dominant.value.toDouble() / total >= AUTO_CATEGORY_MIN_RATIO
        }
    }

    private fun isLearnable(entry: LedgerEntry) = entry.transactionType == "expense" && entry.category in learnableCategories && isLearnableMerchant(entry.merchant)
}
