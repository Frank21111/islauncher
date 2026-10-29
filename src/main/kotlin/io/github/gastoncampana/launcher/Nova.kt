package io.github.gastoncampana.launcher

import java.net.URLDecoder

// Converts a Nova Launcher backup (.novabackup: a zip holding nova.db and nova.xml) into a Layout.
// NovaImport.kt reads the file; everything here is pure so it can be unit-tested.

const val DESKTOP = -100
const val DOCK = -101
private const val NOVA = "com.teslacoilsw.launcher"

/** A row of nova.db's `favorites` table. [type] is Launcher3's itemType: 0 app, 2 folder, 4 widget, 6 shortcut. */
data class NovaFavorite(
    val container: Int,
    val screen: Int,
    val x: Float,
    val y: Float,
    val w: Float = 1f,
    val h: Float = 1f,
    val type: Int = 0,
    val title: String? = null,
    val intent: String? = null,
    val provider: String? = null,
    val up: String? = null,
    val down: String? = null,
)

/** A row of nova.db's `drawer_groups`, with its `appgroups` members ("<package>/<class>#-1"). */
data class NovaTab(val title: String, val type: String, val order: Int, val hideApps: Boolean, val members: List<String>)

/**
 * Parses Nova's "<title>$<intent URI>" (or a bare intent URI) into a Target. Nova's own actions (drawer,
 * notifications, search) return null: the UI provides those itself. [user] replaces Nova's "-1" (current user).
 */
fun novaTarget(value: String?, user: Long): Target? {
    val start = value?.indexOf("#Intent;") ?: -1
    if (start < 0) return null
    val title = value!!.substring(0, start).substringBeforeLast('$', "")
    val fields = value.substring(start + "#Intent;".length).split(';').associate {
        it.substringBefore('=') to URLDecoder.decode(it.substringAfter('=', ""), Charsets.UTF_8)
    }
    val component = fields["component"]?.let(::expandComponent)
    val pkg = fields["package"] ?: component?.substringBefore('/')
    if (pkg == null || pkg == NOVA) return null
    fields["S.shortcut_id"]?.let { return Target.Shortcut(pkg, it, title) }
    return component?.let { Target.App("$it#$user") }
}

/** "pkg/.Cls" → "pkg/pkg.Cls", matching ComponentName.flattenToString(). */
private fun expandComponent(c: String): String {
    val pkg = c.substringBefore('/')
    val cls = c.substringAfter('/')
    return if (cls.startsWith('.')) "$pkg/$pkg$cls" else c
}

/** [hidden] holds the components of Nova's "hidden apps" (`appgroups` rows with groupId -1). */
fun novaLayout(favorites: List<NovaFavorite>, tabs: List<NovaTab>, hidden: List<String>, prefs: Map<String, String>, user: Long): Layout {
    fun appId(novaComponent: String) = novaComponent.substringBeforeLast('#') + "#$user"

    val dockRows = favorites.filter { it.container == DOCK }
    val pageCount = maxOf(prefs["dock_pages_count"]?.toIntOrNull() ?: 1, (dockRows.maxOfOrNull { it.screen } ?: 0) + 1)
    val dock = List(pageCount) { page ->
        List(DOCK_SIZE) { slot ->
            dockRows.find { it.screen == page && it.x.toInt() == slot }
                ?.let { DockIcon(novaTarget(it.intent, user), novaTarget(it.up, user), novaTarget(it.down, user)) } ?: DockIcon()
        }
    }

    // Nova numbers screens with gaps (0, 2, 3); pages keep their order.
    val desktopRows = favorites.filter { it.container == DESKTOP }
    val desktop = desktopRows.map { it.screen }.distinct().sorted().map { screen ->
        desktopRows.filter { it.screen == screen }.mapNotNull { row ->
            val cell = Cell(row.x, row.y, row.w, row.h)
            when (row.type) {
                4 -> row.provider?.let { Item.Widget(it, 0, cell) }
                // Prefixing the title gives shortcuts their label, as in the fling fields.
                0, 1, 6 -> novaTarget("${row.title.orEmpty()}$${row.intent}", user)?.let { Item.Icon(it, row.title.orEmpty(), cell) }
                else -> null // folders: unused in this setup
            }
        }
    }.ifEmpty { listOf(emptyList()) }

    // App groups and the all-apps tab; the private-space tab and drawer folders are dropped.
    val drawerTabs = tabs.sortedBy { it.order }.mapNotNull { tab ->
        when (tab.type) {
            "TAB_APP_GROUP" -> Tab(tab.title, tab.members.map(::appId).toSet(), tab.hideApps)
            "TAB_APPS" -> Tab(tab.title)
            else -> null
        }
    }.ifEmpty { listOf(Tab("Apps")) }

    val defaultPage = prefs["desktop_default_page"]?.toIntOrNull()?.takeIf { it in desktop.indices } ?: 0
    return Layout(dock, desktop, defaultPage, drawerTabs, novaTarget(prefs["gesture_double_tap"], user), hidden.map(::appId).toSet())
}
