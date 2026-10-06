package com.kalemnot.app

import android.content.Context
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import java.util.Locale

/** Tasarımdaki (Tailwind) renk jetonları — açık ve koyu varyant. */
object Pal {
    var dark by mutableStateOf(false)
    private fun c(l: Long, d: Long) = Color(if (dark) d else l)
    val bg get() = c(0xFFFBF9F6, 0xFF121110)
    val low get() = c(0xFFF5F3F0, 0xFF1A1918)
    val mid get() = c(0xFFEFEEEB, 0xFF222120)
    val high get() = c(0xFFEAE8E5, 0xFF2B2A28)
    val highest get() = c(0xFFE4E2DF, 0xFF353432)
    val lowest get() = c(0xFFFFFFFF, 0xFF1C1B1A)
    val text get() = c(0xFF1B1C1A, 0xFFE8E5E1)
    val textDim get() = c(0xFF4D4541, 0xFFB8B2AC)
    val outline get() = c(0xFF7E7570, 0xFF8C847E)
    val outlineV get() = c(0xFFCFC4BE, 0xFF4A4642)
    val primary get() = c(0xFF171615, 0xFFF1EDE8)
    val onPrimary get() = c(0xFFFFFFFF, 0xFF171615)
    val primaryC get() = c(0xFF2C2A29, 0xFFD9D4CE)
    val blue get() = c(0xFF0051D5, 0xFF7DA2FF)
    val blueC = Color(0xFF316BF3)
    val orange = Color(0xFFF76119)
    val errC get() = c(0xFFFFDAD6, 0xFF5A1A17)
    val err get() = c(0xFFBA1A1A, 0xFFFFB4AB)
    val green = Color(0xFF16A34A)
    fun paper(darkPage: Boolean) = if (darkPage || dark) Color(0xFF1A1918) else Color.White
    fun inkArgb(darkPage: Boolean) = if (darkPage) 0xFFF1EDE8.toInt() else 0xFF171615.toInt()
}

fun toggleTheme(ctx: Context) {
    Pal.dark = !Pal.dark
    ctx.getSharedPreferences("p", Context.MODE_PRIVATE).edit().putBoolean("dark2", Pal.dark).apply()
}

val CATEGORIES = listOf("Genel", "Proje", "Günlük", "Ders", "Fikir")
fun catColor(c: String) = when (c) {
    "Proje" -> Color(0xFFF76119); "Günlük" -> Color(0xFF316BF3); "Ders" -> Color(0xFF16A34A)
    "Fikir" -> Color(0xFFB388FF); else -> Color(0xFF8B929C)
}

fun ago(ts: Long): String {
    val m = (System.currentTimeMillis() - ts) / 60000
    return when {
        m < 1 -> "az önce"; m < 60 -> "$m dk önce"; m < 60 * 24 -> "${m / 60} saat önce"
        m < 60 * 48 -> "Dün"; else -> "${m / 1440} gün önce"
    }
}

@Composable
fun AppTheme(content: @Composable () -> Unit) {
    val d = Pal.dark
    val scheme = if (d) darkColorScheme(primary = Pal.primary, background = Pal.bg, surface = Pal.low, onSurface = Pal.text, onBackground = Pal.text)
    else lightColorScheme(primary = Pal.primary, background = Pal.bg, surface = Pal.low, onSurface = Pal.text, onBackground = Pal.text)
    val t = Typography()
    MaterialTheme(
        colorScheme = scheme,
        typography = t.copy(
            bodyLarge = t.bodyLarge.copy(fontFamily = Sans), bodyMedium = t.bodyMedium.copy(fontFamily = Sans),
            titleMedium = t.titleMedium.copy(fontFamily = Sans), labelLarge = t.labelLarge.copy(fontFamily = Mono)
        ), content = content
    )
}

/** JetBrains Mono, büyük harfli küçük etiket (tasarımdaki label-sm). */
@Composable
fun Lbl(text: String, color: Color = Pal.outline, size: Int = 10, modifier: Modifier = Modifier, weight: FontWeight = FontWeight.Medium) =
    Text(text.uppercase(Locale("tr")), color = color, fontFamily = Mono, fontSize = size.sp, fontWeight = weight, letterSpacing = 0.8.sp, maxLines = 1, modifier = modifier)

@Composable
fun Txt(text: String, size: Int = 14, color: Color = Pal.text, weight: FontWeight = FontWeight.Normal, modifier: Modifier = Modifier, maxLines: Int = Int.MAX_VALUE) =
    Text(text, color = color, fontFamily = Sans, fontSize = size.sp, fontWeight = weight, maxLines = maxLines, modifier = modifier, style = TextStyle(lineHeight = (size * 1.4f).sp))

@Composable
fun Chip(label: String, selected: Boolean = false, icon: ImageVector? = null, dot: Color? = null, onClick: () -> Unit) {
    val shape = RoundedCornerShape(12.dp)
    Row(
        Modifier.clip(shape).background(if (selected) Pal.primary else Color.Transparent).clickable(onClick = onClick).padding(horizontal = 14.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        if (dot != null) Box(Modifier.size(7.dp).background(dot, CircleShape))
        if (icon != null) Icon(icon, null, tint = if (selected) Pal.onPrimary else Pal.orange, modifier = Modifier.size(15.dp))
        Text(label, color = if (selected) Pal.onPrimary else Pal.textDim, fontFamily = Mono, fontSize = 12.sp, fontWeight = FontWeight.Medium, maxLines = 1)
    }
}

@Composable
fun IconBtn(icon: ImageVector, desc: String, tint: Color = Pal.textDim, size: Int = 18, box: Int = 32, onClick: () -> Unit) =
    Box(Modifier.size(box.dp).clip(CircleShape).clickable(onClick = onClick), contentAlignment = Alignment.Center) {
        Icon(icon, desc, tint = tint, modifier = Modifier.size(size.dp))
    }

@Composable
fun Btn(label: String, icon: ImageVector? = null, filled: Boolean = false, onClick: () -> Unit) {
    val shape = RoundedCornerShape(12.dp)
    Row(
        Modifier.clip(shape).background(if (filled) Pal.primaryC else Pal.high).clickable(onClick = onClick).padding(horizontal = 18.dp, vertical = 11.dp),
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        if (icon != null) Icon(icon, null, tint = if (filled) Pal.bg else Pal.textDim, modifier = Modifier.size(19.dp))
        Txt(label, 14, if (filled) Pal.bg else Pal.text, FontWeight.Medium)
    }
}

fun Modifier.card(radius: Int = 16, bg: Color = Pal.lowest) = this.clip(RoundedCornerShape(radius.dp)).background(bg)
fun Modifier.hair(radius: Int = 16) = this.border(1.dp, Pal.outlineV.copy(alpha = .35f), RoundedCornerShape(radius.dp))
