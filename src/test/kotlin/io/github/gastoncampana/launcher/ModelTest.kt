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
                    List(DOCK_SIZE - 1) { DockIcon() },
                List(DOCK_SIZE) { DockIcon() },
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
        )
        assertEquals(layout, parseLayout(layout.toJson()))
    }

    @Test
    fun parsePadsDockPagesAndClampsDefaultPage() {
        val layout = parseLayout("""{"dock":[[{}]],"desktop":[[]],"defaultPage":4,"tabs":[]}""")
        assertEquals(listOf(List(DOCK_SIZE) { DockIcon() }), layout.dock)
        assertEquals(0, layout.defaultPage)
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
        assertEquals(Cell(1f, 0.5f), freeCell(items, 1f, 1f))
        assertEquals(Cell(0f, 1.5f, 5f, 2f), freeCell(items, 5f, 2f))
        assertNull(freeCell(listOf(Item.Widget("w", 0, Cell(0f, 0f, GRID.toFloat(), GRID.toFloat()))), 1f, 1f))
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
    }
}
