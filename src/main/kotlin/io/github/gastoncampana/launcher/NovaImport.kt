package io.github.gastoncampana.launcher

import android.content.Context
import android.database.Cursor
import android.database.sqlite.SQLiteDatabase
import android.net.Uri
import android.util.Xml
import org.xmlpull.v1.XmlPullParser
import java.io.File
import java.io.IOException
import java.util.zip.ZipInputStream

/** Reads a .novabackup and saves its shortcut icons for [shortcutIconFile]. Throws on anything unreadable. */
fun readNovaBackup(context: Context, uri: Uri, user: Long): Layout {
    val db = File(context.cacheDir, "nova-import.db")
    db.delete()
    var prefs = emptyMap<String, String>()
    val input = context.contentResolver.openInputStream(uri) ?: throw IOException("Cannot open $uri")
    ZipInputStream(input).use { zip ->
        generateSequence { zip.nextEntry }.forEach { entry ->
            when (entry.name) {
                "nova.db" -> db.outputStream().use { zip.copyTo(it) }
                "nova.xml" -> prefs = readPrefs(zip.readBytes())
            }
        }
    }
    if (!db.exists()) throw IOException("No nova.db in $uri")
    try {
        SQLiteDatabase.openDatabase(db.path, null, SQLiteDatabase.OPEN_READONLY).use { sql ->
            val favorites = sql.rows(
                "select container, screen, cellX, cellY, spanX, spanY, itemType, title, intent, appWidgetProvider, " +
                    "flingUpIntent, flingDownIntent, icon from favorites",
            ) {
                val row = NovaFavorite(
                    getInt(0), getInt(1), getFloat(2), getFloat(3), getFloat(4), getFloat(5), getInt(6),
                    getString(7), getString(8), getString(9), getString(10), getString(11),
                )
                val target = novaTarget("${row.title.orEmpty()}$${row.intent}", user)
                if (target is Target.Shortcut && !isNull(12)) context.shortcutIconFile(target).writeBytes(getBlob(12))
                row
            }
            val members = sql.rows("select groupId, component from appgroups") { getLong(0) to getString(1) }
                .groupBy({ it.first }, { it.second })
            val tabs = sql.rows("select _id, title, groupType, tabOrder, hideApps from drawer_groups") {
                NovaTab(getString(1).orEmpty(), getString(2).orEmpty(), getInt(3), getInt(4) != 0, members[getLong(0)].orEmpty())
            }
            return novaLayout(favorites, tabs, members[-1L].orEmpty(), prefs, user)
        }
    } finally {
        db.delete()
        File(db.path + "-journal").delete()
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
