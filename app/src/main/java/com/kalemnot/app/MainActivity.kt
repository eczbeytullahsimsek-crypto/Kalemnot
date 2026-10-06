package com.kalemnot.app

import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.compose.animation.*
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.lifecycle.viewmodel.compose.viewModel

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        Pal.dark = getSharedPreferences("p", MODE_PRIVATE).getBoolean("dark2", false)
        setContent { AppTheme { App() } }
    }
}

enum class Dest { Library, Canvas, Templates }

@Composable
fun App(vm: NotesVm = viewModel()) {
    val ctx = LocalContext.current
    var dest by remember { mutableStateOf(Dest.Library) }
    var focus by remember { mutableStateOf(false) }
    var settings by remember { mutableStateOf(false) }
    val s = vm.session

    fun toLibrary() { vm.flushNow(); dest = Dest.Library; focus = false }
    fun openNote(id: Long) = vm.open(id) { dest = Dest.Canvas }
    BackHandler(dest != Dest.Library) { if (focus) focus = false else toLibrary() }

    Row(Modifier.fillMaxSize().background(Pal.bg)) {
        AnimatedVisibility(
            !(dest == Dest.Canvas && focus),
            enter = slideInHorizontally { -it } + fadeIn(), exit = slideOutHorizontally { -it } + fadeOut()
        ) {
            Rail(dest, onSettings = { settings = true }) { target ->
                when (target) {
                    Dest.Library -> toLibrary()
                    Dest.Canvas -> if (s != null) dest = Dest.Canvas else vm.newNote(vm.defaultTemplate) { openNote(it) }
                    Dest.Templates -> if (s != null) dest = Dest.Templates else Toast.makeText(ctx, "Önce bir defter aç", Toast.LENGTH_SHORT).show()
                }
            }
        }
        AnimatedContent(dest, Modifier.weight(1f), label = "screen", transitionSpec = { fadeIn(tween(240)) togetherWith fadeOut(tween(140)) }) { d ->
            when {
                d == Dest.Canvas && s != null -> CanvasScreen(vm, s, focus, { focus = it }, { toLibrary() }, { settings = true })
                d == Dest.Templates && s != null -> TemplatesScreen(vm, s) { dest = Dest.Canvas }
                else -> LibraryScreen(vm) { openNote(it) }
            }
        }
    }
    if (settings) SettingsDialog(vm) { settings = false }
}

@Composable
private fun Rail(selected: Dest, onSettings: () -> Unit, onNav: (Dest) -> Unit) {
    Column(
        Modifier.width(80.dp).fillMaxHeight().background(Pal.low).padding(vertical = 20.dp),
        horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.SpaceBetween
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(14.dp)) {
            Box(Modifier.size(40.dp).clip(RoundedCornerShape(12.dp)).background(Pal.primaryC), contentAlignment = Alignment.Center) {
                Icon(Icons.Default.Edit, null, tint = Pal.bg, modifier = Modifier.size(20.dp))
            }
            Box(Modifier.width(32.dp).height(1.dp).background(Pal.highest))
            RailItem(Icons.Default.Edit, "Tuval", selected == Dest.Canvas) { onNav(Dest.Canvas) }
            RailItem(Icons.Default.AutoStories, "Kitaplık", selected == Dest.Library) { onNav(Dest.Library) }
            RailItem(Icons.Default.Grid4x4, "Şablon", selected == Dest.Templates) { onNav(Dest.Templates) }
        }
        Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(10.dp)) {
            IconBtn(Icons.Default.Tune, "Ayarlar", Pal.textDim, 20, 48, onSettings)
            Box(Modifier.size(32.dp).background(Pal.primaryC, CircleShape), contentAlignment = Alignment.Center) {
                Icon(Icons.Default.Person, null, tint = Pal.bg, modifier = Modifier.size(18.dp))
            }
        }
    }
}

@Composable
private fun RailItem(icon: ImageVector, label: String, selected: Boolean, onClick: () -> Unit) {
    Column(
        Modifier.size(52.dp).clip(RoundedCornerShape(12.dp)).background(if (selected) Pal.high else Color.Transparent).clickable(onClick = onClick),
        horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center
    ) {
        Icon(icon, label, tint = if (selected) Pal.text else Pal.textDim, modifier = Modifier.size(22.dp))
        Lbl(label, if (selected) Pal.text else Pal.textDim, 9)
    }
}

@Composable
private fun SettingsDialog(vm: NotesVm, onDismiss: () -> Unit) {
    val ctx = LocalContext.current
    val s = vm.session
    Dialog(onDismiss) {
        Column(Modifier.widthIn(max = 520.dp).clip(RoundedCornerShape(20.dp)).background(Pal.lowest).padding(24.dp), verticalArrangement = Arrangement.spacedBy(18.dp)) {
            Txt("Tuval & Uygulama Ayarları", 18, Pal.text, FontWeight.SemiBold)
            Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).background(Pal.low).padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) { Txt("Koyu tema", 14, Pal.text, FontWeight.Medium); Lbl("Göz yormayan koyu arayüz", Pal.outline, 10) }
                Switch(Pal.dark, { vm.retheme(ctx) })
            }
            Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).background(Pal.low).padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) { Txt("Avuç içi koruması", 14, Pal.text, FontWeight.Medium); Lbl("Parmak kaydırır, yalnızca kalem çizer", Pal.outline, 10) }
                Icon(Icons.Default.FrontHand, null, tint = Pal.green, modifier = Modifier.size(20.dp))
            }
            if (s != null) {
                Lbl("Defter kategorisi", Pal.text, 11)
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    CATEGORIES.forEach { c -> Chip(c, s.meta.category == c, dot = catColor(c)) { vm.updateMeta { it.copy(category = c) } } }
                }
            }
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                Row(Modifier.clip(RoundedCornerShape(12.dp)).background(Pal.primary).clickable(onClick = onDismiss).padding(horizontal = 22.dp, vertical = 11.dp)) {
                    Txt("Tamam", 14, Pal.onPrimary, FontWeight.Medium)
                }
            }
        }
    }
}
