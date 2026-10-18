package cn.localledger

import android.content.Intent
import android.os.Looper
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.*
import org.junit.Test
import org.junit.After
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class AppSmokeTest {
    @After fun releaseProcessResources() { closeTestApplication() }
    @Test fun mainActivityStarts() {
        Robolectric.buildActivity(MainActivity::class.java).use { controller ->
            controller.setup()
            shadowOf(Looper.getMainLooper()).idle()
            assertNotNull(controller.get().findViewById<android.view.View>(android.R.id.content))
        }
    }
    @Test fun deletedEntryNotificationDoesNotCrash() {
        val context = ApplicationProvider.getApplicationContext<LedgerApplication>()
        val intent = Intent(context, MainActivity::class.java).putExtra("entryId", 999L)
        Robolectric.buildActivity(MainActivity::class.java, intent).use { controller ->
            controller.setup()
            shadowOf(Looper.getMainLooper()).idle()
            assertFalse(controller.get().isFinishing)
        }
    }
}
