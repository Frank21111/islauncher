package io.github.gastoncampana.launcher

import org.json.JSONArray
import org.json.JSONObject
import java.math.BigDecimal
import java.math.MathContext
import java.text.DecimalFormat
import java.text.DecimalFormatSymbols
import java.text.Normalizer
import java.util.Locale
import kotlin.math.abs
import kotlin.math.ceil
import kotlin.math.floor
import kotlin.math.ln
import kotlin.math.log10
import kotlin.math.pow
import kotlin.math.round

/** Stable id of a launchable activity: "<flattened component>#<user serial number>". */
typealias AppId = String

/** Columns and rows a grid (desktop, drawer, dock) may have. */
val GRID_SIZES = 2..12

enum class Swipe(val arrow: String) { UP("↑"), DOWN("↓"), LEFT("←"), RIGHT("→") }

/** Something an icon or gesture opens. */
sealed interface Target {
    data class App(val id: AppId) : Target

    /** An app shortcut (LauncherApps deep shortcut). Only the default home app may start one. */
    data class Shortcut(val pkg: String, val id: String, val label: String) : Target
}

/** A dock icon: tapping opens [tap], dragging it up or down opens [up] or [down]. */
data class DockIcon(val tap: Target? = null, val up: Target? = null, val down: Target? = null)

/** A rectangle on the desktop grid, in cells. */
data class Cell(val x: Float, val y: Float, val w: Float = 1f, val h: Float = 1f) {
    fun overlaps(o: Cell) = x < o.x + o.w && o.x < x + w && y < o.y + o.h && o.y < y + h
}

sealed interface Item {
    val cell: Cell

    data class Icon(val target: Target, val label: String, override val cell: Cell) : Item

    /** [widgetId] is 0 until the widget is bound to this launcher: while it is added, or after its app was reinstalled. */
    data class Widget(val provider: String, val widgetId: Int, override val cell: Cell) : Item
}

/** A drawer tab. Without its own [apps] it lists every app that no [hideApps] tab claims. */
data class Tab(val name: String, val apps: Set<AppId>? = null, val hideApps: Boolean = false)

data class Layout(
    /** Each page holds at least [dockSize] icons; those past it stay saved, hidden, until the dock grows again. */
    val dock: List<List<DockIcon>> = listOf(List(5) { DockIcon() }),
    val desktop: List<List<Item>> = listOf(emptyList()),
    val defaultPage: Int = 0,
    val tabs: List<Tab> = listOf(Tab("Apps")),
    val doubleTap: Target? = null,
    /** Left out of every drawer tab; search still finds them, as in Nova. */
    val hidden: Set<AppId> = emptySet(),
    /** "Venenos": apps that open only after a pause. */
    val poisons: Set<AppId> = emptySet(),
    /** The desktop grid. Items sit on half cells. */
    val columns: Int = 5,
    val rows: Int = 5,
    val dockSize: Int = 5,
    /** The dock scrolls around from its last page to its first. */
    val dockWraps: Boolean = true,
    val drawerColumns: Int = 5,
    /** Rows on screen at once: they set the row height of the scrolling drawer. */
    val drawerRows: Int = 6,
    /** Light vibrations on long presses, drops, swipes and settings; the system's touch feedback setting still rules. */
    val haptics: Boolean = true,
    /** A healthy app a poison's pause screen offers to open instead. */
    val instead: Target? = null,
    /** The decimal mark search reads and shows numbers with: ',' (1.234,5) or '.' (1,234.5); null follows the phone's country. */
    val decimal: Char? = null,
    /** Apps paused by Focus mode or a Digital Wellbeing limit leave the desktop, dock and drawer tabs instead of greying out; search still shows them. */
    val hideSuspended: Boolean = false,
)

fun <T> List<T>.replaced(index: Int, f: (T) -> T): List<T> = mapIndexed { i, x -> if (i == index) f(x) else x }

/** [this] with the element at [from] moved to [to]; unchanged when either is out of range. */
fun <T> List<T>.shifted(from: Int, to: Int): List<T> =
    if (from !in indices || to !in indices) this else toMutableList().apply { add(to, removeAt(from)) }

/** [this] without [app] (matched by [find], so under any of its ids) or, when absent, with it as [id]. */
fun <T> Set<AppId>.toggled(app: T, id: AppId, find: (AppId) -> T?): Set<AppId> =
    if (any { find(it) == app }) filterTo(HashSet()) { find(it) != app } else this + id

/** Looks apps up by [AppId], falling back to another activity of the same package and user: some apps switch
 * launcher aliases (Mercado Pago, Naranja X), which renames the activity. */
fun <T> finder(apps: List<T>, id: (T) -> AppId): (AppId) -> T? {
    val byId = apps.associateBy(id)
    val byPackage = apps.reversed().associateBy { packageKey(id(it)) } // reversed: the first activity wins
    return { byId[it] ?: byPackage[packageKey(it)] }
}

/** "<package>#<user serial>": an app under any of its activities. */
fun packageKey(id: AppId) = id.substringBefore('/') + "#" + id.substringAfterLast('#')

/** The apps [tab] lists, in [apps] order. */
fun <T> tabApps(tab: Tab, tabs: List<Tab>, apps: List<T>, find: (AppId) -> T?, hidden: Set<AppId> = emptySet()): List<T> {
    val left = hidden.mapNotNull(find).toSet()
    tab.apps?.let { ids -> return ids.mapNotNull(find).toSet().let { members -> apps.filter { it in members && it !in left } } }
    val claimed = tabs.filter { it.hideApps }.flatMap { it.apps.orEmpty() }.mapNotNull(find).toSet()
    return apps.filter { it !in claimed && it !in left }
}

