package cn.localledger

import android.app.Application
import androidx.room.Room
import cn.localledger.data.*
import cn.localledger.diagnostic.*
import cn.localledger.merchant.MerchantRepository
import cn.localledger.backup.BackupRepository
import cn.localledger.asset.AssetRepository
import cn.localledger.report.ReportRepository
import cn.localledger.ui.design.UiPreferences
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow

class LedgerApplication : Application() {
    val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    val listenerConnected = MutableStateFlow(false)
    val listenerError = MutableStateFlow<String?>(null)
    private val database by lazy { Room.databaseBuilder(this, LedgerDatabase::class.java, "ledger.db").addMigrations(MIGRATION_1_2, MIGRATION_2_3, MIGRATION_3_4, MIGRATION_4_5, MIGRATION_5_6).build() }
    val repository by lazy { LedgerRepository(database) }
    val merchantRepository by lazy { MerchantRepository(database) }
    val diagnosticManager by lazy { DiagnosticManager(DiagnosticRepository(database), scope) }
    val financialRepository by lazy { FinancialRepository(database) }
    val backupRepository by lazy { BackupRepository(database) }
    val assetRepository by lazy { AssetRepository(database) }
    val reportRepository by lazy { ReportRepository(database) }
    val uiPreferences by lazy { UiPreferences(this) }
}
