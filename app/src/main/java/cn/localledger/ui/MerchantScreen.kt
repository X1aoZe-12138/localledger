package cn.localledger.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.horizontalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import cn.localledger.ui.design.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import cn.localledger.merchant.MerchantMemory
import cn.localledger.parser.*

@Composable
fun MerchantManagement(vm: MerchantViewModel) {
    val memories by vm.memories.collectAsStateWithLifecycle()
    val busy by vm.busy.collectAsStateWithLifecycle()
    var category by rememberSaveable { mutableStateOf(learnableCategories.first()) }
    var editingKey by rememberSaveable { mutableStateOf<String?>(null) }
    val editing = memories.firstOrNull { "${it.normalizedName}|${it.category}" == editingKey }
    var adding by rememberSaveable { mutableStateOf(false) }
    var deleting by remember { mutableStateOf<MerchantMemory?>(null) }
    Column(Modifier.fillMaxSize().padding(16.dp), verticalArrangement = Arrangement.spacedBy(AppDimensions.Medium)) {
        Text("使用次数来自真实账单，编辑或删除账单后会自动重算。删除这里只隐藏推荐，不删除历史账单。")
        Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            learnableCategories.forEach { value -> FilterChip(category == value, { category = value }, label = { Text(value) }) }
        }
        Button(enabled = !busy, onClick = { adding = true }) { Text("新增商户") }
        LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            val shown = memories.filter { it.category == category }
            if (shown.isEmpty()) item { EmptyState("常用商户会随着使用逐渐出现", "保存三次同类账单后会出现快捷项，也可以主动新增商户。") }
            items(shown, key = { "${it.normalizedName}|${it.category}" }) { memory ->
                Column(Modifier.fillMaxWidth().padding(vertical = AppDimensions.Small)) {
                    Text(memory.name, style = MaterialTheme.typography.titleMedium)
                    Text("${memory.useCount} 次${if(memory.isManual) " · 手动创建" else ""}${if(memory.isPinned) " · 已置顶" else ""}")
                    Row {
                        TextButton(enabled = !busy, onClick = { vm.pin(memory) }) { Text(if(memory.isPinned) "取消置顶" else "置顶") }
                        TextButton(enabled = !busy, onClick = { editingKey = "${memory.normalizedName}|${memory.category}" }) { Text("编辑") }
                        TextButton(enabled = !busy, onClick = { deleting = memory }) { Text("删除", color = MaterialTheme.colorScheme.error) }
                    }
                }
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = .4f))
            }
        }
    }
    if(adding || editing != null) MerchantDialog(editing, category, { adding = false; editingKey = null }) { name, selected, pinned ->
        vm.save(name, selected, pinned, editing); category = selected; adding = false; editingKey = null
    }
    deleting?.let { memory -> AlertDialog(onDismissRequest = { deleting = null }, title = { Text("删除商户记忆？") },
        text = { Text("${memory.name} 将不再出现在推荐中，历史账单不会删除。") },
        confirmButton = { TextButton(onClick = { vm.delete(memory); deleting = null }) { Text("删除", color = MaterialTheme.colorScheme.error) } },
        dismissButton = { TextButton(onClick = { deleting = null }) { Text("取消") } }) }
}

@Composable
private fun MerchantDialog(current: MerchantMemory?, initialCategory: String, dismiss: () -> Unit,
    save: (String, String, Boolean) -> Unit) {
    var name by rememberSaveable(current?.normalizedName) { mutableStateOf(current?.name.orEmpty()) }
    var category by rememberSaveable(current?.normalizedName) { mutableStateOf(current?.category ?: initialCategory) }
    var pinned by rememberSaveable(current?.normalizedName) { mutableStateOf(current?.isPinned ?: false) }
    val valid = isLearnableMerchant(name)
    AlertDialog(onDismissRequest = dismiss, title = { Text(if(current == null) "新增商户" else "编辑商户") },
        text = { Column(verticalArrangement = Arrangement.spacedBy(AppDimensions.Medium)) {
            OutlinedTextField(name, { name = it.take(100) }, label = { Text("名称") }, singleLine = true, isError = name.isNotBlank() && !valid)
            Text("默认分类")
            Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(AppDimensions.Small)) {
                learnableCategories.forEach { value -> FilterChip(category == value, { category = value }, label = { Text(value) }) }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) { Checkbox(pinned, { pinned = it }); Text("置顶并立即显示") }
            Text("手动创建的商户无需累计 3 次，即可出现在该分类快捷项中。", style = MaterialTheme.typography.bodySmall)
        } },
        confirmButton = { TextButton(enabled = valid, onClick = { save(name.trim(), category, pinned) }) { Text("保存") } },
        dismissButton = { TextButton(onClick = dismiss) { Text("取消") } })
}
