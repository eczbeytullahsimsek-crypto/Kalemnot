package com.kalemnot.app

import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp

@Composable
fun TemplatesScreen(vm: NotesVm, s: Session, onOpenPage: () -> Unit) {
    val ctx = LocalContext.current
    Column(Modifier.fillMaxSize().background(Pal.bg).padding(horizontal = 40.dp, vertical = 28.dp), verticalArrangement = Arrangement.spacedBy(20.dp)) {
        // Üst panel
        Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).background(Pal.low).padding(20.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
                    Lbl("Folio • ${s.meta.title}", Pal.outline, 10, Modifier.clip(RoundedCornerShape(4.dp)).background(Pal.high).padding(horizontal = 8.dp, vertical = 2.dp))
                    Box(Modifier.size(8.dp).background(Pal.blueC, CircleShape)); Lbl("Kaydedildi", Pal.blue, 10)
                }
                Row(verticalAlignment = Alignment.Bottom, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    Txt("Sayfa Mizanpajı & Şablon Kütüphanesi", 26, Pal.text, FontWeight.SemiBold)
                    Lbl("${s.pages.size} aktif yaprak", Pal.textDim, 12)
                }
            }
            Btn("PDF Dışa Aktar", Icons.Default.PictureAsPdf) {
                vm.flushNow(); Toast.makeText(ctx, exportPages(ctx, s.meta.title, s.pages.toList(), true), Toast.LENGTH_LONG).show()
            }
            Spacer(Modifier.width(10.dp))
            Btn("Yeni Yaprak Ekle", Icons.Default.NoteAdd, filled = true) { vm.addPage(vm.defaultTemplate) }
        }
        Row(Modifier.weight(1f).fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(24.dp)) {
            // Sol: şablon kataloğu
            Column(Modifier.weight(4f).fillMaxHeight().verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Icon(Icons.Default.Texture, null, tint = Pal.textDim, modifier = Modifier.size(20.dp))
                    Txt("Kağıt Dokuları & Şablonlar", 16, Pal.text, FontWeight.SemiBold)
                    Spacer(Modifier.weight(1f)); Lbl("${TEMPLATES.size} standart format", Pal.outline)
                }
                TEMPLATES.forEach { t ->
                    val sel = vm.defaultTemplate == t.id
                    Row(
                        Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).background(if (sel) Pal.mid else Pal.low)
                            .border(if (sel) 1.dp else 0.dp, Pal.primary.copy(alpha = .2f), RoundedCornerShape(12.dp))
                            .clickable { vm.defaultTemplate = t.id }.padding(14.dp),
                        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp)
                    ) {
                        TplPreview(t.id, Modifier.size(64.dp, 80.dp).clip(RoundedCornerShape(6.dp)))
                        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Txt(t.name, 15, Pal.text, if (sel) FontWeight.SemiBold else FontWeight.Medium, Modifier.weight(1f), maxLines = 1)
                                if (sel) Icon(Icons.Default.CheckCircle, null, tint = Pal.primary, modifier = Modifier.size(18.dp))
                            }
                            Txt(t.desc, 12, Pal.textDim, maxLines = 2)
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                                Lbl(t.tag1, if (sel) Pal.primary else Pal.outline, 10, Modifier.clip(RoundedCornerShape(4.dp)).background(Pal.highest).padding(horizontal = 6.dp, vertical = 2.dp))
                                Lbl(t.tag2, Pal.outline, 10)
                            }
                            if (sel) Lbl("Bu sayfaya uygula", Pal.blue, 10, Modifier.clickable { vm.setTemplate(s.idx, t.id) }.padding(top = 2.dp))
                        }
                    }
                }
            }
            // Sağ: sayfa ızgarası
            Column(Modifier.weight(8f).fillMaxHeight(), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).background(Pal.low).padding(10.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Icon(Icons.Default.GridView, null, tint = Pal.text, modifier = Modifier.size(18.dp))
                    Txt("Tüm sayfalar (${s.pages.size})", 14, Pal.text, FontWeight.Medium)
                    Spacer(Modifier.weight(1f)); Lbl("Dokun: aç • Şablon adına dokun: değiştir", Pal.outline, 10)
                }
                LazyVerticalGrid(GridCells.Adaptive(160.dp), horizontalArrangement = Arrangement.spacedBy(14.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
                    itemsIndexed(s.pages.toList()) { i, p ->
                        var menu by remember { mutableStateOf(false) }
                        val cur = i == s.idx
                        Column(
                            Modifier.shadow(if (Pal.dark) 0.dp else 3.dp, RoundedCornerShape(12.dp)).clip(RoundedCornerShape(12.dp)).background(Pal.lowest)
                                .border(if (cur) 2.dp else 0.dp, Pal.primary, RoundedCornerShape(12.dp)).padding(8.dp)
                        ) {
                            Box(Modifier.fillMaxWidth().aspectRatio(.75f).clip(RoundedCornerShape(8.dp)).clickable { s.idx = i; onOpenPage() }) {
                                Thumb(p, Modifier.fillMaxSize())
                                if (cur) Lbl("Açık", Pal.onPrimary, 10, Modifier.align(Alignment.TopEnd).clip(RoundedCornerShape(bottomStart = 8.dp)).background(Pal.primary).padding(horizontal = 8.dp, vertical = 2.dp))
                                Row(Modifier.align(Alignment.BottomStart).fillMaxWidth().background(Pal.paper(p.dark).copy(alpha = .85f)).padding(horizontal = 8.dp, vertical = 5.dp), horizontalArrangement = Arrangement.SpaceBetween) {
                                    Lbl("%02d".format(i + 1), if (cur) Pal.primary else Pal.outline, 10, weight = if (cur) FontWeight.Bold else FontWeight.Medium)
                                    Box {
                                        Lbl(tplName(p.template), Pal.outline, 10, Modifier.clickable { menu = true })
                                        DropdownMenu(menu, { menu = false }) {
                                            TEMPLATES.forEach { t -> DropdownMenuItem({ Text(t.name) }, { vm.setTemplate(i, t.id); menu = false }) }
                                        }
                                    }
                                }
                            }
                            Row(Modifier.fillMaxWidth().padding(top = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                                Lbl("Sayfa %02d".format(i + 1), if (cur) Pal.primary else Pal.text, 11, Modifier.weight(1f), FontWeight.SemiBold)
                                IconBtn(Icons.Default.ContentCopy, "Çoğalt", Pal.textDim, 16, 28) { vm.duplicatePage(i) }
                                IconBtn(Icons.Default.Delete, "Sil", Pal.textDim, 16, 28) { vm.deletePage(i) }
                            }
                        }
                    }
                    item {
                        Column(
                            Modifier.aspectRatio(.62f).clip(RoundedCornerShape(12.dp)).background(Pal.low).clickable { vm.addPage(vm.defaultTemplate) },
                            horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center
                        ) {
                            Box(Modifier.size(48.dp).background(Pal.high, CircleShape), contentAlignment = Alignment.Center) { Icon(Icons.Default.Add, null, tint = Pal.textDim, modifier = Modifier.size(24.dp)) }
                            Spacer(Modifier.height(8.dp)); Txt("Yaprak ekle", 14, Pal.text, FontWeight.Medium); Lbl("Seçili: ${tplName(vm.defaultTemplate)}", Pal.outline, 10)
                        }
                    }
                }
            }
        }
    }
}
