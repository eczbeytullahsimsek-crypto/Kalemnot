package com.kalemnot.app

import android.app.Application
import android.content.Context
import androidx.compose.runtime.*
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch

class Session(note: NoteEntity) {
    var meta by mutableStateOf(note)
    val pages = mutableStateListOf<Page>().also { it.addAll(note.strokes.decodePages()) }
    var idx by mutableIntStateOf(0)
}

@OptIn(ExperimentalCoroutinesApi::class)
class NotesVm(app: Application) : AndroidViewModel(app) {
    val dao = Db.create(app).dao()
    val folder = MutableStateFlow<Long?>(null)
    val chip = MutableStateFlow("Tümü")
    val query = MutableStateFlow("")
    val folders = dao.folders()
    val counts = dao.counts()
    val recent = dao.notes(null).map { it.take(4) }
    val notes = combine(folder.flatMapLatest { dao.notes(it) }, chip, query) { l, c, q ->
        var r = when (c) {
            "Tümü" -> l; "Son Açılanlar" -> l.take(6); "Favoriler" -> l.filter { it.favorite }
            else -> l.filter { it.category == c }
        }
        if (q.isNotBlank()) r = r.filter { it.title.contains(q, ignoreCase = true) }
        r
    }

    var session by mutableStateOf<Session?>(null)
    var defaultTemplate by mutableStateOf("dot")

    // ---------- defterler ----------
    fun newNote(tpl: String, cb: (Long) -> Unit) = viewModelScope.launch {
        cb(dao.upsert(NoteEntity(folderId = folder.value, strokes = "T;$tpl")))
    }
    fun open(id: Long, cb: () -> Unit) = viewModelScope.launch {
        flush()
        val n = dao.note(id) ?: return@launch
        session = Session(n); cb()
    }
    fun addFolder(name: String) = viewModelScope.launch { dao.insertFolder(FolderEntity(name = name)) }
    fun deleteFolder(f: FolderEntity) = viewModelScope.launch {
        dao.orphan(f.id); dao.deleteFolder(f); if (folder.value == f.id) folder.value = null
    }
    fun deleteNote(n: NoteEntity) = viewModelScope.launch {
        if (session?.meta?.id == n.id) session = null
        dao.delete(n)
    }
    fun toggleFav(n: NoteEntity) = viewModelScope.launch { dao.upsert(n.copy(favorite = !n.favorite)) }
    fun move(n: NoteEntity, folderId: Long?) = viewModelScope.launch { dao.upsert(n.copy(folderId = folderId)) }

    // ---------- oturum / kayıt ----------
    private var saveJob: Job? = null
    fun scheduleSave() { saveJob?.cancel(); saveJob = viewModelScope.launch { delay(600); flush() } }
    private suspend fun flush() {
        val s = session ?: return
        dao.upsert(s.meta.copy(strokes = s.pages.toList().encodePages(), updatedAt = System.currentTimeMillis()))
    }
    fun flushNow() { saveJob?.cancel(); viewModelScope.launch { flush() } }
    fun updateMeta(f: (NoteEntity) -> NoteEntity) { session?.let { it.meta = f(it.meta); scheduleSave() } }

    // ---------- sayfa işlemleri ----------
    fun addPage(tpl: String) { val s = session ?: return; s.pages.add(Page(template = tpl)); s.idx = s.pages.lastIndex; scheduleSave() }
    fun duplicatePage(i: Int) { val s = session ?: return; s.pages.add(i + 1, s.pages[i]); scheduleSave() }
    fun deletePage(i: Int) {
        val s = session ?: return
        if (s.pages.size > 1) { s.pages.removeAt(i); s.idx = minOf(s.idx, s.pages.lastIndex); scheduleSave() }
    }
    /** Şablon değişince mürekkep rengi yeni zemine göre yeniden kurulur. */
    fun setTemplate(i: Int, tpl: String) { val s = session ?: return; s.pages[i] = s.pages[i].toText().decodePage(tpl); scheduleSave() }

    fun retheme(ctx: Context) {
        val s = session
        val texts = s?.pages?.map { it.toText() }
        toggleTheme(ctx)
        if (s != null && texts != null) texts.forEachIndexed { i, t -> s.pages[i] = t.decodePage() }
    }
}
