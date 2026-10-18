package cn.localledger

import androidx.test.core.app.ApplicationProvider
import cn.localledger.data.LedgerDatabase
import kotlinx.coroutines.cancel

// A process normally owns these resources for its lifetime. Robolectric reuses a JVM,
// so stop its app collectors before it resets SQLite shadows for the next test.
fun closeTestApplication() {
    val app = ApplicationProvider.getApplicationContext<LedgerApplication>()
    app.scope.cancel()
    val field = LedgerApplication::class.java.getDeclaredField("database\$delegate").apply { isAccessible = true }
    val lazy = field.get(app) as Lazy<*>
    if(lazy.isInitialized()) (lazy.value as LedgerDatabase).close()
}
