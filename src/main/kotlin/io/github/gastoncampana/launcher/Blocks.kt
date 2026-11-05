package io.github.gastoncampana.launcher

import org.json.JSONArray
import org.json.JSONObject
import java.net.IDN
import java.time.DayOfWeek
import java.time.LocalDateTime
import java.time.temporal.ChronoUnit
import kotlin.math.abs

// Blocks: apps and sites that cannot open during weekly time ranges. Pure logic; Blocking.kt enforces it.

const val DAY_MINUTES = 1440
const val WEEK_MINUTES = 7 * DAY_MINUTES

/** Least time in a row a block must leave free each week: one locked all week round could only go with a factory reset. */
const val FREE_MINUTES = 60

/** How long a browser may show no address bar after coming to the front, during a block with sites, before it goes. */
const val UNREAD_GRACE_MS = 2500L

/** How far the wall clock may stray from the elapsed one before a change of it is ignored during a block. */
const val MAX_DRIFT_MS = 120_000L

/**
 * A weekly time range: from [start] to [end] (minutes since midnight) on each of [days], the days it starts on. An [end]
 * before [start] runs into the next day ("22:00 to 07:00"); midnight as an end is 1440 (see [span]).
 */
data class Span(val days: Set<DayOfWeek>, val start: Int, val end: Int) {
    val valid get() = days.isNotEmpty() && start in 0..<DAY_MINUTES && end in 1..DAY_MINUTES && start != end

    /** The minutes of the week it covers, Monday 00:00 being 0, as from-to pairs (to excluded); split at the week's end. */
    fun intervals(): List<Pair<Int, Int>> = days.flatMap { day ->
        val from = (day.value - 1) * DAY_MINUTES + start
        val to = (day.value - 1) * DAY_MINUTES + end + if (end < start) DAY_MINUTES else 0
        if (to <= WEEK_MINUTES) listOf(from to to) else listOf(from to WEEK_MINUTES, 0 to to - WEEK_MINUTES)
    }
}

/** A [Span], with an [end] of 0 read as midnight at the end of the day. */
fun span(days: Set<DayOfWeek>, start: Int, end: Int) = Span(days, start, if (end == 0) DAY_MINUTES else end)

/**
 * A block: the apps of [packages] cannot open, and browsers cannot show [sites] (host names, see [site]), during its
 * [spans]. While one of them runs it is locked: it can only grow (see [tightened]) and cannot be removed.
 */
data class Rule(val id: String, val name: String, val packages: Set<String>, val sites: Set<String>, val spans: List<Span>)

/** The minute of the week [t] falls on, Monday 00:00 being 0. */
fun minuteOfWeek(t: LocalDateTime) = (t.dayOfWeek.value - 1) * DAY_MINUTES + t.hour * 60 + t.minute

fun Rule.activeAt(t: LocalDateTime): Boolean {
    val m = minuteOfWeek(t)
    return spans.any { s -> s.intervals().any { (from, to) -> m in from..<to } }
}

/** Each minute of the week, whether [spans] cover it. */
fun coverage(spans: Collection<Span>): BooleanArray = BooleanArray(WEEK_MINUTES).also { c ->
    for (s in spans) for ((from, to) in s.intervals()) c.fill(true, from, to)
}

/**
 * Until when the [rules] that [applies] to cover [t] without a free minute between them (several may run back to back),
 * or null when none covers [t]. A week ahead at most: rules may leave no minute free between them.
 */
fun blockedUntil(rules: List<Rule>, t: LocalDateTime, applies: (Rule) -> Boolean = { true }): LocalDateTime? {
    val c = coverage(rules.filter(applies).flatMap { it.spans })
    val m = minuteOfWeek(t)
    if (!c[m]) return null
    var k = 1
    while (k < WEEK_MINUTES && c[(m + k) % WEEK_MINUTES]) k++
    return t.truncatedTo(ChronoUnit.MINUTES).plusMinutes(k.toLong())
}

/** The first minute after [t] at which one of [rules] starts or ends, to look again then; null when they have no spans. */
fun nextChange(rules: List<Rule>, t: LocalDateTime): LocalDateTime? {
    // From the spans, not their intervals: where one is split at the week's end nothing starts or ends.
    val marks = rules.flatMap { it.spans }.flatMap { s ->
        s.days.flatMap { day ->
            val from = (day.value - 1) * DAY_MINUTES + s.start
            listOf(from, (from - s.start + s.end + if (s.end < s.start) DAY_MINUTES else 0) % WEEK_MINUTES)
        }
    }.toSortedSet()
    if (marks.isEmpty()) return null
    val m = minuteOfWeek(t)
    val next = marks.firstOrNull { it > m } ?: (marks.first() + WEEK_MINUTES)
    return t.truncatedTo(ChronoUnit.MINUTES).plusMinutes((next - m).toLong())
}

