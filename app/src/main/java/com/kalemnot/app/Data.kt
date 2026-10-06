package com.kalemnot.app

import android.content.Context
import android.util.Base64
import androidx.ink.brush.Brush
import androidx.ink.brush.StockBrushes
import androidx.ink.storage.decode
import androidx.ink.storage.encode
import androidx.ink.strokes.Stroke
import androidx.ink.strokes.StrokeInputBatch
import androidx.room.*
import kotlinx.coroutines.flow.Flow
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.net.URLDecoder
import java.net.URLEncoder
import kotlin.math.abs

@Entity data class FolderEntity(@PrimaryKey(autoGenerate = true) val id: Long = 0, val name: String)

@Entity data class NoteEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val folderId: Long? = null,
    val title: String = "Adsız Defter",
    val category: String = "Genel",
    val strokes: String = "",
    val favorite: Boolean = false,
    val updatedAt: Long = System.currentTimeMillis()
)

data class FolderCount(val folderId: Long?, val cnt: Int)

@Dao interface AppDao {
    @Query("SELECT * FROM FolderEntity") fun folders(): Flow<List<FolderEntity>>
    @Insert suspend fun insertFolder(f: FolderEntity): Long
    @Delete suspend fun deleteFolder(f: FolderEntity)
    @Query("UPDATE NoteEntity SET folderId = NULL WHERE folderId = :fid") suspend fun orphan(fid: Long)
    @Query("SELECT folderId AS folderId, COUNT(*) AS cnt FROM NoteEntity GROUP BY folderId") fun counts(): Flow<List<FolderCount>>
    @Query("SELECT * FROM NoteEntity WHERE (:fid IS NULL OR folderId = :fid) ORDER BY updatedAt DESC")
    fun notes(fid: Long?): Flow<List<NoteEntity>>
    @Query("SELECT * FROM NoteEntity WHERE id = :id") suspend fun note(id: Long): NoteEntity?
    @Upsert suspend fun upsert(n: NoteEntity): Long
    @Delete suspend fun delete(n: NoteEntity)
}

@Database(entities = [FolderEntity::class, NoteEntity::class], version = 2, exportSchema = false)
abstract class Db : RoomDatabase() {
    abstract fun dao(): AppDao
    companion object {
        fun create(c: Context) = Room.databaseBuilder(c, Db::class.java, "notes.db").fallbackToDestructiveMigration(true).build()
    }
}

// ---------------- Sayfa modeli ----------------
private var idCounter = 0L
fun boxId() = ++idCounter

data class NoteBox(
    val x: Float, val y: Float, val title: String = "Not", val text: String = "",
    val id: Long = boxId()
)

data class Page(
    val strokes: List<Stroke> = emptyList(),
    val boxes: List<NoteBox> = emptyList(),
    val template: String = "dot"
) {
    /** Koyu zemin (uygulama koyu temada ya da "Siyah Tahta" şablonu) */
    val dark: Boolean get() = Pal.dark || template == "dark"
}

/** Diskte "temaya göre otomatik renk" anlamına gelen mürekkep işaretçisi. */
const val INK_SENT = 0xFF010203.toInt()
private const val RGB = 0x00FFFFFF
private const val ALPHA = 0xFF000000.toInt()

// ---------------- Ink fırçaları ----------------
/** Fırça ailesi. Kaydederken ailenin ayırt edilmesi için epsilon'a küçük bir etiket eklenir. */
enum class Fam(val eps: Float) { PEN(0.100f), MARKER(0.110f), HL(0.120f) }

fun makeBrush(fam: Fam, argb: Int, size: Float): Brush = Brush.createWithColorIntArgb(
    family = when (fam) { Fam.PEN -> StockBrushes.pressurePen(); Fam.MARKER -> StockBrushes.marker(); Fam.HL -> StockBrushes.highlighter() },
    colorIntArgb = argb, size = size, epsilon = fam.eps
)

private fun enc(s: String) = URLEncoder.encode(s, "UTF-8")
private fun dec(s: String) = URLDecoder.decode(s, "UTF-8")
const val PAGE_SEP = "\n#PAGE\n"

private fun Stroke.toLine(ink: Int): String {
    val bytes = ByteArrayOutputStream().use { o -> inputs.encode(o); o.toByteArray() }
    val c = brush.colorIntArgb
    val stored = if ((c and RGB) == (ink and RGB)) (c and ALPHA) or (INK_SENT and RGB) else c
    return "I;$stored;${brush.size};${brush.epsilon};${Base64.encodeToString(bytes, Base64.NO_WRAP)}"
}

private fun lineToStroke(l: String, ink: Int): Stroke? = runCatching {
    val p = l.split(";", limit = 5)
    var col = p[1].toInt()
    if ((col and RGB) == (INK_SENT and RGB)) col = (col and ALPHA) or (ink and RGB)
    val eps = p[3].toFloat()
    val fam = Fam.values().minByOrNull { abs(it.eps - eps) } ?: Fam.PEN
    val inputs = ByteArrayInputStream(Base64.decode(p[4], Base64.NO_WRAP)).use { StrokeInputBatch.decode(it) }
    Stroke(brush = makeBrush(fam, col, p[2].toFloat()), inputs = inputs)
}.getOrNull()

fun Page.toText(): String {
    val ink = Pal.inkArgb(dark)
    return (listOf("T;$template") + strokes.map { it.toLine(ink) } +
        boxes.map { b -> "N;${b.x};${b.y};${enc(b.title)};${enc(b.text)}" }).joinToString("\n")
}

fun String.decodePage(templateOverride: String? = null): Page {
    val lines = lines().filter { it.isNotBlank() }
    val tpl = templateOverride ?: lines.firstOrNull { it.startsWith("T;") }?.removePrefix("T;") ?: "dot"
    val ink = Pal.inkArgb(Pal.dark || tpl == "dark")
    val strokes = mutableListOf<Stroke>(); val boxes = mutableListOf<NoteBox>()
    lines.forEach { l ->
        when {
            l.startsWith("N;") -> runCatching {
                val p = l.split(";", limit = 5)
                boxes += NoteBox(p[1].toFloat(), p[2].toFloat(), dec(p[3]), dec(p[4]))
            }
            l.startsWith("I;") -> lineToStroke(l, ink)?.let { strokes += it }
        }
    }
    return Page(strokes, boxes, tpl)
}

fun List<Page>.encodePages(): String = joinToString(PAGE_SEP) { it.toText() }
fun String.decodePages(): List<Page> = split(PAGE_SEP).map { it.decodePage() }
