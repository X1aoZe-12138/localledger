package cn.localledger.ui.design

import androidx.compose.ui.graphics.*
import androidx.compose.ui.graphics.vector.*
import androidx.compose.ui.unit.dp

// Small original line vectors. No icon font or large icon collection in the APK.
private fun lineIcon(name: String, path: String): ImageVector = ImageVector.Builder(name, 24.dp, 24.dp, 24f, 24f)
    .addPath(PathParser().parsePathString(path).toNodes(), stroke = SolidColor(Color.Black), strokeLineWidth = 1.8f,
        strokeLineCap = StrokeCap.Round, strokeLineJoin = StrokeJoin.Round).build()
object AppIcons {
    val Ledger = lineIcon("Ledger", "M6,3 L19,3 L19,21 L6,21 Q4,21 4,19 L4,5 Q4,3 6,3 M8,3 L8,21 M11,8 L16,8 M11,12 L16,12")
    val Chart = lineIcon("Chart", "M4,4 L4,20 L21,20 M8,16 L8,12 M13,16 L13,8 M18,16 L18,5")
    val Wallet = lineIcon("Wallet", "M4,7 L4,5 Q4,3 6,3 L18,3 L18,7 M4,7 L20,7 L20,20 L4,20 Z M20,11 L14,11 L14,16 L20,16 M17,13.5 L17.1,13.5")
    val Person = lineIcon("Person", "M16,7 A4,4 0,1 1,8,7 A4,4 0,1 1,16,7 M4,21 L4,19 Q4,14 12,14 Q20,14 20,19 L20,21")
    val Back = lineIcon("Back", "M19,12 L5,12 M11,6 L5,12 L11,18")
    val Add = lineIcon("Add", "M12,5 L12,19 M5,12 L19,12")
    val Filter = lineIcon("Filter", "M4,6 L20,6 M7,12 L17,12 M10,18 L14,18")
    val Chevron = lineIcon("Chevron", "M9,6 L15,12 L9,18")
    val Bank = lineIcon("Bank", "M3,8 L12,3 L21,8 Z M5,11 L5,18 M10,11 L10,18 M15,11 L15,18 M20,11 L20,18 M3,21 L21,21")
}
