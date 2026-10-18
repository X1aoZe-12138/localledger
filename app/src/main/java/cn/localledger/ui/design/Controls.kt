package cn.localledger.ui.design

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.*

@Composable
fun nativeDialogContext(): android.content.Context = android.view.ContextThemeWrapper(
    androidx.compose.ui.platform.LocalContext.current,
    if(LocalAppDark.current) cn.localledger.R.style.AppDarkTheme else android.R.style.Theme_Material_Light_NoActionBar)

@Composable
fun SettingRow(title: String, subtitle: String? = null, action: () -> Unit) {
    Row(Modifier.fillMaxWidth().heightIn(min = AppDimensions.Touch).clickable(onClick = action).padding(vertical = AppDimensions.Small), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(AppDimensions.Tiny)) {
            Text(title, style = MaterialTheme.typography.bodyLarge)
            subtitle?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
        }
        Icon(AppIcons.Chevron, null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
fun ChoiceMenu(label: String, choices: List<Pair<String, String>>, selected: String, enabled: Boolean = true, choose: (String) -> Unit) {
    var open by remember { mutableStateOf(false) }
    Box {
        OutlinedButton(enabled = enabled, onClick = { open = true }) { Text("$label · ${choices.firstOrNull { it.first == selected }?.second.orEmpty()} ▾") }
        DropdownMenu(open, { open = false }) { choices.forEach { (id, title) ->
            DropdownMenuItem(text = { Text(if(id == selected) "✓ $title" else title) }, onClick = { choose(id); open = false })
        } }
    }
}

@Composable
fun ToggleRow(title: String, checked: Boolean, enabled: Boolean = true, change: (Boolean) -> Unit) {
    Row(Modifier.fillMaxWidth().heightIn(min = AppDimensions.Touch), verticalAlignment = Alignment.CenterVertically) {
        Text(title, Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium)
        Switch(checked, change, enabled = enabled, modifier = Modifier.semantics { contentDescription = title })
    }
}
