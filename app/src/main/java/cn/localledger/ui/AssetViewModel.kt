package cn.localledger.ui

import android.app.Application
import androidx.lifecycle.*
import cn.localledger.LedgerApplication
import cn.localledger.asset.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*

class AssetViewModel(application: Application) : AndroidViewModel(application) {
    private val repo = (application as LedgerApplication).assetRepository
    val accounts = repo.accounts.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())
    val snapshots = repo.snapshots.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())
    val items = repo.items.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())
    val busy = MutableStateFlow(false)
    val message = MutableStateFlow<String?>(null)
    fun account(name: String, type: String, included: Boolean, note: String, balance: Long, id: String?, done: () -> Unit) = operation(done) { repo.saveAccount(name, type, included, note, balance, id) }
    fun remove(id: String) = operation { repo.archiveOrDelete(id) }
    fun unarchive(id: String) = operation { repo.unarchive(id) }
    fun move(id: String, delta: Int) = operation { repo.move(id, delta) }
    fun inventory(id: String?, balances: Map<String, Long>, note: String, done: () -> Unit) = operation(done) {
        if(id == null) repo.saveSnapshot(balances, note) else repo.editSnapshot(id, balances, note)
    }
    private fun operation(done: () -> Unit = {}, action: suspend () -> Unit) {
        if(busy.value) return
        busy.value = true
        viewModelScope.launch {
            try { withContext(Dispatchers.IO) { action() }; done() }
            catch(e: CancellationException) { throw e }
            catch(_: Exception) { message.value = "无法保存：请检查金额、账户是否变化及设备时间。原记录未被部分修改。" }
            finally { busy.value = false }
        }
    }
}