/** The first free w×h spot on desktop [page], scanning rows in half-cell steps and columns in whole cells. */
fun Layout.freeCell(page: Int, w: Float, h: Float): Cell? {
    for (y2 in 0..((rows - h) * 2).toInt()) for (x in 0..(columns - w).toInt()) {
        val cell = Cell(x.toFloat(), y2 / 2f, w, h)
        if (desktop[page].none { it.cell.overlaps(cell) }) return cell
    }
    return null
}

/**
 * The cells a new widget spans on one side: the [target] cells its app asks for, else its minimum size ([minDp])
 * rounded up to half cells of [cellDp]; from half a cell to the grid's [max].
 */
fun widgetSpan(minDp: Float, target: Int, cellDp: Float, max: Int): Float =
    (if (target > 0) target.toFloat() else ceil(minDp / cellDp * 2) / 2).coerceIn(0.5f, max.toFloat())

fun Layout.fits(c: Cell) = c.w > 0 && c.h > 0 && c.x >= 0 && c.y >= 0 && c.x + c.w <= columns && c.y + c.h <= rows // NaN fails too

/** The free half-cell spot for a [want]-sized item nearest [want] among [items], or null when there is none. */
private fun Layout.nearestFree(items: List<Item>, want: Cell): Cell? {
    val (_, _, w, h) = want
    val spots = (0..((rows - h) * 2).toInt()).flatMap { y2 -> (0..((columns - w) * 2).toInt()).map { x2 -> Cell(x2 / 2f, y2 / 2f, w, h) } }
    return spots.filter { spot -> items.none { it.cell.overlaps(spot) } }
        .minByOrNull { (it.x - want.x) * (it.x - want.x) + (it.y - want.y) * (it.y - want.y) }
}

private fun Item.at(cell: Cell): Item = when (this) {
    is Item.Icon -> copy(cell = cell)
    is Item.Widget -> copy(cell = cell)
}

/**
 * Adds [item], shrunk to the grid if bigger, at the free spot nearest its cell: on page [home] if that has room,
 * else on the first page that does, else on a new page. Also how a thrown item comes back on Deshacer.
 */
fun Layout.placed(home: Int?, item: Item): Layout {
    val want = item.cell.copy(w = minOf(item.cell.w, columns.toFloat()), h = minOf(item.cell.h, rows.toFloat()))
    for (page in listOfNotNull(home?.takeIf { it in desktop.indices }) + desktop.indices.filter { it != home }) {
        val cell = nearestFree(desktop[page], want) ?: continue
        return copy(desktop = desktop.replaced(page) { it + item.at(cell) })
    }
    return copy(desktop = desktop + listOf(listOf(item.at(nearestFree(emptyList(), want)!!))))
}

/** This layout on a [columns]×[rows] desktop grid; items that no longer fit are [placed] again. */
fun Layout.withGrid(columns: Int, rows: Int): Layout {
    val grid = copy(columns = columns, rows = rows)
    val misfits = desktop.flatMapIndexed { page, items -> items.filterNot { grid.fits(it.cell) }.map { page to it } }
    return misfits.fold(grid.copy(desktop = desktop.map { items -> items.filter { grid.fits(it.cell) } })) { layout, (page, item) ->
        layout.placed(page, item)
    }
}

/** Removes desktop [page], [placed] its items on the others; the last page stays. */
fun Layout.withoutPage(page: Int): Layout {
    if (page !in desktop.indices || desktop.size == 1) return this
    val main = if (defaultPage > page) defaultPage - 1 else if (defaultPage == page) 0 else defaultPage
    val rest = copy(desktop = desktop.filterIndexed { i, _ -> i != page }, defaultPage = main)
    return desktop[page].fold(rest) { layout, item -> layout.placed(null, item) }
}

/**
 * Without the desktop icons and dock targets of package [pkg] for user [user] (serial numbers), as Nova drops an app
 * uninstalled from its menu. Shortcuts are [mainUser]'s ([Target.Shortcut] keeps no user); widgets stay for Ordenar.
 */
fun Layout.withoutApp(pkg: String, user: Long, mainUser: Long): Layout {
    fun gone(t: Target?) = when (t) {
        is Target.App -> packageKey(t.id) == "$pkg#$user"
        is Target.Shortcut -> t.pkg == pkg && user == mainUser
        null -> false
    }
    return copy(
        desktop = desktop.map { items -> items.filterNot { it is Item.Icon && gone(it.target) } },
        dock = dock.map { icons -> icons.map { DockIcon(it.tap.takeUnless(::gone), it.up.takeUnless(::gone), it.down.takeUnless(::gone)) } },
    )
}

/** Moves desktop page [from] to [to]; the main page stays the main one wherever it lands. */
fun Layout.withPageMoved(from: Int, to: Int): Layout =
    copy(desktop = desktop.shifted(from, to), defaultPage = desktop.indices.toList().shifted(from, to).indexOf(defaultPage))

/** Where something sits: [item] on desktop [page], or dock [page]'s [slot], whose tap (or [swipe]) opens [target]. */
sealed interface Place {
    data class Desktop(val page: Int, val item: Item) : Place
    data class Dock(val page: Int, val slot: Int, val target: Target, val swipe: Swipe? = null) : Place
}

