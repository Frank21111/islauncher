package io.github.gastoncampana.launcher

import io.github.gastoncampana.launcher.Target.App
import io.github.gastoncampana.launcher.Target.Shortcut
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class ModelTest {
    private val labels = listOf("Calculadora", "Cámara", "Google Maps", "Mapas viejos", "Spotify")

    @Test
    fun searchRanksPrefixThenWordStartThenSubstring() {
        assertEquals(listOf("Mapas viejos", "Google Maps", "Cámara"), search(labels, "ma") { it })
    }

    @Test
    fun searchIgnoresAccentsCaseAndBlankQuery() {
        assertEquals(listOf("Cámara"), search(labels, " CAM ") { it })
        assertEquals(labels, search(labels, "  ") { it })
    }

    @Test
    fun searchRankBreaksTiesOnlyWithinAMatchGroup() {
        val rank = mapOf("Spotify" to 5.0, "Google Maps" to 9.0, "Cámara" to 1.0)
        val apps = listOf("Signal") + labels
        assertEquals(listOf("Spotify", "Signal", "Google Maps", "Mapas viejos"), search(apps, "s", { rank[it] ?: 0.0 }) { it })
        // A heavy use never lifts a word-start match over a prefix one.
        assertEquals(listOf("Mapas viejos", "Google Maps", "Cámara"), search(labels, "ma", { rank[it] ?: 0.0 }) { it })
        assertEquals(apps, search(apps, "", { rank[it] ?: 0.0 }) { it }) // no query: input order
    }

    @Test
    fun useDecaysByDayAndIgnoresAClockSetBack() {
        assertEquals(9.0, decayed(10.0, 100.0, 101.0), 1e-9)
        assertEquals(10.0, decayed(10.0, 100.0, 100.0), 0.0)
        assertEquals(10.0, decayed(10.0, 100.0, 90.0), 0.0)
        // Five opens a month ago weigh less than one yesterday; a few hours sooner wins between single opens.
        assertTrue(decayed(5.0, 0.0, 30.0) < decayed(1.0, 29.0, 30.0))
        assertTrue(decayed(1.0, 29.9, 30.0) > decayed(1.0, 29.8, 30.0))
    }

    @Test
    fun aFadedUseRanksAsNoneButStaysSixtyDays() {
        assertEquals(0.0, useRank(1.0, 150.0, 200.0), 0.0) // one open 50 days ago: below MIN_USE, so as never opened
        assertEquals(0.9, useRank(1.0, 199.0, 200.0), 1e-9)
        // Kept while it ranks, and 60 days at least, so a missing entry means no open in that long.
        assertTrue(kept(1.0, 150.0, 200.0))
        assertFalse(kept(1.0, 140.0, 200.0))
        assertTrue(kept(1000.0, 100.0, 200.0))
        // Before that, one open was dropped after about 44 days: counting is sure only since then, or since the oldest entry.
        assertEquals(199.0, countedSince(listOf(199.5, 199.0), 200.0), 0.0)
        assertEquals(200.0, countedSince(emptyList(), 200.0), 0.0)
        assertEquals(156.3, countedSince(listOf(125.0, 190.0), 200.0), 0.05) // a daily app abandoned 75 days ago
    }

    @Test
    fun menuShortcutsListManifestOnesFirstAndKeepTwoDynamicOnes() {
        // "m" from the manifest, "d" dynamic; the digit is the rank.
        fun pick(vararg ids: String) = menuShortcuts(ids.toList(), { it[0] == 'm' }, { it.drop(1).toInt() })
        assertEquals(listOf("m0", "m1", "d0", "d1"), pick("d1", "m1", "d0", "m0"))
        assertEquals(listOf("m0", "m1", "m2", "m3"), pick("m4", "m3", "m2", "m1", "m0"))
        assertEquals(listOf("d0", "d1", "d2", "d3"), pick("d4", "d3", "d2", "d1", "d0"))
        assertEquals(listOf("m0", "d0", "d1", "d2"), pick("d3", "d2", "d1", "d0", "m0"))
        // A full set of manifest ones gives up its last places to two dynamic ones, or the one there is.
        assertEquals(listOf("m0", "m1", "d0", "d1"), pick("m0", "m1", "m2", "m3", "m4", "d2", "d1", "d0"))
        assertEquals(listOf("m0", "m1", "m2", "d0"), pick("m3", "m2", "m1", "m0", "d0"))
        assertEquals(emptyList<String>(), pick())
    }

    @Test
    fun widgetSpanTakesTargetCellsElseRoundsMinSizeUpToHalfCells() {
        assertEquals(3f, widgetSpan(40f, 3, 90f, 5), 0f)
        assertEquals(1.5f, widgetSpan(100f, 0, 90f, 5), 0f)
        assertEquals(1f, widgetSpan(90f, 0, 90f, 5), 0f)
        assertEquals(0.5f, widgetSpan(0f, 0, 90f, 5), 0f)
        assertEquals(5f, widgetSpan(40f, 8, 90f, 5), 0f) // no wider than the grid
    }

    @Test
    fun swipeUsesDominantAxisAndIgnoresShortDrags() {
        assertNull(swipeOf(10f, -20f, 48f))
        assertEquals(Swipe.UP, swipeOf(10f, -60f, 48f))
        assertEquals(Swipe.DOWN, swipeOf(-30f, 60f, 48f))
        assertEquals(Swipe.LEFT, swipeOf(-80f, 50f, 48f))
        assertEquals(Swipe.RIGHT, swipeOf(80f, -50f, 48f))
    }

    @Test
    fun layoutSurvivesJsonRoundTrip() {
        val layout = Layout(
            dock = listOf(
                listOf(DockIcon(App("a/a.A#0"), up = Shortcut("b", "VOICE", "Voz"), down = App("c/c.C#10"))) +
                    List(3) { DockIcon() },
                List(4) { DockIcon() } + DockIcon(App("x/x.X#0")), // hidden by the smaller dock, kept
            ),
            desktop = listOf(
                listOf(
                    Item.Widget("w/w.Provider", 0, Cell(0f, 0f, 5f, 0.5f)),
                    Item.Widget("w/w.Other", 42, Cell(0.5f, 4.5f, 1f, 0.5f)),
                    Item.Icon(Shortcut("com.android.chrome", "98a6", "Board"), "Board", Cell(0f, 1f)),
                    Item.Icon(App("d/d.D#0"), "D", Cell(1f, 1f)),
                ),
                emptyList(),
            ),
            defaultPage = 1,
            tabs = listOf(Tab("A", setOf("a/a.A#0"), hideApps = true), Tab("Apps")),
            doubleTap = Shortcut("rec", "quick_record", "Grabar"),
            hidden = setOf("i/i.I#0"),
            poisons = setOf("i/i.I#0", "t/t.T#0"),
            columns = 6,
            rows = 7,
            dockSize = 4,
            dockWraps = false,
            drawerColumns = 4,
            drawerRows = 8,
            haptics = false,
            instead = App("k/k.Kindle#0"),
            decimal = '.',
            hideSuspended = true,
        )
        assertEquals(layout, parseLayout(layout.toJson()))
    }

    @Test
    fun hapticsStayOnForLayoutsSavedBeforeTheSetting() {
        assertEquals(true, parseLayout("""{"dock":[[]],"desktop":[[]],"tabs":[]}""").haptics)
    }

    @Test
    fun pausedAppsStayGreyedForLayoutsSavedBeforeTheSetting() {
        assertEquals(false, parseLayout("""{"dock":[[]],"desktop":[[]],"tabs":[]}""").hideSuspended)
    }

    @Test
    fun unusedAppsWereNeitherOpenedNorInstalledInSixtyDaysOnceTrackedThatLong() {
        // [app, day of its last open (null: none kept), install day (null: unknown)], as of day 200.
        val apps = listOf(
            Triple("opened lately", 150.0, 0.0),
            Triple("opened 60 days ago", 140.0, 0.0),
            Triple("not seen opened", null, 0.0),
            Triple("installed lately", null, 150.0),
            Triple("installed 60 days ago", null, 140.0),
            Triple("install unknown", null, null),
            Triple("clock set back", 250.0, 0.0),
        )
        fun pick(tracked: Double) = unused(apps, tracked, 200.0, { it.second }, { it.third })?.map { it.first }
        val old = listOf("opened 60 days ago", "not seen opened", "installed 60 days ago")
        assertEquals(old, pick(100.0))
        assertEquals(old, pick(140.0))
        // Counting for less than 60 days: an app not seen opened may have been opened before.
        assertNull(pick(140.5))
        assertEquals(emptyList<String>(), unused(emptyList<Triple<String, Double?, Double?>>(), 0.0, 200.0, { it.second }, { it.third }))
        // A widget on the desktop is in use, though a tap on it is no open the launcher sees: its app is left out.
        val desktop = listOf(listOf(Item.Widget("w/w.Clock", 3, Cell(0f, 0f, 2f, 1f)), Item.Icon(App("a/a.A#0"), "A", Cell(2f, 0f))), listOf(Item.Widget("h/h.Habits", 0, Cell(0f, 0f))))
        assertEquals(setOf("w", "h"), Layout(desktop = desktop).widgetPackages)
    }

    @Test
    fun uninstallingAnAppTakesItsIconsAndDockTargetsAlong() {
        val gone = App("g/g.Main#0")
        val alias = App("g/g.Alias#0") // another activity of the same package
        val work = App("g/g.Main#10") // the work profile's copy stays
        val chat = Shortcut("g", "chat", "Chat")
        val maps = App("m/m.Maps#0")
        val widget = Item.Widget("g/g.Widget", 3, Cell(0f, 1f, 2f, 1f)) // left to Ordenar: its id must be freed
        val keep = listOf(Item.Icon(work, "G", Cell(2f, 0f)), Item.Icon(maps, "Maps", Cell(4f, 0f)), widget)
        val layout = Layout(
            desktop = listOf(listOf(Item.Icon(gone, "G", Cell(0f, 0f)), Item.Icon(alias, "G2", Cell(1f, 0f)), Item.Icon(chat, "Chat", Cell(3f, 0f))) + keep),
            dock = listOf(listOf(DockIcon(gone, up = maps, down = chat), DockIcon(maps, up = alias), DockIcon(work)) + List(2) { DockIcon() }),
        )
        val after = layout.withoutApp("g", 0, mainUser = 0)
        assertEquals(listOf(keep), after.desktop)
        assertEquals(listOf(DockIcon(up = maps), DockIcon(maps), DockIcon(work)) + List(2) { DockIcon() }, after.dock[0])
        // In the work profile only its own copy goes: shortcuts are the main profile's.
        assertEquals(listOf(DockIcon(gone, up = maps, down = chat), DockIcon(maps, up = alias), DockIcon()), layout.withoutApp("g", 10, mainUser = 0).dock[0].take(3))
    }

    @Test
    fun theAppInsteadOfAPoisonIsUnsetForLayoutsSavedBeforeTheSetting() {
        assertNull(parseLayout("""{"dock":[[]],"desktop":[[]],"tabs":[]}""").instead)
        assertNull(parseLayout(Layout().toJson()).instead) // null leaves the key out
    }

    @Test
    fun calcEvaluatesArithmeticAndIgnoresEverythingElse() {
        for (d in listOf(',', '.')) {
            assertEquals(375.0, calc("125*3", d)!!, 0.0)
            assertEquals(7.0, calc("1 + 2 x 3", d)!!, 0.0)
            assertEquals(9.0, calc("(1+2)×3", d)!!, 0.0)
            assertEquals(0.3, calc("0.1+0.2", d)!!, 1e-12)
            assertEquals(-4.0, calc("-2^2", d)!!, 0.0)
            assertEquals(30.0, calc("200*15%", d)!!, 1e-12)
            assertEquals(2.5, calc("10÷4", d)!!, 0.0)
            // Not sums: app names, lone numbers, broken or undefined expressions.
            for (q in listOf("", "Spotify", "2048", "-5", "7-Eleven", "1+", "(1+2", "1/0", "1..2+1", "2**3", "1.50.000+1", "1,5,0+1")) assertNull(q, calc(q, d))
            assertNull(calc("(".repeat(5000) + "1" + ")".repeat(5000), d)) // pasted junk must not overflow the stack
        }
    }

    @Test
    fun calcReadsNumbersTheWayTheCountryWritesThem() {
        // [query, decimal mark, result as shown]
        val cases = listOf(
            Triple("1.500.000/12", ',', "125.000"),
            Triple("1.500,50 + 200", ',', "1.700,5"),
            Triple("1.500+200", ',', "1.700"),
            Triple("1,5 * 2", ',', "3"),
            Triple("1.5 * 2", ',', "3"), // a lone '.' that is not grouping: still a decimal mark
            Triple("12.345.678,9 * 1", ',', "12.345.678,9"),
            Triple("1,500,000/12", '.', "125,000"),
            Triple("1,500.50 + 200", '.', "1,700.5"),
            Triple("1,500+200", '.', "1,700"),
            Triple("1.500+200", '.', "201.5"),
            Triple("1,5 * 2", '.', "3"),
            // A percentage added or taken off is of what comes before it, as on a calculator.
            Triple("1500 + 21%", ',', "1.815"),
            Triple("1500 - 10%", ',', "1.350"),
            Triple("200 * 10%", ',', "20"),
            Triple("1/3", ',', "0,3333333333"),
            // No grouping starts with 0 or with more than three digits: those marks are decimal ones.
            Triple("0.500+1", ',', "1,5"),
            Triple("0.125*8", ',', "1"),
            Triple("0,125*8", '.', "1"),
            Triple("1234.567+0", ',', "1.234,567"),
            Triple("0*-1", ',', "0"), // no "-0"
            Triple("0/-5", '.', "0"),
        )
        for ((q, d, shown) in cases) assertEquals("$q with '$d'", shown, formatNumber(calc(q, d)!!, d))
        assertNull(calc("0.000.001+0", ','))
        assertEquals("1.234,5", formatNumber(1234.5, ','))
        assertEquals("1,234.5", formatNumber(1234.5, '.'))
        assertEquals("1,5e-7", formatNumber(1.5e-7, ',')) // too small or too long: scientific
        assertEquals("2e15", formatNumber(2e15, '.'))
    }

    @Test
    fun searchAnswersSumsConversionsPhonesLinksAndAddresses() {
        // [query, decimal mark, answer]
        val cases = listOf(
            Triple("1500 + 21%", ',', Answer.Value("1.815")),
            // Conversions: a/en/to/in to a unit, else the metric or imperial counterpart; four significant digits.
            Triple("5 km a mi", ',', Answer.Value("3,107 mi")),
            Triple("5 km to mi", '.', Answer.Value("3.107 mi")),
            Triple("5 kilómetros en millas", ',', Answer.Value("3,107 mi")),
            Triple("5km", ',', Answer.Value("3,107 mi")),
            Triple("100000 km", ',', Answer.Value("62.137 mi")), // no digits dropped off the whole part
            Triple("10 m", ',', Answer.Value("32,81 ft")), // m is the metre
            Triple("12 pulgadas", ',', Answer.Value("30,48 cm")),
            Triple("5 in", '.', Answer.Value("12.7 cm")), // "in" is the inch when nothing follows it
            Triple("5 in in cm", '.', Answer.Value("12.7 cm")),
            Triple("1.500 m a km", ',', Answer.Value("1,5 km")),
            Triple("3.5kg", ',', Answer.Value("7,716 lb")), // a lone '.' before a non-group: the decimal mark
            Triple("70 kg", ',', Answer.Value("154,3 lb")),
            Triple("1 oz", ',', Answer.Value("28,35 g")), // oz is mass…
            Triple("1 fl oz", ',', Answer.Value("29,57 ml")), // …unless fluid
            Triple("1 kg a oz", ',', Answer.Value("35,27 oz")),
            Triple("2 tazas en ml", ',', Answer.Value("473,2 ml", usCup = true)),
            Triple("1 l a tazas", ',', Answer.Value("4,227 tazas", usCup = true)), // kitchen units keep the word typed
            Triple("3 cucharadas en ml", ',', Answer.Value("44,36 ml")),
            Triple("1 tsp", '.', Answer.Value("4.929 ml")),
            Triple("1 l", ',', Answer.Value("33,81 fl oz")),
            Triple("350 f", ',', Answer.Value("176,7 °C")),
            Triple("100 °C a °F", ',', Answer.Value("212 °F")),
            Triple("-40 c", ',', Answer.Value("-40 °F")),
            Triple("100 km/h a mph", ',', Answer.Value("62,14 mph")),
            Triple("10 m/s", ',', Answer.Value("36 km/h")),
            Triple("1 ha", ',', Answer.Value("2,471 ac")),
            Triple("100 m2 a ft2", ',', Answer.Value("1.076 ft²")),
            Triple("0.250 kg", ',', Answer.Value("0,5512 lb")),
            Triple("12345.678 m", ',', Answer.Value("40.504 ft")),
            Triple("-0 m", ',', Answer.Value("0 ft")),
            // Next to a volume, oz is the fluid ounce.
            Triple("8 oz a ml", ',', Answer.Value("236,6 ml")),
            Triple("1 taza a oz", ',', Answer.Value("8 fl oz", usCup = true)),
            Triple("1 l a oz", ',', Answer.Value("33,81 fl oz")),
            Triple("100 g a oz", ',', Answer.Value("3,527 oz")),
            // Phones: 8 to 15 digits, after a + or in groups; never also a sum.
            Triple("11 5555-1234", ',', Answer.Phone("1155551234")),
            Triple("011 15 5555-1234", ',', Answer.Phone("0111555551234")),
            Triple("(011) 4555-1234", ',', Answer.Phone("01145551234")),
            Triple("+54 9 11 5555-1234", ',', Answer.Phone("+5491155551234")),
            Triple("+5491155551234", ',', Answer.Phone("+5491155551234")),
            Triple("555-123-4567", '.', Answer.Phone("5551234567")),
            Triple("1-800-555-1234", '.', Answer.Phone("18005551234")),
            Triple("11-5555-1234", ',', Answer.Phone("1155551234")),
            Triple("011-4555-1234", ',', Answer.Phone("01145551234")),
            Triple("0800-333-1234", ',', Answer.Phone("08003331234")),
            Triple("02944-42-1234", ',', Answer.Phone("02944421234")), // a five-digit area code with its 0
            Triple("11  5555-1234", ',', Answer.Phone("1155551234")), // pasted with a double space
            Triple("(011)-4555-1234", ',', Answer.Phone("01145551234")),
            Triple("1155559999", ',', Answer.Phone("1155559999")), // digits alone, as many as a phone has
            Triple("45551234", ',', Answer.Phone("45551234")),
            // Subtractions stay sums: two groups, or a group no phone has (five digits or more, without a leading 0).
            Triple("1000-250", ',', Answer.Value("750")),
            Triple("1500000-250000", ',', Answer.Value("1.250.000")),
            Triple("25000-7500", ',', Answer.Value("17.500")),
            Triple("1500-200-300", ',', Answer.Value("1.000")),
            Triple("25000-7500-1500", ',', Answer.Value("16.000")),
            Triple("50000-12000-3500", ',', Answer.Value("34.500")),
            Triple("150000-25000-1000", ',', Answer.Value("124.000")),
            Triple("150000-35000-1200", ',', Answer.Value("113.800")),
            Triple("29-10-2024", ',', Answer.Value("-2.005")), // a date is no phone
            // Links and e-mail addresses.
            Triple("google.com", ',', Answer.Link("https://google.com")),
            Triple("www.mercadolibre.com.ar/ofertas", ',', Answer.Link("https://www.mercadolibre.com.ar/ofertas")),
            Triple("http://example.org/a?b=1", ',', Answer.Link("http://example.org/a?b=1")),
            Triple("HTTPS://Example.com/A", ',', Answer.Link("https://Example.com/A")), // Android matches schemes in lower case
            Triple(" ana@example.com ", ',', Answer.Email("ana@example.com")),
            Triple("mailto:x@y.com", ',', Answer.Email("x@y.com")),
        )
        for ((q, d, a) in cases) assertEquals("$q with '$d'", a, answer(q, d))
        // Nothing to answer: app names, lone numbers too short for a phone, mixed dimensions.
        for (q in listOf("", "Spotify", "Mi Fit", "4 in a row", "km", "5 km a kg", "1.500", "a.b", "1234567", "1234 567")) {
            assertNull(q, answer(q, ','))
        }
    }

    @Test
    fun whatsAppTakesArgentineMobilesWithTheNineAfterTheCountryCode() {
        // What PhoneNumberUtils.formatNumberToE164 gives for "11 5555-1234" and for "011 15 5555-1234" in AR.
        assertEquals("5491155551234", whatsAppNumber("+541155551234"))
        assertEquals("5491155551234", whatsAppNumber("+5491155551234"))
        assertEquals("14155550123", whatsAppNumber("+14155550123"))
        assertEquals("59899123456", whatsAppNumber("+59899123456")) // Uruguay starts with 5 too
    }

    @Test
    fun theNumberConventionFollowsThePhoneUnlessChosen() {
        assertNull(parseLayout("""{"dock":[[]],"desktop":[[]],"tabs":[]}""").decimal)
        assertNull(parseLayout(Layout().toJson()).decimal) // null leaves the key out
        assertEquals(',', parseLayout(Layout(decimal = ',').toJson()).decimal)
        assertNull(parseLayout("""{"dock":[[]],"desktop":[[]],"tabs":[],"decimal":"x"}""").decimal)
    }

    @Test
    fun restoringKeepsLiveWidgetsAndDropsItemsOffTheGrid() {
        val live = Item.Widget("w", 7, Cell(0f, 0f, 5f, 1f))
        val gone = Item.Widget("w", 8, Cell(0f, 1f, 5f, 1f))
        val icon = Item.Icon(App("a/a.A#0"), "A", Cell(4f, 4f))
        val bad = listOf(Cell(4.5f, 0f), Cell(0f, -1f), Cell(Float.NaN, 0f), Cell(0f, 0f, 0f, 1f))
            .map { Item.Icon(App("b/b.B#0"), "B", it) }
        val other = Item.Widget("w", 9, Cell(0f, 2f, 5f, 1f)) // id 9 is live here, but holds another provider's widget
        val backup = Layout(desktop = listOf(listOf(live, gone, other, icon) + bad))
        assertEquals(
            listOf(live, gone.copy(widgetId = 0), other.copy(widgetId = 0), icon),
            backup.restored(mapOf(7 to "w", 9 to "x")).desktop[0],
        )
        val huge = Layout(dock = List(5000) { List(5) { DockIcon() } }, desktop = List(5000) { emptyList() }, defaultPage = 4999)
        val capped = huge.restored(emptyMap())
        assertEquals(MAX_PAGES to MAX_PAGES, capped.dock.size to capped.desktop.size)
        assertEquals(0, capped.defaultPage)
    }

    @Test
    fun restoredRejectsABackupPastAnyRealLayout() {
        val icon = Item.Icon(App("a/a.A#0"), "A", Cell(0f, 0f))
        val long = "x".repeat(MAX_TEXT + 1)
        val full = Layout(
            desktop = List(2) { List(MAX_ITEMS / 2) { icon } },
            tabs = List(MAX_TABS) { Tab("x".repeat(MAX_TEXT)) },
            dock = listOf(List(GRID_SIZES.last) { DockIcon() }),
        )
        assertEquals(MAX_ITEMS, full.restored(emptyMap()).desktop.flatten().size)
        listOf(
            Layout(desktop = List(2) { List(MAX_ITEMS / 2 + 1) { icon } }),
            Layout(tabs = List(MAX_TABS + 1) { Tab("t") }),
            Layout(dock = listOf(List(GRID_SIZES.last + 1) { DockIcon() })),
            Layout(desktop = listOf(listOf(icon.copy(label = long)))),
            Layout(desktop = listOf(listOf(Item.Widget(long, 0, Cell(0f, 0f))))),
            Layout(tabs = listOf(Tab(long))),
            Layout(tabs = listOf(Tab("t", setOf(long)))),
            Layout(hidden = setOf(long)),
            Layout(hidden = (0..MAX_TEXTS).map { "$it" }.toSet()),
            Layout(poisons = setOf(long)),
            Layout(doubleTap = Shortcut("p", long, "l")),
            Layout(instead = App(long)),
            Layout(dock = listOf(listOf(DockIcon(down = Shortcut("p", "s", long))))),
        ).forEach { hostile -> assertThrows(IllegalArgumentException::class.java) { hostile.restored(emptyMap()) } }
    }

    @Test
    fun iconSampleShrinksOnlyWhatIsTooBig() {
        assertEquals(1, iconSample(192, 192))
        assertEquals(1, iconSample(-1, -1)) // not an image
        assertEquals(2, iconSample(1024, 10))
        assertEquals(128, iconSample(65535, 65535))
    }

    @Test
    fun theGuideOpensByItselfOnlyOnAFreshInstallsFirstLaunch() {
        assertTrue(opensGuide(saved = false, launchedBefore = false))
        assertFalse(opensGuide(saved = true, launchedBefore = false)) // an existing user's first launch of this version
        assertFalse(opensGuide(saved = false, launchedBefore = true)) // nothing changed since the guide opened
        assertFalse(opensGuide(saved = true, launchedBefore = true))
    }

    @Test
    fun parsePadsDockPagesAndClampsDefaultPage() {
        val layout = parseLayout("""{"dock":[[{}]],"desktop":[[]],"defaultPage":4,"tabs":[]}""")
        assertEquals(listOf(List(5) { DockIcon() }), layout.dock)
        assertEquals(0, layout.defaultPage)
        // Grid sizes from a hand-edited file stay in range.
        val wild = parseLayout("""{"dock":[[]],"desktop":[[]],"tabs":[],"columns":99,"rows":0,"dockSize":3,"drawerColumns":-1}""")
        assertEquals(listOf(GRID_SIZES.last, GRID_SIZES.first, GRID_SIZES.first), listOf(wild.columns, wild.rows, wild.drawerColumns))
        assertEquals(listOf(List(3) { DockIcon() }), wild.dock)
    }

    @Test
    fun aSmallerDockHidesItsLastIconsUntilItGrowsAgain() {
        val last = DockIcon(App("e/e.E#0"))
        val layout = Layout(dock = listOf(List(4) { DockIcon() } + last))
        assertEquals(listOf(List(4) { DockIcon() } + last), layout.withDockSize(3).dock)
        assertEquals(listOf(List(4) { DockIcon() } + last + DockIcon()), layout.withDockSize(3).withDockSize(6).dock)
    }

    @Test
    fun shrinkingTheGridMovesWhatNoLongerFits() {
        val a = Item.Icon(App("a/a.A#0"), "A", Cell(0f, 0f))
        val far = Item.Icon(App("b/b.B#0"), "B", Cell(4f, 4f))
        val wide = Item.Widget("w", 7, Cell(0f, 1f, 5f, 1f))
        val layout = Layout(desktop = listOf(listOf(a, far, wide), emptyList()))
        // To the nearest free spot on its page; a widget wider than the grid shrinks to it.
        assertEquals(
            listOf(listOf(a, far.copy(cell = Cell(3f, 3f)), wide.copy(cell = Cell(0f, 1f, 4f, 1f))), emptyList()),
            layout.withGrid(4, 4).desktop,
        )
        assertEquals(layout.desktop, layout.withGrid(6, 6).desktop) // growing moves nothing
        // A full page sends it to another page with room, else to a new one.
        val full = Item.Widget("f", 1, Cell(0f, 0f, 4f, 4f))
        val out = Item.Icon(App("c/c.C#0"), "C", Cell(4f, 0f))
        val moved = listOf(listOf(full), listOf(out.copy(cell = Cell(3f, 0f))))
        assertEquals(moved, Layout(desktop = listOf(listOf(full, out), emptyList())).withGrid(4, 4).desktop)
        assertEquals(moved, Layout(desktop = listOf(listOf(full, out))).withGrid(4, 4).desktop)
        // Moves and new items stay inside a grid that is not square.
        val tall = Layout(columns = 4, rows = 6)
        assertEquals(listOf(a.copy(cell = Cell(3f, 5f))), tall.moved(null, a, 0, Cell(9f, 9f)).desktop[0])
        assertEquals(Cell(0f, 5.5f, 4f, 0.5f), Layout(desktop = listOf(listOf(Item.Widget("t", 1, Cell(0f, 0f, 4f, 5.5f)))), columns = 4, rows = 6).freeCell(0, 4f, 0.5f))
    }

    @Test
    fun removingAPageMovesItsItemsToTheOthers() {
        val a = Item.Icon(App("a/a.A#0"), "A", Cell(0f, 0f))
        val b = Item.Icon(App("b/b.B#0"), "B", Cell(0f, 0f))
        val c = Item.Icon(App("c/c.C#0"), "C", Cell(2f, 3f))
        val layout = Layout(desktop = listOf(listOf(a), listOf(b, c), emptyList()), defaultPage = 2)
        val removed = layout.withoutPage(1)
        assertEquals(listOf(listOf(a, b.copy(cell = Cell(1f, 0f)), c), emptyList()), removed.desktop)
        assertEquals(1, removed.defaultPage) // still the same page
        assertEquals(0, layout.withoutPage(2).defaultPage) // the main page itself: the first one takes over
        val single = Layout(desktop = listOf(listOf(a)))
        assertEquals(single, single.withoutPage(0)) // the last page stays
    }

    @Test
    fun movingAPageTakesTheMainPageAlong() {
        val a = listOf<Item>(Item.Icon(App("a/a.A#0"), "A", Cell(0f, 0f)))
        val b = listOf<Item>(Item.Icon(App("b/b.B#0"), "B", Cell(0f, 0f)))
        val c = emptyList<Item>()
        val layout = Layout(desktop = listOf(a, b, c), defaultPage = 1)
        fun moved(from: Int, to: Int) = layout.withPageMoved(from, to).let { it.desktop to it.defaultPage }
        assertEquals(listOf(b, a, c) to 0, moved(1, 0)) // the main page itself
        assertEquals(listOf(b, c, a) to 0, moved(0, 2)) // a page across it
        assertEquals(listOf(a, c, b) to 2, moved(2, 1))
        assertEquals(listOf(a, b, c) to 1, moved(0, 0))
        assertEquals(layout, layout.withPageMoved(0, 3)) // off the end: nothing changes
        assertEquals(listOf("x", "z", "y"), listOf("x", "y", "z").shifted(2, 1)) // dock pages and tabs move the same way
    }

    @Test
    fun movingAnItemToAnotherPageKeepsItsSpotWhenFree() {
        val a = Item.Icon(App("a/a.A#0"), "A", Cell(2f, 3f))
        val b = Item.Icon(App("b/b.B#0"), "B", Cell(2f, 3f))
        val w = Item.Widget("w", 7, Cell(0f, 0f, 5f, 2f))
        val full = Item.Widget("f", 1, Cell(0f, 0f, 5f, 5f))
        val layout = Layout(desktop = listOf(listOf(a, w), listOf(b), listOf(full)))
        assertEquals(listOf(b, w), layout.moved(0, w, 1, w.cell).desktop[1]) // its own spot, free there
        // Taken: the nearest free spot of its size.
        assertEquals(listOf(listOf(w), listOf(b, a.copy(cell = Cell(2f, 2f)))), layout.moved(0, a, 1, a.cell).desktop.take(2))
        assertEquals(layout, layout.moved(0, a, 2, a.cell)) // no room: nothing changes, so the menu can say so
    }

    @Test
    fun auditListsWhatIsBrokenTwiceOrEmpty() {
        val gone = App("gone/g.G#0")
        val mail = App("m/m.Mail#0")
        val spotify = App("s/s.S#0")
        val maps = App("g/g.Maps#0")
        val deadShortcut = Shortcut("x", "1", "X")
        val ok = Item.Widget("w/w.P", 0, Cell(0f, 0f, 2f, 1f)) // unbound, as a Nova import leaves it, but its app is there
        val orphan = Item.Widget("o/o.P", 0, Cell(2f, 0f, 2f, 1f))
        val dead = Item.Widget("d/d.P", 5, Cell(0f, 1f, 2f, 1f))
        val goneIcon = Item.Icon(gone, "G", Cell(0f, 4f))
        val mailIcon = Item.Icon(mail, "Mail", Cell(1f, 4f))
        val shortcutIcon = Item.Icon(deadShortcut, "X", Cell(2f, 4f))
        val alias = Item.Icon(App("m/m.Alias#0"), "Correo", Cell(0f, 0f)) // another activity of the same package
        val spotifyIcons = listOf(Item.Icon(spotify, "S", Cell(0f, 0f)), Item.Icon(spotify, "S", Cell(1f, 0f)))
        val layout = Layout(
            desktop = listOf(listOf(ok, orphan, dead, goneIcon, mailIcon, shortcutIcon), listOf(alias), emptyList(), spotifyIcons),
            dock = listOf(
                listOf(DockIcon(mail, up = gone), DockIcon(gone), DockIcon(down = mail), DockIcon(maps), DockIcon(), DockIcon(gone)),
                listOf(DockIcon(maps)) + List(4) { DockIcon() },
            ),
        )
        // A paused work profile's app or one mid-update is alive while the app list still knows it: [alive] decides.
        val audit = layout.audit({ it != gone && it != deadShortcut }) { it != orphan && it != dead }
        assertEquals(
            listOf(
                Place.Desktop(0, orphan), Place.Desktop(0, dead), Place.Desktop(0, goneIcon), Place.Desktop(0, shortcutIcon),
                Place.Dock(0, 0, gone, Swipe.UP), Place.Dock(0, 1, gone), // the sixth icon is past dockSize, hidden: left alone
            ),
            audit.broken,
        )
        // By package, across pages or the dock and a page; a dock swipe is no icon, and two on one page or in the dock are no twins.
        assertEquals(listOf(listOf(Place.Desktop(0, mailIcon), Place.Desktop(1, alias), Place.Dock(0, 0, mail))), audit.twins)
        assertEquals(listOf(2), audit.empty)
        assertEquals(7, audit.size) // twins may be on purpose: listed, not counted
        // A lone empty page is not offered: the last page stays, the main one when all are empty.
        assertEquals(0, Layout().audit({ true }) { true }.size)
        assertEquals(listOf(0, 2), Layout(desktop = List(3) { emptyList() }, defaultPage = 1).audit({ true }) { true }.empty)
    }

    @Test
    fun aWidgetBeingSetUpIsFoundWhereverItMoved() {
        val w = Item.Widget("w", 0, Cell(0f, 0f, 2f, 1f))
        val twin = w.copy(cell = Cell(0f, 2f, 2f, 1f))
        val layout = Layout(desktop = listOf(listOf(twin), listOf(w)))
        assertEquals(1 to w, layout.locate(1, w)) // where it was, though an equal one sits on another page
        assertEquals(0 to twin, Layout(desktop = listOf(listOf(twin))).locate(1, w)) // its page was removed
        assertNull(Layout().locate(0, w))
    }

    @Test
    fun togglingMatchesAnAppUnderAnyOfItsIds() {
        val find = finder(listOf("a/a.New#0", "b/b.B#0")) { it }
        assertEquals(setOf("b/b.B#0"), setOf("a/a.Old#0", "b/b.B#0").toggled("a/a.New#0", "a/a.New#0", find))
        assertEquals(setOf("b/b.B#0", "a/a.New#0"), setOf("b/b.B#0").toggled("a/a.New#0", "a/a.New#0", find))
    }

    @Test
    fun finderFallsBackToAnotherActivityOfTheSamePackageAndUser() {
        val find = finder(listOf("n/n.StartupActivityDefault#0", "n/n.Other#10")) { it }
        assertEquals("n/n.StartupActivityDefault#0", find("n/n.StartupActivityDefault#0"))
        assertEquals("n/n.StartupActivityDefault#0", find("n/n.StartupActivity#0"))
        assertEquals("n/n.Other#10", find("n/n.Gone#10"))
        assertNull(find("x/x.A#0"))
    }

    @Test
    fun hidingTabsKeepTheirAppsOutOfTheAllAppsTab() {
        val all = listOf("a/a.A#0", "b/b.B#0", "c/c.C#0")
        val tabs = listOf(Tab("A", setOf("a/a.A#0", "gone/g.G#0"), hideApps = true), Tab("B", setOf("b/b.B#0")), Tab("Apps"))
        val find = finder(all) { it }
        assertEquals(listOf("a/a.A#0"), tabApps(tabs[0], tabs, all, find))
        assertEquals(listOf("b/b.B#0"), tabApps(tabs[1], tabs, all, find))
        assertEquals(listOf("b/b.B#0", "c/c.C#0"), tabApps(tabs[2], tabs, all, find))
    }

    @Test
    fun hiddenAppsLeaveEveryTab() {
        val all = listOf("a/a.A#0", "i/i.I#0")
        val tabs = listOf(Tab("A", setOf("a/a.A#0", "i/i.I#0")), Tab("Apps"))
        val find = finder(all) { it }
        val hidden = setOf("i/i.Renamed#0") // matched like any id, alias changes included
        assertEquals(listOf("a/a.A#0"), tabApps(tabs[0], tabs, all, find, hidden))
        assertEquals(listOf("a/a.A#0"), tabApps(tabs[1], tabs, all, find, hidden))
    }

    @Test
    fun freeCellSkipsOccupiedCellsAndHalfRows() {
        val items = listOf(
            Item.Widget("w", 0, Cell(0f, 0f, 5f, 0.5f)),
            Item.Icon(App("a/a.A#0"), "A", Cell(0f, 0.5f)),
        )
        val layout = Layout(desktop = listOf(items, listOf(Item.Widget("w", 0, Cell(0f, 0f, 5f, 5f)))))
        assertEquals(Cell(1f, 0.5f), layout.freeCell(0, 1f, 1f))
        assertEquals(Cell(0f, 1.5f, 5f, 2f), layout.freeCell(0, 5f, 2f))
        assertNull(layout.freeCell(1, 1f, 1f))
    }

    @Test
    fun movingSnapsToTheNearestFreeHalfCell() {
        val a = Item.Icon(App("a/a.A#0"), "A", Cell(0f, 0f))
        val b = Item.Icon(App("b/b.B#0"), "B", Cell(2f, 2f))
        val w = Item.Widget("w", 7, Cell(0f, 4f, 5f, 1f))
        val layout = Layout(desktop = listOf(listOf(a, b, w), emptyList()))

        // Snaps to half cells and stays inside the page.
        assertEquals(listOf(b, w, a.copy(cell = Cell(3.5f, 1f))), layout.moved(0, a, 0, Cell(3.6f, 1.2f)).desktop[0])
        assertEquals(Cell(4f, 0f), layout.moved(0, a, 0, Cell(6f, -2f)).desktop[0].last().cell)
        // Onto B: the closest spot that does not overlap it.
        assertEquals(Cell(2f, 1f), layout.moved(0, a, 0, Cell(2f, 1.8f)).desktop[0].last().cell)
        // To another page, widgets included.
        val toPage1 = layout.moved(0, w, 1, Cell(0f, 0f, 5f, 1f))
        assertEquals(listOf(a, b), toPage1.desktop[0])
        assertEquals(listOf(w.copy(cell = Cell(0f, 0f, 5f, 1f))), toPage1.desktop[1])
        // No room, or an item that is not there: nothing changes.
        val full = Layout(desktop = listOf(listOf(a), listOf(Item.Widget("x", 1, Cell(0f, 0f, 5f, 5f)))))
        assertEquals(full, full.moved(0, a, 1, Cell(0f, 0f)))
        assertEquals(layout, layout.moved(1, a, 0, Cell(1f, 1f)))
        // A new item, dragged from the drawer.
        val c = Item.Icon(App("c/c.C#0"), "C", Cell(0f, 0f))
        assertEquals(listOf(c.copy(cell = Cell(1f, 3f))), layout.moved(null, c, 1, Cell(1.1f, 2.9f)).desktop[1])
        assertEquals(full, full.moved(null, c, 1, Cell(0f, 0f)))
        // An equal item already there stays: a drawer drop adds a copy, a move from another page leaves it alone.
        assertEquals(listOf(a, b, w, a.copy(cell = Cell(1f, 0f))), layout.moved(null, a, 0, Cell(0f, 0f)).desktop[0])
        val twins = Layout(desktop = listOf(listOf(a), listOf(a)))
        assertEquals(listOf(emptyList(), listOf(a, a.copy(cell = Cell(1f, 0f)))), twins.moved(0, a, 1, Cell(0f, 0f)).desktop)
    }

    @Test
    fun aResizedSideSnapsToHalfCellsWithinItsLimitsThePageAndItsNeighbours() {
        val w = Item.Widget("w", 7, Cell(1f, 1f, 2f, 2f))
        val right = Item.Icon(App("a/a.A#0"), "A", Cell(4f, 2f)) // beside its rows
        val below = Item.Icon(App("b/b.B#0"), "B", Cell(1.5f, 4f)) // under its columns
        val corner = Item.Icon(App("c/c.C#0"), "C", Cell(0f, 0f)) // diagonal: never in the way
        val layout = Layout(desktop = listOf(listOf(corner, w, right, below)))
        val free = Limits(minW = 1f, minH = 1.5f)
        fun stretched(edge: Edge, by: Float, limits: Limits = free) = layout.stretched(0, w, edge, by, limits)

        assertEquals(Cell(1f, 1f, 2.5f, 2f), stretched(Edge.RIGHT, 0.6f)) // half cells
        assertEquals(w.cell, stretched(Edge.RIGHT, 0.2f))
        assertEquals(Cell(1f, 1f, 3f, 2f), stretched(Edge.RIGHT, 5f)) // up to its neighbour
        assertEquals(Cell(1f, 1f, 2.5f, 2f), stretched(Edge.RIGHT, 5f, free.copy(maxW = 2.5f))) // its max
        assertEquals(Cell(1f, 1f, 1f, 2f), stretched(Edge.RIGHT, -5f)) // its min
        assertEquals(Cell(0f, 1f, 3f, 2f), stretched(Edge.LEFT, -4f)) // past the corner item, to the page's side
        assertEquals(Cell(2f, 1f, 1f, 2f), stretched(Edge.LEFT, 1.8f))
        assertEquals(Cell(1f, 1f, 2f, 3f), stretched(Edge.BOTTOM, 4f))
        assertEquals(Cell(1f, 0f, 2f, 3f), stretched(Edge.TOP, -0.8f))
        assertEquals(Cell(1f, 1.5f, 2f, 1.5f), stretched(Edge.TOP, 2f))
        // On its own page, the grid stops it.
        assertEquals(Cell(1f, 1f, 4f, 4f), Layout(desktop = listOf(listOf(w))).stretched(0, w, Edge.RIGHT, 9f, free).let {
            Layout(desktop = listOf(listOf(w.copy(cell = it)))).stretched(0, w.copy(cell = it), Edge.BOTTOM, 9f, free)
        })
        // Smaller than its min already (a Nova import, a provider update): it may grow, never shrink further.
        val small = Item.Widget("s", 8, Cell(0f, 0f, 1f, 1f))
        val tight = Layout(desktop = listOf(listOf(small)))
        assertEquals(small.cell, tight.stretched(0, small, Edge.RIGHT, -1f, Limits(2f, 2f)))
        assertEquals(Cell(0f, 0f, 3f, 1f), tight.stretched(0, small, Edge.RIGHT, 2f, Limits(2f, 2f)))
    }

    @Test
    fun resizingAnItemKeepsItsPlaceUnlessItWouldOverlapOrLeaveThePage() {
        val w = Item.Widget("w", 7, Cell(1f, 1f, 2f, 2f))
        val a = Item.Icon(App("a/a.A#0"), "A", Cell(4f, 2f))
        val layout = Layout(desktop = listOf(listOf(w, a)))
        assertEquals(listOf(w.copy(cell = Cell(0f, 1f, 3f, 2.5f)), a), layout.resized(0, w, Cell(0f, 1f, 3f, 2.5f)).desktop[0])
        assertEquals(layout, layout.resized(0, w, Cell(1f, 1f, 3.5f, 2f))) // onto A
        assertEquals(layout, layout.resized(0, w, Cell(1f, 1f, 2f, 4.5f))) // off the page
        assertEquals(layout, layout.resized(0, w.copy(widgetId = 8), Cell(1f, 1f, 1f, 1f))) // not there
        assertEquals(layout, layout.resized(1, w, Cell(1f, 1f, 1f, 1f)))
    }

    @Test
    fun aResizeKeepsAnOverlapTheWidgetAlreadyHad() {
        val w = Item.Widget("w", 7, Cell(0f, 0f, 2f, 2f))
        val a = Item.Icon(App("a/a.A#0"), "A", Cell(1f, 1f)) // under it already: a Nova import that let items overlap
        val layout = Layout(desktop = listOf(listOf(w, a)))
        val wider = layout.stretched(0, w, Edge.RIGHT, 1f, Limits(1f, 1f))
        val shorter = layout.stretched(0, w, Edge.BOTTOM, -0.5f, Limits(1f, 1f))
        assertEquals(Cell(0f, 0f, 3f, 2f), wider)
        assertEquals(Cell(0f, 0f, 2f, 1.5f), shorter)
        assertEquals(listOf(w.copy(cell = wider), a), layout.resized(0, w, wider).desktop[0])
        assertEquals(listOf(w.copy(cell = shorter), a), layout.resized(0, w, shorter).desktop[0])
    }

    @Test
    fun aWidgetsResizeSpanIgnoresAMinResizeAboveItsMinAndAMaxBelowIt() {
        assertEquals(1.5f to 4f, resizeSpan(250f, 110f, 400f, 100f, 5))
        assertEquals(2.5f to 0f, resizeSpan(250f, 0f, 0f, 100f, 5)) // neither set
        assertEquals(2.5f to 2.5f, resizeSpan(250f, 0f, 250f, 100f, 5))
        assertEquals(1f to 0f, resizeSpan(100f, 200f, 0f, 100f, 5)) // a min resize above the min: none
        assertEquals(2.5f to 0f, resizeSpan(250f, 0f, 180f, 100f, 5)) // a max below the min: none
        assertEquals(5f to 0f, resizeSpan(900f, 0f, 0f, 100f, 5)) // no wider than the grid
    }

    @Test
    fun anItemPutBackTakesItsSpotElseTheNearestFreeOneElseAnotherPage() {
        val a = Item.Icon(App("a/a.A#0"), "A", Cell(2f, 2f))
        val b = Item.Icon(App("b/b.B#0"), "B", Cell(2f, 2f))
        val full = Item.Widget("f", 1, Cell(0f, 0f, 5f, 5f))
        assertEquals(listOf(emptyList(), listOf(a)), Layout(desktop = listOf(emptyList(), emptyList())).placed(1, a).desktop)
        assertEquals(listOf(b, a.copy(cell = Cell(2f, 1f))), Layout(desktop = listOf(listOf(b))).placed(0, a).desktop[0]) // taken meanwhile
        assertEquals(listOf(listOf(full), listOf(a)), Layout(desktop = listOf(listOf(full), emptyList())).placed(0, a).desktop)
        assertEquals(listOf(listOf(a)), Layout().placed(3, a).desktop) // its page is gone
    }
}
