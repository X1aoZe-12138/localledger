package cn.localledger.ui.design

import androidx.compose.runtime.*

val LocalBackRegistration = staticCompositionLocalOf<( (() -> Unit)? ) -> Unit> { {} }
@Composable fun ScreenBack(enabled: Boolean, action: () -> Unit) {
    val register = LocalBackRegistration.current
    val current by rememberUpdatedState(action)
    DisposableEffect(enabled, register) {
        if(enabled) register { current() }
        onDispose { if(enabled) register(null) }
    }
}