/** The longest run of minutes [c] leaves free, a run across Sunday night into Monday counted whole. */
fun freeRun(c: BooleanArray): Int {
    val last = c.indexOfLast { it }
    if (last < 0) return c.size
    var best = 0
    var run = 0
    for (i in 1..c.size) {
        run = if (c[(last + i) % c.size]) 0 else run + 1
        best = maxOf(best, run)
    }
    return best
}

/** Whether [new] blocks everything [old] does, and at least when it does: what a running block may become. */
fun tightened(old: Rule, new: Rule): Boolean {
    if (!new.packages.containsAll(old.packages) || !new.sites.containsAll(old.sites)) return false
    val before = coverage(old.spans)
    val after = coverage(new.spans)
    return before.indices.all { !before[it] || after[it] }
}

enum class RuleProblem { NOTHING_BLOCKED, NO_SPANS, BAD_SPAN, NO_FREE_HOUR, LOOSENED }

/** What keeps [rule] from being saved at [t] in place of [old] (null: a new one), or null when nothing does. */
fun problemSaving(rule: Rule, old: Rule?, t: LocalDateTime): RuleProblem? = when {
    rule.packages.isEmpty() && rule.sites.isEmpty() -> RuleProblem.NOTHING_BLOCKED
    rule.spans.isEmpty() -> RuleProblem.NO_SPANS
    rule.spans.any { !it.valid } -> RuleProblem.BAD_SPAN
    freeRun(coverage(rule.spans)) < FREE_MINUTES -> RuleProblem.NO_FREE_HOUR
    old != null && old.activeAt(t) && !tightened(old, rule) -> RuleProblem.LOOSENED
    else -> null
}

/** Whether [rule] may be removed at [t]: not while it runs. */
fun canRemove(rule: Rule, t: LocalDateTime) = !rule.activeAt(t)

// --- Sites ---

/** A host name in ASCII: labels of letters, digits and dashes, the last one starting with a letter (so no IP address). */
private val HOST = Regex("""(?:[a-z0-9](?:[a-z0-9-]*[a-z0-9])?\.)+[a-z][a-z0-9-]*[a-z0-9]""")

private val SCHEME = Regex("^[A-Za-z][A-Za-z0-9+.-]*://")

/** Bidi marks and spaces that browsers put around an address (Samsung Internet starts it with U+200E). */
private val INVISIBLE = Regex("[\u200b-\u200f\u202a-\u202e\u2066-\u2069\u00a0\ufeff]")

/**
 * The host of an address such as "https://user@Bücher.de:8080/x?y", in lower-case ASCII ("xn--bcher-kva.de"), or
 * null when it has none. Browsers show international names as they are written; a block stores their ASCII form.
 */
private fun hostOf(address: String): String? {
    val host = SCHEME.replace(address, "").substringBefore('/').substringBefore('?').substringBefore('#').substringAfterLast('@').substringBefore(':')
    val ascii = try {
        IDN.toASCII(host, IDN.ALLOW_UNASSIGNED)
    } catch (e: IllegalArgumentException) { // a label too long or malformed
        return null
    }
    return ascii.lowercase().trimEnd('.').takeIf(HOST::matches)
}

/**
 * What the owner typed as a site to block, as the host it matches: "https://www.YouTube.com/watch" is "youtube.com".
 * Null for anything that is not a host name.
 */
fun site(typed: String): String? = hostOf(INVISIBLE.replace(typed, "").trim())?.removePrefix("www.")?.takeIf { '.' in it }

/** Whether [host] is one of [sites] or under one: "m.youtube.com" is under "youtube.com". */
fun blockedSite(host: String, sites: Collection<String>) = sites.any { host == it || host.endsWith(".$it") }

/** The hosts written in [text], word by word: an address bar's text, or a description that also holds a page title. */
fun hostsIn(text: CharSequence?): List<String> {
    if (text.isNullOrEmpty()) return emptyList()
    return INVISIBLE.replace(text, " ").split(' ', '\n', '\t').mapNotNull { word -> hostOf(word.trim('(', ')', '.', ',', ';')) }
}

/** A node of a browser's window as the blocking service sees it: [id] without its package, [centerY] in px. */
data class BarNode(
    val id: String?,
    val text: CharSequence?,
    val desc: CharSequence?,
    val editable: Boolean,
    val focused: Boolean,
    val centerY: Float,
    /** Inside the page itself: its text is the site's, never the browser's. */
    val web: Boolean,
)

sealed interface Address {
    /** The hosts the address bar shows: more than one when a page title shares it (a Custom Tab's in Firefox). */
    data class Site(val hosts: Set<String>) : Address

