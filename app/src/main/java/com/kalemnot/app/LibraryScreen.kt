package com.kalemnot.app

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@Composable
fun LibraryScreen(vm: NotesVm, onOpen: (Long) -> Unit) {
    val folders by vm.folders.collectAsState(emptyList())
    val counts by vm.counts.collectAsState(emptyList())
    val notes by vm.notes.collectAsState(emptyList())
    val recent by vm.recent.collectAsState(emptyList())
    val sel by vm.folder.collectAsState()
    val chip by vm.chip.collectAsState()
    val q by vm.query.collectAsState()
    var folderMenu by remember { mutableStateOf(false) }
    var dialog by remember { mutableStateOf(false) }
    var fname by remember { mutableStateOf("") }
    val total = counts.sumOf { it.cnt }
    val full: LazyGridItemSpanScope.() -> GridItemSpan = { GridItemSpan(maxLineSpan) }

    LazyVerticalGrid(
        GridCells.Adaptive(250.dp), Modifier.fillMaxSize().background(Pal.bg),
        contentPadding = PaddingValues(horizontal = 40.dp, vertical = 32.dp),
        horizontalArrangement = Arrangement.spacedBy(20.dp), verticalArrangement = Arrangement.spacedBy(20.dp)
    ) {
        // Başlık
        item(span = full) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        Box(Modifier.size(8.dp).background(Pal.blueC, CircleShape))
                        Lbl("Arşiv ve çalışma alanı", Pal.textDim, 12); Lbl("/", Pal.outlineV, 12); Lbl("$total defter", Pal.textDim, 12)
                    }
                    Spacer(Modifier.height(4.dp))
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        Txt("Kütüphane & Notlar", 26, Pal.text, FontWeight.SemiBold)
                        Lbl("Otomatik kayıt", Pal.textDim, 10, Modifier.clip(RoundedCornerShape(4.dp)).background(Pal.high).padding(horizontal = 6.dp, vertical = 2.dp))
                    }
                }
                Btn("Hızlı Not Aç", Icons.Default.NoteAlt) { vm.newNote("plain") { id -> onOpen(id) } }
                Spacer(Modifier.width(10.dp))
                Btn("Yeni Defter Oluştur", Icons.Default.Add, filled = true) { vm.newNote(vm.defaultTemplate) { id -> onOpen(id) } }
            }
        }
        // Arama + filtre
        item(span = full) {
            Row(
                Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).background(Pal.low).padding(6.dp),
                verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Row(
                    Modifier.weight(1f).clip(RoundedCornerShape(12.dp)).background(Pal.lowest).padding(horizontal = 14.dp, vertical = 11.dp),
                    verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Icon(Icons.Default.Search, null, tint = Pal.textDim, modifier = Modifier.size(20.dp))
                    Box(Modifier.weight(1f)) {
                        if (q.isEmpty()) Txt("Defterlerde ara...", 14, Pal.outline)
                        BasicTextField(q, { vm.query.value = it }, singleLine = true, cursorBrush = SolidColor(Pal.primary),
                            textStyle = TextStyle(fontFamily = Sans, fontSize = 14.sp, color = Pal.text), modifier = Modifier.fillMaxWidth())
                    }
                }
                Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(2.dp), verticalAlignment = Alignment.CenterVertically) {
                    Chip("Tümü ($total)", chip == "Tümü") { vm.chip.value = "Tümü" }
                    Chip("Son Açılanlar", chip == "Son Açılanlar") { vm.chip.value = "Son Açılanlar" }
                    Chip("Favoriler", chip == "Favoriler", icon = Icons.Default.Star) { vm.chip.value = "Favoriler" }
                    CATEGORIES.forEach { c -> Chip(c, chip == c, dot = catColor(c)) { vm.chip.value = c } }
                }
                Box {
                    Chip(folders.firstOrNull { it.id == sel }?.name ?: "Tüm klasörler", false, Icons.Default.Folder) { folderMenu = true }
                    DropdownMenu(folderMenu, { folderMenu = false }) {
                        DropdownMenuItem({ Text("Tüm klasörler") }, { vm.folder.value = null; folderMenu = false })
                        folders.forEach { f ->
                            DropdownMenuItem({ Text("${f.name}  (${counts.firstOrNull { it.folderId == f.id }?.cnt ?: 0})") },
                                { vm.folder.value = f.id; folderMenu = false },
                                trailingIcon = { Icon(Icons.Default.Close, "Sil", Modifier.size(16.dp).clickable { vm.deleteFolder(f); folderMenu = false }) })
                        }
                        DropdownMenuItem({ Text("+ Yeni klasör") }, { folderMenu = false; dialog = true })
                    }
                }
            }
        }
        // Son karalamalar
        if (recent.isNotEmpty()) {
            item(span = full) {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        Icon(Icons.Default.HistoryEdu, null, tint = Pal.textDim, modifier = Modifier.size(18.dp))
                        Txt("Son Karalamalar & Çizim Parçaları", 16, Pal.text, FontWeight.Medium)
                        Spacer(Modifier.weight(1f)); Lbl("Otomatik kaydedildi", Pal.textDim)
                    }
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                        for (i in 0 until 4) {
                            val n = recent.getOrNull(i)
                            if (n == null) Spacer(Modifier.weight(1f)) else {
                                val pg = remember(n.strokes, Pal.dark) { runCatching { n.strokes.decodePages().first() }.getOrDefault(Page()) }
                                Box(Modifier.weight(1f).height(150.dp).shadow(1.dp, RoundedCornerShape(12.dp)).clip(RoundedCornerShape(12.dp)).background(Pal.lowest).clickable { onOpen(n.id) }) {
                                    Thumb(pg, Modifier.fillMaxSize().alpha(.5f))
                                    Row(Modifier.fillMaxWidth().padding(12.dp), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                                        Lbl("#" + n.id.toString().padStart(3, '0'), Pal.textDim)
                                        Box(Modifier.size(8.dp).background(catColor(n.category), CircleShape))
                                    }
                                    Column(Modifier.align(Alignment.BottomStart).padding(12.dp)) {
                                        Txt(n.title, 14, Pal.text, FontWeight.Medium, maxLines = 1)
                                        Lbl(ago(n.updatedAt), Pal.textDim)
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
        // Koleksiyon başlığı
        item(span = full) {
            Row(Modifier.fillMaxWidth().padding(top = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                Txt("Koleksiyon Defterleri", 20, Pal.text, FontWeight.SemiBold)
                Spacer(Modifier.weight(1f)); Lbl("Sırala:", Pal.textDim, 12); Spacer(Modifier.width(6.dp)); Lbl("Son güncellenen", Pal.text, 12)
            }
        }
        if (notes.isEmpty()) item(span = full) {
            Box(Modifier.fillMaxWidth().padding(48.dp), contentAlignment = Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(Icons.Default.AutoStories, null, tint = Pal.outlineV, modifier = Modifier.size(56.dp))
                    Spacer(Modifier.height(10.dp)); Lbl("Boş çalışma alanı", Pal.textDim, 11)
                    Spacer(Modifier.height(4.dp)); Txt("Yeni bir defter ile başla.", 13, Pal.outline)
                }
            }
        }
        items(notes, key = { it.id }) { n ->
            val pg = remember(n.strokes, Pal.dark) { runCatching { n.strokes.decodePages() }.getOrDefault(listOf(Page())) }
            var menu by remember { mutableStateOf(false) }
            Column(
                Modifier.animateItem().shadow(if (Pal.dark) 0.dp else 3.dp, RoundedCornerShape(16.dp)).clip(RoundedCornerShape(16.dp))
                    .background(Pal.lowest).clickable { onOpen(n.id) }.padding(14.dp)
            ) {
                Box(Modifier.fillMaxWidth().height(190.dp).clip(RoundedCornerShape(12.dp)).background(Pal.high)) {
                    Thumb(pg.first(), Modifier.fillMaxSize())
                    Box(Modifier.align(Alignment.CenterStart).fillMaxHeight().width(12.dp).background(catColor(n.category).copy(alpha = .35f)))
                    Row(Modifier.fillMaxWidth().padding(start = 22.dp, top = 10.dp, end = 10.dp), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                        Lbl(tplName(pg.first().template), Pal.text, 10, Modifier.clip(RoundedCornerShape(4.dp)).background(Pal.lowest.copy(alpha = .85f)).padding(horizontal = 7.dp, vertical = 2.dp))
                        Box {
                            Box(Modifier.size(28.dp).clip(CircleShape).background(Pal.lowest.copy(alpha = .85f)).clickable { menu = true }, contentAlignment = Alignment.Center) {
                                Icon(Icons.Default.MoreVert, "Menü", tint = Pal.textDim, modifier = Modifier.size(16.dp))
                            }
                            DropdownMenu(menu, { menu = false }) {
                                DropdownMenuItem({ Text(if (n.favorite) "Favoriden çıkar" else "Favorilere ekle") }, { vm.toggleFav(n); menu = false })
                                DropdownMenuItem({ Text("Klasörsüz") }, { vm.move(n, null); menu = false })
                                folders.forEach { f -> DropdownMenuItem({ Text("→ ${f.name}") }, { vm.move(n, f.id); menu = false }) }
                                DropdownMenuItem({ Text("Sil", color = Pal.err) }, { vm.deleteNote(n); menu = false })
                            }
                        }
                    }
                    Row(Modifier.align(Alignment.BottomStart).padding(start = 22.dp, bottom = 10.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(5.dp)) {
                        Icon(Icons.Default.AutoStories, null, tint = Pal.textDim, modifier = Modifier.size(14.dp))
                        Lbl("${pg.size} sayfa", Pal.text, 10, Modifier.clip(RoundedCornerShape(4.dp)).background(Pal.lowest.copy(alpha = .85f)).padding(horizontal = 6.dp, vertical = 2.dp))
                    }
                }
                Spacer(Modifier.height(12.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Txt(n.title, 16, Pal.text, FontWeight.SemiBold, Modifier.weight(1f), maxLines = 1)
                    Icon(if (n.favorite) Icons.Default.Star else Icons.Default.StarBorder, "Favori", tint = if (n.favorite) Pal.orange else Pal.outline,
                        modifier = Modifier.size(20.dp).clickable { vm.toggleFav(n) })
                }
                Spacer(Modifier.height(8.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    Lbl("#" + n.category.lowercase(), Pal.textDim, 10, Modifier.clip(RoundedCornerShape(6.dp)).background(Pal.mid).padding(horizontal = 8.dp, vertical = 3.dp))
                    folders.firstOrNull { it.id == n.folderId }?.let { f ->
                        Lbl("#" + f.name.lowercase(), Pal.textDim, 10, Modifier.clip(RoundedCornerShape(6.dp)).background(Pal.mid).padding(horizontal = 8.dp, vertical = 3.dp))
                    }
                }
                Spacer(Modifier.height(10.dp))
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                    Lbl("${ago(n.updatedAt)} düzenlendi", Pal.textDim)
                    Box(Modifier.size(6.dp).background(if (n.favorite) Pal.blueC else Pal.outline, CircleShape))
                }
            }
        }
        // Alt bilgi kartı
        item(span = full) {
            Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).background(Pal.mid).padding(20.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                Box(Modifier.size(48.dp).clip(RoundedCornerShape(12.dp)).background(Pal.lowest), contentAlignment = Alignment.Center) {
                    Icon(Icons.Default.CloudSync, null, tint = Pal.primary, modifier = Modifier.size(24.dp))
                }
                Column(Modifier.weight(1f)) {
                    Txt("Vektörel Kalem Motoru & Yerel Arşiv", 16, Pal.text, FontWeight.Medium)
                    Txt("Çizimler Google Ink motoruyla vektörel olarak saklanır; PDF ve PNG olarak dışa aktarabilirsin.", 13, Pal.textDim)
                }
                Lbl("$total defter", Pal.textDim, 11)
            }
        }
    }
    if (dialog) AlertDialog(
        onDismissRequest = { dialog = false }, containerColor = Pal.lowest, title = { Text("Yeni klasör") },
        text = { OutlinedTextField(fname, { fname = it }, singleLine = true, label = { Text("Ad") }) },
        confirmButton = { TextButton({ if (fname.isNotBlank()) vm.addFolder(fname.trim()); fname = ""; dialog = false }) { Text("Ekle") } },
        dismissButton = { TextButton({ dialog = false }) { Text("İptal") } })
}
