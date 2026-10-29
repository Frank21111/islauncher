package io.github.gastoncampana.launcher

import org.json.JSONArray
import org.json.JSONObject
import java.text.Normalizer
import kotlin.math.abs
import kotlin.math.ceil
import kotlin.math.pow

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
)

fun <T> List<T>.replaced(index: Int, f: (T) -> T): List<T> = mapIndexed { i, x -> if (i == index) f(x) else x }

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

private fun packageKey(id: AppId) = id.substringBefore('/') + "#" + id.substringAfterLast('#')

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
 * else on the first page that does, else on a new page.
 */
private fun Layout.placed(home: Int?, item: Item): Layout {
    val want = item.cell.copy(w = minOf(item.cell.w, columns.toFloat()), h = minOf(item.cell.h, rows.toFloat()))
    for (page in listOfNotNull(home) + desktop.indices.filter { it != home }) {
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

/**
 * A layout read from a backup file. Widgets whose id is not one of this install's ([live], id to provider) for
 * the same provider become placeholders to set up again; items off the grid are dropped.
 */
fun Layout.restored(live: Map<Int, String?>): Layout {
    // Every page stays composed, so a file with thousands would make each start run out of memory.
    val pages = desktop.take(MAX_PAGES).map { page ->
        page.filter { fits(it.cell) }
            .map { if (it is Item.Widget && live[it.widgetId] != it.provider) it.copy(widgetId = 0) else it }
    }
    return copy(dock = dock.take(MAX_PAGES), desktop = pages, defaultPage = defaultPage.takeIf { it in pages.indices } ?: 0)
}

/** Most desktop or dock pages a restored backup keeps. */
const val MAX_PAGES = 20

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

/** Prefix matches first, then word-start matches, then substrings; input order is kept within each group. */
fun <T> search(items: List<T>, query: String, label: (T) -> String): List<T> {
    val q = fold(query.trim())
    if (q.isEmpty()) return items
    return items.mapNotNull { item ->
        val l = fold(label(item))
        when {
            l.startsWith(q) -> 0
            l.split(' ', '-', '.', '_').any { it.startsWith(q) } -> 1
            q in l -> 2
            else -> null
        }?.let { it to item }
    }.sortedBy { it.first }.map { it.second }
}

/**
 * The value of a search query that is a sum, like "125*3" or "1,5 + 2" (null for anything else, a lone number
 * included). Both '.' and ',' are decimal points; x × ÷ ^ % and parentheses work as on a calculator.
 */
fun calc(query: String): Double? {
    val s = query.filterNot { it == ' ' }.map { CALC_ALIASES[it] ?: it }.joinToString("")
    // The parser recurses per parenthesis or power, so pasted junk could overflow the stack.
    if (s.length > 100 || s.drop(1).none { it in "+-*/^%" }) return null
    return try {
        Calc(s).run { sum().takeIf { done() && it.isFinite() } }
    } catch (e: NumberFormatException) {
        null
    }
}

private val CALC_ALIASES = mapOf('x' to '*', '×' to '*', '÷' to '/', ',' to '.')

/** Recursive descent over [s]: sum = term (± term)*, term = power (× power)*, power = factor (^ power)?. */
private class Calc(private val s: String) {
    private var i = 0

    fun done() = i == s.length

    private fun next(vararg ops: Char) = s.getOrNull(i)?.takeIf { it in ops }?.also { i++ }

    fun sum(): Double {
        var v = term()
        while (true) v = when (next('+', '-')) {
            '+' -> v + term()
            '-' -> v - term()
            else -> return v
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
                while (s.getOrNull(i)?.let { it.isDigit() || it == '.' } == true) i++
                s.substring(start, i).toDouble() // throws when empty or "1..2"
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