    /** An address bar that holds no site: the browser's own page (a new tab, its home). */
    data object Own : Address

    /** The owner is typing in the address bar: what it holds is not the page. */
    data object Typing : Address

    /** No address bar on screen: hidden while scrolling, full screen, or a browser this cannot read. */
    data object Unknown : Address
}

/** View ids an address bar has in the browsers tried (url_bar, url_field, omnibarTextInput, location_bar_edit_text, ADDRESSBAR_URL_BOX, customTabDomain). */
private val BAR_ID = Regex("url|omnibar|location_bar|address|domain", RegexOption.IGNORE_CASE)

/** Address bars sit at the top of the screen, or at the bottom (Edge), never in the middle: there are home-page tiles and suggestions. */
private const val TOP_BAND = 0.25f
private const val BOTTOM_BAND = 0.80f

/**
 * What the address bar among [nodes] (a browser's windows, popups included) shows, on a screen [height] px tall. Only
 * nodes outside the page count, with an address bar's id, at the top or bottom of the screen; their text and their
 * description both (Firefox writes the address in the description).
 */
fun readAddress(nodes: List<BarNode>, height: Int): Address {
    var bar = false
    val hosts = LinkedHashSet<String>()
    for (n in nodes) {
        if (n.web) continue
        val edge = n.centerY <= TOP_BAND * height || n.centerY >= BOTTOM_BAND * height
        val isBar = n.id?.let(BAR_ID::containsMatchIn) == true
        // Only a field of the browser's own: one of the page's (a login) never stops the block.
        if (n.editable && n.focused && (isBar || edge)) return Address.Typing
        if (isBar && edge) {
            bar = true
            hosts += hostsIn(n.text) + hostsIn(n.desc)
        }
    }
    return if (hosts.isNotEmpty()) Address.Site(hosts) else if (bar) Address.Own else Address.Unknown
}

// --- A clock the owner cannot move ---

/** The time a block reads, [epochMs], when the time since boot was [elapsedMs] on boot number [boot]. */
data class Anchor(val epochMs: Long, val elapsedMs: Long, val boot: Int)

/**
 * The time blocks go by, from the wall clock [wallMs]: during a block ([running] at the time the elapsed clock gives)
 * a change of the wall clock by more than [MAX_DRIFT_MS] is ignored, so setting it forward does not end the block. After
 * a reboot the elapsed clock starts over: the wall clock counts again, but never earlier than the last time read.
 */
fun trustedTime(wallMs: Long, elapsedMs: Long, boot: Int, anchor: Anchor?, running: (Long) -> Boolean): Anchor {
    if (anchor == null) return Anchor(wallMs, elapsedMs, boot)
    if (anchor.boot != boot || elapsedMs < anchor.elapsedMs) return Anchor(maxOf(wallMs, anchor.epochMs), elapsedMs, boot)
    val expected = anchor.epochMs + (elapsedMs - anchor.elapsedMs)
    return Anchor(if (abs(wallMs - expected) > MAX_DRIFT_MS && running(expected)) expected else wallMs, elapsedMs, boot)
}

// --- Storage: prefs "blocks", apart from the layout so backups and Nova imports never touch them ---

fun rulesToJson(rules: List<Rule>): String = JSONArray(rules.map { r ->
    JSONObject()
        .put("id", r.id)
        .put("name", r.name)
        .put("packages", JSONArray(r.packages.toList()))
        .put("sites", JSONArray(r.sites.toList()))
        .put("spans", JSONArray(r.spans.map { s -> JSONObject().put("days", JSONArray(s.days.map { it.value }.sorted())).put("start", s.start).put("end", s.end) }))
}).toString()

fun parseRules(json: String): List<Rule> {
    val rules = JSONArray(json)
    return List(rules.length()) { i ->
        val r = rules.getJSONObject(i)
        val spans = r.getJSONArray("spans")
        Rule(
            r.getString("id"),
            r.optString("name"),
            r.getJSONArray("packages").let { a -> List(a.length()) { a.getString(it) }.toSet() },
            r.getJSONArray("sites").let { a -> List(a.length()) { a.getString(it) }.toSet() },
            List(spans.length()) { j ->
                val s = spans.getJSONObject(j)
                val days = s.getJSONArray("days").let { a -> List(a.length()) { DayOfWeek.of(a.getInt(it)) }.toSet() }
                Span(days, s.getInt("start"), s.getInt("end"))
            },
        )
    }
}

fun anchorToJson(a: Anchor): String = JSONObject().put("epoch", a.epochMs).put("elapsed", a.elapsedMs).put("boot", a.boot).toString()

fun parseAnchor(json: String): Anchor = JSONObject(json).let { Anchor(it.getLong("epoch"), it.getLong("elapsed"), it.getInt("boot")) }
