package io.github.gastoncampana.launcher

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.DayOfWeek.FRIDAY
import java.time.DayOfWeek.MONDAY
import java.time.DayOfWeek.SATURDAY
import java.time.DayOfWeek.SUNDAY
import java.time.DayOfWeek.TUESDAY
import java.time.DayOfWeek.WEDNESDAY
import java.time.DayOfWeek
import java.time.LocalDateTime

class BlocksTest {
    @Test
    fun setupRequiresAllUserAccessButNotSecureSettings() {
        for (secure in listOf(false, true)) {
            for (accessibility in listOf(false, true)) {
                for (notifications in listOf(false, true)) {
                    for (admin in listOf(false, true)) {
                        assertEquals(accessibility && notifications && admin,
                            BlockSetup(secure, accessibility, notifications, admin).complete)
                    }
                }
            }
        }
    }

    private val weekdays = setOf(MONDAY, TUESDAY, WEDNESDAY, DayOfWeek.THURSDAY, FRIDAY)
    private val work = Rule("w", "Trabajo", setOf("com.instagram.android"), setOf("youtube.com"), listOf(span(weekdays, 9 * 60, 18 * 60)))
    private val night = Rule("n", "Noche", setOf("com.instagram.android"), emptySet(), listOf(span(DayOfWeek.entries.toSet(), 23 * 60, 7 * 60)))

    /** 2026-11-02 is a Monday. */
    private fun at(day: Int, hour: Int, minute: Int = 0) = LocalDateTime.of(2026, 11, 2 + day, hour, minute)

    @Test
    fun spanRunsFromItsStartUntilJustBeforeItsEnd() {
        assertFalse(work.activeAt(at(0, 8, 59)))
        assertTrue(work.activeAt(at(0, 9)))
        assertTrue(work.activeAt(at(4, 17, 59)))
        assertFalse(work.activeAt(at(0, 18)))
        assertFalse(work.activeAt(at(5, 12))) // Saturday
    }

    @Test
    fun overnightSpanRunsIntoTheNextDayAndAroundTheWeek() {
        assertTrue(night.activeAt(at(0, 23, 30)))
        assertTrue(night.activeAt(at(1, 6, 59)))
        assertFalse(night.activeAt(at(1, 7)))
        // Sunday 23:00 runs into Monday morning: the end of the week wraps to its start.
        assertTrue(night.activeAt(at(6, 23, 30)))
        assertTrue(night.activeAt(at(0, 3)))
    }

    @Test
    fun midnightAsAnEndIsTheEndOfTheDay() {
        val evening = span(setOf(SATURDAY), 20 * 60, 0)
        assertEquals(DAY_MINUTES, evening.end)
        assertTrue(evening.valid)
        assertEquals(listOf(5 * DAY_MINUTES + 1200 to 6 * DAY_MINUTES), evening.intervals())
        assertFalse(Span(setOf(SATURDAY), 600, 600).valid)
        assertFalse(Span(emptySet(), 600, 700).valid)
    }

    @Test
    fun blockedUntilJoinsBlocksThatRunBackToBack() {
        val evening = night.copy(id = "e", spans = listOf(span(weekdays, 18 * 60, 23 * 60)))
        val rules = listOf(work, evening, night)
        val instagram = { r: Rule -> "com.instagram.android" in r.packages }
        // Monday 10:00: work until 18, the evening until 23, the night until Tuesday 7.
        assertEquals(at(1, 7), blockedUntil(rules, at(0, 10, 15), instagram))
        assertEquals(at(0, 18), blockedUntil(rules, at(0, 10)) { "youtube.com" in it.sites })
        assertNull(blockedUntil(rules, at(5, 12), instagram))
    }

    @Test
    fun nextChangeIsTheNextStartOrEndOfAnySpan() {
        val rules = listOf(work, night)
        assertEquals(at(0, 9), nextChange(rules, at(0, 7, 30)))
        assertEquals(at(0, 18), nextChange(rules, at(0, 9)))
        assertEquals(at(0, 23), nextChange(rules, at(0, 18, 1)))
        // Sunday night: the next one is Monday 7:00, across the week's end.
        assertEquals(at(7, 7), nextChange(rules, at(6, 23, 30)))
        assertNull(nextChange(emptyList(), at(0, 0)))
    }

