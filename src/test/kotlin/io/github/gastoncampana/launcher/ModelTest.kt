package io.github.gastoncampana.launcher

import io.github.gastoncampana.launcher.Target.App
import io.github.gastoncampana.launcher.Target.Shortcut
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
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
        )
        assertEquals(layout, parseLayout(layout.toJson()))
    }

    @Test
    fun calcEvaluatesArithmeticAndIgnoresEverythingElse() {
        assertEquals(375.0, calc("125*3")!!, 0.0)
        assertEquals(7.0, calc("1 + 2 x 3")!!, 0.0)
        assertEquals(9.0, calc("(1+2)×3")!!, 0.0)
        assertEquals(3.0, calc("1,5 * 2")!!, 0.0) // a comma is a decimal point too
        assertEquals(0.3, calc("0.1+0.2")!!, 1e-12)
        assertEquals(-4.0, calc("-2^2")!!, 0.0)
        assertEquals(30.0, calc("200*15%")!!, 1e-12)
        assertEquals(2.5, calc("10÷4")!!, 0.0)
        // Not sums: app names, lone numbers, broken or undefined expressions.
        for (q in listOf("", "Spotify", "2048", "-5", "7-Eleven", "1+", "(1+2", "1/0", "1..2+1", "2**3")) assertNull(q, calc(q))
        assertNull(calc("(".repeat(5000) + "1" + ")".repeat(5000))) // pasted junk must not overflow the stack
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
}
