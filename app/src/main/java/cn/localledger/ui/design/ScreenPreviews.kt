package cn.localledger.ui.design

import android.content.res.Configuration
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import cn.localledger.EditorContent
import cn.localledger.asset.*
import cn.localledger.data.*
import cn.localledger.ui.*
import cn.localledger.statistics.*
import java.time.*

// These previews use the same presentation components as the running app, without a database.
private val previewTime = LocalDate.now().atTime(12, 30).atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()
private val previewCategories = defaultIncomeCategories.mapIndexed { i, (id, name) -> IncomeCategoryEntity(id, name, i) }
private val previewEntries = listOf(
    LedgerEntry(id = 1, amount = 1763, timestamp = previewTime, merchant = "示例食堂", category = "餐饮", platform = "WeChat"),
    LedgerEntry(id = 2, amount = 20000, timestamp = previewTime - 3600000, merchant = "餐厅", category = "餐饮", splitEnabled = true, splitCalculationMode = "MANUAL_RECEIVED", receivedAmountCents = 16000),
    LedgerEntry(id = 3, amount = 4800, timestamp = previewTime - 7200000, merchant = "Steam", category = "娱乐", platform = "Alipay"),
    LedgerEntry(id = 4, amount = 1275, timestamp = previewTime - 10800000, merchant = "便利店", category = "生活"),
    LedgerEntry(id = 5, amount = 2650, timestamp = previewTime - 14400000, merchant = "未知商户", category = "待分类"),
    LedgerEntry(id = 6, amount = 3025, timestamp = previewTime - 86400000, merchant = "示例餐馆", category = "餐饮", platform = "WeChat"),
    LedgerEntry(id = 7, amount = 800000, timestamp = previewTime - 2 * 86400000, transactionType = "income", incomeCategoryId = "salary"),
    LedgerEntry(id = 8, amount = 10000, timestamp = previewTime - 2 * 86400000 - 3600000, transactionType = "refund"))

private val previewStatisticsEntries = (0..6).flatMap { day ->
    listOf("餐饮", "生活", "娱乐").mapIndexed { index, category ->
        previewEntries[0].copy(id = day * 3L + index + 1, category = category,
            timestamp = LocalDate.now().minusDays(day.toLong()).atTime(12, 0).atZone(ZoneId.systemDefault()).toInstant().toEpochMilli(),
            amount = (day + 1) * (index + 1) * 345L)
    }
}

@Composable private fun PreviewFrame(content: @Composable () -> Unit) { AppTheme { Surface(Modifier.fillMaxSize()) { content() } } }

@Preview(name = "账单 Light", widthDp = 360, heightDp = 780)
@Preview(name = "账单 Dark", widthDp = 412, heightDp = 860, uiMode = Configuration.UI_MODE_NIGHT_YES)
@Composable fun BillsPreview() = PreviewFrame { BillsContent(previewEntries, previewCategories, true, null, {}, {}, {}) }

@Preview(name = "编辑 AA Light", widthDp = 360, heightDp = 900)
@Preview(name = "编辑 AA Dark", widthDp = 412, heightDp = 900, uiMode = Configuration.UI_MODE_NIGHT_YES)
@Composable fun EditorPreview() = PreviewFrame { EditorContent(previewEntries[1], false, emptyList(), previewCategories, { null }, {}, {}, {}) }

@Preview(name = "统计 Light", widthDp = 360, heightDp = 780)
@Preview(name = "统计 Dark", widthDp = 412, heightDp = 860, uiMode = Configuration.UI_MODE_NIGHT_YES)
@Composable fun StatisticsPreview() = PreviewFrame { StatisticsContent(previewStatisticsEntries, emptyList(), {}) }

@Preview(name = "14天柱图 Light", widthDp = 320, heightDp = 640)
@Preview(name = "14天柱图 Dark", widthDp = 320, heightDp = 640, uiMode = Configuration.UI_MODE_NIGHT_YES)
@Composable fun Bars14Preview() = PreviewFrame {
    val today = LocalDate.now()
    val report = ExpenseStatistics.report(previewStatisticsEntries, today.minusDays(13), today)
    Column(Modifier.padding(AppDimensions.Page), verticalArrangement = Arrangement.spacedBy(AppDimensions.Medium)) {
        StackedBars(report, StatisticObject.TOTAL, "", emptyList()) {}
    }
}

