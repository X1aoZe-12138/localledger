package cn.localledger.diagnostic

import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*

class DiagnosticManager(val repository: DiagnosticRepository, private val scope: CoroutineScope,
    private val clock: () -> Long = System::currentTimeMillis) {
    val error = MutableStateFlow<String?>(null)
    val active = repository.active.stateIn(scope, SharingStarted.Eagerly, null)
    init {
        scope.launch {
            repository.active.collectLatest { session ->
                if(session != null) {
                    // No Activity timer, alarm permission, or extra service. Notifications also enforce the persisted deadline.
                    delay((session.expectedEndTime - clock()).coerceAtLeast(0))
                    safely { repository.expire(clock()) }
                }
            }
        }
    }
    suspend fun start(minutes: Int) = repository.start(minutes, clock())
    suspend fun stop() = repository.stop(clock())
    suspend fun refresh() = repository.expire(clock())
    fun capture(receivedAt: Long, read: () -> NotificationSnapshot) {
        scope.launch { safely { repository.record(receivedAt, read) } }
    }
    private suspend fun safely(action: suspend () -> Unit) {
        try { action() }
        catch (e: CancellationException) { throw e }
        catch (_: Exception) { error.value = "部分诊断通知未能保存，请检查存储空间；自动记账继续独立运行。" }
    }
}
