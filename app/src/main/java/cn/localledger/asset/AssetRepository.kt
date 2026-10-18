package cn.localledger.asset

import androidx.room.withTransaction
import cn.localledger.data.LedgerDatabase
import java.util.UUID

class AssetRepository(private val db: LedgerDatabase) {
    private val dao = db.assetDao()
    val accounts = dao.observeAccounts()
    val snapshots = dao.observeSnapshots()
    val items = dao.observeItems()

    suspend fun saveAccount(name: String, type: String, included: Boolean, note: String,
        initialBalance: Long = 0, id: String? = null, now: Long = System.currentTimeMillis()) = db.withTransaction {
        require(name.trim().isNotEmpty() && name.trim().length <= 100 && type in accountTypes && note.length <= 1000)
        require(initialBalance in 0..MAX_BALANCE && now in 0..MAX_TIME)
        val accounts = dao.accounts()
        if(id == null) {
            // Compact ranks first so adding after a deletion still appends, never jumps above existing accounts.
            accounts.forEachIndexed { index, a -> if(a.displayOrder != index) dao.updateAccount(a.copy(displayOrder = index)) }
            dao.insertAccount(AssetAccount(UUID.randomUUID().toString(), name.trim(), type, initialBalance,
                included, displayOrder = accounts.size, note = note, createdAt = now, updatedAt = now))
        }
        else dao.updateAccount(accounts.first { it.id == id }.copy(name = name.trim(), accountType = type,
            isIncludedInTotal = included, note = note, updatedAt = now))
    }
    suspend fun archiveOrDelete(id: String) = db.withTransaction {
        val account = dao.accounts().first { it.id == id }
        if(dao.items().any { it.accountId == id }) dao.updateAccount(account.copy(isArchived = true, updatedAt = System.currentTimeMillis()))
        else dao.deleteAccount(id)
    }
    suspend fun unarchive(id: String) = db.withTransaction {
        dao.updateAccount(dao.accounts().first { it.id == id }.copy(isArchived = false, updatedAt = System.currentTimeMillis()))
    }
    suspend fun move(id: String, delta: Int) = db.withTransaction {
        val list = dao.accounts().toMutableList()
        val from = list.indexOfFirst { it.id == id }; val to = from + delta
        if(from >= 0 && to in list.indices) {
            list.add(to, list.removeAt(from))
            list.forEachIndexed { i, a -> dao.updateAccount(a.copy(displayOrder = i)) }
        }
    }
    suspend fun saveSnapshot(balances: Map<String, Long>, note: String,
        now: Long = System.currentTimeMillis()): String = db.withTransaction {
        require(note.length <= 1000 && now in 0..MAX_TIME)
        val enabled = dao.accounts().filterNot { it.isArchived }
        require(enabled.isNotEmpty() && balances.keys == enabled.map { it.id }.toSet()) { "账户已变化，请重新打开盘点" }
        require(balances.values.all { it in 0..MAX_BALANCE })
        require(dao.snapshots().firstOrNull()?.timestamp?.let { now >= it } != false) { "设备时间早于最新盘点，请校正时间" }
        val id = UUID.randomUUID().toString()
        val total = enabled.filter { it.isIncludedInTotal }.sumOf { balances.getValue(it.id) }
        dao.insertSnapshot(AssetSnapshot(id, now, total, note, now))
        enabled.forEach { a ->
            val balance = balances.getValue(a.id)
            dao.updateAccount(a.copy(currentBalanceCents = balance, updatedAt = now))
            dao.insertItem(AssetSnapshotItem(UUID.randomUUID().toString(), id, a.id, a.name, balance, a.isIncludedInTotal))
        }
        id
    }
    suspend fun editSnapshot(id: String, balances: Map<String, Long>, note: String) = db.withTransaction {
        require(note.length <= 1000 && balances.values.all { it in 0..MAX_BALANCE })
        val snapshots = dao.snapshots(); val snapshot = snapshots.first { it.id == id }
        val items = dao.items().filter { it.snapshotId == id }
        require(balances.keys == items.map { it.accountId }.toSet())
        dao.updateSnapshot(snapshot.copy(totalBalanceCents = items.filter { it.includedInTotal }.sumOf { balances.getValue(it.accountId) }, note = note))
        items.forEach { dao.updateItem(it.copy(balanceCents = balances.getValue(it.accountId))) }
        // Only latest corrections affect current balances. New accounts absent from this snapshot remain unchanged.
        if(snapshots.first().id == id) dao.accounts().forEach { a -> balances[a.id]?.let {
            dao.updateAccount(a.copy(currentBalanceCents = it, updatedAt = System.currentTimeMillis()))
        } }
    }
}