@Preview(name = "资产总览 Light", widthDp = 360, heightDp = 780)
@Preview(name = "资产总览 Dark", widthDp = 412, heightDp = 860, uiMode = Configuration.UI_MODE_NIGHT_YES)
@Composable fun AssetsPreview() = PreviewFrame {
    val accounts = listOf(AssetAccount("wechat", "微信", "WECHAT", 50000, createdAt = previewTime, updatedAt = previewTime), AssetAccount("bank", "银行卡", "BANK", 2000000, createdAt = previewTime, updatedAt = previewTime))
    val snapshots = listOf(AssetSnapshot("latest", previewTime, 2050000, createdAt = previewTime), AssetSnapshot("old", previewTime - 86400000 * 7, 1950000, createdAt = previewTime))
    Column(Modifier.padding(AppDimensions.Page), verticalArrangement = Arrangement.spacedBy(AppDimensions.Medium)) {
        AssetSummary(accounts, snapshots)
        SectionHeader("账户")
        accounts.forEach { account -> SettingRow(account.name, displayMoney(account.currentBalanceCents), {}) }
        AssetTrend(snapshots)
    }
}

@Preview(name = "报告选项 Light", widthDp = 360, heightDp = 900)
@Preview(name = "报告选项 Dark", widthDp = 412, heightDp = 900, uiMode = Configuration.UI_MODE_NIGHT_YES)
@Composable fun ReportPreview() = PreviewFrame {
    Column(Modifier.verticalScroll(rememberScrollState()).padding(AppDimensions.Page)) {
        SectionHeader("AI 财务报告", "仅整理本地数据，分享前请检查隐私")
        ChoiceMenu("报告类型", listOf("ANALYSIS" to "消费分析", "PURCHASE" to "购买决策"), "ANALYSIS", choose = {})
        ChoiceMenu("范围", listOf("30" to "近30天"), "30", choose = {})
        ReportOptionsContent(mapOf("assets" to true, "income" to true, "categories" to true, "details" to true, "names" to true), false) { _, _ -> }
        Button(onClick = {}) { Text("预览报告") }
    }
}

@Preview(name = "我的 Light", widthDp = 360, heightDp = 900)
@Preview(name = "我的 Dark", widthDp = 412, heightDp = 900, uiMode = Configuration.UI_MODE_NIGHT_YES)
@Composable fun MyPreview() = PreviewFrame { MyContent(true, true, true, "0.9.2-beta.1", "SYSTEM", {}, {}, {}, {}, {}, {}, {}, {}, {}, {}) }

@Preview(name = "账单空状态 / 大字体", widthDp = 320, heightDp = 640, fontScale = 1.3f)
@Composable fun EmptyBillsPreview() = PreviewFrame { BillsContent(emptyList(), previewCategories, false, null, {}, {}, {}) }

@Preview(name = "长商户 / 大金额 / 大字体", widthDp = 320, heightDp = 640, fontScale = 1.3f)
@Composable fun LargeBillPreview() = PreviewFrame { BillsContent(listOf(previewEntries[0].copy(amount = 99999999999, merchant = "很长的商户名称用于检查窄屏文字省略")), previewCategories, true, null, {}, {}, {}) }

@Preview(name = "热力图 Light", widthDp = 360, heightDp = 780)
@Preview(name = "热力图 Dark", widthDp = 412, heightDp = 860, uiMode = Configuration.UI_MODE_NIGHT_YES)
@Composable fun HeatmapPreview() = PreviewFrame {
    val today = LocalDate.now()
    val entries = (0..29).filter { it % 3 != 0 }.map { day -> previewEntries[0].copy(id = day.toLong() + 1,
        timestamp = today.minusDays(day.toLong()).atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli(), amount = if(day == 28) 500000 else (day + 1) * 245L) }
    val report = ExpenseStatistics.report(entries, today.minusDays(29), today, StatisticObject.TOTAL, "")
    Column(Modifier.padding(AppDimensions.Page), verticalArrangement = Arrangement.spacedBy(AppDimensions.Medium)) { Heatmap(report, MaterialTheme.colorScheme.primary, {}) }
}
