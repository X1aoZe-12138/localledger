package cn.localledger.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import cn.localledger.ui.design.*

@Composable
fun MyContent(granted: Boolean, connected: Boolean, notifications: Boolean, version: String, theme: String,
    setTheme: (String) -> Unit, authorize: () -> Unit, reminder: () -> Unit, diagnostic: () -> Unit,
    merchants: () -> Unit, income: () -> Unit, report: () -> Unit, data: () -> Unit, logs: () -> Unit, details: () -> Unit) {
    var guide by rememberSaveable { mutableStateOf(false) }
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(AppDimensions.Page), verticalArrangement = Arrangement.spacedBy(AppDimensions.Small)) {
        SectionHeader("自动记账")
        SettingRow("通知使用权", "${if(granted) "已授权" else "未授权"} · ${if(connected) "自动记账已连接" else "自动记账未连接"}", authorize)
        SettingRow("记账提醒", if(notifications) "已允许" else "未允许 · 不影响账单保存", reminder)
        SettingRow("未识别通知", "查看可能需要补录的支付通知", logs)
        SettingRow("通知诊断", "限时记录所有应用通知，仅保存在本机", diagnostic)
        SettingRow("小米 / HyperOS 后台设置", if(guide) "收起说明" else "后台漏记时查看", { guide = !guide })
        AnimatedVisibility(guide) { Column {
            Text("若长期后台使用后漏记，可允许自启动、将电池策略设为「无限制」，并在最近任务中锁定 App。\n\n请同时允许微信、支付宝自身的支付通知。没有系统通知的支付仍需手动补录。\n\n侧载应用无法授权时，可在应用详情右上角查看「允许受限制的设置」。", style = MaterialTheme.typography.bodySmall)
            TextButton(onClick = details) { Text("打开应用详情") }
        } }
        SectionHeader("账本")
        SettingRow("商户管理", "常用商户与置顶快捷项", merchants)
        SettingRow("收入标签", "管理收入来源和排序", income)
        SectionHeader("分析")
        SettingRow("AI 财务报告", "整理本地数据，导出 Markdown 后自行分享", report)
        SectionHeader("数据")
        SettingRow("数据管理", "备份、恢复与交易 CSV 导出", data)
        SectionHeader("外观")
        Text("主题", style = MaterialTheme.typography.bodyMedium)
        Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(AppDimensions.Small)) {
            UiPreferences.modes.forEach { (id, name) -> FilterChip(theme == id, { setTheme(id) }, label = { Text(name) }) }
        }
        SectionHeader("关于")
        Text("本地记账 · $version", style = MaterialTheme.typography.bodyMedium)
        Text("无联网权限、无广告和遥测。账本仅保存在本机，卸载或清除数据会永久删除账单，请定期备份。", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Spacer(Modifier.height(AppDimensions.Section))
    }
}
