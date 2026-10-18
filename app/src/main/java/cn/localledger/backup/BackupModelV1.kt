package cn.localledger.backup

import cn.localledger.data.*
import cn.localledger.merchant.MerchantPreferenceEntity
import cn.localledger.asset.*

data class BackupModelV1(
    val transactions: List<LedgerEntry>,
    val merchantPreferences: List<MerchantPreferenceEntity>,
    val incomeCategories: List<IncomeCategoryEntity>,
    val receipts: List<Receipt>,
    val settings: List<LedgerSetting>,
    val assetAccounts: List<AssetAccount> = emptyList(),
    val assetSnapshots: List<AssetSnapshot> = emptyList(),
    val assetSnapshotItems: List<AssetSnapshotItem> = emptyList()
)
class InvalidBackup(message: String = "备份文件损坏或格式不受支持") : IllegalArgumentException(message)
data class RestoreSummary(val transactions: Int, val income: Int, val merchants: Int, val split: Int, val categories: Int, val assetAccounts: Int = 0, val assetSnapshots: Int = 0)
