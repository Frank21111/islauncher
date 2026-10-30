package io.github.gastoncampana.launcher

import android.content.Context
import android.database.Cursor
import android.database.sqlite.SQLiteDatabase
import android.net.Uri
import android.util.Xml
import org.xmlpull.v1.XmlPullParser
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.IOException
import java.io.InputStream
import java.io.OutputStream
import java.util.zip.ZipInputStream

private const val MAX_ENTRY_BYTES = 64L shl 20 // a Nova database is a few MB; a bigger entry would only fill the cache

/** Reads a .novabackup and saves its shortcut icons for [shortcutIconFile]. Throws on anything unreadable. */
fun readNovaBackup(context: Context, uri: Uri, user: Long): Layout {
    val db = File(context.cacheDir, "nova-import.db")
    db.delete()
    try {
        var prefs: Map<String, String>? = null
        val input = context.contentResolver.openInputStream(uri) ?: throw IOException("Cannot open $uri")
        ZipInputStream(input).use { zip ->
            generateSequence { zip.nextEntry }.forEach { entry ->
                when (entry.name) {
                    "nova.db" -> db.outputStream().use { zip.copyAtMost(it) }
                    "nova.xml" -> prefs = readPrefs(ByteArrayOutputStream().also { zip.copyAtMost(it) }.toByteArray())
                }
            }
        }
        // Without its prefs, a cut-off backup would import onto the default grid and lose what lies beyond it.
        if (!db.exists() || prefs == null) throw IOException("No nova.db or nova.xml in $uri")
        SQLiteDatabase.openDatabase(db.path, null, SQLiteDatabase.OPEN_READONLY).use { sql ->
            val icons = HashMap<Target.Shortcut, ByteArray>()
            val favorites = sql.rows(
                "select container, screen, cellX, cellY, spanX, spanY, itemType, title, intent, appWidgetProvider, " +
                    "flingUpIntent, flingDownIntent, icon from favorites",
            ) {
                val row = NovaFavorite(
                    getInt(0), getInt(1), getFloat(2), getFloat(3), getFloat(4), getFloat(5), getInt(6),
                    getString(7), getString(8), getString(9), getString(10), getString(11),
                )
                val target = novaTarget("${row.title.orEmpty()}$${row.intent}", user)
                if (target is Target.Shortcut && !isNull(12)) icons[target] = getBlob(12)
                row
            }
            val members = sql.rows("select groupId, component from appgroups") { getLong(0) to getString(1) }
                .groupBy({ it.first }, { it.second })
            val tabs = sql.rows("select _id, title, groupType, tabOrder, hideApps from drawer_groups") {
                NovaTab(getString(1).orEmpty(), getString(2).orEmpty(), getInt(3), getInt(4) != 0, members[getLong(0)].orEmpty())
            }
            val layout = novaLayout(favorites, tabs, members[-1L].orEmpty(), prefs!!, user).restored(emptyMap())
            // Only once the whole backup read fine: a failed import must not change the icons of the current layout.
            icons.forEach { (target, png) -> context.shortcutIconFile(target).writeBytes(png) }
            return layout
        }
    } finally {
        db.delete()
        File(db.path + "-journal").delete()
    }
}

private fun InputStream.copyAtMost(out: OutputStream) {
    val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
    var total = 0L
    while (true) {
        val n = read(buffer)
        if (n < 0) return
        total += n
        if (total > MAX_ENTRY_BYTES) throw IOException("Backup entry over $MAX_ENTRY_BYTES bytes")
        out.write(buffer, 0, n)
    }
}

private fun <T> SQLiteDatabase.rows(query: String, row: Cursor.() -> T): List<T> =
    rawQuery(query, null).use { c -> buildList { while (c.moveToNext()) add(c.row()) } }

/** SharedPreferences XML (`<int name="k" value="1"/>`, `<string name="k">v</string>`) as strings. */
private fun readPrefs(xml: ByteArray): Map<String, String> {
    val parser = Xml.newPullParser().apply { setInput(xml.inputStream(), "UTF-8") }
    val prefs = HashMap<String, String>()
    while (parser.next() != XmlPullParser.END_DOCUMENT) {
        if (parser.eventType != XmlPullParser.START_TAG) continue
        val name = parser.getAttributeValue(null, "name") ?: continue
        prefs[name] = parser.getAttributeValue(null, "value") ?: if (parser.name == "string") parser.nextText() else continue
    }
    return prefs
}