/** What Ajustes → Ordenar lists ([audit]). Nothing in it goes unless the owner removes it. */
data class Audit(
    /** Icons, dock taps and swipes, and widgets whose app or widget is gone. */
    val broken: List<Place> = emptyList(),
    /** The places of one app (by [packageKey]) on more than one page, or in the dock and on a page. */
    val twins: List<List<Place>> = emptyList(),
    /** Empty desktop pages, but one: the main one when all are empty (the last page stays). */
    val empty: List<Int> = emptyList(),
) {
    /** What to fix: twins may be on purpose, so they are listed but not counted. */
    val size get() = broken.size + empty.size
}

/**
 * What needs the owner's attention: icons and dock targets [alive] rejects, widgets [widgetAlive] rejects, the same
 * app in more than one place, and empty pages. Only the dock icons shown count, and only their taps make twins: a
 * swipe shows no icon. Twins are of live apps only (a gone one is broken already), and the dock counts as one page.
 */
fun Layout.audit(alive: (Target) -> Boolean, widgetAlive: (Item.Widget) -> Boolean): Audit {
    val items = desktop.flatMapIndexed { page, items -> items.map { Place.Desktop(page, it) } }
    val docked = dock.flatMapIndexed { page, icons ->
        icons.take(dockSize).flatMapIndexed { slot, icon ->
            listOfNotNull(
                icon.tap?.let { Place.Dock(page, slot, it) },
                icon.up?.let { Place.Dock(page, slot, it, Swipe.UP) },
                icon.down?.let { Place.Dock(page, slot, it, Swipe.DOWN) },
            )
        }
    }
    val broken = items.filter { (_, item) -> if (item is Item.Icon) !alive(item.target) else !widgetAlive(item as Item.Widget) } +
        docked.filter { !alive(it.target) }
    val twins = (items + docked.filter { it.swipe == null })
        .mapNotNull { place ->
            val app = (if (place is Place.Desktop) (place.item as? Item.Icon)?.target else (place as Place.Dock).target) as? Target.App
            app?.takeIf(alive)?.let { packageKey(it.id) to place }
        }
        .groupBy({ it.first }, { it.second }).values
        .filter { places -> places.distinctBy { (it as? Place.Desktop)?.page ?: -1 }.size > 1 }
    val empty = desktop.indices.filter { desktop[it].isEmpty() }
    return Audit(broken, twins, if (empty.size == desktop.size) empty - defaultPage else empty)
}

/**
 * Where widget [w], last seen on [page], is now: there as it was, else the first one of the same provider and id
 * on any page (a drag, a grid change or a removed page moved it). Null once it is gone.
 */
fun Layout.locate(page: Int, w: Item.Widget): Pair<Int, Item.Widget>? {
    if (w in desktop.getOrNull(page).orEmpty()) return page to w
    desktop.forEachIndexed { i, items ->
        items.firstOrNull { it is Item.Widget && it.provider == w.provider && it.widgetId == w.widgetId }?.let { return i to it as Item.Widget }
    }
    return null
}

fun Layout.withDockSize(size: Int) = copy(dockSize = size, dock = dock.map { it.padded(size) })

private fun List<DockIcon>.padded(size: Int) = this + List(maxOf(0, size - this.size)) { DockIcon() }

/**
 * Moves [item] from page [from] (null: a new item) to the free half-cell spot of page [to] nearest [want];
 * unchanged when none is free.
 */
fun Layout.moved(from: Int?, item: Item, to: Int, want: Cell): Layout {
    if ((from != null && item !in desktop.getOrNull(from).orEmpty()) || to !in desktop.indices) return this
    // Only the moved item leaves page [to]: an equal one there (a twin, or the same app the drawer drops) stays.
    val others = if (from == to) desktop[to] - item else desktop[to]
    val moved = item.at(nearestFree(others, want.copy(w = item.cell.w, h = item.cell.h)) ?: return this)
    return copy(desktop = desktop.mapIndexed { i, page -> if (i == to) others + moved else if (i == from) page - item else page })
}

/** A side of a desktop item, which its resize frame drags. */
enum class Edge { LEFT, TOP, RIGHT, BOTTOM }

/** The sizes a widget may be resized to, in cells: at least [minW]×[minH], at most [maxW]×[maxH] (0: only the grid's). */
data class Limits(val minW: Float, val minH: Float, val maxW: Float = 0f, val maxH: Float = 0f)

/**
 * One side's least and most cells in a widget's [Limits], from its provider's [min], [minResize] and [maxResize] sizes
 * in dp, rounded as [widgetSpan] rounds a new widget's. As the platform documents, a min resize size above the min
 * has no effect, nor a max one below it; 0 is none.
 */
fun resizeSpan(min: Float, minResize: Float, maxResize: Float, cellDp: Float, max: Int): Pair<Float, Float> =
    widgetSpan(if (minResize > 0 && minResize < min) minResize else min, 0, cellDp, max) to
        if (maxResize > 0 && maxResize >= min) widgetSpan(maxResize, 0, cellDp, max) else 0f

/**
 * [item]'s cell on desktop [page] with its [edge] dragged [by] cells (right and down are positive), snapped to half
 * cells, within [limits], the page and the items beside it. A limit it already breaks only keeps it from getting worse.
 */