    @Test
    fun aBlockMustLeaveAnHourFreeEachWeek() {
        val always = work.copy(spans = listOf(span(DayOfWeek.entries.toSet(), 0, DAY_MINUTES)))
        assertEquals(RuleProblem.NO_FREE_HOUR, problemSaving(always, null, at(0, 12)))
        // 23 hours a day, the free hour each night: allowed.
        val almost = work.copy(spans = listOf(span(DayOfWeek.entries.toSet(), 0, 23 * 60)))
        assertNull(problemSaving(almost, null, at(0, 12)))
        // Free only from Sunday 23:30 to Monday 0:30: one hour across the week's end counts.
        val wrap = work.copy(spans = listOf(Span(setOf(MONDAY), 30, DAY_MINUTES), Span(DayOfWeek.entries.toSet() - MONDAY - SUNDAY, 0, DAY_MINUTES), Span(setOf(SUNDAY), 0, 23 * 60 + 30)))
        assertEquals(60, freeRun(coverage(wrap.spans)))
        assertNull(problemSaving(wrap, null, at(0, 12)))
    }

    @Test
    fun aBlockNeedsSomethingToBlockAndValidSpans() {
        assertEquals(RuleProblem.NOTHING_BLOCKED, problemSaving(work.copy(packages = emptySet(), sites = emptySet()), null, at(0, 12)))
        assertEquals(RuleProblem.NO_SPANS, problemSaving(work.copy(spans = emptyList()), null, at(0, 12)))
        assertEquals(RuleProblem.BAD_SPAN, problemSaving(work.copy(spans = listOf(Span(emptySet(), 60, 120))), null, at(0, 12)))
        assertNull(problemSaving(work.copy(packages = emptySet()), null, at(0, 12)))
    }

    @Test
    fun aRunningBlockCanOnlyGrow() {
        val running = at(0, 12)
        val more = work.copy(name = "Otro", packages = work.packages + "com.twitter.android", sites = work.sites + "x.com",
            spans = work.spans + span(setOf(SATURDAY), 10 * 60, 12 * 60))
        assertNull(problemSaving(more, work, running))
        assertEquals(RuleProblem.LOOSENED, problemSaving(work.copy(packages = emptySet()), work, running))
        assertEquals(RuleProblem.LOOSENED, problemSaving(work.copy(sites = emptySet()), work, running))
        // Shorter by a minute is looser, even with the same number of spans.
        assertEquals(RuleProblem.LOOSENED, problemSaving(work.copy(spans = listOf(span(weekdays, 9 * 60, 17 * 60 + 59))), work, running))
        // A longer span in place of the old one is not.
        assertNull(problemSaving(work.copy(spans = listOf(span(weekdays, 8 * 60, 19 * 60))), work, running))
        // Outside its spans anything goes, and it can be removed.
        assertNull(problemSaving(work.copy(packages = emptySet()), work, at(5, 12)))
        assertFalse(canRemove(work, running))
        assertTrue(canRemove(work, at(5, 12)))
    }

    @Test
    fun sitesAreStoredAsTheirHostWithoutWww() {
        assertEquals("youtube.com", site("https://www.YouTube.com/watch?v=abc"))
        assertEquals("m.youtube.com", site(" m.youtube.com:443/feed "))
        assertEquals("xn--bcher-kva.de", site("Bücher.de"))
        assertEquals("example.com", site("http://user@example.com/" + "a".repeat(100)))
        assertNull(site("youtube"))
        assertNull(site("192.168.0.1"))
        assertNull(site("www.com"))
        assertNull(site(""))
    }

    @Test
    fun aSiteBlocksItsSubdomainsOnly() {
        assertTrue(blockedSite("youtube.com", setOf("youtube.com")))
        assertTrue(blockedSite("m.youtube.com", setOf("youtube.com")))
        assertFalse(blockedSite("notyoutube.com", setOf("youtube.com")))
        assertFalse(blockedSite("youtube.com.ar", setOf("youtube.com")))
    }

    @Test
    fun hostsInReadsAddressesWordByWord() {
        assertEquals(listOf("en.wikipedia.org"), hostsIn("\u200een.wikipedia.org"))
        assertEquals(listOf("en.wikipedia.org"), hostsIn(" en.wikipedia.org/wiki/Android_version_history. Buscar o ingresar la dirección"))
        assertEquals(listOf("xn--bcher-kva.de"), hostsIn("bücher.de/kinder"))
        assertEquals(emptyList<String>(), hostsIn("Buscar en Google o escribir una URL"))
        assertEquals(emptyList<String>(), hostsIn(null))
    }

    // Nodes as the phase-0 probe read them on a 1080 × 2400 screen.
    private val height = 2400
    private fun node(id: String?, text: String? = null, desc: String? = null, y: Float = 173f, editable: Boolean = false, focused: Boolean = false, web: Boolean = false) =
        BarNode(id, text, desc, editable, focused, y, web)

