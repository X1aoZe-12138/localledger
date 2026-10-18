package cn.localledger.ui.design

import android.content.Context
import androidx.datastore.core.Serializer
import androidx.datastore.dataStore
import kotlinx.coroutines.flow.*
import java.io.IOException
import java.io.InputStream
import java.io.OutputStream

// One small device preference does not need a protobuf or JSON runtime.
internal object ThemeSerializer : Serializer<String> {
    override val defaultValue = "SYSTEM"
    override suspend fun readFrom(input: InputStream): String = input.readBytes().decodeToString().trim()
        .takeIf { it in UiPreferences.modes } ?: defaultValue
    override suspend fun writeTo(t: String, output: OutputStream) { output.write(t.encodeToByteArray()) }
}
private val Context.appearanceStore by dataStore(fileName = "appearance.theme", serializer = ThemeSerializer)
class UiPreferences(context: Context) {
    private val store = context.applicationContext.appearanceStore
    val theme = store.data.catch { if(it is IOException) emit("SYSTEM") else throw it }
    suspend fun setTheme(mode: String) { require(mode in modes); store.updateData { mode } }
    companion object { val modes = linkedMapOf("SYSTEM" to "跟随系统", "LIGHT" to "浅色", "DARK" to "深色") }
}