fun Layout.stretched(page: Int, item: Item, edge: Edge, by: Float, limits: Limits): Cell {
    val (x, y, w, h) = item.cell
    val others = (desktop.getOrNull(page).orEmpty() - item).map { it.cell }
    val rowsBeside = others.filter { it.y < y + h && y < it.y + it.h }
    val columnsBeside = others.filter { it.x < x + w && x < it.x + it.w }
    fun snapped(side: Float) = round((side + by) * 2) / 2
    fun clamp(to: Float, now: Float, low: Float, high: Float) = to.coerceIn(minOf(low, now), maxOf(high, now))
    return when (edge) {
        Edge.RIGHT -> {
            val wall = rowsBeside.filter { it.x >= x + w }.minOfOrNull { it.x } ?: columns.toFloat()
            item.cell.copy(w = clamp(snapped(x + w), x + w, x + limits.minW, if (limits.maxW > 0) minOf(wall, x + limits.maxW) else wall) - x)
        }
        Edge.BOTTOM -> {
            val wall = columnsBeside.filter { it.y >= y + h }.minOfOrNull { it.y } ?: rows.toFloat()
            item.cell.copy(h = clamp(snapped(y + h), y + h, y + limits.minH, if (limits.maxH > 0) minOf(wall, y + limits.maxH) else wall) - y)
        }
        Edge.LEFT -> {
            val wall = rowsBeside.filter { it.x + it.w <= x }.maxOfOrNull { it.x + it.w } ?: 0f
            val left = clamp(snapped(x), x, if (limits.maxW > 0) maxOf(wall, x + w - limits.maxW) else wall, x + w - limits.minW)
            item.cell.copy(x = left, w = x + w - left)
        }
        Edge.TOP -> {
            val wall = columnsBeside.filter { it.y + it.h <= y }.maxOfOrNull { it.y + it.h } ?: 0f
            val top = clamp(snapped(y), y, if (limits.maxH > 0) maxOf(wall, y + h - limits.maxH) else wall, y + h - limits.minH)
            item.cell.copy(y = top, h = y + h - top)
        }
    }
}

/**
 * [item] on desktop [page] at [cell], where it stays in the page's order; unchanged when it would leave the page or
 * overlap an item it did not overlap already (a Nova import may have let them).
 */
fun Layout.resized(page: Int, item: Item, cell: Cell): Layout {
    val items = desktop.getOrNull(page) ?: return this
    val i = items.indexOf(item)
    if (i < 0 || !fits(cell) || (items - item).any { it.cell.overlaps(cell) && !it.cell.overlaps(item.cell) }) return this
    return copy(desktop = desktop.replaced(page) { it.replaced(i) { item.at(cell) } })
}

/**
 * A layout read from a backup file. Widgets whose id is not one of this install's ([live], id to provider) for
 * the same provider become placeholders to set up again; items off the grid are dropped. Throws on a layout no real
 * one comes near: it would be saved, and then fail on every start.
 */
fun Layout.restored(live: Map<Int, String?>): Layout {
    require(withinBudget()) { "Not a layout this app could have saved" }
    // Every page stays composed, so a file with thousands would make each start run out of memory.
    val pages = desktop.take(MAX_PAGES).map { page ->
        page.filter { fits(it.cell) }
            .map { if (it is Item.Widget && live[it.widgetId] != it.provider) it.copy(widgetId = 0) else it }
    }
    return copy(dock = dock.take(MAX_PAGES), desktop = pages, defaultPage = defaultPage.takeIf { it in pages.indices } ?: 0)
}

private fun Layout.withinBudget(): Boolean {
    val items = desktop.flatten()
    val targets = listOfNotNull(doubleTap, instead) + items.filterIsInstance<Item.Icon>().map { it.target } +
        dock.flatten().flatMap { listOfNotNull(it.tap, it.up, it.down) }
    val texts = targets.flatMap {
        when (it) {
            is Target.App -> listOf(it.id)
            is Target.Shortcut -> listOf(it.pkg, it.id, it.label)
        }
    } + items.map {
        when (it) {
            is Item.Icon -> it.label
            is Item.Widget -> it.provider
        }
    } + tabs.map { it.name } + tabs.flatMap { it.apps.orEmpty() } + hidden + poisons
    return items.size <= MAX_ITEMS && tabs.size <= MAX_TABS && dock.all { it.size <= GRID_SIZES.last } &&
        texts.size <= MAX_TEXTS && texts.all { it.length <= MAX_TEXT }
}

/** Most desktop or dock pages a restored backup keeps. */
const val MAX_PAGES = 20

/** What a restored backup may hold, past any real layout: desktop items (the largest grid, full, on every page), drawer tabs, labels and ids, and the length of one. */
const val MAX_ITEMS = 2880
const val MAX_TABS = 200
const val MAX_TEXTS = 20_000
const val MAX_TEXT = 4096

/** Widest side a saved shortcut icon is decoded to, in px. */
private const val MAX_ICON_PX = 512

/** The BitmapFactory sample size that brings a [width] × [height] px icon within [MAX_ICON_PX]: an imported one can be any size. */
fun iconSample(width: Int, height: Int) = generateSequence(1) { it * 2 }.first { maxOf(width, height) / it <= MAX_ICON_PX }

/**
 * Whether Ajustes opens on its guide by itself: on a fresh install's first launch, while no layout is [saved] (any
 * change, a restore or a Nova import saves one, so an existing user has one), and never once [launchedBefore].
 */
fun opensGuide(saved: Boolean, launchedBefore: Boolean) = !saved && !launchedBefore

fun Layout.toJson(): String = JSONObject()
    .put("dock", JSONArray(dock.map { page -> JSONArray(page.map { it.toJson() }) }))
    .put("desktop", JSONArray(desktop.map { page -> JSONArray(page.map { it.toJson() }) }))
    .put("defaultPage", defaultPage)
    .put("tabs", JSONArray(tabs.map { tab -> JSONObject().put("name", tab.name).put("hide", tab.hideApps).put("apps", tab.apps?.let { JSONArray(it.toList()) }) }))
    .put("doubleTap", doubleTap?.toJson()) // null removes the key
    .put("hidden", JSONArray(hidden.toList()))
    .put("poisons", JSONArray(poisons.toList()))
    .put("columns", columns)
    .put("rows", rows)
    .put("dockSize", dockSize)
    .put("dockWraps", dockWraps)
    .put("drawerColumns", drawerColumns)
    .put("drawerRows", drawerRows)
    .put("haptics", haptics)
    .put("instead", instead?.toJson())
    .put("decimal", decimal?.toString())
    .put("hideSuspended", hideSuspended)
    .toString()

