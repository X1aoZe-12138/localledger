package cn.localledger.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.animation.*
import androidx.compose.animation.core.tween
import androidx.compose.ui.Alignment
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.semantics.*
import cn.localledger.ui.design.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import cn.localledger.data.*
import cn.localledger.parser.Money

@Composable
fun SplitControls(enabled: Boolean, count: Int, self: String, received: String, original: Long?,
    setEnabled: (Boolean) -> Unit, setCount: (Int) -> Unit, setSelf: (String) -> Unit, setReceived: (String) -> Unit) {
    HorizontalDivider()
    Text("账单调整", style = MaterialTheme.typography.titleMedium)
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
        Text("AA / 分摊"); Switch(enabled, setEnabled, modifier = Modifier.semantics { contentDescription = "AA / 分摊" })
    }
    val focus = LocalFocusManager.current
    AnimatedVisibility(enabled, enter = expandVertically(tween(200)) + fadeIn(tween(200)), exit = shrinkVertically(tween(200)) + fadeOut(tween(200))) {
      Column(verticalArrangement = Arrangement.spacedBy(AppDimensions.Medium)) {
        Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
            OutlinedButton(modifier = Modifier.sizeIn(minWidth = 48.dp, minHeight = 48.dp).semantics { contentDescription = "减少人数" }, enabled = count > 2, onClick = { setCount(count - 1) }) { Text("‹") }
            Text("$count 人", Modifier.padding(top = 12.dp))
            OutlinedButton(modifier = Modifier.sizeIn(minWidth = 48.dp, minHeight = 48.dp).semantics { contentDescription = "增加人数" }, enabled = count < 99, onClick = { setCount(count + 1) }) { Text("›") }
        }
        Text("两种金额任选一种；均留空时按人数平均分摊。", style = MaterialTheme.typography.bodySmall)
        OutlinedTextField(self, setSelf, label = { Text("我实际承担（元）") }, singleLine = true,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal, imeAction = ImeAction.Done), keyboardActions = KeyboardActions(onDone = { focus.clearFocus() }), prefix = { Text("¥") }, modifier = Modifier.fillMaxWidth())
        OutlinedTextField(received, setReceived, label = { Text("我已收回（元）") }, singleLine = true,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal, imeAction = ImeAction.Done), keyboardActions = KeyboardActions(onDone = { focus.clearFocus() }), prefix = { Text("¥") }, modifier = Modifier.fillMaxWidth())
        val candidate = original?.let { splitEntry(it, count, self, received) }
        val error = when {
            original == null -> "请先填写有效原始支付金额"
            candidate == null -> "AA 金额须为非负金额，最多两位小数"
            else -> SplitExpense.validationError(candidate)
        }
        if(error != null) Text(error, color = MaterialTheme.colorScheme.error)
        else candidate?.let {
            Text("实际支付 ¥${displayNumber(it.amount)}\n个人承担 ¥${displayNumber(it.personalExpenseCents)}", style = MaterialTheme.typography.titleMedium)
            Text(when(it.splitCalculationMode) {
                "MANUAL_RECEIVED" -> "AA · 已收回 ¥${displayNumber(it.receivedAmountCents!!)}"
                "MANUAL_SELF" -> "AA · 手动承担"
                else -> "AA · ${it.participantCount}人"
            }, style = MaterialTheme.typography.bodySmall)
        }
      }
    }
}

fun splitEntry(amount: Long, count: Int, self: String, received: String): LedgerEntry? {
    val selfCents = if(self.isBlank()) null else SplitExpense.parseNonNegative(self) ?: return null
    val receivedCents = if(received.isBlank()) null else SplitExpense.parseNonNegative(received) ?: return null
    return LedgerEntry(amount = amount, splitEnabled = true, participantCount = count,
        splitCalculationMode = when { selfCents != null -> "MANUAL_SELF"; receivedCents != null -> "MANUAL_RECEIVED"; else -> "EQUAL" },
        manualSelfAmountCents = selfCents, receivedAmountCents = receivedCents)
}