    @Test
    fun readsTheAddressBarOfEachBrowser() {
        val wiki = Address.Site(setOf("en.wikipedia.org"))
        // Chrome: its status icon has a bar id too, and holds no host.
        assertEquals(wiki, readAddress(listOf(node("location_bar_status_icon", desc = "La conexión es segura"), node("url_bar", "en.wikipedia.org/wiki/Android_version_history", editable = true)), height))
        // Samsung Internet starts it with a left-to-right mark.
        assertEquals(wiki, readAddress(listOf(node("location_bar_edit_text", "\u200een.wikipedia.org", y = 163f, editable = true)), height))
        // Edge has its bar at the bottom.
        assertEquals(wiki, readAddress(listOf(node("url_bar", "en.wikipedia.org", y = 2061f, editable = true)), height))
        // Firefox writes it in the description; in a Custom Tab, after the page title.
        assertEquals(wiki, readAddress(listOf(node("ADDRESSBAR_URL_BOX", desc = " en.wikipedia.org/wiki/Android_version_history. Buscar o ingresar la dirección", y = 184f)), height))
        assertEquals(wiki, readAddress(listOf(node("ADDRESSBAR_URL_BOX", desc = "Android version history - Wikipedia en.wikipedia.org", y = 184f)), height))
        // DuckDuckGo's Custom Tab shows the domain alone.
        assertEquals(wiki, readAddress(listOf(node("customTabDomain", "en.wikipedia.org", y = 178f)), height))
    }

    @Test
    fun homePagesAreTheBrowsersOwnEvenWithAddressesOnThem() {
        // Firefox's home: a recent tab's address mid-screen is not the page.
        val firefox = listOf(node("ADDRESSBAR_URL_BOX", desc = "Buscar", y = 184f), node("recent.tab.url", "https://en.wikipedia.org/wiki/Android_version_history", y = 1388f))
        assertEquals(Address.Own, readAddress(firefox, height))
        // Opera's home: its search bar sits a fifth down the screen; its tiles carry no bar id.
        val opera = listOf(node("top_omnibar_placeholder", desc = "Busca o pregunta cualquier cosa", y = 488f), node("favorite_item", "Booking.com", y = 776f))
        assertEquals(Address.Own, readAddress(opera, height))
    }

    @Test
    fun typingInTheBarIsNotThePageButAFieldOfThePageIs() {
        val suggestions = listOf(node("line_1", "example.net", y = 293f), node("line_1", "example.net", y = 480f))
        assertEquals(Address.Typing, readAddress(listOf(node("url_bar", "example.net", editable = true, focused = true)) + suggestions, height))
        // DuckDuckGo types in a field of its own id, at the top.
        assertEquals(Address.Typing, readAddress(listOf(node("inputField", "example.net", y = 304f, editable = true, focused = true)), height))
        // A focused login field of the page does not stop the block: its bar still says where it is.
        val login = listOf(node("url_bar", "news.ycombinator.com/login", editable = true), node(null, "", y = 700f, editable = true, focused = true, web = true))
        assertEquals(Address.Site(setOf("news.ycombinator.com")), readAddress(login, height))
    }

    @Test
    fun aHiddenBarIsUnknownAndPageTextNeverCounts() {
        val page = listOf(node(null, "sshhole.com", y = 200f, web = true), node("titleline", "The Lisp curse", y = 600f, web = true))
        assertEquals(Address.Unknown, readAddress(page, height))
    }

    @Test
    fun aWallClockSetForwardDuringABlockIsIgnored() {
        val start = Anchor(1_000_000L, 5_000L, 3)
        val running = { _: Long -> true }
        // A minute later by both clocks: the wall clock counts.
        assertEquals(1_060_000L, trustedTime(1_060_000L, 65_000L, 3, start, running).epochMs)
        // Set a day forward a minute later: the elapsed clock counts.
        assertEquals(1_060_000L, trustedTime(1_000_000L + 86_400_000L, 65_000L, 3, start, running).epochMs)
        // Outside a block, the wall clock always counts.
        assertEquals(1_000_000L + 86_400_000L, trustedTime(1_000_000L + 86_400_000L, 65_000L, 3, start) { false }.epochMs)
        // After a reboot it counts again, but never earlier than the last time read.
        assertEquals(1_000_000L, trustedTime(500_000L, 1_000L, 4, start, running).epochMs)
        assertEquals(2_000_000L, trustedTime(2_000_000L, 1_000L, 4, start, running).epochMs)
    }

    @Test
    fun rulesSurviveTheirJson() {
        val rules = listOf(work, night.copy(sites = setOf("x.com")))
        assertEquals(rules, parseRules(rulesToJson(rules)))
        val anchor = Anchor(1L, 2L, 3)
        assertEquals(anchor, parseAnchor(anchorToJson(anchor)))
    }
}