fun parseLayout(json: String): Layout {
    val root = JSONObject(json)
    fun size(key: String, default: Int) = root.optInt(key, default).coerceIn(GRID_SIZES)
    val dockSize = size("dockSize", 5)
    // Every dock page has at least dockSize icons, so the UI can index it safely.
    val dock = root.getJSONArray("dock").arrays().map { page ->
        page.objects().map { DockIcon(it.target("tap"), it.target("up"), it.target("down")) }.padded(dockSize)
    }.ifEmpty { listOf(List(dockSize) { DockIcon() }) }
    val desktop = root.getJSONArray("desktop").arrays().map { page -> page.objects().map { it.item() } }.ifEmpty { Layout().desktop }
    val tabs = root.getJSONArray("tabs").objects().map {
        Tab(it.getString("name"), it.optJSONArray("apps")?.strings()?.toSet(), it.optBoolean("hide"))
    }
    val defaultPage = root.optInt("defaultPage").takeIf { it in desktop.indices } ?: 0
    val hidden = root.optJSONArray("hidden")?.strings()?.toSet().orEmpty()
    val poisons = root.optJSONArray("poisons")?.strings()?.toSet().orEmpty()
    return Layout(
        dock = dock,
        desktop = desktop,
        defaultPage = defaultPage,
        tabs = tabs,
        doubleTap = root.target("doubleTap"),
        hidden = hidden,
        poisons = poisons,
        columns = size("columns", 5),
        rows = size("rows", 5),
        dockSize = dockSize,
        dockWraps = root.optBoolean("dockWraps", true),
        drawerColumns = size("drawerColumns", 5),
        drawerRows = size("drawerRows", 6),
        haptics = root.optBoolean("haptics", true),
        instead = root.target("instead"),
        decimal = root.optString("decimal").singleOrNull()?.takeIf { it == ',' || it == '.' },
        hideSuspended = root.optBoolean("hideSuspended"),
    )
}

private fun Target.toJson() = when (this) {
    is Target.App -> JSONObject().put("app", id)
    is Target.Shortcut -> JSONObject().put("pkg", pkg).put("shortcut", id).put("label", label)
}

private fun DockIcon.toJson() = JSONObject().put("tap", tap?.toJson()).put("up", up?.toJson()).put("down", down?.toJson())

private fun Item.toJson(): JSONObject {
    val o = JSONObject().put("x", cell.x.toDouble()).put("y", cell.y.toDouble()).put("w", cell.w.toDouble()).put("h", cell.h.toDouble())
    return when (this) {
        is Item.Icon -> o.put("target", target.toJson()).put("label", label)
        is Item.Widget -> o.put("widget", provider).put("widgetId", widgetId)
    }
}

private fun JSONObject.target(key: String): Target? = optJSONObject(key)?.let {
    if (it.has("app")) Target.App(it.getString("app")) else Target.Shortcut(it.getString("pkg"), it.getString("shortcut"), it.getString("label"))
}

private fun JSONObject.item(): Item {
    val cell = Cell(getDouble("x").toFloat(), getDouble("y").toFloat(), getDouble("w").toFloat(), getDouble("h").toFloat())
    return if (has("widget")) Item.Widget(getString("widget"), getInt("widgetId"), cell) else Item.Icon(target("target")!!, getString("label"), cell)
}

private fun JSONArray.strings() = List(length()) { getString(it) }
private fun JSONArray.objects() = List(length()) { getJSONObject(it) }
private fun JSONArray.arrays() = List(length()) { getJSONArray(it) }

private val MARKS = Regex("\\p{Mn}+")

/** Lowercase without accents, so "camara" finds "Cámara". */
fun fold(s: String): String = Normalizer.normalize(s, Normalizer.Form.NFD).replace(MARKS, "").lowercase()

/**
 * Prefix matches first, then word-start matches, then substrings; within each group the highest [rank] comes first,
 * and input order breaks the remaining ties.
 */
fun <T> search(items: List<T>, query: String, rank: (T) -> Double = { 0.0 }, label: (T) -> String): List<T> {
    val q = fold(query.trim())
    if (q.isEmpty()) return items
    return items.mapNotNull { item ->
        val l = fold(label(item))
        when {
            l.startsWith(q) -> 0
            l.split(' ', '-', '.', '_').any { it.startsWith(q) } -> 1
            q in l -> 2
            else -> null
        }?.let { Triple(it, -rank(item), item) }
    }.sortedWith(compareBy({ it.first }, { it.second })).map { it.third }
}

/** Most shortcuts an app's menu lists, as in Launcher3. */
const val MENU_SHORTCUTS = 4

/**
 * The shortcuts an app's menu lists, as Launcher3 picks them: those from its manifest first, each kind by [rank], at
 * most [MENU_SHORTCUTS]; up to two dynamic ones (recent chats) take the place of the last manifest ones.
 */
fun <T> menuShortcuts(all: List<T>, manifest: (T) -> Boolean, rank: (T) -> Int): List<T> {
    val (fixed, dynamic) = all.sortedBy(rank).partition(manifest)
    val shown = minOf(dynamic.size, maxOf(2, MENU_SHORTCUTS - fixed.size))
    return fixed.take(MENU_SHORTCUTS - shown) + dynamic.take(shown)
}

