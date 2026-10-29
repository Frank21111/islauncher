package io.github.gastoncampana.launcher

import org.json.JSONArray
import org.json.JSONObject
import java.text.Normalizer
import kotlin.math.abs
import kotlin.math.pow

/** Stable id of a launchable activity: "<flattened component>#<user serial number>". */
typealias AppId = String

const val DOCK_SIZE = 5

/** Columns and rows of a desktop page. Items may sit on half cells. */
const val GRID = 5

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
    val dock: List<List<DockIcon>> = listOf(List(DOCK_SIZE) { DockIcon() }),
    val desktop: List<List<Item>> = listOf(emptyList()),
    val defaultPage: Int = 0,
    val tabs: List<Tab> = listOf(Tab("Apps")),
    val doubleTap: Target? = null,
    /** Left out of every drawer tab; search still finds them, as in Nova. */
    val hidden: Set<AppId> = emptySet(),
    /** "Venenos": apps that open only after a pause. */
    val poisons: Set<AppId> = emptySet(),
)

fun <T> List<T>.replaced(index: Int, f: (T) -> T): List<T> = mapIndexed { i, x -> if (i == index) f(x) else x }

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

/** The first free w×h spot on a page, scanning rows in half-cell steps and columns in whole cells. */
fun freeCell(items: List<Item>, w: Float, h: Float): Cell? {
    for (y2 in 0..((GRID - h) * 2).toInt()) for (x in 0..(GRID - w).toInt()) {
        val cell = Cell(x.toFloat(), y2 / 2f, w, h)
        if (items.none { it.cell.overlaps(cell) }) return cell
    }
    return null
}

/**
 * Moves [item] from page [from] (null: a new item) to the free half-cell spot of page [to] nearest [want];
 * unchanged when none is free.
 */
fun Layout.moved(from: Int?, item: Item, to: Int, want: Cell): Layout {
    if ((from != null && item !in desktop.getOrNull(from).orEmpty()) || to !in desktop.indices) return this
    // Only the moved item leaves page [to]: an equal one there (a twin, or the same app the drawer drops) stays.
    val others = if (from == to) desktop[to] - item else desktop[to]
    val (w, h) = item.cell.w to item.cell.h
    val spots = (0..((GRID - h) * 2).toInt()).flatMap { y2 -> (0..((GRID - w) * 2).toInt()).map { x2 -> Cell(x2 / 2f, y2 / 2f, w, h) } }
    val cell = spots.filter { spot -> others.none { it.cell.overlaps(spot) } }
        .minByOrNull { (it.x - want.x) * (it.x - want.x) + (it.y - want.y) * (it.y - want.y) } ?: return this
    val moved = when (item) {
        is Item.Icon -> item.copy(cell = cell)
        is Item.Widget -> item.copy(cell = cell)
    }
    return copy(desktop = desktop.mapIndexed { i, page -> if (i == to) others + moved else if (i == from) page - item else page })
}

/**
 * A layout read from a backup file. Widgets whose id is not one of this install's ([live], id to provider) for
 * the same provider become placeholders to set up again; items off the grid are dropped.
 */
fun Layout.restored(live: Map<Int, String?>): Layout {
    // Every page stays composed, so a file with thousands would make each start run out of memory.
    val pages = desktop.take(MAX_PAGES).map { page ->
        page.filter { it.cell.let { (x, y, w, h) -> w > 0 && h > 0 && x >= 0 && y >= 0 && x + w <= GRID && y + h <= GRID } } // NaN fails too
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
    .toString()

fun parseLayout(json: String): Layout {
    val root = JSONObject(json)
    // Every dock page has exactly DOCK_SIZE icons, so the UI can index it safely.
    val dock = root.getJSONArray("dock").arrays().map { page ->
        (page.objects().map { DockIcon(it.target("tap"), it.target("up"), it.target("down")) } + List(DOCK_SIZE) { DockIcon() }).take(DOCK_SIZE)
    }.ifEmpty { Layout().dock }
    val desktop = root.getJSONArray("desktop").arrays().map { page -> page.objects().map { it.item() } }.ifEmpty { Layout().desktop }
    val tabs = root.getJSONArray("tabs").objects().map {
        Tab(it.getString("name"), it.optJSONArray("apps")?.strings()?.toSet(), it.optBoolean("hide"))
    }
    val defaultPage = root.optInt("defaultPage").takeIf { it in desktop.indices } ?: 0
    val hidden = root.optJSONArray("hidden")?.strings()?.toSet().orEmpty()
    val poisons = root.optJSONArray("poisons")?.strings()?.toSet().orEmpty()
    return Layout(dock, desktop, defaultPage, tabs, root.target("doubleTap"), hidden, poisons)
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
