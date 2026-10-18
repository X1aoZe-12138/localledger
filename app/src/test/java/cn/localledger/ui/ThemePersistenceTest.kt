package cn.localledger.ui

import android.app.Application
import androidx.test.core.app.ApplicationProvider
import cn.localledger.ui.design.*
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.flow.first
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.ByteArrayInputStream

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], application = Application::class)
class ThemePersistenceTest {
    @Test fun appearanceSurvivesRepositoryRecreationAndInvalidChoiceDoesNotOverwrite() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Application>()
        val settings = UiPreferences(context)
        settings.setTheme("DARK")
        assertEquals("DARK", UiPreferences(context).theme.first())
        assertTrue(runCatching { settings.setTheme("invalid") }.isFailure)
        assertEquals("DARK", settings.theme.first())
        settings.setTheme("LIGHT")
        assertEquals("LIGHT", settings.theme.first())
        settings.setTheme("SYSTEM")
        assertEquals("SYSTEM", settings.theme.first())
    }
    @Test fun unknownOrEmptyStoredAppearanceFallsBackToSystem() = runBlocking {
        assertEquals("SYSTEM", ThemeSerializer.readFrom(ByteArrayInputStream(byteArrayOf())))
        assertEquals("SYSTEM", ThemeSerializer.readFrom(ByteArrayInputStream("future-mode".encodeToByteArray())))
    }
}