/** Weight an app's use keeps per day: each open adds 1, and a week later it counts about half. */
const val USE_DECAY = 0.9

/** A use [score] last updated on day [since] as of day [now] (days since 1970, fractional); a clock set back keeps it. */
fun decayed(score: Double, since: Double, now: Double): Double = score * USE_DECAY.pow((now - since).coerceAtLeast(0.0))

/** Days without an open, and since install, that make an app a candidate to uninstall. */
const val UNUSED_DAYS = 60

/** Below this a use score ranks as none: a single open fades under it in about six weeks. */
const val MIN_USE = 0.01

/** The search rank of a use [score] last updated on day [since], as of day [now]: [decayed], none below [MIN_USE]. */
fun useRank(score: Double, since: Double, now: Double): Double = decayed(score, since, now).takeIf { it >= MIN_USE } ?: 0.0

/** Whether a use entry stays: while it ranks, and [UNUSED_DAYS] at least, so a missing one means no open in that long. */
fun kept(score: Double, since: Double, now: Double) = useRank(score, since, now) > 0 || now - since < UNUSED_DAYS

/**
 * The day opens count from (see [unused]) for a use store first read on day [now], its entries last updated on days
 * [since]: the oldest, but no earlier than [MIN_USE] alone kept them, as before [kept] (a single open, about 44 days).
 */
fun countedSince(since: Collection<Double>, now: Double): Double = (since + now).min().coerceAtLeast(now - ln(MIN_USE) / ln(USE_DECAY))

/** The packages of the desktop's widgets: in use, though a tap on a widget is no open the launcher sees. */
val Layout.widgetPackages: Set<String> get() = desktop.flatten().filterIsInstance<Item.Widget>().mapTo(HashSet()) { it.provider.substringBefore('/') }

/**
 * Ajustes → Sin abrir hace tiempo: the [apps] whose [lastOpen] (null: none kept, never or long ago) and [installed]
 * day (null: unknown, so not listed) are [UNUSED_DAYS] old as of [now]; days since 1970. Null while opens have been
 * counted (since day [tracked]) for less than that: an app not seen opened may have been opened before.
 */
fun <T> unused(apps: List<T>, tracked: Double, now: Double, lastOpen: (T) -> Double?, installed: (T) -> Double?): List<T>? {
    if (now - tracked < UNUSED_DAYS) return null
    return apps.filter { app -> lastOpen(app).let { it == null || now - it >= UNUSED_DAYS } && installed(app)?.let { now - it >= UNUSED_DAYS } == true }
}

/** What search answers besides apps, above them: one of these at most (see [answer]). */
sealed interface Answer {
    /** A sum or a converted quantity, as shown and copied ("3,107 mi"); [usCup] when a cup took part. */
    data class Value(val text: String, val usCup: Boolean = false) : Answer

    /** A phone number as typed, without its separators: digits, after a '+' when it had one. */
    data class Phone(val number: String) : Answer

    /** A web address, with its scheme. */
    data class Link(val url: String) : Answer

    data class Email(val address: String) : Answer
}

/**
 * The [Answer] to search [query], numbers read and shown with [decimal] as the decimal mark. A phone number comes
 * first, so "11 5555-1234" never also shows a sum.
 */
fun answer(query: String, decimal: Char): Answer? {
    val q = query.trim().replace(Regex("""\s+"""), " ")
    return phoneOf(q)?.let(Answer::Phone)
        ?: q.removePrefix("mailto:").takeIf(EMAIL::matches)?.let(Answer::Email)
        // Android matches an intent's scheme in lower case only: "HTTPS://" would find no browser.
        ?: q.takeIf(LINK::matches)?.let { Answer.Link(if ("://" in it) it.substringBefore(':').lowercase() + ":" + it.substringAfter(':') else "https://$it") }
        ?: convert(q, decimal)
        ?: calc(q, decimal)?.let { Answer.Value(formatNumber(it, decimal)) }
}

private val EMAIL = Regex("""[^\s@]+@[^\s@]+\.[A-Za-z]{2,}""")
private val LINK = Regex("""(?i)https?://\S+|([a-z0-9-]+\.)+[a-z]{2,}([/?#]\S*)?""")
private val PHONE = Regex("""\+?(\(\d+\)[ -]?|\d+[ -])*\d+""")

/**
 * [q] as a phone number: 8 to 15 digits alone or after a '+', or in groups split by spaces, dashes or parentheses whose
 * last group has four digits. Dashes alone make a subtraction of two numbers ("25000-7500") or of one no phone group has,
 * five digits or more without a leading 0 ("25000-7500-1500"); a day and month before a year make a date.
 */
private fun phoneOf(q: String): String? {
    val digits = q.filter(Char::isDigit)
    if (!PHONE.matches(q) || digits.length !in 8..15) return null
    if (q.startsWith('+')) return "+$digits"
    if (q == digits) return digits // "1155559999": no sum is that many digits alone
    val groups = q.split(' ', '-', '(', ')').filter(String::isNotEmpty)
    val subtraction = q.none { it == ' ' || it == '(' } && (groups.size == 2 || groups.any { it.length > 4 && it[0] != '0' })
    val date = groups.size == 3 && groups[0].length <= 2 && groups[1].length <= 2 // "29-10-2024"
    return digits.takeIf { groups.size > 1 && groups.last().length == 4 && !subtraction && !date }
}

/**
 * The digits wa.me takes for [e164] (as PhoneNumberUtils formats it): an Argentine mobile needs its 9 after the 54,
 * which national forms such as "11 5555-1234" leave out. Landlines get it too: WhatsApp numbers there are mobiles.
 */
