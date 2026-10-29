package io.github.gastoncampana.launcher

import io.github.gastoncampana.launcher.Target.App
import io.github.gastoncampana.launcher.Target.Shortcut
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class NovaTest {
    private val spotify = "#Intent;action=android.intent.action.MAIN;category=android.intent.category.LAUNCHER;" +
        "launchFlags=0x10200000;component=com.spotify.music/.MainActivity;end"
    private val voice = "Voz$#Intent;action=android.intent.action.MAIN;category=com.android.launcher3.DEEP_SHORTCUT;" +
        "launchFlags=0x10200000;package=com.openai.chatgpt;component=com.openai.chatgpt/.MainActivity;S.shortcut_id=VOICE;end"
    private val record = "Grabar$#Intent;action=android.intent.action.MAIN;category=com.android.launcher3.DEEP_SHORTCUT;" +
        "package=com.google.android.apps.recorder;component=com.google.android.apps.recorder/.ui.recordings.MainActivity;" +
        "S.shortcut_id=quick_record;end"

    @Test
    fun parsesActivitiesShortcutsAndSkipsNovaActions() {
        assertEquals(App("com.spotify.music/com.spotify.music.MainActivity#0"), novaTarget(spotify, 0))
        assertEquals(
            App("com.android.chrome/com.google.android.apps.chrome.Main#10"),
            novaTarget("Chrome$#Intent;component=com.android.chrome/com.google.android.apps.chrome.Main;end", 10),
        )
        assertEquals(Shortcut("com.openai.chatgpt", "VOICE", "Voz"), novaTarget(voice, 0))
        assertEquals(Shortcut("p", "a b", ""), novaTarget("#Intent;package=p;S.shortcut_id=a%20b;end", 0))
        assertNull(
            novaTarget(
                "Cajón$#Intent;action=com.teslacoilsw.launcher.ACTION;component=com.teslacoilsw.launcher/.NovaShortcutHandler;" +
                    "S.LAUNCHER_ACTION=APP_DRAWER;end",
                0,
            ),
        )
        assertNull(novaTarget(null, 0))
        assertNull(novaTarget("", 0))
    }

    @Test
    fun convertsDockDesktopTabsAndGestures() {
        val chrome = "#Intent;category=com.android.launcher3.DEEP_SHORTCUT;package=com.android.chrome;" +
            "component=com.android.chrome/com.google.android.apps.chrome.Main;S.shortcut_id=98a6;end"
        val favorites = listOf(
            NovaFavorite(DOCK, 1, 0f, 0f, intent = "#Intent;component=x/.X;end"),
            NovaFavorite(DOCK, 0, 4f, 0f, intent = spotify, up = "U$#Intent;component=u/.U;end", down = voice),
            NovaFavorite(DESKTOP, 3, 0f, 0f, 5f, 1.5f, type = 4, provider = "d/d.W"),
            NovaFavorite(DESKTOP, 0, 0f, 1f, type = 6, title = "Board", intent = chrome),
            NovaFavorite(DESKTOP, 0, 0.5f, 4.5f, 1f, 0.5f, type = 4, provider = "h/h.W"),
            NovaFavorite(DESKTOP, 0, 1f, 1f, title = "Notes", intent = "#Intent;component=w/.W;end"),
            NovaFavorite(DESKTOP, 0, 2f, 2f, type = 2, title = "Carpeta"),
            NovaFavorite(-200, 0, 0f, 0f, intent = "#Intent;component=f/.F;end"),
        )
        val tabs = listOf(
            NovaTab("Apps", "TAB_APPS", 4, false, emptyList()),
            NovaTab("A", "TAB_APP_GROUP", 1, true, listOf("com.spotify.music/com.spotify.music.MainActivity#-1")),
            NovaTab("Venenos", "TAB_WORK_APPS", 5, false, emptyList()),
            NovaTab("Juegos", "FOLDER_APP_GROUP", 5, true, listOf("g/g.G#-1")),
        )
        val prefs = mapOf("desktop_default_page" to "1", "gesture_double_tap" to record)

        val hidden = listOf("com.instagram.android/com.instagram.android.activity.MainTabActivity#-1")

        val layout = novaLayout(favorites, tabs, hidden, prefs, user = 0)

        assertEquals(2, layout.dock.size)
        assertEquals(
            DockIcon(App("com.spotify.music/com.spotify.music.MainActivity#0"), App("u/u.U#0"), Shortcut("com.openai.chatgpt", "VOICE", "Voz")),
            layout.dock[0][4],
        )
        assertEquals(DockIcon(), layout.dock[0][0])
        assertEquals(DockIcon(App("x/x.X#0")), layout.dock[1][0])
        assertEquals(
            listOf(
                Item.Icon(Shortcut("com.android.chrome", "98a6", "Board"), "Board", Cell(0f, 1f)),
                Item.Widget("h/h.W", 0, Cell(0.5f, 4.5f, 1f, 0.5f)),
                Item.Icon(App("w/w.W#0"), "Notes", Cell(1f, 1f)),
            ),
            layout.desktop[0],
        )
        assertEquals(listOf(Item.Widget("d/d.W", 0, Cell(0f, 0f, 5f, 1.5f))), layout.desktop[1])
        assertEquals(1, layout.defaultPage)
        assertEquals(listOf(Tab("A", setOf("com.spotify.music/com.spotify.music.MainActivity#0"), hideApps = true), Tab("Apps")), layout.tabs)
        assertEquals(Shortcut("com.google.android.apps.recorder", "quick_record", "Grabar"), layout.doubleTap)
        assertEquals(setOf("com.instagram.android/com.instagram.android.activity.MainTabActivity#0"), layout.hidden)
    }

    @Test
    fun dockPageCountComesFromPrefsWhenLarger() {
        val layout = novaLayout(emptyList(), emptyList(), emptyList(), mapOf("dock_pages_count" to "3"), user = 0)
        assertEquals(3, layout.dock.size)
        assertEquals(listOf(emptyList<Item>()), layout.desktop)
    }
}