fun whatsAppNumber(e164: String): String {
    val digits = e164.filter(Char::isDigit)
    return if (digits.startsWith("54") && !digits.startsWith("549")) "549" + digits.drop(2) else digits
}

/** [value] grouped in thousands, with [decimal] as the decimal mark; scientific where ten decimals would round to 0 or the digits run off the screen. */
fun formatNumber(value: Double, decimal: Char): String {
    val symbols = DecimalFormatSymbols(Locale.ROOT).apply {
        decimalSeparator = decimal
        groupingSeparator = groupMark(decimal)
        exponentSeparator = "e"
    }
    val pattern = if (value != 0.0 && abs(value) < 1e-6 || abs(value) >= 1e15) "0.#####E0" else "#,##0.##########"
    return DecimalFormat(pattern, symbols).format(value + 0.0) // -0.0 + 0.0 is 0.0: never "-0"
}

private fun groupMark(decimal: Char) = if (decimal == ',') '.' else ','

/**
 * Number [t] with [decimal] as its decimal mark and the other mark grouping thousands ("1.500.000", "1.500,50"), or
 * null. Grouping starts with one to three digits, not a 0; else a lone mark of the other kind is a decimal mark too:
 * "1.5", "0.500" and "1234.567" read 1,5, 0,5 and 1234,567.
 */
private fun number(t: String, decimal: Char): Double? {
    val group = groupMark(decimal)
    val plain = when {
        Regex("""[1-9]\d{0,2}(\$group\d{3})+(\$decimal\d*)?""").matches(t) -> t.replace(group.toString(), "")
        t.count { it == group } == 1 && decimal !in t -> t.replace(group, decimal)
        else -> t
    }.replace(decimal, '.')
    return plain.takeIf { it.all { c -> c.isDigit() || c == '.' } }?.toDoubleOrNull() // "1..2" and "" fail here
}

/**
 * A unit search converts: [factor] of its dimension's base unit (m, g, ml, km/h, m²; temperatures go by formula) and
 * [pair], the symbol of its metric or imperial counterpart. Kitchen units have no [symbol]: they show the word typed.
 */
private class Measure(val symbol: String, val dimension: String, val factor: Double, val pair: String, vararg val names: String)

/** Millilitres in the cup search converts with: the US one, as recipes online mean it. Argentine cups vary (200 to 250 ml), so the answer names it. */
const val US_CUP_ML = 236.5882365

private val CUP = Measure("", "volume", US_CUP_ML, "ml", "taza", "tazas", "cup", "cups")

private val MEASURES = listOf(
    Measure("m", "length", 1.0, "ft", "metro", "metros", "meter", "meters", "metre", "metres"),
    Measure("km", "length", 1000.0, "mi", "kilometro", "kilometros", "kilometer", "kilometers", "kilometre", "kilometres"),
    Measure("cm", "length", 0.01, "in", "centimetro", "centimetros", "centimeter", "centimeters", "centimetre", "centimetres"),
    Measure("mm", "length", 0.001, "in", "milimetro", "milimetros", "millimeter", "millimeters", "millimetre", "millimetres"),
    Measure("mi", "length", 1609.344, "km", "milla", "millas", "mile", "miles"),
    Measure("yd", "length", 0.9144, "m", "yarda", "yardas", "yard", "yards"),
    Measure("ft", "length", 0.3048, "m", "pie", "pies", "foot", "feet"),
    Measure("in", "length", 0.0254, "cm", "pulgada", "pulgadas", "inch", "inches"),
    Measure("g", "mass", 1.0, "oz", "gramo", "gramos", "gram", "grams", "gr"),
    Measure("kg", "mass", 1000.0, "lb", "kilo", "kilos", "kilogramo", "kilogramos", "kilogram", "kilograms"),
    Measure("lb", "mass", 453.59237, "kg", "lbs", "libra", "libras", "pound", "pounds"),
    Measure("oz", "mass", 28.349523125, "g", "onza", "onzas", "ounce", "ounces"),
    Measure("ml", "volume", 1.0, "fl oz", "mililitro", "mililitros", "milliliter", "milliliters", "millilitre", "millilitres", "cc"),
    Measure("l", "volume", 1000.0, "fl oz", "lt", "litro", "litros", "liter", "liters", "litre", "litres"),
    CUP,
    Measure("", "volume", 14.78676478125, "ml", "cda", "cdas", "cucharada", "cucharadas", "tbsp", "tablespoon", "tablespoons"),
    Measure("", "volume", 4.92892159375, "ml", "cdta", "cdtas", "cucharadita", "cucharaditas", "tsp", "teaspoon", "teaspoons"),
    Measure("fl oz", "volume", 29.5735295625, "ml", "floz", "onza liquida", "onzas liquidas", "fluid ounce", "fluid ounces"),
    Measure("°C", "temperature", 1.0, "°F", "c", "ºc", "celsius", "centigrados"),
    Measure("°F", "temperature", 1.0, "°C", "f", "ºf", "fahrenheit"),
    Measure("km/h", "speed", 1.0, "mph", "kmh", "kph", "kilometros por hora", "kilometers per hour"),
    Measure("mph", "speed", 1.609344, "km/h", "millas por hora", "miles per hour"),
    Measure("m/s", "speed", 3.6, "km/h", "metros por segundo", "meters per second"),
    Measure("m²", "area", 1.0, "ft²", "m2", "metro cuadrado", "metros cuadrados", "square meter", "square meters", "sq m"),
    Measure("km²", "area", 1e6, "mi²", "km2"),
    Measure("ft²", "area", 0.09290304, "m²", "ft2", "sq ft", "pie cuadrado", "pies cuadrados", "square foot", "square feet"),
    Measure("mi²", "area", 2589988.110336, "km²", "mi2", "sq mi"),
    Measure("ha", "area", 1e4, "ac", "hectarea", "hectareas", "hectare", "hectares"),
    Measure("ac", "area", 4046.8564224, "ha", "acre", "acres"),
)

private val BY_NAME = MEASURES.flatMap { m -> (m.names.toList() + m.symbol).filter(String::isNotEmpty).map { fold(it) to m } }.toMap()
private val OZ = BY_NAME.getValue("oz")
private val FLUID_OZ = BY_NAME.getValue("fl oz")
private val QUANTITY = Regex("""(-?[\d.,]+) ?(.+)""")
private val CONNECTED = Regex("""(.+?) (?:a|en|to|in) (.+)""")

/** "5 km a mi", "2 tazas en ml" or "70 kg" (to its counterpart), to four significant digits at least. */
private fun convert(q: String, decimal: Char): Answer.Value? {
    val (amount, units) = QUANTITY.matchEntire(fold(q).replace(Regex("""\s+"""), " "))?.destructured ?: return null
    val n = (number(amount.removePrefix("-"), decimal) ?: return null) * if (amount.startsWith('-')) -1 else 1
    val alone = BY_NAME[units]
    val (first, second, typed) = if (alone != null) {
        Triple(alone, MEASURES.first { it.symbol == alone.pair }, "")
    } else {
        val (a, b) = CONNECTED.matchEntire(units)?.destructured ?: return null
        Triple(BY_NAME[a] ?: return null, BY_NAME[b] ?: return null, b)
    }
    // Next to a volume, oz is the fluid ounce: "8 oz a ml", "1 taza a oz".
    val from = if (first === OZ && second.dimension == "volume") FLUID_OZ else first
    val to = if (second === OZ && first.dimension == "volume") FLUID_OZ else second
    if (from.dimension != to.dimension) return null
    val value = when {
        from.dimension != "temperature" -> n * from.factor / to.factor
        from == to -> n
        from.symbol == "°C" -> n * 9 / 5 + 32
        else -> (n - 32) * 5 / 9
    }
    return Answer.Value(formatNumber(significant(value), decimal) + " " + to.symbol.ifEmpty { typed }, usCup = from === CUP || to === CUP)
}

/** [v] to four significant digits, or its whole part where that is longer. */
private fun significant(v: Double): Double {
    if (v == 0.0 || !v.isFinite()) return v
    return BigDecimal(v).round(MathContext(maxOf(4, floor(log10(abs(v))).toInt() + 1))).toDouble()
}

/**
 * The value of a search query that is a sum, like "125*3" or "1.500,5 + 2" (null for anything else, a lone number
 * included). [decimal] is the decimal mark and the other one groups thousands (see [number]); x × ÷ ^ % and
 * parentheses work as on a calculator, so "1500 + 21%" adds 21% of 1500.
 */
fun calc(query: String, decimal: Char): Double? {
    val s = query.filterNot { it == ' ' }.map { CALC_ALIASES[it] ?: it }.joinToString("")
    // The parser recurses per parenthesis or power, so pasted junk could overflow the stack.
    if (s.length > 100 || s.drop(1).none { it in "+-*/^%" }) return null
    return try {
        Calc(s, decimal).run { sum().takeIf { done() && it.isFinite() } }
    } catch (e: NumberFormatException) {
        null
    }
}

private val CALC_ALIASES = mapOf('x' to '*', '×' to '*', '÷' to '/')
private val PERCENT = Regex("""[\d.,]+%""")

/** Recursive descent over [s]: sum = term (± term)*, term = power (× power)*, power = factor (^ power)?. */
private class Calc(private val s: String, private val decimal: Char) {
    private var i = 0

    fun done() = i == s.length

    private fun next(vararg ops: Char) = s.getOrNull(i)?.takeIf { it in ops }?.also { i++ }

    fun sum(): Double {
        var v = term()
        while (true) {
            val op = next('+', '-') ?: return v
            val start = i
            val t = term()
            val share = if (PERCENT.matches(s.substring(start, i))) v * t else t // "1500 + 21%": 21% of 1500
            v = if (op == '+') v + share else v - share
        }
    }

    private fun term(): Double {
        var v = power()
        while (true) v = when (next('*', '/')) {
            '*' -> v * power()
            '/' -> v / power()
            else -> return v
        }
    }

    private fun power(): Double {
        val base = factor()
        return if (next('^') != null) base.pow(power()) else base
    }

    private fun factor(): Double {
        val v = when {
            next('-') != null -> return -power() // -2^2 is -4, as on paper
            next('(') != null -> sum().also { next(')') ?: throw NumberFormatException("no closing parenthesis") }
            else -> {
                val start = i
                while (s.getOrNull(i)?.let { it.isDigit() || it == '.' || it == ',' } == true) i++
                number(s.substring(start, i), decimal) ?: throw NumberFormatException("not a number")
            }
        }
        return if (next('%') != null) v / 100 else v
    }
}

/** Direction of a drag by its dominant axis, or null when it is shorter than [minDistance]. */
fun swipeOf(dx: Float, dy: Float, minDistance: Float): Swipe? = when {
    maxOf(abs(dx), abs(dy)) < minDistance -> null
    abs(dx) > abs(dy) -> if (dx > 0) Swipe.RIGHT else Swipe.LEFT
    else -> if (dy > 0) Swipe.DOWN else Swipe.UP
}
