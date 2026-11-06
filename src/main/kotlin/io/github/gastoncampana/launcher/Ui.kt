package io.github.gastoncampana.launcher

import android.app.SearchManager
import android.app.admin.DevicePolicyManager
import android.appwidget.AppWidgetHostView
import android.appwidget.AppWidgetProviderInfo
import android.content.ClipData
import android.content.ClipboardManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.provider.DocumentsContract
import android.provider.Settings
import android.telephony.PhoneNumberUtils
import android.util.SizeF
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.widget.RemoteViews
import androidx.activity.compose.BackHandler
import androidx.annotation.StringRes
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.TimePicker
import androidx.compose.material3.rememberTimePickerState
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.LocalContentColor
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.paneTitle
import androidx.compose.ui.semantics.semantics
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.IndicationNodeFactory
import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.interaction.InteractionSource
import androidx.compose.foundation.interaction.PressInteraction
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyGridScope
import androidx.compose.foundation.lazy.grid.LazyGridState
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.PagerState
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.systemGestureExclusion
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.ColorMatrix
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.ContentDrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.layer.GraphicsLayer
import androidx.compose.ui.graphics.layer.drawLayer
import androidx.compose.ui.graphics.rememberGraphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedback
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.PointerInputChange
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.util.VelocityTracker
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.node.DelegatableNode
import androidx.compose.ui.node.DrawModifierNode
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.font.Typeface
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.Velocity
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.round
import androidx.compose.ui.unit.sp
import androidx.compose.ui.unit.toSize
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.core.graphics.drawable.toBitmap
import androidx.core.net.toUri
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.asExecutor
import kotlinx.coroutines.delay
import java.net.IDN
import java.text.DateFormat
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.TextStyle as DayStyle
import java.util.Locale
import java.text.NumberFormat
import java.util.Date
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlin.math.round
import kotlin.math.roundToInt

// Sizes and colours measured on Nova screenshots (448 dp wide).
private val ICON = 55.dp
private val DRAWER_ICON = 54.dp
private val SWIPE_MIN = 48.dp
private val DOCK_HEIGHT = 83.dp
private val ACCENT = Color(0xFFD64526)
private val TAB_OFF = Color(0xFF313030)
private val TAB_OFF_TEXT = Color(0xFFACABAA)
private val HINT = Color(0xFF808080)
private val TRACK = Color(0xFF1E1E1E)
private val BLUE = Color(0xFF4285F4)
private val RED = Color(0xFFEA4335)
private val YELLOW = Color(0xFFFBBC04)
private val GREEN = Color(0xFF34A853)
private val CONDENSED = FontFamily(Typeface(android.graphics.Typeface.create("sans-serif-condensed", android.graphics.Typeface.NORMAL)))
private val LABEL = TextStyle(color = Color.White, fontSize = 13.sp, fontFamily = CONDENSED, shadow = Shadow(Color.Black.copy(alpha = 0.6f), Offset(0f, 1f), 4f))
private val GREY = ColorFilter.colorMatrix(ColorMatrix().apply { setToSaturation(0f) })
private val TAB_HEIGHT = 40.dp
private val TAB_GAP = 11.dp // above the tabs
private val GRID_PADDING = PaddingValues(start = 8.dp, end = 8.dp, top = 25.dp, bottom = 16.dp)
private val LABEL_SPACE = 28.dp // a label under an icon, with its gap
private val PREVIEW_HEIGHT = 160.dp // a widget's preview in the widget picker, at most
private val SHORTCUT_ICON = 32.dp // an app shortcut's, in its menu
// Widget previews load and inflate here, two at a time: a fling neither stutters nor floods the IO pool.
private val PREVIEW_LOADER = Dispatchers.IO.limitedParallelism(2)
private const val ICON_SHARE = 0.62f // of a cell's width, at most: Nova's icon on a 5-column grid
private const val PRESSED_SCALE = 0.92f // an icon while the finger is on it
private const val DOCK_LOOPS = 1000 // virtual dock pages, so it scrolls around in both directions
private const val EDGE_HOLD_MS = 600L // holding a dragged item at a side this long flips the page
private const val THROW_DP_PER_S = 3000f // a dragged desktop item released this fast is thrown off; a calibration knob
private const val THROW_MS = 200 // a thrown item's flight out, fading
private const val HELD_STILL_MS = 40L // no move this long before a release: the finger stopped (Android's own rule)
private const val PAUSE_SECONDS = 10 // before a poison app opens
private const val PAUSE_STEP_SECONDS = 5 // added for each time it was opened today

/** The one open menu or dialog; the activity clears it on home, back and when leaving. */
sealed interface Popup {
    data class DesktopMenu(val page: Int, val at: Offset) : Popup
    data class ItemMenu(val page: Int, val item: Item) : Popup
    data class MoveItem(val page: Int, val item: Item) : Popup
    data class Resize(val page: Int, val item: Item.Widget) : Popup
    data class DockMenu(val virtualPage: Int, val slot: Int) : Popup
    data class DockSetup(val page: Int, val slot: Int) : Popup
    data class AppMenu(val id: AppId) : Popup
    data class TabMenu(val index: Int) : Popup
    class PickApp(val title: String, val back: Popup? = null, val onPick: (App?) -> Unit) : Popup // back: shown again on cancel
    class Rename(val title: String, val initial: String, val onDone: (String) -> Unit) : Popup
    data class Widgets(val page: Int) : Popup
    class Pause(val app: App, val open: () -> Unit) : Popup
    class Confirm(val title: String, val text: String, val action: String, val onYes: () -> Unit) : Popup
    class Grid(val title: String, val columns: Int, val rows: Int, val note: (Int, Int) -> String, val onDone: (Int, Int) -> Unit) : Popup
    data class EditTab(val index: Int) : Popup
    data object Numbers : Popup
    data object PausedApps : Popup

    /** Ajustes → Bloqueos: the block of [id] to edit, or a new one (null). */
    class EditBlock(val id: String?) : Popup

    /** Apps to tick, like the hidden ones: [ids] reads the set from a layout, [put] writes it back. */
    class Checklist(val title: String, val ids: (Layout) -> Set<AppId>, val put: (Layout, Set<AppId>) -> Layout) : Popup
}

@Composable
fun LauncherScreen(l: Launcher) {
    val haptic = rememberHaptic(l)
    val mover = remember { Mover(l, haptic) }
    MaterialTheme(colorScheme = novaColors()) {
        Box(Modifier.fillMaxSize()) {
            // Hidden, not removed, under Ajustes: its clear preview strips show the wallpaper, and pages and widgets keep their state.
            // The full-screen widget picker covers it too: TalkBack must not reach what is underneath.
            val covered = l.settingsOpen || l.popup is Popup.Widgets
            Box(Modifier.graphicsLayer { alpha = if (l.settingsOpen) 0f else 1f }.then(if (covered) Modifier.clearAndSetSemantics {} else Modifier)) {
                Home(l, mover)
            }
            key(l.drawerResets) {
                AnimatedVisibility(l.drawerOpen, enter = slideInVertically { it / 4 } + fadeIn(), exit = slideOutVertically { it / 4 } + fadeOut()) {
                    // While one of its apps is dragged to the home screen, the drawer stays (the gesture lives in it) but unseen.
                    Box(Modifier.graphicsLayer { alpha = if (mover.drag?.page == null && mover.drag != null) 0f else 1f }) { Drawer(l, mover) }
                }
            }
            // The block editor covers Ajustes whole: TalkBack must not reach what is underneath.
            if (l.settingsOpen) Box(if (l.popup is Popup.EditBlock) Modifier.clearAndSetSemantics {} else Modifier) { SettingsScreen(l) }
            when (val p = l.popup) {
                is Popup.PickApp -> AppPicker(l, p)
                is Popup.Rename -> NameDialog(l, p)
                is Popup.Widgets -> WidgetPicker(l, p.page)
                is Popup.Pause -> key(p) { PauseScreen(l, p) } // keyed: a poison opened instead gets its own countdown
                is Popup.Confirm -> ConfirmDialog(l, p)
                is Popup.Checklist -> AppChecklist(l, p)
                is Popup.Grid -> GridDialog(l, p)
                is Popup.EditTab -> l.layout.tabs.getOrNull(p.index)?.let { TabDialog(l, p.index, it) }
                is Popup.DockSetup -> l.layout.dock.getOrNull(p.page)?.getOrNull(p.slot)?.let { DockDialog(l, p, it) }
                is Popup.EditBlock -> key(p) { BlockEditor(l, p) }
                else -> Unit // menus render next to what they belong to
            }
        }
    }
}

/**
 * Every vibration goes through here, so the setting in Ajustes silences them all. The system's touch feedback
 * setting still applies: nothing forces it.
 */
@Composable
private fun rememberHaptic(l: Launcher): (HapticFeedbackType) -> Unit {
    val feedback = LocalHapticFeedback.current
    return remember(feedback) { { type -> if (l.layout.haptics) feedback.performHapticFeedback(type) } }
}

/**
 * Text fields vibrate on their own when selecting text: this sends [content]'s through [rememberHaptic] too. Each
 * dialog provides its own [LocalHapticFeedback], so the field itself is wrapped.
 */
@Composable
private fun GatedHaptics(l: Launcher, content: @Composable () -> Unit) {
    val haptic = rememberHaptic(l)
    val feedback = remember(haptic) {
        object : HapticFeedback {
            override fun performHapticFeedback(hapticFeedbackType: HapticFeedbackType) = haptic(hapticFeedbackType)
        }
    }
    CompositionLocalProvider(LocalHapticFeedback provides feedback, content)
}

// --- Home: desktop pages, dock, search bar ---

@Composable
private fun Home(l: Launcher, mover: Mover) {
    val layout = l.layout
    val pages = layout.desktop.size
    val pager = key(pages, layout.defaultPage) { rememberPagerState(initialPage = layout.defaultPage) { pages } }
    SideEffect { mover.pager = pager }
    val haptic = rememberHaptic(l)
    LaunchedEffect(l.homePresses) {
        if (l.homePresses == 0) return@LaunchedEffect
        // Like Nova: home goes back to the main page, and opens search when already there.
        if (pager.currentPage != layout.defaultPage) pager.animateScrollToPage(layout.defaultPage) else l.openSearch()
    }
    // Holding a dragged item at a side of the desktop flips to the page there.
    val edge by remember(mover) { derivedStateOf { mover.edge() } }
    LaunchedEffect(edge, pager) {
        while (edge != 0) {
            delay(EDGE_HOLD_MS)
            val next = pager.currentPage + edge
            if (next !in 0..<pager.pageCount) break
            haptic(HapticFeedbackType.SegmentTick)
            pager.animateScrollToPage(next)
        }
    }
    // "Quitado · Deshacer" while an item thrown off can come back; its host is a polite live region for TalkBack.
    val bar = remember { SnackbarHostState() }
    val removed = stringResource(R.string.removed)
    val undo = stringResource(R.string.undo)
    LaunchedEffect(l.thrown) {
        val t = l.thrown ?: return@LaunchedEffect // settled elsewhere: this cancels the bar shown
        val undone = bar.showSnackbar(removed, undo, duration = SnackbarDuration.Short) == SnackbarResult.ActionPerformed
        // A throw that replaced it before this effect is cancelled keeps its own bar.
        if (l.thrown !== t) return@LaunchedEffect
        if (undone) l.undoThrow() else l.settle()
    }
    Box(Modifier.fillMaxSize()) {
        Column(
            Modifier
                .fillMaxSize()
                .onVerticalSwipe(
                    up = {
                        haptic(HapticFeedbackType.GestureThresholdActivate)
                        l.drawerOpen = true
                    },
                    down = {
                        haptic(HapticFeedbackType.GestureThresholdActivate)
                        l.expandNotifications()
                    },
                )
                // Under the resize frame, TalkBack must not reach (nor click) what it covers.
                .then(if (l.popup is Popup.Resize) Modifier.clearAndSetSemantics {} else Modifier)
                .systemBarsPadding(),
        ) {
            Box(Modifier.weight(1f)) {
                HorizontalPager(
                    pager,
                    Modifier.fillMaxSize().onGloballyPositioned { mover.bounds = it.boundsInRoot() },
                    beyondViewportPageCount = pages,
                ) { page -> DesktopPage(l, mover, page) }
                SnackbarHost(bar, Modifier.align(Alignment.BottomCenter))
            }
            PageLine(pager)
            // A hidden dock takes no drops: they land on the desktop.
            if (layout.showDock) Dock(l, mover) else SideEffect { mover.dock = Rect.Zero }
            if (layout.showSearchBar) SearchPill(l)
        }
        mover.drag?.let { d ->
            val size = with(LocalDensity.current) { d.size.toSize().toDpSize() }
            Box(Modifier.offset { (d.pointer - d.grab).round() }.size(size).drawBehind { drawLayer(d.layer) })
        }
        mover.flight?.let { f ->
            // Flies on the throw's way while it fades, then leaves the desktop.
            val t = remember(f) { Animatable(0f) }
            LaunchedEffect(f) {
                t.animateTo(1f, tween(THROW_MS, easing = LinearEasing))
                l.throwAway(f.page, f.drag.item)
                mover.flight = null
            }
            val size = with(LocalDensity.current) { f.drag.size.toSize().toDpSize() }
            Box(
                Modifier
                    .offset { (f.drag.pointer - f.drag.grab + f.velocity * (t.value * THROW_MS / 1000f)).round() }
                    .size(size)
                    .graphicsLayer { alpha = 1f - t.value }
                    .drawBehind { drawLayer(f.drag.layer) },
            )
        }
        (l.popup as? Popup.Resize)?.let { ResizeFrame(l, mover, it) }
    }
}

/**
 * An item being dragged, from desktop [page] or (null) from the drawer. Home draws [layer], which the item keeps
 * recording but no longer draws in its place, under the finger.
 */
private class Drag(val page: Int?, val item: Item, val layer: GraphicsLayer, val size: IntSize, val grab: Offset) {
    var pointer by mutableStateOf(Offset.Zero) // root coordinates
    val velocity = VelocityTracker() // of [pointer]: root coordinates, so a page flip under the finger adds none
}

/** A desktop item thrown off at [velocity] (px/s): Home flies it out, then takes it off [page]. */
private class Flight(val page: Int, val drag: Drag, val velocity: Offset)

/**
 * Drags items onto the desktop page shown (the nearest free spot) or into an empty dock slot; a desktop item released
 * at a throw's speed is thrown off instead, with a bar to undo.
 */
private class Mover(private val l: Launcher, private val haptic: (HapticFeedbackType) -> Unit) {
    var drag by mutableStateOf<Drag?>(null)
    var flight by mutableStateOf<Flight?>(null)
    var pager: PagerState? = null // the desktop's, set by Home
    var bounds = Rect.Zero // the desktop pager, in root coordinates
    var dock = Rect.Zero
    var dockPage = { 0 } // the dock page shown, set by Dock

    /** -1 or 1 while the dragged item is held at that side of the desktop, else 0. */
    fun edge(): Int {
        val x = drag?.pointer?.x ?: return 0
        val margin = bounds.width * 0.08f
        return if (x < bounds.left + margin) -1 else if (x > bounds.right - margin) 1 else 0
    }

    /** Whether Home draws [item] (dragged or flying) instead of its own place. */
    fun lifted(item: Item) = drag?.item == item || flight?.drag?.item == item

    fun drop() {
        val d = drag ?: return
        drag = null
        if (d.page == null) l.hideDrawer()
        val app = (d.item as? Item.Icon)?.target as? Target.App
        if (app != null && d.pointer in dock) return dropOnDock(d, app) // however fast: the dock is where it was aimed
        val velocity = d.velocity.calculateVelocity().let { Offset(it.x, it.y) }
        if (velocity.getDistance() > THROW_DP_PER_S * l.resources.displayMetrics.density) {
            // Thrown: a desktop item goes, one from the drawer is only cancelled (nothing added).
            if (d.page != null) {
                haptic(HapticFeedbackType.GestureThresholdActivate)
                flight = Flight(d.page, d, velocity)
            } else {
                haptic(HapticFeedbackType.Reject)
            }
            return
        }
        if (d.pointer !in bounds) return // released over the search bar or page line: cancelled
        val page = pager?.currentPage ?: return
        val at = d.pointer - d.grab - bounds.topLeft
        val want = d.item.cell.copy(x = at.x / bounds.width * l.layout.columns, y = at.y / bounds.height * l.layout.rows)
        val before = l.layout
        l.update { it.moved(d.page, d.item, page, want) }
        if (l.layout === before) return refuse(l.getString(R.string.desktop_no_room_page))
        haptic(HapticFeedbackType.Confirm)
        placedPaused(app)
    }

    /** A paused app Ajustes hides is placed all the same, out of sight until it resumes: say so. */
    private fun placedPaused(app: Target.App?) {
        if (l.hides(app)) l.toast(l.getString(R.string.paused_placed, l.labelOf(app)))
    }

    // Only an empty slot: replacing a dock icon by accident would lose its swipes' setup with no undo.
    private fun dropOnDock(d: Drag, app: Target.App) {
        val page = dockPage()
        val size = l.layout.dockSize
        val slot = ((d.pointer.x - dock.left) / dock.width * size).toInt().coerceIn(0, size - 1)
        val tap = l.layout.dock[page][slot].tap
        if (tap != null) return refuse(if (l.hides(tap)) l.getString(R.string.dock_slot_paused, l.labelOf(tap)) else l.getString(R.string.dock_slot_taken))
        l.update { layout ->
            layout.copy(
                dock = layout.dock.replaced(page) { it.replaced(slot) { icon -> icon.copy(tap = app) } },
                desktop = if (d.page == null) layout.desktop else layout.desktop.replaced(d.page) { it - d.item },
            )
        }
        haptic(HapticFeedbackType.Confirm)
        placedPaused(app)
    }

    private fun refuse(why: String) {
        haptic(HapticFeedbackType.Reject)
        l.toast(why)
    }
}

@Composable
private fun DesktopPage(l: Launcher, mover: Mover, page: Int) {
    val haptic = rememberHaptic(l)
    BoxWithConstraints(
        Modifier
            .fillMaxSize()
            .pointerInput(page) {
                detectTapGestures(
                    onDoubleTap = {
                        l.layout.doubleTap?.let {
                            haptic(HapticFeedbackType.Confirm)
                            l.open(it)
                        }
                    },
                    onLongPress = { at ->
                        // A widget that ignores touches lets the press through; the widget opens its own menu. A hidden
                        // paused app's spot looks empty, so it takes the press too.
                        val point = Cell(at.x / size.width * l.layout.columns, at.y / size.height * l.layout.rows, 0f, 0f)
                        if (l.layout.desktop.getOrNull(page).orEmpty().none { it.cell.overlaps(point) && !(it is Item.Icon && l.hides(it.target)) }) {
                            haptic(HapticFeedbackType.LongPress)
                            l.popup = Popup.DesktopMenu(page, at)
                        }
                    },
                )
            },
    ) {
        val cell = DpSize(maxWidth / l.layout.columns, maxHeight / l.layout.rows)
        SideEffect { l.cellSize = cell }
        val icon = iconSize(ICON, cell.width, cell.height - LABEL_SPACE)
        // A paused app's icon Ajustes hides keeps its spot, saved, until the pause ends. A bound widget is keyed by
        // its id, so a resize or a move on the page keeps its cached view where it is.
        for (item in l.layout.desktop.getOrNull(page).orEmpty().filterNot { it is Item.Icon && l.hides(it.target) }) key(if (item is Item.Widget && item.widgetId != 0) item.widgetId else item) {
            val size = DpSize(cell.width * item.cell.w, cell.height * item.cell.h)
            val at = Modifier.offset(cell.width * item.cell.x, cell.height * item.cell.y).size(size)
            Movable(l, mover, page, item, { l.popup = Popup.ItemMenu(page, item) }, at) {
                when (item) {
                    is Item.Icon -> DesktopIcon(l, item, icon)
                    is Item.Widget -> DesktopWidget(l, page, item, size)
                }
                ItemMenu(l, page, item)
            }
        }
        (l.popup as? Popup.DesktopMenu)?.takeIf { it.page == page }?.let { DesktopMenu(l, it) }
    }
}

/**
 * An item that a long press lifts, on desktop [page] or (null) in the drawer: the press opens its menu
 * ([onLongPress]), and dragging closes the menu and moves it, as in Nova.
 */
@Composable
private fun Movable(
    l: Launcher,
    mover: Mover,
    page: Int?,
    item: Item,
    onLongPress: () -> Unit,
    modifier: Modifier,
    content: @Composable BoxScope.() -> Unit,
) {
    val layer = rememberGraphicsLayer()
    val haptic = rememberHaptic(l)
    val keyboard = LocalSoftwareKeyboardController.current // open when dragging from search results
    var coords by remember { mutableStateOf<LayoutCoordinates?>(null) }
    Box(
        modifier
            .onGloballyPositioned { coords = it }
            .drawWithContent {
                // Recorded while lifted too, so the copy under the finger springs back from PressScale's pressed size.
                layer.record { this@drawWithContent.drawContent() }
                if (!mover.lifted(item)) drawLayer(layer) // lifted: Home draws it under the finger, or flying off
            }
            .longPressDrag(
                item,
                onLongPress = {
                    haptic(HapticFeedbackType.LongPress) // a drag starts from this press, so it adds none
                    onLongPress()
                },
                onDragStart = { grab ->
                    l.popup = null
                    keyboard?.hide()
                    coords?.let { mover.drag = Drag(page, item, layer, it.size, grab) }
                },
                onDrag = { change ->
                    val d = mover.drag
                    val c = coords
                    if (d != null && c != null) {
                        d.pointer = c.localToRoot(change.position)
                        // As Android's own tracker: the release repeats the last position, so it adds no sample (it
                        // would read as a stop), but a finger held still before it lifts makes no throw.
                        if (change.pressed) {
                            change.historical.forEach { d.velocity.addPosition(it.uptimeMillis, c.localToRoot(it.position)) }
                            d.velocity.addPosition(change.uptimeMillis, d.pointer)
                        } else if (change.uptimeMillis - change.previousUptimeMillis > HELD_STILL_MS) {
                            d.velocity.resetTracking()
                        }
                    }
                },
                onDrop = mover::drop,
                onCancel = { if (mover.drag?.item == item) mover.drag = null },
            ),
        content = content,
    )
}

@Composable
private fun DesktopMenu(l: Launcher, menu: Popup.DesktopMenu) {
    Box(Modifier.offset { IntOffset(menu.at.x.roundToInt(), menu.at.y.roundToInt()) }) {
        DropdownMenu(true, { l.popup = null }) {
            MenuItem(l, stringResource(R.string.widget_add)) { l.popup = Popup.Widgets(menu.page) }
            MenuItem(l, stringResource(R.string.menu_wallpaper)) {
                l.startIntent(Intent.createChooser(Intent(Intent.ACTION_SET_WALLPAPER), null), l.getString(R.string.open_target_wallpaper_picker))
            }
            MenuItem(l, stringResource(R.string.settings)) {
                l.settle() // Ajustes can remove, reorder or regrid the pages a thrown item would go back to
                l.settingsOpen = true
            }
        }
    }
}

@Composable
private fun DesktopIcon(l: Launcher, item: Item.Icon, icon: Dp) {
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Column(
            Modifier.clickable(interactionSource = null, indication = PressScale) { l.open(item.target) },
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            TargetIcon(l, item.target, icon)
            Text(item.label, Modifier.padding(top = 6.dp, start = 4.dp, end = 4.dp), style = LABEL, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
    }
}

@Composable
private fun DesktopWidget(l: Launcher, page: Int, item: Item.Widget, size: DpSize) {
    val view = remember(item.widgetId) { if (item.widgetId != 0) l.widgetView(item.widgetId) else null }
    if (view == null) {
        WidgetPlaceholder(l, item) { l.bindWidget(page, item) }
    } else {
        AndroidView({ view }, Modifier.fillMaxSize()) { it.updateAppWidgetSize(Bundle(), listOf(SizeF(size.width.value, size.height.value))) }
    }
}

/** A widget that is not bound to this launcher (its setup was interrupted, or its app was reinstalled). */
@Composable
private fun WidgetPlaceholder(l: Launcher, item: Item.Widget, onClick: () -> Unit) {
    val name = remember(item.provider) {
        l.providerOf(item)?.loadLabel(l.packageManager) ?: item.provider.substringBefore('/')
    }
    Box(
        Modifier
            .fillMaxSize()
            .padding(6.dp)
            .clip(RoundedCornerShape(16.dp))
            .background(Color.Black.copy(alpha = 0.45f))
            .clickable(onClick = onClick)
            .padding(6.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text("+ $name", style = LABEL, textAlign = TextAlign.Center, maxLines = 2, overflow = TextOverflow.Ellipsis)
    }
}

@Composable
private fun ItemMenu(l: Launcher, page: Int, item: Item) {
    val target = (item as? Item.Icon)?.target
    val app = l.appOf(target)
    val open = l.popup == Popup.ItemMenu(page, item)
    val shortcuts = rememberShortcuts(l, app.takeIf { target is Target.App }, open) // a pinned shortcut's icon lists none
    DropdownMenu(open && shortcuts != null, { l.popup = null }) {
        if (app != null) AppShortcuts(l, app, shortcuts.orEmpty())
        if (item is Item.Icon) MenuItem(l, stringResource(R.string.rename)) {
            // This icon's label only: the drawer and search keep the app's name.
            l.popup = Popup.Rename(l.getString(R.string.desktop_rename_icon), item.label) { name ->
                l.updatePage(page) { items -> items.map { if (it == item) item.copy(label = name) else it } }
            }
        }
        if (item is Item.Widget) {
            // Composed only while shown, so the provider's binder lookup runs on opening; none for a placeholder.
            val resizable = remember(item) { (l.widgetInfo(item)?.resizeMode ?: AppWidgetProviderInfo.RESIZE_NONE) != AppWidgetProviderInfo.RESIZE_NONE }
            if (resizable) MenuItem(l, stringResource(R.string.widget_resize)) { l.popup = Popup.Resize(page, item) }
        }
        if (l.layout.desktop.size > 1) MenuItem(l, stringResource(R.string.desktop_move_to_page)) { l.popup = Popup.MoveItem(page, item) }
        if (app != null) MenuItem(l, stringResource(R.string.menu_app_info)) { l.showInfo(app) }
        if (app != null && target is Target.App && app.removable) MenuItem(l, stringResource(R.string.uninstall)) { l.uninstall(app) }
        if (item is Item.Widget && item.widgetId != 0) MenuItem(l, stringResource(R.string.widget_reconfigure)) { l.unbind(page, item) }
        MenuItem(l, stringResource(R.string.remove)) { l.remove(page, item) }
    }
    // The other pages, the main one marked: the item keeps its spot there if free, else takes the nearest one.
    DropdownMenu(l.popup == Popup.MoveItem(page, item), { l.popup = null }) {
        for (to in l.layout.desktop.indices) if (to != page) {
            val name = stringResource(R.string.settings_page, to + 1)
            MenuItem(l, if (to == l.layout.defaultPage) "$name · ${stringResource(R.string.desktop_page_main)}" else name) {
                val before = l.layout
                l.update { it.moved(page, item, to, item.cell) }
                if (l.layout === before) l.toast(l.getString(R.string.desktop_no_room_page_n, to + 1))
            }
        }
    }
}

/**
 * [Popup.Resize]'s frame over its widget, with a handle on each side its provider's resizeMode lets move. A handle
 * moves its side in half cells, a tick each, within the widget's limits, the page and its neighbours; the widget takes
 * the new size on release. A tap outside ends it, as Back, home and [Launcher.closeAll] do.
 */
@Composable
private fun ResizeFrame(l: Launcher, mover: Mover, p: Popup.Resize) {
    val haptic = rememberHaptic(l)
    val info = remember(p.item.widgetId) { l.widgetInfo(p.item) } ?: return
    val limits = remember(info, l.layout.columns, l.layout.rows) { l.resizeLimits(info) }
    var cell by remember(p) { mutableStateOf(p.item.cell) }
    val bounds = mover.bounds // the desktop pager, in root coordinates: Home's too
    val unit = Size(bounds.width / l.layout.columns, bounds.height / l.layout.rows) // px per cell
    val frame = Rect(
        Offset(bounds.left + cell.x * unit.width, bounds.top + cell.y * unit.height),
        Size(cell.w * unit.width, cell.h * unit.height),
    )
    val sides = buildList {
        if (info.resizeMode and AppWidgetProviderInfo.RESIZE_HORIZONTAL != 0) addAll(listOf(Edge.LEFT, Edge.RIGHT))
        if (info.resizeMode and AppWidgetProviderInfo.RESIZE_VERTICAL != 0) addAll(listOf(Edge.TOP, Edge.BOTTOM))
    }
    val handle = 48.dp
    val density = LocalDensity.current
    val (handlePx, dotPx) = with(density) { handle.toPx() to 9.dp.toPx() }
    /** The widget takes [to], else the frame goes back to the widget's size. */
    fun commit(to: Cell): Boolean {
        val before = l.layout
        if (to != p.item.cell) l.update { it.resized(p.page, p.item, to) }
        if (l.layout === before) {
            cell = p.item.cell
            return false
        }
        l.popup = Popup.Resize(p.page, p.item.copy(cell = to))
        return true
    }
    val title = stringResource(R.string.widget_resize)
    val grow = stringResource(R.string.resize_grow)
    val shrink = stringResource(R.string.resize_shrink)
    // Over all of Home, so what is under it takes no touch; a tap on the frame or its handles leaves it open.
    Box(
        Modifier
            .fillMaxSize()
            .semantics { paneTitle = title }
            .pointerInput(frame) { detectTapGestures { if (it !in frame.inflate(handle.toPx() / 2)) l.popup = null } },
    ) {
        Box(
            Modifier
                .offset { frame.topLeft.round() }
                .size(with(density) { frame.size.toDpSize() })
                .border(4.dp, Color.Black.copy(alpha = 0.4f), RoundedCornerShape(8.dp)) // white between dark lines: seen
                .padding(1.dp) // over a light wallpaper or widget too
                .border(2.dp, Color.White, RoundedCornerShape(7.dp)),
        )
        for (edge in sides) {
            val across = edge == Edge.LEFT || edge == Edge.RIGHT
            val at = when (edge) {
                Edge.LEFT -> Offset(frame.left, frame.center.y)
                Edge.TOP -> Offset(frame.center.x, frame.top)
                Edge.RIGHT -> Offset(frame.right, frame.center.y)
                Edge.BOTTOM -> Offset(frame.center.x, frame.bottom)
            }
            // Kept on the desktop, so a full-width widget's side handles are not half off the screen nor a top one
            // half under the status bar (whose touches go to the system); its dot stays on the side, whole.
            val box = Offset(
                (at.x - handlePx / 2).coerceAtMost(bounds.right - handlePx).coerceAtLeast(bounds.left),
                (at.y - handlePx / 2).coerceAtLeast(bounds.top),
            )
            val dot = Offset(at.x.coerceAtMost(bounds.right - dotPx).coerceAtLeast(bounds.left + dotPx), at.y.coerceAtLeast(bounds.top + dotPx)) - box
            val label = stringResource(
                when (edge) {
                    Edge.LEFT -> R.string.resize_edge_left
                    Edge.TOP -> R.string.resize_edge_top
                    Edge.RIGHT -> R.string.resize_edge_right
                    Edge.BOTTOM -> R.string.resize_edge_bottom
                },
            )
            val out = if (edge == Edge.LEFT || edge == Edge.TOP) -0.5f else 0.5f // half a cell outwards
            Box(
                Modifier
                    .offset { box.round() }
                    .size(handle)
                    .systemGestureExclusion() // at the screen's side, the drag would otherwise be Back
                    .semantics {
                        // TalkBack cannot drag: it moves the side half a cell per action.
                        contentDescription = label
                        customActions = listOf(
                            CustomAccessibilityAction(grow) { commit(l.layout.stretched(p.page, p.item, edge, out, limits)) },
                            CustomAccessibilityAction(shrink) { commit(l.layout.stretched(p.page, p.item, edge, -out, limits)) },
                        )
                    }
                    .pointerInput(p, edge, unit) {
                        var moved = 0f // px along the side's axis since the drag began
                        detectDragGestures(
                            onDragStart = { moved = 0f },
                            onDragEnd = { commit(cell) },
                            onDragCancel = { cell = p.item.cell },
                        ) { change, amount ->
                            change.consume()
                            moved += if (across) amount.x else amount.y
                            val next = l.layout.stretched(p.page, p.item, edge, moved / if (across) unit.width else unit.height, limits)
                            if (next != cell) {
                                cell = next
                                haptic(HapticFeedbackType.SegmentFrequentTick)
                            }
                        }
                    }
                    .drawBehind {
                        drawCircle(Color.Black.copy(alpha = 0.4f), dotPx, dot) // seen on a light wallpaper too
                        drawCircle(Color.White, 7.dp.toPx(), dot)
                    },
            )
        }
    }
}

/** Nova's "line" page indicator: shows the current page while scrolling, then fades out. */
@Composable
private fun PageLine(pager: PagerState) {
    val scrolling = pager.isScrollInProgress
    val alpha by animateFloatAsState(if (scrolling) 1f else 0f, tween(if (scrolling) 100 else 700), label = "pageLine")
    Box(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = 24.dp)
            .height(2.dp)
            .graphicsLayer { this.alpha = alpha }
            .drawBehind {
                val w = size.width / pager.pageCount
                drawRect(Color.White.copy(alpha = 0.25f))
                drawRect(Color.White, Offset((pager.currentPage + pager.currentPageOffsetFraction) * w, 0f), Size(w, size.height))
            },
    )
}

// --- Dock: pages that wrap around; each icon opens one app on tap and others on a swipe up or down ---

@Composable
private fun Dock(l: Launcher, mover: Mover) {
    val pages = l.layout.dock.size
    val loops = if (l.layout.dockWraps) DOCK_LOOPS else 1
    val main = l.layout.defaultDockPage
    // A new pager on each reset: the dock is on its main page again before it shows.
    val pager = key(pages, loops, main, l.dockResets) {
        rememberPagerState(initialPage = if (loops == 1) main else pages * loops / 2 + main) { pages * loops }
    }
    SideEffect { mover.dockPage = { pager.currentPage % pages } }
    BoxWithConstraints(Modifier.fillMaxWidth().height(DOCK_HEIGHT).onGloballyPositioned { mover.dock = it.boundsInRoot() }) {
        val size = l.layout.dockSize
        val iconSize = iconSize(ICON, maxWidth / size, maxHeight)
        HorizontalPager(pager, Modifier.fillMaxSize(), beyondViewportPageCount = 1) { virtualPage ->
            Row(Modifier.fillMaxSize()) {
                l.layout.dock[virtualPage % pages].take(size).forEachIndexed { slot, icon ->
                    DockSlot(l, virtualPage, slot, icon, iconSize, Modifier.weight(1f))
                }
            }
        }
    }
}

@Composable
private fun DockSlot(l: Launcher, virtualPage: Int, slot: Int, icon: DockIcon, iconSize: Dp, modifier: Modifier) {
    val page = virtualPage % l.layout.dock.size
    val haptic = rememberHaptic(l)
    val tap = icon.tap?.takeUnless(l::hides) // a paused app Ajustes hides: an empty slot until the pause ends
    Box(
        modifier
            .fillMaxHeight()
            .pointerInput(icon) {
                var total = 0f
                detectVerticalDragGestures(
                    onDragStart = { total = 0f },
                    onDragEnd = {
                        val swipe = swipeOf(0f, total, SWIPE_MIN.toPx()) ?: return@detectVerticalDragGestures
                        haptic(HapticFeedbackType.GestureThresholdActivate)
                        // Without a target of its own, a swipe does what it does elsewhere on the home screen.
                        when (swipe) {
                            Swipe.UP -> if (icon.up != null) l.open(icon.up) else l.drawerOpen = true
                            Swipe.DOWN -> if (icon.down != null) l.open(icon.down) else l.expandNotifications()
                            else -> Unit
                        }
                    },
                ) { change, dy ->
                    change.consume()
                    total += dy
                }
            }
            .combinedClickable(
                interactionSource = null,
                indication = PressScale,
                hapticFeedbackEnabled = false, // its own would bypass the setting
                onClick = { l.open(tap) },
                onLongClick = {
                    haptic(HapticFeedbackType.LongPress)
                    // With no app, the menu would hold Configurar alone: its dialog opens straight away.
                    l.popup = if (l.appOf(icon.tap) == null) Popup.DockSetup(page, slot) else Popup.DockMenu(virtualPage, slot)
                },
            ),
        contentAlignment = Alignment.Center,
    ) {
        tap?.let { TargetIcon(l, it, iconSize) }
        val app = l.appOf(icon.tap)
        val open = l.popup == Popup.DockMenu(virtualPage, slot)
        val shortcuts = rememberShortcuts(l, app.takeIf { icon.tap is Target.App }, open)
        DropdownMenu(open && shortcuts != null, { l.popup = null }) {
            if (app != null) AppShortcuts(l, app, shortcuts.orEmpty())
            MenuItem(l, stringResource(R.string.dock_configure)) { l.popup = Popup.DockSetup(page, slot) }
            // No Desinstalar here: Info de la app has it.
            app?.let { MenuItem(l, stringResource(R.string.menu_app_info)) { l.showInfo(it) } }
        }
    }
}

// --- Search bar: search, assistant, Lens and the date. Nova's weather needs the network, so it is left out. ---

@Composable
private fun SearchPill(l: Launcher) {
    Row(
        Modifier
            .padding(start = 16.dp, end = 16.dp, top = 19.dp, bottom = 23.dp)
            .fillMaxWidth()
            .height(44.dp)
            .clip(CircleShape)
            .background(Color.Black)
            .clickable { l.openSearch() }
            .padding(start = 14.dp, end = 11.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        SearchGlyph(HINT, Modifier.size(20.dp))
        Spacer(Modifier.weight(1f))
        AssistantGlyph(Modifier.clip(CircleShape).clickable { l.startIntent(Intent(Intent.ACTION_VOICE_COMMAND), l.getString(R.string.open_target_assistant)) }.padding(4.dp))
        Spacer(Modifier.width(1.dp))
        LensGlyph(
            Modifier.clip(CircleShape).clickable {
                l.open(Target.App("com.google.ar.lens/com.google.vr.apps.ornament.app.lens.LensLauncherActivity#${l.userSerial}"))
            }.padding(4.dp),
        )
        Text(
            l.today,
            Modifier
                .clip(CircleShape)
                .clickable {
                    l.startIntent(Intent.makeMainSelectorActivity(Intent.ACTION_MAIN, Intent.CATEGORY_APP_CALENDAR), l.getString(R.string.open_target_calendar))
                }
                .padding(4.dp),
            color = Color.White,
            fontSize = 13.sp,
        )
    }
}

@Composable
private fun SearchGlyph(color: Color, modifier: Modifier) = Canvas(modifier) {
    val s = size.minDimension
    val stroke = s * 0.11f
    drawCircle(color, s * 0.3f, Offset(s * 0.4f, s * 0.4f), style = Stroke(stroke))
    drawLine(color, Offset(s * 0.63f, s * 0.63f), Offset(s * 0.92f, s * 0.92f), stroke * 1.2f, StrokeCap.Square)
}

/** The four Assistant dots, in dp measured on Nova's bar. */
@Composable
private fun AssistantGlyph(modifier: Modifier) = Canvas(modifier.size(22.dp, 20.dp)) {
    val u = 1.dp.toPx()
    drawCircle(BLUE, 6.1f * u, Offset(6.1f * u, 6.1f * u))
    drawCircle(RED, 3f * u, Offset(16.1f * u, 8.9f * u))
    drawCircle(YELLOW, 3.4f * u, Offset(16.1f * u, 16.2f * u))
    drawCircle(GREEN, 1.4f * u, Offset(20.6f * u, 5.2f * u))
}

/** Google Lens: three rounded corners, a centre dot and a small dot, on a 24 dp square. */
@Composable
private fun LensGlyph(modifier: Modifier) = Canvas(modifier.size(24.dp)) {
    val u = 1.dp.toPx()
    val e = 1.4f * u // half the stroke, so corners sit inside the square
    val far = 24 * u - e
    fun corner(color: Color, path: Path.() -> Unit) = drawPath(Path().apply(path), color, style = Stroke(2.8f * u))
    corner(RED) { moveTo(e, 9.4f * u); lineTo(e, 5 * u); quadraticTo(e, e, 5 * u, e); lineTo(9.4f * u, e) }
    corner(YELLOW) { moveTo(14.6f * u, e); lineTo(19 * u, e); quadraticTo(far, e, far, 5 * u); lineTo(far, 9.4f * u) }
    corner(BLUE) { moveTo(e, 14.6f * u); lineTo(e, 19 * u); quadraticTo(e, far, 5 * u, far); lineTo(9.4f * u, far) }
    drawCircle(BLUE, 4.6f * u, Offset(12 * u, 12 * u))
    drawCircle(GREEN, 2.6f * u, Offset(19.8f * u, 19.8f * u))
}

// --- Drawer: search, bubble tabs, app grid ---

/** Nova's "Buscar con" row. The query goes to the chosen app; this launcher sends nothing itself. */
private class Provider(val name: String, val pkg: String, val intent: (String) -> Intent)

private val PROVIDERS = listOf(
    Provider("Google", "com.google.android.googlequicksearchbox") { q ->
        Intent(Intent.ACTION_WEB_SEARCH).putExtra(SearchManager.QUERY, q).setPackage("com.google.android.googlequicksearchbox")
    },
    Provider("DuckDuckGo", "com.duckduckgo.mobile.android") { q ->
        Intent(Intent.ACTION_VIEW, "https://duckduckgo.com/?q=${android.net.Uri.encode(q)}".toUri()).setPackage("com.duckduckgo.mobile.android")
    },
    Provider("Play Store", "com.android.vending") { q ->
        Intent(Intent.ACTION_VIEW, "market://search?q=${android.net.Uri.encode(q)}".toUri()).setPackage("com.android.vending")
    },
    Provider("Spotify", "com.spotify.music") { q -> Intent(Intent.ACTION_VIEW, "spotify:search:${android.net.Uri.encode(q)}".toUri()).setPackage("com.spotify.music") },
    Provider("Firefox Focus", "org.mozilla.focus") { q ->
        Intent(Intent.ACTION_WEB_SEARCH).putExtra(SearchManager.QUERY, q).setPackage("org.mozilla.focus")
    },
)

@Composable
private fun Drawer(l: Launcher, mover: Mover) {
    var query by remember { mutableStateOf("") }
    val focus = remember { FocusRequester() }
    val scope = rememberCoroutineScope()
    val tabs = l.layout.tabs
    val pager = rememberPagerState { tabs.size } // one page per tab, swiped sideways as in Nova
    val searching = query.isNotBlank()
    val results = if (searching) search(l.apps, query, l::usage) { it.label } else emptyList() // the most used first among equals
    val tabbed = l.tabbed
    fun appsOf(tab: Int) = tabs.getOrNull(tab)?.let { tabApps(it, tabs, tabbed, l.find, l.layout.hidden) } ?: tabbed
    val haptic = rememberHaptic(l)
    // A pull keeps scrolling past the point that closed the drawer: only that point ticks.
    fun close() {
        if (!l.drawerOpen) return
        haptic(HapticFeedbackType.GestureThresholdActivate)
        l.drawerOpen = false
    }

    // Pulling down past the top of the grid closes the drawer, as does flicking down from there.
    val closePx = with(LocalDensity.current) { SWIPE_MIN.toPx() }
    val pullToClose = remember {
        object : NestedScrollConnection {
            private var pulled = 0f
            override fun onPostScroll(consumed: Offset, available: Offset, source: NestedScrollSource): Offset {
                pulled = if (source == NestedScrollSource.UserInput && available.y > 0) pulled + available.y else 0f
                if (pulled > closePx) close()
                return Offset.Zero
            }

            // Runs when every drag ends, so the next gesture starts from zero.
            override suspend fun onPreFling(available: Velocity): Velocity {
                if (pulled > 0 && available.y > 0) close()
                pulled = 0f
                return Velocity.Zero
            }
        }
    }

    Column(
        Modifier
            .fillMaxSize()
            .background(Color.Black)
            .onVerticalSwipe(down = ::close)
            .systemBarsPadding(),
    ) {
        GatedHaptics(l) { SearchField(query, { query = it }, focus) { results.firstOrNull()?.let(l::start) } }
        BoxWithConstraints(Modifier.weight(1f)) {
            // Rows split the height under the tabs without the keyboard, so they keep their size while searching.
            val padding = GRID_PADDING.calculateTopPadding() + GRID_PADDING.calculateBottomPadding()
            val cell = DpSize((maxWidth - 16.dp) / l.layout.drawerColumns, (maxHeight - TAB_GAP - TAB_HEIGHT - padding) / l.layout.drawerRows)
            Column(Modifier.fillMaxSize().imePadding()) {
                if (searching) {
                    val grid = rememberLazyGridState()
                    Box(Modifier.weight(1f)) {
                        val decimal = l.decimal
                        // A bare "booking.com" or "mr.robot" is more likely an app's name: a link only where no app matches.
                        val answer = remember(query, decimal) { answer(query, decimal) }?.takeUnless { it is Answer.Link && results.isNotEmpty() }
                        AppGrid(
                            l,
                            mover,
                            results,
                            grid,
                            cell,
                            pullToClose,
                            header = { answer?.let { item(span = { GridItemSpan(maxLineSpan) }) { SearchAnswer(l, it, decimal) } } },
                        ) {
                            item(span = { GridItemSpan(maxLineSpan) }) { SearchProviders(l, query) }
                        }
                        FastScroller(grid, l.layout.drawerColumns, Modifier.align(Alignment.CenterEnd))
                    }
                } else {
                    Tabs(l, pager.currentPage) { scope.launch { pager.animateScrollToPage(it) } }
                    val grids = remember(tabs.size) { List(tabs.size) { LazyGridState() } }
                    Box(Modifier.weight(1f)) {
                        HorizontalPager(pager, Modifier.fillMaxSize()) { page -> AppGrid(l, mover, appsOf(page), grids[page], cell, pullToClose) }
                        // One scroller over the pages, so it stays put while swiping between tabs.
                        grids.getOrNull(pager.currentPage)?.let { FastScroller(it, l.layout.drawerColumns, Modifier.align(Alignment.CenterEnd)) }
                    }
                }
            }
        }
    }

    LaunchedEffect(l.focusSearch) { // also when search opens while the drawer is still fading out
        if (l.focusSearch) {
            l.focusSearch = false
            focus.requestFocus()
        }
    }
}

@Composable
private fun AppGrid(
    l: Launcher,
    mover: Mover,
    apps: List<App>,
    grid: LazyGridState,
    cell: DpSize,
    pullToClose: NestedScrollConnection,
    header: LazyGridScope.() -> Unit = {},
    footer: LazyGridScope.() -> Unit = {},
) {
    val height = cell.height.coerceAtLeast(48.dp)
    val icon = iconSize(DRAWER_ICON, cell.width, height - LABEL_SPACE)
    LazyVerticalGrid(
        GridCells.Fixed(l.layout.drawerColumns),
        Modifier.fillMaxSize().nestedScroll(pullToClose),
        grid,
        contentPadding = GRID_PADDING,
    ) {
        header()
        items(apps, key = { it.id }) { app -> DrawerApp(l, mover, app, height, icon) }
        footer()
    }
}

/** Nova's icon size, shrunk to fit a smaller cell ([width] wide, [height] for the icon itself). */
private fun iconSize(nova: Dp, width: Dp, height: Dp) = minOf(nova, width * ICON_SHARE, height).coerceAtLeast(16.dp)

private const val WHATSAPP = "com.whatsapp"

/**
 * What search answers besides apps ([answer]), above them: a sum or conversion a tap copies, or what can be done with
 * a phone number, link or e-mail address, as bubbles in the "Buscar con" row's shape. Each opens through
 * [Launcher.startIntent], so a poison app pauses; nothing typed is kept.
 */
@Composable
private fun SearchAnswer(l: Launcher, answer: Answer, decimal: Char) {
    class Action(val label: String, val what: String, val intent: Intent)
    val (title, actions) = when (answer) {
        is Answer.Value -> return ValueAnswer(answer, decimal)
        is Answer.Phone -> {
            val number = answer.number
            // wa.me wants the international form; without one (no country to read a national number by), no WhatsApp.
            val whatsApp = remember(number, l.country) {
                (PhoneNumberUtils.formatNumberToE164(number, l.country) ?: number.takeIf { it.startsWith('+') })?.let(::whatsAppNumber)
            }
            stringResource(R.string.answer_phone) to listOfNotNull(
                Action(stringResource(R.string.answer_call), stringResource(R.string.open_target_phone), Intent(Intent.ACTION_DIAL, Uri.fromParts("tel", number, null))),
                whatsApp?.takeIf { l.appOf(Target.Shortcut(WHATSAPP, "", "")) != null }?.let {
                    Action("WhatsApp", "WhatsApp", Intent(Intent.ACTION_VIEW, "https://wa.me/$it".toUri()).setPackage(WHATSAPP))
                },
                Action(stringResource(R.string.answer_sms), stringResource(R.string.open_target_messages), Intent(Intent.ACTION_SENDTO, Uri.fromParts("smsto", number, null))),
            )
        }
        is Answer.Link -> stringResource(R.string.answer_link) to listOf(
            Action(stringResource(R.string.answer_open), stringResource(R.string.open_target_link), Intent(Intent.ACTION_VIEW, answer.url.toUri())),
        )
        is Answer.Email -> stringResource(R.string.answer_email) to listOf(
            Action(stringResource(R.string.answer_write), stringResource(R.string.open_target_email), Intent(Intent.ACTION_SENDTO, Uri.fromParts("mailto", answer.address, null))),
        )
    }
    Column(Modifier.padding(start = 16.dp, top = 8.dp, bottom = 8.dp)) {
        Text(title, color = Color.White, fontSize = 16.sp)
        // Scrolls rather than clips a third bubble at a large font size.
        Row(Modifier.padding(top = 16.dp).horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            for (a in actions) Bubble(a.label, false, Modifier.clickable { l.startIntent(a.intent, a.what) })
        }
    }
}

/** A sum's or a conversion's result; a tap copies it. */
@Composable
private fun ValueAnswer(value: Answer.Value, decimal: Char) {
    val context = LocalContext.current
    val clipLabel = stringResource(R.string.search_calc_result)
    Row(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = 8.dp, vertical = 8.dp)
            .clip(RoundedCornerShape(16.dp))
            .background(TAB_OFF)
            .clickable { context.getSystemService(ClipboardManager::class.java).setPrimaryClip(ClipData.newPlainText(clipLabel, value.text)) }
            .padding(horizontal = 20.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text("= ${value.text}", color = Color.White, fontSize = 28.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
            if (value.usCup) Text(stringResource(R.string.search_us_cup, formatNumber(round(US_CUP_ML * 10) / 10, decimal)), color = TAB_OFF_TEXT, fontSize = 14.sp)
        }
        Text(stringResource(R.string.copy), color = TAB_OFF_TEXT, fontSize = 14.sp)
    }
}

@Composable
private fun SearchField(query: String, onChange: (String) -> Unit, focus: FocusRequester, onGo: () -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .padding(top = 11.dp)
            .height(56.dp)
            .padding(start = 34.dp, end = 24.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        SearchGlyph(Color.White, Modifier.size(22.dp))
        Spacer(Modifier.width(22.dp))
        BasicTextField(
            query,
            onChange,
            Modifier.weight(1f).focusRequester(focus),
            singleLine = true,
            textStyle = TextStyle(Color.White, 18.sp),
            cursorBrush = SolidColor(ACCENT),
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Go),
            keyboardActions = KeyboardActions(onGo = { onGo() }),
            decorationBox = { field ->
                Box {
                    if (query.isEmpty()) Text(stringResource(R.string.search_apps_hint), color = HINT, fontSize = 18.sp)
                    field()
                }
            },
        )
    }
}

@Composable
private fun Tabs(l: Launcher, selected: Int, onSelect: (Int) -> Unit) {
    val haptic = rememberHaptic(l)
    LazyRow(
        Modifier.padding(top = TAB_GAP),
        contentPadding = PaddingValues(horizontal = 24.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        itemsIndexed(l.layout.tabs) { i, tab ->
            Box {
                Bubble(
                    tab.name,
                    i == selected,
                    Modifier.combinedClickable(
                        hapticFeedbackEnabled = false, // its own would bypass the setting
                        onClick = { onSelect(i) },
                        onLongClick = {
                            haptic(HapticFeedbackType.LongPress)
                            l.popup = Popup.TabMenu(i)
                        },
                    ),
                )
                DropdownMenu(l.popup == Popup.TabMenu(i), { l.popup = null }) {
                    MenuItem(l, stringResource(R.string.rename)) {
                        l.popup = Popup.Rename(l.getString(R.string.drawer_rename_tab), tab.name) { name ->
                            l.update { it.copy(tabs = it.tabs.replaced(i) { t -> t.copy(name = name) }) }
                        }
                    }
                    MenuItem(l, stringResource(R.string.drawer_new_tab)) { l.popup = newTab(l) }
                    if (tab.apps != null) {
                        MenuItem(l, stringResource(R.string.delete)) {
                            onSelect(0)
                            l.update { it.copy(tabs = it.tabs.filterIndexed { j, _ -> j != i }) }
                        }
                    }
                }
            }
        }
    }
}

/** Nova's "bubble" tab, also used for the search providers. */
@Composable
private fun Bubble(text: String, selected: Boolean, modifier: Modifier) {
    Box(
        Modifier
            .widthIn(min = 88.dp)
            .height(TAB_HEIGHT)
            .clip(RoundedCornerShape(12.dp))
            .background(if (selected) ACCENT else TAB_OFF)
            .then(modifier)
            .padding(horizontal = 16.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(text, color = if (selected) Color.Black else TAB_OFF_TEXT, fontSize = 16.sp, fontWeight = FontWeight.Medium, maxLines = 1)
    }
}

@Composable
private fun DrawerApp(l: Launcher, mover: Mover, app: App, height: Dp, icon: Dp) {
    // Taking the app out of the tab shown removes it from under its menu; the menu must not reopen when it shows up again.
    DisposableEffect(app.id) { onDispose { if (l.popup == Popup.AppMenu(app.id)) l.popup = null } }
    val item = remember(app.id, app.label) { Item.Icon(Target.App(app.id), app.label, Cell(0f, 0f)) } // what a drag drops
    Movable(l, mover, null, item, { l.popup = Popup.AppMenu(app.id) }, Modifier) {
        Column(
            Modifier
                .fillMaxWidth()
                .height(height)
                .clickable(interactionSource = null, indication = PressScale) { l.start(app) },
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            AppImage(app, icon)
            Text(app.label, Modifier.padding(top = 6.dp, start = 2.dp, end = 2.dp), style = LABEL, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        val open = l.popup == Popup.AppMenu(app.id)
        val shortcuts = rememberShortcuts(l, app, open)
        DropdownMenu(open && shortcuts != null, { l.popup = null }) {
            AppShortcuts(l, app, shortcuts.orEmpty())
            MenuItem(l, stringResource(R.string.add_to_home_screen)) { l.addToDesktop(Target.App(app.id), app.label) }
            // Tab toggles leave the menu open so several can be set at once.
            l.layout.tabs.forEachIndexed { i, tab ->
                val members = tab.apps ?: return@forEachIndexed
                val inTab = members.any { l.find(it) == app }
                DropdownMenuItem({ Text(ticked(inTab, tab.name)) }, onClick = {
                    l.update { it.copy(tabs = it.tabs.replaced(i) { t -> t.copy(apps = members.toggled(app, app.id, l.find)) }) }
                })
            }
            val hidden = l.layout.hidden.any { l.find(it) == app }
            MenuItem(l, stringResource(if (hidden) R.string.drawer_show_app else R.string.drawer_hide_app)) {
                l.update { it.copy(hidden = it.hidden.toggled(app, app.id, l.find)) }
            }
            val poison = l.isPoison(app)
            MenuItem(l, stringResource(if (poison) R.string.poison_unmark else R.string.poison_mark)) {
                l.update { it.copy(poisons = it.poisons.toggled(app, app.id, l.find)) }
            }
            MenuItem(l, stringResource(R.string.menu_app_info)) { l.showInfo(app) }
            if (app.removable) MenuItem(l, stringResource(R.string.uninstall)) { l.uninstall(app) }
        }
    }
}

@Composable
private fun FastScroller(grid: LazyGridState, columns: Int, modifier: Modifier) {
    // In pixels: counting visible items made the thumb jump, as that count changes a whole row at a time.
    val info = grid.layoutInfo
    val row = info.visibleItemsInfo.firstOrNull()?.size?.height ?: return
    val rows = (info.totalItemsCount + columns - 1) / columns
    val range = rows * row + info.beforeContentPadding + info.afterContentPadding - info.viewportSize.height
    if (range <= 0) return
    val scope = rememberCoroutineScope()
    val thumb = 48.dp // fixed length, as in Nova
    BoxWithConstraints(
        modifier
            .fillMaxHeight()
            .width(24.dp)
            .padding(vertical = 16.dp)
            .pointerInput(row, range) {
                val thumbPx = thumb.toPx()
                detectVerticalDragGestures { change, _ ->
                    // The thumb's center follows the finger.
                    val target = ((change.position.y - thumbPx / 2) / (size.height - thumbPx) * range).toInt().coerceIn(0, range)
                    scope.launch { grid.scrollToItem(target / row * columns, target % row) }
                }
            },
    ) {
        val scrolled = grid.firstVisibleItemIndex / columns * row + grid.firstVisibleItemScrollOffset
        val progress = (scrolled.toFloat() / range).coerceIn(0f, 1f)
        Box(Modifier.align(Alignment.TopEnd).padding(end = 6.dp).width(4.dp).fillMaxHeight().background(TRACK, CircleShape))
        Box(
            Modifier
                .align(Alignment.TopEnd)
                .offset(y = (maxHeight - thumb) * progress)
                .padding(end = 6.dp)
                .width(4.dp)
                .height(thumb)
                .background(ACCENT, CircleShape),
        )
    }
}

@Composable
private fun SearchProviders(l: Launcher, query: String) {
    Column(Modifier.padding(start = 16.dp, top = 8.dp)) {
        Text(stringResource(R.string.search_with), color = Color.White, fontSize = 16.sp)
        Row(Modifier.padding(top = 16.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            for (p in PROVIDERS) {
                val app = l.appOf(Target.Shortcut(p.pkg, "", ""))
                val open = Modifier.clip(CircleShape).clickable { l.startIntent(p.intent(query), p.name) }
                if (app != null) {
                    Image(app.icon, p.name, open.padding(6.dp).size(36.dp))
                } else { // app not installed: its initial, so the row keeps Nova's shape
                    Box(open.padding(6.dp).size(36.dp).background(TAB_OFF, CircleShape), contentAlignment = Alignment.Center) {
                        Text(p.name.take(1), color = TAB_OFF_TEXT, fontSize = 16.sp, fontWeight = FontWeight.Medium)
                    }
                }
            }
        }
    }
}

// --- Shared pieces ---

@Composable
private fun TargetIcon(l: Launcher, target: Target, size: Dp, modifier: Modifier = Modifier) {
    val app = l.appOf(target)
    when (target) {
        is Target.App -> AppImage(app, size, modifier)
        is Target.Shortcut -> Box(modifier.size(size)) {
            val icon = remember(target) { l.shortcutIcon(target) }
            if (icon != null) Image(icon, target.label, Modifier.fillMaxSize()) else AppImage(app, size)
            // The publishing app's badge, as Nova draws it, unless the icon has one already.
            val badged = remember(target, app) { l.isBrowser(target.pkg) } // app: a new one after each package change
            if (app != null && !badged) Image(app.icon, null, Modifier.size(size * 0.4f).align(Alignment.BottomEnd).offset(4.dp, 2.dp))
        }
    }
}

@Composable
private fun AppImage(app: App?, size: Dp, modifier: Modifier = Modifier) {
    if (app == null) {
        Box(modifier.size(size).clip(CircleShape).background(Color.White.copy(alpha = 0.15f)))
    } else {
        // Apps paused by Digital Wellbeing are greyed out, as in Nova.
        Image(app.icon, app.label, modifier.size(size), alpha = if (app.suspended) 0.6f else 1f, colorFilter = if (app.suspended) GREY else null)
    }
}

/**
 * The pressed feel of an icon: it shrinks a little while the finger is on it and springs back. An indication, so the
 * icon's own clickable tracks the touch: a long press, a drag, paging or scrolling takes it over, and that cancels.
 */
private data object PressScale : IndicationNodeFactory {
    override fun create(interactionSource: InteractionSource): DelegatableNode = PressScaleNode(interactionSource)
}

private class PressScaleNode(private val source: InteractionSource) : Modifier.Node(), DrawModifierNode {
    private val factor = Animatable(1f)

    // One after the other, not the latest only: a quick tap (a press and its release at once) still dips.
    override fun onAttach() {
        coroutineScope.launch {
            source.interactions.collect {
                when (it) {
                    is PressInteraction.Press -> factor.animateTo(PRESSED_SCALE, spring())
                    is PressInteraction.Release, is PressInteraction.Cancel -> factor.animateTo(1f, spring())
                }
            }
        }
    }

    // Drawn smaller, not laid out smaller: the touch area stays put.
    override fun ContentDrawScope.draw() = scale(factor.value) { this@draw.drawContent() }
}

/**
 * The shortcuts [app]'s menu lists, read off the main thread each time the menu is [open]. Null until the first read is
 * in, so the menu waits for them (tens of ms) instead of opening and then pushing its rows down under the finger; a
 * reopen shows the last ones until the new read. None for no [app] (a widget, a pinned shortcut's icon).
 */
@Composable
private fun rememberShortcuts(l: Launcher, app: App?, open: Boolean): List<AppShortcut>? {
    val iconPx = with(LocalDensity.current) { SHORTCUT_ICON.roundToPx() }
    var shortcuts by remember(app?.id) { mutableStateOf(if (app == null) emptyList<AppShortcut>() else null) }
    LaunchedEffect(app, open) {
        if (open && app != null) shortcuts = withContext(Dispatchers.IO) { l.shortcutsOf(app, iconPx) }
    }
    return shortcuts
}

/**
 * [app]'s own [shortcuts] ("New message", recent chats) at the top of its menu, as in Nova: a tap opens one, as a
 * desktop shortcut opens, and + pins it to the desktop.
 */
@Composable
private fun AppShortcuts(l: Launcher, app: App, shortcuts: List<AppShortcut>) {
    if (shortcuts.isEmpty()) return
    for (s in shortcuts) key(s.target.id) {
        // The long label where it fits, as Launcher3 shows it (Android means it for menus); a pinned icon gets the short one.
        var short by remember { mutableStateOf(s.info.longLabel.isNullOrBlank()) }
        val label = if (short) s.target.label else s.info.longLabel.toString()
        DropdownMenuItem(
            text = { Text(label, maxLines = 1, overflow = TextOverflow.Ellipsis, onTextLayout = { if (it.hasVisualOverflow) short = true }) },
            onClick = {
                l.popup = null
                l.open(s.target)
            },
            leadingIcon = { Image(s.icon ?: app.icon, null, Modifier.size(SHORTCUT_ICON)) },
            trailingIcon = {
                IconAction(MORE, stringResource(R.string.shortcut_add_to_home, label), tint = ACCENT) {
                    l.popup = null
                    l.pin(s.info)
                }
            },
        )
    }
    HorizontalDivider()
}

/** A menu entry's [label] marked on or off: a tab the app is in, the option chosen. */
private fun ticked(on: Boolean, label: String) = "${if (on) "✓" else "○"} $label"

/** [target]'s name in a summary: an app's or shortcut's own as it is ("eBay"), else "Nada" or "No instalada". */
private fun Launcher.nameOf(target: Target?) =
    labelOf(target).let { if (target is Target.Shortcut || appOf(target) != null) it else it.replaceFirstChar { c -> c.uppercase() } }

/** A menu entry that closes the menu, then acts (the action may open another popup). */
@Composable
private fun MenuItem(l: Launcher, text: String, onClick: () -> Unit) = DropdownMenuItem(text = { Text(text) }, onClick = {
    l.popup = null
    onClick()
})

@Composable
private fun PickerDialog(l: Launcher, title: String, query: String, onQuery: (String) -> Unit, onDismiss: () -> Unit = { l.popup = null }, rows: LazyListScope.() -> Unit) {
    Dialog(onDismiss) {
        Surface(shape = RoundedCornerShape(28.dp), color = MaterialTheme.colorScheme.surfaceContainerHigh) {
            Column(Modifier.padding(16.dp).heightIn(max = 560.dp)) {
                Text(title, Modifier.padding(start = 4.dp, bottom = 8.dp), style = MaterialTheme.typography.titleMedium)
                GatedHaptics(l) { OutlinedTextField(query, onQuery, Modifier.fillMaxWidth(), placeholder = { Text(stringResource(R.string.search)) }, singleLine = true) }
                LazyColumn(content = rows)
            }
        }
    }
}

@Composable
private fun AppPicker(l: Launcher, p: Popup.PickApp) {
    var query by remember { mutableStateOf("") }
    PickerDialog(l, p.title, query, { query = it }, { l.popup = p.back }) {
        fun choose(app: App?) {
            l.popup = null
            p.onPick(app)
        }
        item { PickerRow(null, stringResource(R.string.picker_none)) { choose(null) } }
        items(search(l.apps, query) { it.label }, key = { it.id }) { app -> PickerRow(app.icon, app.label) { choose(app) } }
    }
}

/**
 * A widget in the picker, under its app's [appLabel] and [icon]. Its previews stay loaded while the picker is open,
 * so a row scrolled back into view shows them at once.
 */
private class WidgetChoice(val info: AppWidgetProviderInfo, val appLabel: String, val name: String, val icon: ImageBitmap) {
    val label = "$appLabel · $name"
    var previews by mutableStateOf<List<RemoteViews>?>(null)
    var failed by mutableIntStateOf(0) // of previews, those that would not inflate
    var image by mutableStateOf<ImageBitmap?>(null)
    var imageLoaded by mutableStateOf(false) // the icon waits, so it does not flash before the image
}

/** Widgets grouped by app as in Nova, each with its preview and the cells it takes. Search finds app and widget names. */
@Composable
private fun WidgetPicker(l: Launcher, page: Int) {
    val pm = LocalContext.current.packageManager
    val iconPx = with(LocalDensity.current) { ICON.roundToPx() }
    val widgets = remember {
        val icons = HashMap<String, ImageBitmap>()
        l.widgetProviders()
            .map {
                val pkg = it.provider.packageName
                // An app with widgets but no launcher entry still has an icon.
                val icon = icons.getOrPut(pkg) {
                    l.appOf(Target.Shortcut(pkg, "", ""))?.icon ?: pm.getApplicationIcon(it.activityInfo.applicationInfo).toBitmap(iconPx, iconPx).asImageBitmap()
                }
                WidgetChoice(it, pm.getApplicationLabel(it.activityInfo.applicationInfo).toString(), it.loadLabel(pm), icon)
            }
            // Not on label: "·" sorts after letters, so "Google" would follow "Google Fotos".
            .sortedWith(compareBy({ fold(it.appLabel) }, { fold(it.name) }))
    }
    var query by remember { mutableStateOf("") }
    // Full screen like Nova's, so several previews fit; the Surface takes every touch, and back closes it as any popup.
    Surface(Modifier.fillMaxSize(), color = Color.Black, contentColor = MaterialTheme.colorScheme.onSurface) {
        Column(Modifier.navigationBarsPadding().imePadding()) {
            Bar(stringResource(R.string.widget_add)) { l.popup = null }
            GatedHaptics(l) {
                OutlinedTextField(query, { query = it }, Modifier.fillMaxWidth().padding(16.dp), placeholder = { Text(stringResource(R.string.search)) }, singleLine = true)
            }
            LazyColumn(Modifier.weight(1f).padding(horizontal = 16.dp)) {
                // Apps in the order of their best match.
                for ((pkg, found) in search(widgets, query) { it.label }.groupBy { it.info.provider.packageName }) {
                    item(key = pkg) { PickerRow(found[0].icon, found[0].appLabel, onClick = null) }
                    items(found, key = { it.info.provider.flattenToString() }) { WidgetRow(l, page, it) }
                }
            }
        }
    }
}

@Composable
private fun WidgetRow(l: Launcher, page: Int, widget: WidgetChoice) {
    // The function addWidget sizes it with, so the preview and "2 × 2" match what lands on the desktop.
    val (w, h) = remember(widget) { l.widgetCells(widget.info) }
    val number = remember { NumberFormat.getNumberInstance() } // half cells read "1.5" or "1,5"
    Column(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .clickable {
                l.popup = null
                l.addWidget(page, widget.info)
            }
            .padding(8.dp),
    ) {
        WidgetPreview(l, widget, DpSize(l.cellSize.width * w, l.cellSize.height * h))
        Row(Modifier.padding(top = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(widget.name, Modifier.weight(1f), maxLines = 2, overflow = TextOverflow.Ellipsis)
            val cells = stringResource(R.string.widget_cells, number.format(w), number.format(h))
            Text(
                "${number.format(w)} × ${number.format(h)}",
                Modifier.padding(start = 12.dp).clearAndSetSemantics { contentDescription = cells },
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                style = MaterialTheme.typography.bodyMedium,
            )
        }
    }
}

/**
 * A widget's preview, laid out at its size on the desktop ([real]) and scaled down to fit, as Launcher3 shows it:
 * one of [Launcher.widgetPreviews] (the next one when it fails to inflate), else its preview image, else its app's icon.
 */
@Composable
private fun WidgetPreview(l: Launcher, widget: WidgetChoice, real: DpSize) = BoxWithConstraints(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
    val scale = minOf(1f, maxWidth / real.width, PREVIEW_HEIGHT / real.height)
    val size = real * scale
    val placeholder = Modifier.size(size).clip(RoundedCornerShape(16.dp)).background(Color.White.copy(alpha = 0.08f))
    val views = widget.previews
    LaunchedEffect(widget) {
        if (widget.previews == null) widget.previews = withContext(PREVIEW_LOADER) { l.widgetPreviews(widget.info) }
    }
    val view = views?.getOrNull(widget.failed)
    when {
        views == null -> Box(placeholder)
        view != null -> key(widget.failed) {
            Box(Modifier.size(size)) {
                AndroidView(
                    {
                        PreviewHost(it).apply {
                            onError = { widget.failed++ }
                            setAppWidget(-1, widget.info) // no widget id: nothing is allocated or bound
                            setExecutor(PREVIEW_LOADER.asExecutor()) // inflates off the main thread
                            updateAppWidget(view)
                        }
                    },
                    // Centred on the box, then scaled into it.
                    Modifier.requiredSize(real).graphicsLayer {
                        scaleX = scale
                        scaleY = scale
                    },
                    onRelease = { it.setExecutor(null) }, // a row scrolled away drops its pending inflation
                )
            }
        }
        else -> {
            val px = with(LocalDensity.current) { size.toSize() }
            LaunchedEffect(widget) {
                if (widget.imageLoaded) return@LaunchedEffect
                widget.image = withContext(PREVIEW_LOADER) { l.widgetPreviewImage(widget.info, px.width.roundToInt(), px.height.roundToInt()) }
                widget.imageLoaded = true
            }
            widget.image?.let { Image(it, null, Modifier.size(size)) } ?: Box(placeholder, contentAlignment = Alignment.Center) {
                if (widget.imageLoaded) Image(widget.icon, null, Modifier.size(ICON))
            }
        }
    }
}

/**
 * Hosts a widget's preview as Launcher3 does, under no widget id. Touches and focus never reach it, so they go to its
 * row and its buttons never fire; it is hidden from accessibility too.
 */
private class PreviewHost(context: Context) : AppWidgetHostView(context) {
    var onError: () -> Unit = {}
    private var failed = false

    init {
        importantForAccessibility = IMPORTANT_FOR_ACCESSIBILITY_NO_HIDE_DESCENDANTS
        descendantFocusability = ViewGroup.FOCUS_BLOCK_DESCENDANTS
    }

    override fun dispatchTouchEvent(event: MotionEvent) = false

    // Shown when the layout fails to inflate, lay out or draw: the picker moves on to the next preview instead.
    override fun getErrorView(): View = super.getErrorView().also {
        if (!failed) onError()
        failed = true
    }
}

/** A picker entry: tapped with [onClick], or a header (TalkBack can jump between them) when that is null. */
@Composable
private fun PickerRow(icon: ImageBitmap?, label: String, checked: Boolean? = null, onClick: (() -> Unit)?) {
    Row(
        Modifier
            .fillMaxWidth()
            .then(if (onClick == null) Modifier.semantics(mergeDescendants = true) { heading() } else Modifier.clickable(onClick = onClick))
            .padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.size(40.dp)) { icon?.let { Image(it, null, Modifier.fillMaxSize()) } }
        Spacer(Modifier.width(16.dp))
        Text(label, Modifier.weight(1f))
        checked?.let { Checkbox(it, null) }
    }
}

@Composable
private fun AppChecklist(l: Launcher, p: Popup.Checklist) {
    var query by remember { mutableStateOf("") }
    val ids = p.ids(l.layout)
    // Ticked first, in the order the list opened with, so a tap does not move the row away.
    val ticked = remember { ids.mapNotNull(l.find).toSet() }
    PickerDialog(l, p.title, query, { query = it }) {
        items(search(l.apps.sortedByDescending { it in ticked }, query) { it.label }, key = { it.id }) { app ->
            PickerRow(app.icon, app.label, checked = ids.any { l.find(it) == app }) {
                l.update { p.put(it, p.ids(it).toggled(app, app.id, l.find)) }
            }
        }
    }
}

@Composable
private fun ConfirmDialog(l: Launcher, p: Popup.Confirm) = AlertDialog(
    onDismissRequest = { l.popup = null },
    title = { DialogTitle(p.title) },
    text = { Text(p.text) },
    confirmButton = {
        TextButton({
            l.popup = null
            p.onYes()
        }) { Text(p.action) }
    },
    dismissButton = { TextButton({ l.popup = null }) { Text(stringResource(R.string.cancel)) } },
)

@Composable
private fun NameDialog(l: Launcher, p: Popup.Rename) {
    var name by remember { mutableStateOf(p.initial) }
    AlertDialog(
        onDismissRequest = { l.popup = null },
        title = { DialogTitle(p.title) },
        text = { GatedHaptics(l) { OutlinedTextField(name, { name = it }, Modifier.fillMaxWidth(), singleLine = true) } },
        confirmButton = {
            TextButton({
                l.popup = null
                p.onDone(name.trim())
            }, enabled = name.isNotBlank()) { Text(stringResource(R.string.done)) }
        },
        dismissButton = { TextButton({ l.popup = null }) { Text(stringResource(R.string.cancel)) } },
    )
}

/** "Venenos": a pause before a poison app opens, with how often it was opened today. */
@Composable
private fun PauseScreen(l: Launcher, p: Popup.Pause) {
    val opens = remember { l.opensToday(p.app) }
    val seconds = PAUSE_SECONDS + PAUSE_STEP_SECONDS * opens // longer with each open today
    var left by remember { mutableIntStateOf(seconds) }
    LaunchedEffect(Unit) {
        while (left > 0) {
            delay(1000)
            left--
        }
    }
    val progress by animateFloatAsState(1f - left / seconds.toFloat(), tween(1000, easing = LinearEasing), label = "pause")
    Dialog({ l.popup = null }, DialogProperties(usePlatformDefaultWidth = false, decorFitsSystemWindows = false)) {
        Column(
            Modifier.fillMaxSize().background(Color.Black).systemBarsPadding().padding(32.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            Box(contentAlignment = Alignment.Center) {
                CircularProgressIndicator({ progress }, Modifier.size(120.dp), color = ACCENT, strokeWidth = 4.dp, trackColor = TRACK)
                AppImage(p.app, 72.dp)
            }
            Text(p.app.label, Modifier.padding(top = 24.dp), color = Color.White, fontSize = 24.sp)
            Text(
                stringResource(if (left > 0) R.string.poison_breathe else R.string.poison_open_anyway_question),
                Modifier.padding(top = 8.dp),
                color = TAB_OFF_TEXT,
                fontSize = 18.sp,
            )
            val today = if (opens == 0) stringResource(R.string.poison_opens_none) else pluralStringResource(R.plurals.poison_opens_today, opens, opens)
            Text(today, Modifier.padding(top = 4.dp), color = HINT, fontSize = 14.sp)
            Text(
                pluralStringResource(R.plurals.poison_wait_step, PAUSE_STEP_SECONDS, PAUSE_STEP_SECONDS),
                color = HINT,
                fontSize = 14.sp,
                textAlign = TextAlign.Center,
            )
            Spacer(Modifier.height(48.dp))
            Button({ l.popup = null }, colors = ButtonDefaults.buttonColors(containerColor = ACCENT, contentColor = Color.Black)) {
                Text(stringResource(R.string.poison_go_back))
            }
            // The healthy app picked in Ajustes, if installed and not this one; through open, so a poison there pauses too.
            val instead = l.layout.instead?.takeIf { t -> l.appOf(t)?.let { l.packageOf(it) != l.packageOf(p.app) } == true }
            if (instead != null) {
                OutlinedButton(
                    {
                        l.popup = null
                        l.open(instead)
                    },
                    Modifier.padding(top = 12.dp),
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = Color.White),
                    contentPadding = ButtonDefaults.ButtonWithIconContentPadding,
                ) {
                    TargetIcon(l, instead, 24.dp, Modifier.clearAndSetSemantics {}) // the text names it
                    Text(stringResource(R.string.poison_open_instead, l.labelOf(instead)), Modifier.padding(start = 8.dp))
                }
            }
            TextButton(
                {
                    l.popup = null
                    l.countOpen(p.app)
                    p.open()
                },
                Modifier.padding(top = 8.dp),
                enabled = left == 0,
            ) { Text(if (left > 0) stringResource(R.string.poison_open_in, left) else stringResource(R.string.poison_open_anyway)) }
        }
    }
}

// --- Settings: Nova's sections, over the wallpaper ---

// Nova's settings, measured on its screenshots.
private val MUTED = Color(0xFF919191) // line icons, group titles, grid cells, − and +, switch track, dialog buttons
private val BAR = Color(0xFF0C0C0C) // a section's app bar
private val DIVIDER = Color(0xFF3F3F3F)

/** Google Sans at [weight], as in Nova's titles: a Pixel system family; other phones get the default sans. */
private fun googleSans(weight: Int) = FontFamily(
    Typeface(android.graphics.Typeface.create(android.graphics.Typeface.create("google-sans", android.graphics.Typeface.NORMAL), weight, false)),
)
private val TITLE_FONT = googleSans(500)
private val HEADER_FONT = googleSans(700)

/** The dynamic dark scheme in Nova's greys, so stock switches, checkboxes, fields, dialogs and text buttons match it. */
@Composable
private fun novaColors(): ColorScheme {
    val context = LocalContext.current
    // Remembered: a new scheme on every LauncherScreen recomposition would invalidate the whole tree.
    return remember(context) {
        dynamicDarkColorScheme(context).copy(
            primary = MUTED,
            onPrimary = Color(0xFF1A1C1C), // a checked switch's thumb, a checkbox's tick
            onSurface = Color(0xFFFCFCFC),
            onSurfaceVariant = Color(0xFFBDBDBD), // summaries, dialog text
            surfaceContainerHigh = TRACK, // dialogs; Nova's #0F0F0F all but vanishes over the black pages
            surfaceContainerHighest = Color.Black, // an unchecked switch's track
        )
    }
}

/** A line icon on a 24-unit square, like Nova's settings icons. Literal coordinates only: it runs scaled. */
private typealias Glyph = DrawScope.(Color) -> Unit

private val LINE = Stroke(1.8f, cap = StrokeCap.Round, join = StrokeJoin.Round)

/** A polyline through x0, y0, x1, y1, … */
private fun DrawScope.line(color: Color, vararg xy: Float) = drawPath(
    Path().apply {
        moveTo(xy[0], xy[1])
        for (i in 2..<xy.size step 2) lineTo(xy[i], xy[i + 1])
    },
    color,
    style = LINE,
)

private val BOOK: Glyph = { c -> // an open book: the guide
    line(c, 12f, 7f, 12f, 20f)
    line(c, 12f, 7f, 3f, 4.5f, 3f, 17.5f, 12f, 20f, 21f, 17.5f, 21f, 4.5f, 12f, 7f)
}
private val PHONE: Glyph = { c -> // a phone between two brackets: Nova's home screen
    drawRoundRect(c, Offset(7f, 3f), Size(10f, 18f), CornerRadius(2f), style = LINE)
    line(c, 3f, 8f, 3f, 16f)
    line(c, 21f, 8f, 21f, 16f)
}
private val APPS: Glyph = { c -> // 3 × 3 dots in a ring: Nova's drawer
    drawCircle(c, 9.5f, Offset(12f, 12f), style = LINE)
    for (i in 0..8) drawCircle(c, 1.3f, Offset(8f + i % 3 * 4, 8f + i / 3 * 4))
}
private val DOCK_BAR: Glyph = { c -> // a phone with its dock
    drawRoundRect(c, Offset(6f, 2.5f), Size(12f, 19f), CornerRadius(2f), style = LINE)
    line(c, 6f, 16f, 18f, 16f)
    for (i in 0..2) drawCircle(c, 1f, Offset(9f + i * 3, 18.8f))
}
private val TAP: Glyph = { c -> // a touch and its rings
    drawCircle(c, 2.5f, Offset(12f, 12f))
    drawCircle(c, 6f, Offset(12f, 12f), style = LINE)
    drawCircle(c.copy(alpha = c.alpha / 2), 10f, Offset(12f, 12f), style = LINE)
}
private val RESTORE: Glyph = { c -> // clock hands in an arc that turns back
    drawArc(c, 180f, 300f, false, Offset(4f, 4f), Size(16f, 16f), style = LINE)
    line(c, 1.5f, 9.5f, 4f, 12.5f, 6.5f, 9.5f)
    line(c, 12f, 8f, 12f, 12f, 15f, 14f)
}
private val HOUSE: Glyph = { c ->
    line(c, 3f, 11f, 12f, 3.5f, 21f, 11f)
    line(c, 5.5f, 9f, 5.5f, 20f, 18.5f, 20f, 18.5f, 9f)
}
private val TRASH: Glyph = { c ->
    line(c, 4f, 7f, 20f, 7f)
    line(c, 9.5f, 7f, 9.5f, 4f, 14.5f, 4f, 14.5f, 7f)
    line(c, 6f, 7f, 7f, 20f, 17f, 20f, 18f, 7f)
}
private val BACK: Glyph = { c ->
    line(c, 19.5f, 12f, 4.5f, 12f)
    line(c, 11f, 5.5f, 4.5f, 12f, 11f, 18.5f)
}
private val LOCK: Glyph = { c -> // a padlock: blocks, and one that runs
    drawRoundRect(c, Offset(5f, 10.5f), Size(14f, 10f), CornerRadius(2f), style = LINE)
    drawArc(c, 180f, 180f, false, Offset(8f, 3.5f), Size(8f, 8f), style = LINE)
    line(c, 8f, 7.5f, 8f, 10.5f)
    line(c, 16f, 7.5f, 16f, 10.5f)
    line(c, 12f, 14.5f, 12f, 16.5f)
}
private val CHECK: Glyph = { c -> line(c, 5f, 12.5f, 10f, 17.5f, 19f, 7f) }
private val UP: Glyph = { c -> line(c, 6f, 15f, 12f, 9f, 18f, 15f) }
private val DOWN: Glyph = { c -> line(c, 6f, 9f, 12f, 15f, 18f, 9f) }
private val NEXT: Glyph = { c -> line(c, 9f, 6f, 15f, 12f, 9f, 18f) }

/** Nova's − and + buttons: a black sign on a disc. */
private fun disc(plus: Boolean): Glyph = { c ->
    drawCircle(c, 10f, Offset(12f, 12f))
    line(Color.Black, 7f, 12f, 17f, 12f)
    if (plus) line(Color.Black, 12f, 7f, 12f, 17f)
}
private val LESS = disc(plus = false)
private val MORE = disc(plus = true)

@Composable
private fun GlyphIcon(glyph: Glyph, color: Color, modifier: Modifier = Modifier) =
    Canvas(modifier.size(24.dp)) { scale(size.width / 24f, Offset.Zero) { glyph(color) } }

/** [glyph] in an M3 icon button: 48 dp to touch, dimmed when not [enabled]. [label] is what TalkBack reads. */
@Composable
private fun IconAction(glyph: Glyph, label: String, tint: Color = MUTED, enabled: Boolean = true, onClick: () -> Unit) = IconButton(
    onClick,
    Modifier.semantics { contentDescription = label },
    enabled = enabled,
    colors = IconButtonDefaults.iconButtonColors(contentColor = tint),
) { GlyphIcon(glyph, LocalContentColor.current) }

private enum class Section(@StringRes val title: Int, val glyph: Glyph) {
    GUIDE(R.string.guide, BOOK),
    DESKTOP(R.string.home_screen, PHONE),
    DRAWER(R.string.settings_drawer, APPS),
    DOCK(R.string.settings_dock, DOCK_BAR),
    GESTURES(R.string.settings_gestures, TAP),
    BLOCKS(R.string.blocks, LOCK),
    BACKUP(R.string.settings_backup, RESTORE),
    TIDY(R.string.tidy, TRASH), // opened from DESKTOP or the guide, not from the main list
    UNUSED(R.string.unused, TRASH), // opened from DRAWER or the guide, not from the main list
}

@Composable
private fun SettingsScreen(l: Launcher) {
    // Opened by itself on a fresh install's first launch: on the guide, and back from there goes home.
    val welcome = remember { l.welcome }
    DisposableEffect(Unit) { onDispose { l.welcome = false } }
    // The sections opened on the way, each with its scroll: back returns to the one before, as it was left.
    val trail = remember { mutableStateListOf<Pair<Section, ScrollState>>().apply { if (welcome) add(Section.GUIDE to ScrollState(0)) } }
    val open: (Section) -> Unit = { trail.add(it to ScrollState(0)) }
    val mainScroll = remember { ScrollState(0) }
    val back: () -> Unit = { if (trail.isEmpty() || welcome && trail.size == 1) l.settingsOpen = false else trail.removeAt(trail.lastIndex) }
    BackHandler(onBack = back)
    // Clear, over the hidden Home, so a preview strip shows the wallpaper; a Surface still takes every touch.
    Surface(Modifier.fillMaxSize(), color = Color.Transparent, contentColor = MaterialTheme.colorScheme.onSurface) {
        val s = trail.lastOrNull()?.first
        // Separate branches (and keys), so the main list and each section keep their own state.
        if (s == null) MainList(l, mainScroll, open) else key(s) { Column {
            Bar(stringResource(s.title), back)
            val layout = l.layout
            when (s) {
                Section.DESKTOP -> {
                    // The main page's own icons, in reading order.
                    val mine = layout.desktop.getOrNull(layout.defaultPage).orEmpty().filterIsInstance<Item.Icon>()
                        .sortedWith(compareBy({ it.cell.y }, { it.cell.x })).map { it.target }.filterNot(l::hides)
                    PreviewIcons(l, mine.ifEmpty { l.apps.map { Target.App(it.id) } }, layout.columns, ICON, labels = true, Modifier.height(148.dp))
                }
                Section.DRAWER -> DrawerPreview(l, layout)
                Section.DOCK -> PreviewIcons(l, layout.dock.getOrNull(layout.defaultDockPage).orEmpty().take(layout.dockSize).map { it.tap?.takeUnless(l::hides) }, layout.dockSize, ICON, labels = false, Modifier.height(120.dp))
                else -> Unit
            }
            Column(Modifier.weight(1f).fillMaxWidth().background(Color.Black).verticalScroll(trail.last().second).navigationBarsPadding().padding(bottom = 16.dp)) {
                when (s) {
                    Section.GUIDE -> GuideSettings(welcome, open)
                    Section.DESKTOP -> DesktopSettings(l, layout) { open(Section.TIDY) }
                    Section.TIDY -> TidySettings(l, layout)
                    Section.DRAWER -> DrawerSettings(l, layout) { open(Section.UNUSED) }
                    Section.UNUSED -> UnusedSettings(l, layout)
                    Section.DOCK -> DockSettings(l, layout)
                    Section.GESTURES -> {
                        Header(stringResource(R.string.home_screen), divider = false)
                        SettingRow(stringResource(R.string.gesture_double_tap), l.nameOf(layout.doubleTap), {
                            l.popup = Popup.PickApp(l.getString(R.string.gesture_pick_double_tap)) { app ->
                                l.update { it.copy(doubleTap = app?.let { a -> Target.App(a.id) }) }
                            }
                        }) { layout.doubleTap?.let { Box(Modifier.padding(end = 8.dp)) { TargetIcon(l, it, 36.dp) } } }
                        Header(stringResource(R.string.settings_general))
                        SwitchRow(l, stringResource(R.string.haptics), stringResource(R.string.haptics_summary), layout.haptics) { on ->
                            l.update { it.copy(haptics = on) }
                        }
                    }
                    Section.BLOCKS -> BlocksSettings(l)
                    Section.BACKUP -> BackupSettings(l)
                }
            }
        } }
    }
}

/** Nova's first page: a big title over one row per section. No back arrow, as in Nova: system back closes it. */
@Composable
private fun MainList(l: Launcher, scroll: ScrollState, open: (Section) -> Unit) {
    val layout = l.layout
    Column(Modifier.fillMaxSize().background(Color.Black).statusBarsPadding().verticalScroll(scroll).navigationBarsPadding()) {
        Text(stringResource(R.string.settings), Modifier.padding(start = 24.dp, top = 88.dp, bottom = 32.dp), fontSize = 32.sp, fontFamily = TITLE_FONT)
        for (s in Section.entries) {
            val summary = when (s) {
                Section.GUIDE -> stringResource(R.string.guide_summary)
                Section.DESKTOP -> stringResource(R.string.settings_grid_summary, layout.columns, layout.rows) + " · " +
                    pluralStringResource(R.plurals.count_pages, layout.desktop.size, layout.desktop.size)
                Section.DRAWER -> stringResource(R.string.settings_grid_summary, layout.drawerColumns, layout.drawerRows) + " · " +
                    layout.tabs.joinToString(" · ") { it.name }
                Section.DOCK -> pluralStringResource(R.plurals.count_icons, layout.dockSize, layout.dockSize) + " · " +
                    pluralStringResource(R.plurals.count_pages, layout.dock.size, layout.dock.size)
                Section.GESTURES -> stringResource(R.string.settings_gestures_summary, l.nameOf(layout.doubleTap)) +
                    " · " + stringResource(if (layout.haptics) R.string.haptics else R.string.haptics_off)
                Section.BLOCKS -> BlockStore.rules.let { rules ->
                    if (rules.isEmpty()) stringResource(R.string.blocks_summary_none)
                    else BlockStore.now().let { now -> rules.count { it.activeAt(now) } }.let { n ->
                        pluralStringResource(R.plurals.count_blocks, rules.size, rules.size) + if (n > 0) " · " + pluralStringResource(R.plurals.blocks_running, n, n) else ""
                    }
                }
                Section.BACKUP -> l.backupFolder?.let { stringResource(R.string.settings_backup_summary_weekly, folderName(l, it)) }
                    ?: stringResource(R.string.settings_backup_summary)
                Section.TIDY, Section.UNUSED -> continue
            }
            SettingRow(stringResource(s.title), summary, { open(s) }, s.glyph)
        }
        SettingRow(stringResource(R.string.settings_home_app), stringResource(R.string.settings_home_app_summary), {
            l.startIntent(Intent(Settings.ACTION_HOME_SETTINGS), l.getString(R.string.open_target_settings))
        }, HOUSE)
    }
}

/** Nova's section bar, painted under the status bar. */
@Composable
private fun Bar(title: String, onBack: () -> Unit) = Row(
    Modifier.fillMaxWidth().background(BAR).statusBarsPadding().height(64.dp).padding(start = 10.dp),
    verticalAlignment = Alignment.CenterVertically,
) {
    IconAction(BACK, stringResource(R.string.back), MaterialTheme.colorScheme.onSurface, onClick = onBack)
    Text(title, Modifier.padding(start = 11.dp), fontSize = 22.sp, fontFamily = TITLE_FONT)
}

/** Nova's preview strip: [columns] cells sized as Home sizes them; a null target is an empty dock slot. Clear: the wallpaper shows. */
@Composable
private fun PreviewIcons(l: Launcher, targets: List<Target?>, columns: Int, nova: Dp, labels: Boolean, modifier: Modifier) =
    BoxWithConstraints(modifier.fillMaxWidth()) {
        val icon = iconSize(nova, maxWidth / columns, maxHeight - if (labels) LABEL_SPACE else 0.dp)
        Row(Modifier.fillMaxSize(), verticalAlignment = Alignment.CenterVertically) {
            repeat(columns) { i ->
                Column(Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally) {
                    if (i < targets.size) {
                        val target = targets[i]
                        if (target == null) AppImage(null, icon) else TargetIcon(l, target, icon)
                        if (labels && target != null) {
                            Text(l.labelOf(target), Modifier.padding(top = 6.dp, start = 4.dp, end = 4.dp), style = LABEL, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        }
                    }
                }
            }
        }
    }

/** The drawer as it opens: its tabs with the first one on, and a row of that tab's apps. */
@Composable
private fun DrawerPreview(l: Launcher, layout: Layout) = Column(Modifier.fillMaxWidth().background(Color.Black)) {
    // Bubbles, not Tabs: its long press would bring the drawer's unconfirmed "Eliminar" into Ajustes.
    LazyRow(Modifier.padding(top = TAB_GAP), contentPadding = PaddingValues(horizontal = 24.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        itemsIndexed(layout.tabs) { i, tab -> Bubble(tab.name, i == 0, Modifier) }
    }
    val apps = layout.tabs.firstOrNull()?.let { tabApps(it, layout.tabs, l.tabbed, l.find, layout.hidden) } ?: l.tabbed
    PreviewIcons(l, apps.take(layout.drawerColumns).map { Target.App(it.id) }, layout.drawerColumns, DRAWER_ICON, labels = true, Modifier.height(128.dp).padding(horizontal = 8.dp))
}

/**
 * A guide entry: its [title] and [how] to do it, a string, or with [args] a plurals counted by the first of them; [opens]
 * is its section of Ajustes, if it has one.
 */
private class Tip(@StringRes val title: Int, val how: Int, val opens: Section? = null, vararg val args: Int)

/**
 * Ajustes → Guía: what the app does, part by part, so it is not found only by chance. It tells what the code does, so
 * a new or changed feature changes its entry. One with a section of Ajustes opens it, as the main list does.
 * [welcome]: it opened by itself, so its first line says where to find it again.
 */
@Composable
private fun GuideSettings(welcome: Boolean, open: (Section) -> Unit) {
    val guide = listOf(
        R.string.home_screen to listOf(
            Tip(R.string.guide_move, R.string.guide_move_how),
            Tip(R.string.guide_throw, R.string.guide_throw_how),
            Tip(R.string.guide_shortcuts, R.string.guide_shortcuts_how),
            Tip(R.string.guide_rename, R.string.guide_rename_how),
            Tip(R.string.desktop_move_to_page, R.string.guide_move_page_how),
            Tip(R.string.uninstall, R.string.guide_uninstall_how),
            Tip(R.string.guide_home_menu, R.string.guide_home_menu_how),
            Tip(R.string.settings_pages, R.string.guide_pages_how, Section.DESKTOP),
            Tip(R.string.desktop_grid, R.string.guide_grid_how, Section.DESKTOP),
            Tip(R.string.tidy, R.string.guide_tidy_how, Section.TIDY),
            Tip(R.string.paused_apps, R.string.guide_paused_how, Section.DESKTOP),
            Tip(R.string.guide_pin, R.string.guide_pin_how),
        ),
        R.string.guide_widgets to listOf(
            Tip(R.string.widget_add, R.string.guide_widget_add_how),
            Tip(R.string.widget_resize, R.string.guide_resize_how),
            Tip(R.string.widget_reconfigure, R.string.guide_reconfigure_how),
            Tip(R.string.guide_placeholder, R.string.guide_placeholder_how),
        ),
        R.string.settings_dock to listOf(
            Tip(R.string.dock_show, R.string.guide_dock_show_how, Section.DOCK),
            Tip(R.string.guide_dock_setup, R.string.guide_dock_setup_how),
            Tip(R.string.guide_dock_drop, R.string.guide_dock_drop_how),
            Tip(R.string.guide_dock_pages, R.string.guide_dock_pages_how, Section.DOCK),
        ),
        R.string.settings_drawer to listOf(
            Tip(R.string.guide_drawer, R.string.guide_drawer_how),
            Tip(R.string.drawer_grid, R.string.guide_drawer_grid_how, Section.DRAWER),
            Tip(R.string.settings_tabs, R.string.guide_tabs_how, Section.DRAWER),
            Tip(R.string.guide_tab_apps, R.string.guide_tab_apps_how, Section.DRAWER),
            Tip(R.string.guide_hide, R.string.guide_hide_how, Section.DRAWER),
            Tip(R.string.add_to_home_screen, R.string.guide_add_home_how),
            Tip(R.string.guide_scroller, R.string.guide_scroller_how),
            Tip(R.string.unused, R.plurals.guide_unused_how, Section.UNUSED, UNUSED_DAYS),
        ),
        R.string.settings_search to listOf(
            Tip(R.string.guide_search_open, R.string.guide_search_open_how),
            Tip(R.string.guide_ranking, R.string.guide_ranking_how),
            Tip(R.string.guide_calc, R.string.guide_calc_how),
            Tip(R.string.search_numbers, R.string.guide_numbers_how, Section.DRAWER),
            Tip(R.string.guide_convert, R.string.guide_convert_how),
            Tip(R.string.guide_actions, R.string.guide_actions_how),
            Tip(R.string.search_with, R.string.guide_search_with_how),
            Tip(R.string.guide_bar, R.string.guide_bar_how, Section.DESKTOP),
        ),
        R.string.poisons to listOf(
            Tip(R.string.poison_mark, R.string.guide_poison_how, Section.DRAWER),
            Tip(R.string.guide_pause, R.plurals.guide_pause_how, null, PAUSE_SECONDS, PAUSE_STEP_SECONDS),
            Tip(R.string.poison_instead, R.string.guide_instead_how, Section.DRAWER),
            Tip(R.string.guide_poison_where, R.string.guide_poison_where_how),
        ),
        R.string.blocks to listOf(
            Tip(R.string.blocks_add, R.string.guide_blocks_how, Section.BLOCKS),
            Tip(R.string.guide_blocks_locked, R.string.guide_blocks_locked_how),
            Tip(R.string.guide_blocks_limits, R.string.guide_blocks_limits_how),
        ),
        R.string.guide_gestures to listOf(
            Tip(R.string.guide_swipe_up, R.string.guide_swipe_up_how),
            Tip(R.string.guide_swipe_down, R.string.guide_swipe_down_how),
            Tip(R.string.gesture_double_tap, R.string.guide_double_tap_how, Section.GESTURES),
            Tip(R.string.guide_home, R.string.guide_home_how),
            Tip(R.string.haptics, R.string.guide_haptics_how, Section.GESTURES),
        ),
        R.string.guide_settings to listOf(
            Tip(R.string.guide_open_settings, R.string.guide_open_settings_how),
            Tip(R.string.settings_home_app, R.string.guide_home_app_how),
            Tip(R.string.guide_backup, R.string.guide_backup_how, Section.BACKUP),
            Tip(R.string.backup_weekly, R.string.guide_weekly_how, Section.BACKUP),
            Tip(R.string.backup_import_nova, R.string.guide_nova_how, Section.BACKUP),
            Tip(R.string.guide_offline, R.string.guide_offline_how),
        ),
    )
    if (welcome) {
        Text(
            stringResource(R.string.guide_welcome),
            Modifier.padding(start = 24.dp, end = 16.dp, top = 16.dp, bottom = 8.dp),
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            fontSize = 14.sp,
            lineHeight = 17.sp,
        )
    }
    guide.forEachIndexed { i, (group, tips) ->
        Header(stringResource(group), divider = i > 0)
        for (t in tips) {
            // Only a line with numbers is formatted: the calculator's has a bare %.
            val how = if (t.args.isEmpty()) stringResource(t.how) else pluralStringResource(t.how, t.args[0], *t.args.toTypedArray())
            SettingRow(stringResource(t.title), how, t.opens?.let { s -> { open(s) } }) {
                if (t.opens != null) GlyphIcon(NEXT, MUTED, Modifier.padding(12.dp))
            }
        }
    }
}

@Composable
private fun DesktopSettings(l: Launcher, layout: Layout, onTidy: () -> Unit) {
    Header(stringResource(R.string.settings_layout), divider = false)
    GridSetting(l, stringResource(R.string.desktop_grid), layout.columns, layout.rows, { c, r -> gridNote(l, l.layout, c, r) }) { c, r ->
        l.update { it.withGrid(c, r) }
    }
    val attention = rememberAudit(l, layout)?.size
    SettingRow(
        stringResource(R.string.tidy),
        when (attention) {
            null -> null
            0 -> stringResource(R.string.tidy_all_clear)
            else -> pluralStringResource(R.plurals.tidy_summary, attention, attention)
        },
        onTidy,
    )
    SwitchRow(l, stringResource(R.string.search_bar_show), stringResource(R.string.search_bar_show_summary), layout.showSearchBar) { on ->
        l.update { it.copy(showSearchBar = on) }
    }
    // Focus mode and Digital Wellbeing pause apps; greyed (as in Nova) or left out of the desktop, dock and drawer tabs.
    SettingRow(stringResource(R.string.paused_apps), stringResource(if (layout.hideSuspended) R.string.paused_hide_summary else R.string.paused_dim_summary), { l.popup = Popup.PausedApps }) {
        Box {
            DropdownMenu(l.popup == Popup.PausedApps, { l.popup = null }) {
                MenuItem(l, ticked(!layout.hideSuspended, stringResource(R.string.paused_dim))) { l.update { it.copy(hideSuspended = false) } }
                MenuItem(l, ticked(layout.hideSuspended, stringResource(R.string.paused_hide))) { l.update { it.copy(hideSuspended = true) } }
            }
        }
    }
    Header(stringResource(R.string.settings_pages))
    layout.desktop.forEachIndexed { i, items ->
        val main = i == layout.defaultPage
        val summary = listOfNotNull(
            stringResource(R.string.desktop_page_main).takeIf { main },
            if (items.isEmpty()) stringResource(R.string.settings_page_empty) else pluralStringResource(R.plurals.count_items, items.size, items.size),
        )
        SettingRow(stringResource(R.string.settings_page, i + 1), summary.joinToString(" · ")) {
            if (layout.desktop.size > 1) {
                IconAction(UP, stringResource(R.string.move_up), enabled = i > 0) { l.update { it.withPageMoved(i, i - 1) } }
                IconAction(DOWN, stringResource(R.string.move_down), enabled = i < layout.desktop.lastIndex) { l.update { it.withPageMoved(i, i + 1) } }
            }
            // Nova marks the main page with a house; another page's house makes it the main one.
            if (main) GlyphIcon(HOUSE, ACCENT, Modifier.padding(12.dp))
            else IconAction(HOUSE, stringResource(R.string.desktop_make_main)) { l.update { it.copy(defaultPage = i) } }
            if (layout.desktop.size > 1) IconAction(TRASH, stringResource(R.string.remove)) {
                val remove = { l.update { it.withoutPage(i) } }
                if (items.isEmpty()) remove()
                else l.popup = Popup.Confirm(
                    l.getString(R.string.settings_remove_page, i + 1),
                    l.resources.getQuantityString(R.plurals.desktop_remove_page_text, items.size, items.size),
                    l.getString(R.string.remove),
                    remove,
                )
            }
        }
    }
    if (layout.desktop.size < MAX_PAGES) AddRow(stringResource(R.string.settings_add_page)) { l.update { it.copy(desktop = it.desktop + listOf(emptyList())) } }
}

/** What Listo would do to the desktop, from [withGrid] itself. */
private fun gridNote(context: Context, layout: Layout, columns: Int, rows: Int): String {
    val next = layout.withGrid(columns, rows)
    val moved = layout.desktop.withIndex().sumOf { (i, page) -> page.count { it !in next.desktop[i] } }
    val added = next.desktop.size - layout.desktop.size
    val res = context.resources
    val note = if (moved == 0) res.getString(R.string.desktop_grid_note_unchanged) else res.getQuantityString(R.plurals.desktop_grid_note_moved, moved, moved)
    return note + if (added == 0) "" else " " + res.getQuantityString(R.plurals.desktop_grid_note_pages_added, added, added)
}

/**
 * Ajustes → Escritorio → Ordenar: what is broken, in more than one place or empty, worked out when it opens and after
 * each removal. Nothing goes unless tapped; "Quitar todo lo roto" leaves twins and pages alone.
 */
@Composable
private fun TidySettings(l: Launcher, layout: Layout) {
    val audit = rememberAudit(l, layout) ?: return
    if (audit.size == 0 && audit.twins.isEmpty()) return SettingRow(stringResource(R.string.tidy_all_clear), stringResource(R.string.tidy_all_clear_summary))
    @Composable
    fun row(place: Place) = SettingRow(placeName(l, place), placeWhere(l, place)) { IconAction(TRASH, stringResource(R.string.remove)) { l.remove(place) } }
    if (audit.broken.isNotEmpty()) {
        Header(stringResource(R.string.tidy_broken), divider = false)
        val n = audit.broken.size
        SettingRow(stringResource(R.string.tidy_remove_broken), stringResource(R.string.tidy_remove_broken_summary), {
            l.popup = Popup.Confirm(
                l.getString(R.string.tidy_remove_broken),
                l.resources.getQuantityString(R.plurals.tidy_remove_broken_text, n, n),
                l.getString(R.string.remove),
            ) { audit.broken.forEach { l.remove(it) } } // by item and slot: no removal moves another
        }) { GlyphIcon(TRASH, ACCENT, Modifier.padding(12.dp)) }
        audit.broken.forEach { row(it) }
    }
    if (audit.twins.isNotEmpty()) {
        Header(stringResource(R.string.tidy_twins), divider = audit.broken.isNotEmpty())
        audit.twins.flatten().forEach { row(it) }
    }
    if (audit.empty.isNotEmpty()) {
        Header(stringResource(R.string.tidy_empty_pages), divider = audit.broken.isNotEmpty() || audit.twins.isNotEmpty())
        for (page in audit.empty) SettingRow(stringResource(R.string.settings_page, page + 1), stringResource(R.string.settings_page_empty)) {
            // The list can lag a removal by a moment: only a page still empty goes.
            IconAction(TRASH, stringResource(R.string.remove)) { l.update { if (it.desktop.getOrNull(page)?.isEmpty() == true) it.withoutPage(page) else it } }
        }
    }
}

/** [Launcher.audit] of [layout], worked out off the main thread (binder calls); null until the first one is in. */
@Composable
private fun rememberAudit(l: Launcher, layout: Layout): Audit? = // apps: a new list after each package change
    produceState<Audit?>(null, layout, l.apps) { value = withContext(Dispatchers.IO) { l.audit(layout) } }.value

/** What Ordenar calls [place]: its icon's label, else its app's name, or its package's once the app is gone. */
private fun placeName(l: Launcher, place: Place): String {
    fun name(t: Target) = when (t) {
        is Target.App -> l.appOf(t)?.label ?: t.id.substringBefore('/')
        is Target.Shortcut -> t.label.ifEmpty { t.pkg }
    }
    return when (place) {
        is Place.Desktop -> when (val item = place.item) {
            is Item.Icon -> item.label.ifBlank { name(item.target) }
            is Item.Widget -> l.getString(R.string.tidy_widget, item.provider.substringBefore('/'))
        }
        is Place.Dock -> name(place.target)
    }
}

/** "Página 2", or "Dock 1 · ícono 3 · deslizar ↑". */
private fun placeWhere(l: Launcher, place: Place): String = when (place) {
    is Place.Desktop -> l.getString(R.string.settings_page, place.page + 1)
    is Place.Dock -> l.getString(R.string.tidy_dock, place.page + 1, place.slot + 1) + (place.swipe?.let { " · " + l.getString(R.string.tidy_swipe, it.arrow) } ?: "")
}

/**
 * Ajustes → Cajón de apps → Sin abrir hace tiempo ([Launcher.unusedApps]): candidates to uninstall, worked out on open
 * and after each package change. Its note says the launcher only sees its own opens.
 */
@Composable
private fun UnusedSettings(l: Launcher, layout: Layout) {
    val apps = remember(layout, l.apps) { l.unusedApps() }
    Text(
        pluralStringResource(R.plurals.unused_note, UNUSED_DAYS, UNUSED_DAYS),
        Modifier.padding(start = 24.dp, end = 16.dp, top = 16.dp, bottom = 8.dp),
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        fontSize = 14.sp,
        lineHeight = 17.sp,
    )
    when {
        apps == null -> SettingRow(stringResource(R.string.unused_waiting), stringResource(R.string.unused_waiting_text, dayName(l.tracked), dayName(l.tracked + UNUSED_DAYS)))
        apps.isEmpty() -> SettingRow(stringResource(R.string.unused_none), pluralStringResource(R.plurals.unused_none_summary, UNUSED_DAYS, UNUSED_DAYS))
        else -> for (app in apps) {
            SettingRow(app.label, l.lastOpen(app)?.let { stringResource(R.string.unused_last_open, dayName(it)) } ?: stringResource(R.string.unused_no_opens)) {
                Box(Modifier.padding(end = 8.dp)) { TargetIcon(l, Target.App(app.id), 36.dp) }
                IconAction(TRASH, stringResource(R.string.uninstall)) { l.uninstall(app) }
            }
        }
    }
}

/** Day [day] (since 1970) as the phone writes dates: "3 nov 2026". */
private fun dayName(day: Double): String = DateFormat.getDateInstance(DateFormat.MEDIUM).format(Date((day * DAY_MS).toLong()))

@Composable
private fun DrawerSettings(l: Launcher, layout: Layout, onUnused: () -> Unit) {
    Header(stringResource(R.string.settings_layout), divider = false)
    val rowsNote = stringResource(R.string.drawer_grid_note)
    GridSetting(l, stringResource(R.string.drawer_grid), layout.drawerColumns, layout.drawerRows, { _, _ -> rowsNote }) { c, r ->
        l.update { it.copy(drawerColumns = c, drawerRows = r) }
    }
    Header(stringResource(R.string.settings_tabs))
    layout.tabs.forEachIndexed { i, tab ->
        // Installed apps only, each once: an app under two of its ids, or uninstalled, is not shown twice or at all.
        val summary = if (tab.apps == null) stringResource(R.string.drawer_tab_all_apps)
        else tab.apps.mapNotNull(l.find).toSet().size.let { n -> pluralStringResource(R.plurals.count_apps, n, n) } +
            if (tab.hideApps) " · " + stringResource(R.string.drawer_tab_hides_apps) else ""
        fun move(to: Int) = l.update { it.copy(tabs = it.tabs.shifted(i, to)) }
        SettingRow(tab.name, summary, { l.popup = Popup.EditTab(i) }) {
            IconAction(UP, stringResource(R.string.move_up), enabled = i > 0) { move(i - 1) }
            IconAction(DOWN, stringResource(R.string.move_down), enabled = i < layout.tabs.lastIndex) { move(i + 1) }
        }
    }
    AddRow(stringResource(R.string.drawer_add_tab)) { l.popup = newTab(l) }
    Header(stringResource(R.string.settings_apps))
    val hidden = layout.hidden.mapNotNull(l.find).toSet().size
    SettingRow(
        stringResource(R.string.drawer_hidden_apps),
        if (hidden == 0) stringResource(R.string.drawer_hidden_apps_none) else pluralStringResource(R.plurals.count_apps, hidden, hidden),
        { l.popup = Popup.Checklist(l.getString(R.string.drawer_hidden_apps), { it.hidden }, { layout, ids -> layout.copy(hidden = ids) }) },
    )
    val poisons = layout.poisons.mapNotNull(l.find).toSet().size
    SettingRow(
        stringResource(R.string.poisons),
        if (poisons == 0) stringResource(R.string.poison_summary_none) else pluralStringResource(R.plurals.count_apps, poisons, poisons),
        { l.popup = Popup.Checklist(l.getString(R.string.poisons), { it.poisons }, { layout, ids -> layout.copy(poisons = ids) }) },
    )
    SettingRow(
        stringResource(R.string.poison_instead),
        layout.instead?.let { l.nameOf(it) } ?: stringResource(R.string.poison_instead_none),
        {
            l.popup = Popup.PickApp(l.getString(R.string.poison_pick_instead)) { app ->
                l.update { it.copy(instead = app?.let { a -> Target.App(a.id) }) }
            }
        },
    ) { layout.instead?.let { Box(Modifier.padding(end = 8.dp)) { TargetIcon(l, it, 36.dp) } } }
    val unused = remember(layout, l.apps) { l.unusedApps() } // apps: a new list after each package change
    SettingRow(
        stringResource(R.string.unused),
        when {
            unused == null -> stringResource(R.string.unused_ready_on, dayName(l.tracked + UNUSED_DAYS))
            unused.isEmpty() -> stringResource(R.string.unused_none)
            else -> pluralStringResource(R.plurals.count_apps, unused.size, unused.size)
        },
        onUnused,
    )
    Header(stringResource(R.string.settings_search))
    // How 1234.5 looks with each choice; automatic names the country's.
    val formats = listOf(null to stringResource(R.string.search_numbers_auto, formatNumber(1234.5, l.countryDecimal)), ',' to formatNumber(1234.5, ','), '.' to formatNumber(1234.5, '.'))
    SettingRow(stringResource(R.string.search_numbers), formats.first { it.first == layout.decimal }.second, { l.popup = Popup.Numbers }) {
        Box { DropdownMenu(l.popup == Popup.Numbers, { l.popup = null }) { for ((d, label) in formats) MenuItem(l, ticked(d == layout.decimal, label)) { l.update { it.copy(decimal = d) } } } }
    }
}

/** Names a new drawer tab, then goes straight to choosing its apps. */
private fun newTab(l: Launcher) = Popup.Rename(l.getString(R.string.drawer_new_tab), "") { name ->
    val i = l.layout.tabs.size
    l.update { it.copy(tabs = it.tabs + Tab(name, emptySet(), hideApps = true)) }
    l.popup = tabChecklist(i, name)
}

/** The checklist of drawer tab [i]'s apps, titled [title]. */
private fun tabChecklist(i: Int, title: String) = Popup.Checklist(title, { it.tabs.getOrNull(i)?.apps.orEmpty() }) { layout, ids ->
    layout.copy(tabs = layout.tabs.replaced(i) { it.copy(apps = ids) })
}

/** Renames drawer tab [i]; a tab of chosen apps can also pick them, leave the others or go. */
@Composable
private fun TabDialog(l: Launcher, i: Int, tab: Tab) {
    val onClose = { l.popup = null }
    var name by remember { mutableStateOf(tab.name) }
    var hide by remember { mutableStateOf(tab.hideApps) }
    val haptic = rememberHaptic(l)
    AlertDialog(
        onDismissRequest = onClose,
        title = { DialogTitle(stringResource(R.string.drawer_tab)) },
        text = {
            Column {
                GatedHaptics(l) { OutlinedTextField(name, { name = it }, Modifier.fillMaxWidth(), singleLine = true) }
                if (tab.apps != null) {
                    val count = tab.apps.mapNotNull(l.find).toSet().size
                    Row(
                        Modifier
                            .padding(top = 8.dp)
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(12.dp))
                            .clickable {
                                // The checklist takes this dialog's place, so the name and switch are kept first.
                                val title = name.trim().ifEmpty { tab.name }
                                l.update { it.copy(tabs = it.tabs.replaced(i) { t -> t.copy(name = title, hideApps = hide) }) }
                                l.popup = tabChecklist(i, title)
                            }
                            .padding(vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(stringResource(R.string.drawer_pick_apps), Modifier.weight(1f), fontSize = 16.sp)
                        Text(pluralStringResource(R.plurals.count_apps, count, count), color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 14.sp)
                    }
                    Row(Modifier.padding(top = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                        Text(stringResource(R.string.drawer_hide_from_all_tabs), Modifier.weight(1f).padding(end = 16.dp), fontSize = 16.sp)
                        Switch(hide, {
                            hide = it
                            haptic(if (it) HapticFeedbackType.ToggleOn else HapticFeedbackType.ToggleOff)
                        })
                    }
                    // In the body and the accent, away from Listo: the one action here that loses something.
                    Row(
                        Modifier
                            .padding(top = 8.dp)
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(12.dp))
                            .clickable {
                                onClose()
                                val remove = { l.update { it.copy(tabs = it.tabs.filterIndexed { j, _ -> j != i }) } }
                                if (tab.apps.isEmpty()) remove()
                                else l.popup = Popup.Confirm(
                                    l.getString(R.string.drawer_remove_tab_title, tab.name),
                                    l.getString(R.string.drawer_remove_tab_text),
                                    l.getString(R.string.remove),
                                    remove,
                                )
                            }
                            .padding(vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        GlyphIcon(TRASH, ACCENT, Modifier.padding(end = 12.dp))
                        Text(stringResource(R.string.drawer_remove_tab), color = ACCENT, fontSize = 16.sp)
                    }
                }
            }
        },
        confirmButton = {
            TextButton({
                onClose()
                l.update { it.copy(tabs = it.tabs.replaced(i) { t -> t.copy(name = name.trim(), hideApps = hide) }) }
            }, enabled = name.isNotBlank()) { Text(stringResource(R.string.done)) }
        },
        dismissButton = { TextButton(onClose) { Text(stringResource(R.string.cancel)) } },
    )
}

/** A dock slot's tap and swipes. A row opens the app picker, which comes back here, so several can be set in a row. */
@Composable
private fun DockDialog(l: Launcher, p: Popup.DockSetup, icon: DockIcon) {
    // [none]: what the gesture does without a target of its own. The picker is titled [label] too.
    @Composable
    fun TargetRow(@StringRes label: Int, target: Target?, none: String, set: (DockIcon, Target?) -> DockIcon) = Row(
        Modifier
            .padding(top = 8.dp)
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .clickable {
                l.popup = Popup.PickApp(l.getString(label), back = p) { app ->
                    l.updateDock(p.page, p.slot) { set(it, app?.let { a -> Target.App(a.id) }) }
                    l.popup = p
                }
            }
            .padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f).padding(end = 12.dp)) {
            Text(stringResource(label), fontSize = 16.sp)
            Text(if (target == null) none else l.nameOf(target), color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 14.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        // The row already reads the app's name.
        Box(Modifier.size(36.dp).clearAndSetSemantics {}) { target?.let { TargetIcon(l, it, 36.dp) } }
    }
    AlertDialog(
        onDismissRequest = { l.popup = null },
        // A pinned shortcut by its own name, not its app's ("Booking", not Chrome).
        title = { DialogTitle((icon.tap as? Target.Shortcut)?.let(l::labelOf) ?: l.appOf(icon.tap)?.label ?: stringResource(R.string.dock_icon)) },
        text = {
            Column {
                TargetRow(R.string.dock_pick_tap, icon.tap, l.nameOf(null)) { d, t -> d.copy(tap = t) }
                // Without its own, a swipe does what it does elsewhere on the home screen (DockSlot).
                TargetRow(R.string.dock_pick_swipe_up, icon.up, stringResource(R.string.settings_drawer)) { d, t -> d.copy(up = t) }
                TargetRow(R.string.dock_pick_swipe_down, icon.down, stringResource(R.string.notifications)) { d, t -> d.copy(down = t) }
            }
        },
        confirmButton = { TextButton({ l.popup = null }) { Text(stringResource(R.string.done)) } },
    )
}

@Composable
private fun DockSettings(l: Launcher, layout: Layout) {
    Header(stringResource(R.string.settings_layout), divider = false)
    SwitchRow(l, stringResource(R.string.dock_show), stringResource(R.string.dock_show_summary), layout.showDock) { on ->
        l.update { it.copy(showDock = on) }
    }
    SettingRow(stringResource(R.string.dock_icons_per_page), stringResource(R.string.dock_icons_per_page_summary)) {
        Stepper(l, layout.dockSize) { n -> l.update { it.withDockSize(n) } }
    }
    SwitchRow(l, stringResource(R.string.dock_infinite_scroll), stringResource(R.string.dock_infinite_scroll_summary), layout.dockWraps) { wraps ->
        l.update { it.copy(dockWraps = wraps) }
    }
    Header(stringResource(R.string.settings_pages))
    layout.dock.forEachIndexed { i, icons ->
        val used = icons.count { it != DockIcon() }
        val main = i == layout.defaultDockPage
        val summary = listOfNotNull(
            stringResource(R.string.desktop_page_main).takeIf { main },
            if (used == 0) stringResource(R.string.settings_page_empty) else pluralStringResource(R.plurals.count_icons, used, used),
        )
        SettingRow(stringResource(R.string.settings_page, i + 1), summary.joinToString(" · ")) {
            if (layout.dock.size > 1) {
                IconAction(UP, stringResource(R.string.move_up), enabled = i > 0) { l.update { it.withDockPageMoved(i, i - 1) } }
                IconAction(DOWN, stringResource(R.string.move_down), enabled = i < layout.dock.lastIndex) { l.update { it.withDockPageMoved(i, i + 1) } }
            }
            // As on the desktop pages: the house marks the main one, and makes another page the main one.
            if (main) GlyphIcon(HOUSE, ACCENT, Modifier.padding(12.dp))
            else IconAction(HOUSE, stringResource(R.string.desktop_make_main)) { l.update { it.copy(defaultDockPage = i) } }
            if (layout.dock.size > 1) {
                IconAction(TRASH, stringResource(R.string.remove)) {
                    val remove = { l.update { it.withoutDockPage(i) } }
                    if (used == 0) remove()
                    else l.popup = Popup.Confirm(
                        l.getString(R.string.settings_remove_page, i + 1),
                        l.getString(R.string.dock_remove_page_text),
                        l.getString(R.string.remove),
                        remove,
                    )
                }
            }
        }
    }
    if (layout.dock.size < MAX_PAGES) AddRow(stringResource(R.string.settings_add_page)) { l.update { it.copy(dock = it.dock + listOf(List(it.dockSize) { DockIcon() })) } }
}

@Composable
private fun BackupSettings(l: Launcher) {
    Header(stringResource(R.string.backup_manual), divider = false)
    SettingRow(stringResource(R.string.backup_save), stringResource(R.string.backup_save_summary), l::saveBackup)
    SettingRow(stringResource(R.string.backup_restore), stringResource(R.string.backup_restore_summary), {
        l.popup = Popup.Confirm(
            l.getString(R.string.backup_restore_title),
            l.getString(R.string.backup_restore_text),
            l.getString(R.string.choose_file),
            l::restoreBackup,
        )
    })
    Header(stringResource(R.string.backup_automatic))
    val folder = l.backupFolder
    // The switch reads backupFolder, so cancelling the picker leaves it off; the row picks another folder.
    // Only turning it off ticks: turning it on is up to the picker.
    val haptic = rememberHaptic(l)
    SettingRow(
        stringResource(R.string.backup_weekly),
        folder?.let { stringResource(R.string.backup_weekly_in, folderName(l, it)) } ?: stringResource(R.string.backup_weekly_off),
        l::chooseBackupFolder,
    ) {
        Switch(folder != null, { on ->
            if (on) {
                l.chooseBackupFolder()
            } else {
                l.stopAutoBackup()
                haptic(HapticFeedbackType.ToggleOff)
            }
        })
    }
    Header("Nova Launcher")
    SettingRow(stringResource(R.string.backup_import_nova), stringResource(R.string.backup_import_nova_summary), {
        l.popup = Popup.Confirm(
            l.getString(R.string.backup_import_nova),
            l.getString(R.string.backup_import_nova_text),
            l.getString(R.string.choose_file),
            l::importNova,
        )
    })
}

private fun folderName(context: Context, tree: Uri) =
    DocumentsContract.getTreeDocumentId(tree).substringAfter(':').ifEmpty { context.getString(R.string.backup_chosen_folder) }

/** A row of Ajustes: [icon] on the main list, [trailing] for its buttons, switch or picture. */
@Composable
private fun SettingRow(title: String, summary: String? = null, onClick: (() -> Unit)? = null, icon: Glyph? = null, trailing: @Composable () -> Unit = {}) {
    Row(
        Modifier
            .fillMaxWidth()
            // Without a click, title and summary still read as one item; its buttons keep their own focus.
            .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier.semantics(mergeDescendants = true) {})
            .heightIn(min = 72.dp)
            .padding(start = if (icon != null) 34.dp else 24.dp, end = 16.dp, top = 12.dp, bottom = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        icon?.let { GlyphIcon(it, MUTED, Modifier.padding(end = 20.dp)) }
        Column(Modifier.weight(1f).padding(end = 8.dp)) {
            Text(title, fontSize = 18.sp, lineHeight = 22.sp)
            summary?.let { Text(it, color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 14.sp, lineHeight = 17.sp) }
        }
        trailing()
    }
}

/** A row with a switch that ticks on or off after [onChange] (so turning haptics off is silent). Tapping the row flips it too. */
@Composable
private fun SwitchRow(l: Launcher, title: String, summary: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    val haptic = rememberHaptic(l)
    val flip = { on: Boolean ->
        onChange(on)
        haptic(if (on) HapticFeedbackType.ToggleOn else HapticFeedbackType.ToggleOff)
    }
    SettingRow(title, summary, { flip(!checked) }) { Switch(checked, flip) }
}

/** "Agregar …": an accent + so it reads as an action, not one more item. */
@Composable
private fun AddRow(title: String, onClick: () -> Unit) = SettingRow(title, onClick = onClick) { GlyphIcon(MORE, ACCENT, Modifier.padding(12.dp)) }

/** A group's title in Nova's bold grey; [divider] puts Nova's short centred line between it and the group above. */
@Composable
private fun Header(text: String, divider: Boolean = true) {
    if (divider) Box(Modifier.fillMaxWidth().padding(top = 12.dp, bottom = 4.dp), contentAlignment = Alignment.Center) {
        Box(Modifier.size(128.dp, 3.dp).background(DIVIDER, CircleShape))
    }
    Text(text, Modifier.semantics { heading() }.padding(start = 24.dp, top = 16.dp, bottom = 4.dp), color = MUTED, fontSize = 14.sp, fontFamily = HEADER_FONT)
}

/** Nova's − n + or, [vertical], + n −, within GRID_SIZES; SpaceBetween spreads it over a wider [modifier]. */
@Composable
private fun Stepper(l: Launcher, value: Int, modifier: Modifier = Modifier, vertical: Boolean = false, onChange: (Int) -> Unit) {
    val haptic = rememberHaptic(l)
    val step = { n: Int ->
        haptic(HapticFeedbackType.SegmentTick)
        onChange(n)
    }
    val less = @Composable { IconAction(LESS, stringResource(R.string.stepper_fewer), enabled = value > GRID_SIZES.first) { step(value - 1) } }
    val more = @Composable { IconAction(MORE, stringResource(R.string.stepper_more), enabled = value < GRID_SIZES.last) { step(value + 1) } }
    // A fixed box, so the buttons stay put between 9 and 10.
    val number = @Composable {
        Box(Modifier.size(48.dp), contentAlignment = Alignment.Center) { Text("$value", color = MaterialTheme.colorScheme.onSurface, fontSize = 16.sp) }
    }
    if (vertical) Column(modifier) { more(); number(); less() }
    else Row(modifier, Arrangement.SpaceBetween, Alignment.CenterVertically) { less(); number(); more() }
}

/** Nova's grid picture: a rounded square centred in each of [columns] × [rows] cells. */
@Composable
private fun GridCells(columns: Int, rows: Int, modifier: Modifier) = Canvas(modifier) {
    val cell = Size(size.width / columns, size.height / rows)
    val side = cell.minDimension * 0.88f
    for (x in 0..<columns) for (y in 0..<rows) {
        val at = Offset((x + 0.5f) * cell.width - side / 2, (y + 0.5f) * cell.height - side / 2)
        drawRoundRect(MUTED, at, Size(side, side), CornerRadius(side * 0.26f))
    }
}

@Composable
private fun GridSetting(l: Launcher, title: String, columns: Int, rows: Int, note: (Int, Int) -> String, onDone: (Int, Int) -> Unit) =
    SettingRow(title, "$columns × $rows", { l.popup = Popup.Grid(title, columns, rows, note, onDone) }) {
        GridCells(columns, rows, Modifier.padding(end = 8.dp).size(48.dp, 60.dp))
    }

/** Nova's dialog title: centred and bold. */
@Composable
private fun DialogTitle(text: String) = Text(text, Modifier.fillMaxWidth(), textAlign = TextAlign.Center, fontSize = 20.sp, fontWeight = FontWeight.Bold)

/** Applies both sizes at once, so items move only for the final grid. */
@Composable
private fun GridDialog(l: Launcher, p: Popup.Grid) {
    var c by remember { mutableIntStateOf(p.columns) }
    var r by remember { mutableIntStateOf(p.rows) }
    val note = remember(c, r) { p.note(c, r) }
    AlertDialog(
        onDismissRequest = { l.popup = null },
        title = { DialogTitle(p.title) },
        text = {
            Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
                // As in Nova: columns under the cells, rows beside the last ones. No size depends on c or r,
                // so a button stays under the finger.
                Row(verticalAlignment = Alignment.Bottom) {
                    Column(Modifier.weight(1f, fill = false).widthIn(max = 256.dp)) {
                        GridCells(c, r, Modifier.fillMaxWidth().aspectRatio(256f / 332f))
                        Stepper(l, c, Modifier.fillMaxWidth().padding(top = 8.dp)) { c = it }
                    }
                    Stepper(l, r, Modifier.padding(start = 16.dp, bottom = 56.dp), vertical = true) { r = it }
                }
                Text(note, Modifier.padding(top = 16.dp), textAlign = TextAlign.Center, style = MaterialTheme.typography.bodyMedium)
            }
        },
        confirmButton = {
            TextButton({
                l.popup = null
                if (c != p.columns || r != p.rows) p.onDone(c, r)
            }) { Text(stringResource(R.string.done)) }
        },
        dismissButton = { TextButton({ l.popup = null }) { Text(stringResource(R.string.cancel)) } },
    )
}

// --- Ajustes → Bloqueos ---

private const val MAX_BLOCK_NAME = 60
private const val MAX_SITE_TYPED = 253 // a host name's longest

/** A note in a section's grey, as Sin abrir hace tiempo starts. */
@Composable
private fun NoteText(text: String, color: Color = MaterialTheme.colorScheme.onSurfaceVariant) =
    Text(text, Modifier.padding(start = 24.dp, end = 16.dp, top = 16.dp, bottom = 8.dp), color = color, fontSize = 14.sp, lineHeight = 17.sp)

/** The adb commands of scripts/blocks-setup.sh but its last step, which the accessibility row does here once they ran. */
private fun blockCommands(pkg: String) = listOf(
    "adb shell pm grant $pkg android.permission.WRITE_SECURE_SETTINGS",
    "adb shell dpm set-active-admin $pkg/.BlockAdmin",
    "adb shell cmd notification allow_listener $pkg/.BlockListener",
).joinToString("\n")

/**
 * Ajustes → Bloqueos: what blocks need ([BlockSetup]), the blocks, and the browsers a site block reads. The setup is read
 * again on each return, as adb or Ajustes change it outside the app.
 */
@Composable
private fun BlocksSettings(l: Launcher) {
    val rules = BlockStore.rules
    var refreshes by remember { mutableIntStateOf(0) }
    val setup = remember(l.resumes, rules, refreshes) { l.blockSetup() }
    val now by produceState(BlockStore.now(), rules) {
        while (true) {
            delay(30_000)
            value = BlockStore.now()
        }
    }
    NoteText(stringResource(R.string.blocks_note))
    SettingRow(stringResource(R.string.blocks_privacy), stringResource(R.string.blocks_privacy_summary))
    Header(stringResource(R.string.blocks_setup), divider = false)
    @Composable
    fun need(title: Int, on: Boolean, adbOnly: Boolean, open: () -> Unit) = SettingRow(
        stringResource(title),
        stringResource(if (on) R.string.blocks_on else if (adbOnly) R.string.blocks_off_adb else R.string.blocks_off_tap),
        if (adbOnly) null else open,
    ) { if (on) GlyphIcon(CHECK, MUTED, Modifier.padding(12.dp)) }
    NoteText(stringResource(if (setup.complete) R.string.blocks_setup_ready else R.string.blocks_setup_steps))
    need(R.string.blocks_accessibility, setup.accessibility, adbOnly = false) {
        // With the adb permission it turns itself on; else Ajustes does (Android may ask to allow restricted settings).
        if (!l.enableBlockService()) l.startIntent(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS), l.getString(R.string.blocks_accessibility))
        refreshes++
    }
    need(R.string.blocks_notifications, setup.notifications, adbOnly = false) {
        l.startIntent(Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS), l.getString(R.string.blocks_notifications))
    }
    need(R.string.blocks_admin, setup.admin, adbOnly = false) {
        // Not through startIntent: Ajustes refuses this one in a new task.
        try {
            l.startActivity(
                Intent(DevicePolicyManager.ACTION_ADD_DEVICE_ADMIN)
                    .putExtra(DevicePolicyManager.EXTRA_DEVICE_ADMIN, ComponentName(l, BlockAdmin::class.java))
                    .putExtra(DevicePolicyManager.EXTRA_ADD_EXPLANATION, l.getString(R.string.blocks_admin_explain)),
            )
        } catch (e: RuntimeException) {
            l.toast(l.getString(R.string.open_failed, l.getString(R.string.blocks_admin)))
        }
    }
    if (!setup.complete) {
        SettingRow(stringResource(R.string.blocks_restricted), stringResource(R.string.blocks_restricted_summary), {
            l.startIntent(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, "package:${l.packageName}".toUri()), l.getString(R.string.blocks_restricted))
        })
    }
    Header(stringResource(R.string.blocks_protection))
    NoteText(stringResource(if (setup.secureSettings) R.string.blocks_protection_on else R.string.blocks_protection_basic))
    need(R.string.blocks_secure_settings, setup.secureSettings, adbOnly = true) {}
    if (!setup.secureSettings) {
        // Android shows its own note that something was copied.
        SettingRow(stringResource(R.string.blocks_how), stringResource(R.string.blocks_how_summary), {
            l.getSystemService(ClipboardManager::class.java).setPrimaryClip(ClipData.newPlainText("adb", blockCommands(l.packageName)))
        })
    }
    Header(stringResource(R.string.blocks))
    for (r in rules) {
        val until = if (r.activeAt(now)) blockedUntil(listOf(r), now) else null
        val summary = listOfNotNull(
            until?.let { stringResource(R.string.blocks_running_until, l.untilText(it)) } ?: r.spans.joinToString("; ") { spanText(l, it) },
            r.packages.size.takeIf { it > 0 }?.let { pluralStringResource(R.plurals.count_apps, it, it) },
            r.sites.size.takeIf { it > 0 }?.let { pluralStringResource(R.plurals.count_sites, it, it) },
        ).joinToString(" · ")
        SettingRow(r.name.ifBlank { stringResource(R.string.blocks_unnamed) }, summary, { l.popup = Popup.EditBlock(r.id) }) {
            if (until != null) GlyphIcon(LOCK, ACCENT, Modifier.padding(12.dp))
        }
    }
    if (setup.complete) AddRow(stringResource(R.string.blocks_add)) { l.popup = Popup.EditBlock(null) }
    else SettingRow(stringResource(R.string.blocks_add), stringResource(R.string.blocks_add_needs_setup))
    if (rules.any { it.sites.isNotEmpty() }) {
        Header(stringResource(R.string.blocks_browsers))
        val browsers = remember(l.apps) { l.browserPackages() } // apps: a new list after each package change
        for (app in l.apps.distinctBy { it.info.componentName.packageName }.filter { it.info.componentName.packageName in browsers }) {
            val read = BlockStore.lastRead(app.info.componentName.packageName)
            // Noon of that day: dayName's midnight UTC is the day before west of Greenwich.
            SettingRow(app.label, read?.let { stringResource(R.string.blocks_browser_read, dayName(it.toEpochDay() + 0.5)) } ?: stringResource(R.string.blocks_browser_unread)) {
                Box(Modifier.padding(end = 8.dp)) { AppImage(app, 36.dp) }
            }
        }
    }
    if (rules.isEmpty() && (setup.accessibility || setup.admin || setup.notifications)) {
        Header(stringResource(R.string.settings_general))
        SettingRow(stringResource(R.string.blocks_turn_off), stringResource(R.string.blocks_turn_off_summary), {
            l.turnOffBlocks()
            refreshes++
            if (setup.accessibility && !setup.secureSettings) {
                l.startIntent(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS), l.getString(R.string.blocks_accessibility))
            } else if (setup.notifications) l.startIntent(Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS), l.getString(R.string.blocks_notifications))
        })
    }
}

/** "lun, mar, mié · 09:00–18:00", in the phone's language and time format. */
private fun spanText(context: Context, s: Span): String {
    val days = if (s.days.size == 7) context.getString(R.string.blocks_every_day)
    else DayOfWeek.entries.filter { it in s.days }.joinToString(", ") { it.getDisplayName(DayStyle.SHORT_STANDALONE, Locale.getDefault()) }
    return context.getString(R.string.blocks_span_summary, days, minuteText(context, s.start), minuteText(context, s.end % DAY_MINUTES))
}

/** Minute [m] of a day in the phone's time format: "18:00" or "6:00 p. m.". On a day with no clock change. */
private fun minuteText(context: Context, m: Int): String =
    android.text.format.DateFormat.getTimeFormat(context).format(Date.from(LocalDate.of(2001, 1, 1).atTime(m / 60, m % 60).atZone(ZoneId.systemDefault()).toInstant()))

private fun problemText(p: RuleProblem) = when (p) {
    RuleProblem.NOTHING_BLOCKED -> R.string.blocks_problem_nothing
    RuleProblem.NO_SPANS -> R.string.blocks_problem_no_spans
    RuleProblem.BAD_SPAN -> R.string.blocks_problem_bad_span
    RuleProblem.NO_FREE_HOUR -> R.string.blocks_problem_free
    RuleProblem.LOOSENED -> R.string.blocks_problem_loosened
}

/** What the block editor has open over it. */
private enum class BlockDialog { APPS, HOURS }

/**
 * A block's name, apps, sites and hours, saved once [problemSaving] finds nothing. While the block runs, what it had
 * stays (its trash only says so): only additions save, and it cannot be removed.
 */
@Composable
private fun BlockEditor(l: Launcher, p: Popup.EditBlock) {
    val old = remember { BlockStore.rules.firstOrNull { it.id == p.id } }
    val blank = remember { old ?: Rule(BlockStore.newId(), "", emptySet(), emptySet(), emptyList()) }
    var draft by remember { mutableStateOf(blank) }
    val now by produceState(BlockStore.now()) {
        while (true) {
            delay(30_000)
            value = BlockStore.now()
        }
    }
    val until = old?.takeIf { it.activeAt(now) }?.let { blockedUntil(listOf(it), now) }
    val kept = if (until != null) old else null
    var dialog by remember { mutableStateOf<BlockDialog?>(null) }
    var typed by remember { mutableStateOf("") }
    val close = { l.popup = null }
    BackHandler(onBack = close)
    val addSite = {
        val host = site(typed)
        if (host == null) l.toast(l.getString(R.string.blocks_site_invalid))
        else {
            draft = draft.copy(sites = draft.sites + host)
            typed = ""
        }
    }
    val save = {
        // Checked again as it is: the block may have started since the screen opened.
        val current = BlockStore.rules
        val before = current.firstOrNull { it.id == draft.id }
        val problem = problemSaving(draft, before, BlockStore.now())
        if (problem != null) l.toast(l.getString(problemText(problem)))
        else {
            BlockStore.save(if (before == null) current + draft else current.map { if (it.id == draft.id) draft else it })
            l.guardBlocks()
            close()
        }
    }
    @Composable
    fun Trash(isKept: Boolean, onClick: () -> Unit) =
        IconAction(TRASH, stringResource(R.string.remove), tint = if (isKept) TAB_OFF else MUTED) { if (isKept) l.toast(l.getString(R.string.blocks_kept)) else onClick() }
    Surface(Modifier.fillMaxSize(), color = Color.Black, contentColor = MaterialTheme.colorScheme.onSurface) {
        Column(Modifier.navigationBarsPadding().imePadding()) {
            Bar(stringResource(if (old == null) R.string.blocks_new else R.string.blocks_edit), close)
            Column(Modifier.weight(1f).verticalScroll(rememberScrollState())) {
                until?.let { NoteText(stringResource(R.string.blocks_locked, l.untilText(it)), ACCENT) }
                GatedHaptics(l) {
                    OutlinedTextField(
                        draft.name,
                        { draft = draft.copy(name = it.take(MAX_BLOCK_NAME)) },
                        Modifier.fillMaxWidth().padding(start = 24.dp, end = 16.dp, top = 16.dp),
                        label = { Text(stringResource(R.string.blocks_name)) },
                        singleLine = true,
                    )
                }
                Header(stringResource(R.string.blocks_apps))
                val byPackage = l.apps.associateBy { it.info.componentName.packageName }
                for (pkg in draft.packages.sortedBy { fold(byPackage[it]?.label ?: it) }) {
                    val app = byPackage[pkg]
                    SettingRow(app?.label ?: pkg) {
                        Box(Modifier.padding(end = 8.dp)) { AppImage(app, 36.dp) }
                        Trash(kept?.packages?.contains(pkg) == true) { draft = draft.copy(packages = draft.packages - pkg) }
                    }
                }
                AddRow(stringResource(R.string.blocks_pick_apps)) { dialog = BlockDialog.APPS }
                Header(stringResource(R.string.blocks_sites))
                NoteText(stringResource(R.string.blocks_site_note))
                Row(Modifier.fillMaxWidth().padding(start = 24.dp, end = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                    Box(Modifier.weight(1f)) {
                        GatedHaptics(l) {
                            OutlinedTextField(
                                typed,
                                { typed = it.take(MAX_SITE_TYPED) },
                                Modifier.fillMaxWidth(),
                                placeholder = { Text("youtube.com") },
                                singleLine = true,
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri, imeAction = ImeAction.Done),
                                keyboardActions = KeyboardActions(onDone = { addSite() }),
                            )
                        }
                    }
                    TextButton(addSite, enabled = typed.isNotBlank()) { Text(stringResource(R.string.add)) }
                }
                for (host in draft.sites.sorted()) {
                    SettingRow(IDN.toUnicode(host)) { Trash(kept?.sites?.contains(host) == true) { draft = draft.copy(sites = draft.sites - host) } }
                }
                Header(stringResource(R.string.blocks_hours))
                draft.spans.forEachIndexed { i, s ->
                    SettingRow(spanText(l, s)) { Trash(kept?.spans?.contains(s) == true) { draft = draft.copy(spans = draft.spans.filterIndexed { j, _ -> j != i }) } }
                }
                AddRow(stringResource(R.string.blocks_add_hours)) { dialog = BlockDialog.HOURS }
                if (old != null) {
                    Header(stringResource(R.string.settings_general))
                    val remove = if (until != null) null else ({
                        l.popup = Popup.Confirm(
                            l.getString(R.string.blocks_remove_title, old.name.ifBlank { l.getString(R.string.blocks_unnamed) }),
                            l.getString(R.string.blocks_remove_text),
                            l.getString(R.string.remove),
                        ) {
                            val current = BlockStore.rules.firstOrNull { it.id == old.id }
                            if (current != null && canRemove(current, BlockStore.now())) BlockStore.save(BlockStore.rules - current)
                            else if (current != null) l.toast(l.getString(R.string.blocks_problem_loosened))
                        }
                    })
                    SettingRow(stringResource(R.string.blocks_remove), until?.let { stringResource(R.string.blocks_remove_locked, l.untilText(it)) }, remove) {
                        GlyphIcon(TRASH, if (until == null) ACCENT else TAB_OFF, Modifier.padding(12.dp))
                    }
                }
            }
            val changed = draft != blank
            val problem = problemSaving(draft, old, now)
            Row(Modifier.fillMaxWidth().background(BAR).padding(horizontal = 16.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                Text(
                    if (changed && problem != null) stringResource(problemText(problem)) else "",
                    Modifier.weight(1f).padding(end = 12.dp),
                    color = ACCENT,
                    fontSize = 14.sp,
                    lineHeight = 17.sp,
                )
                Button(save, enabled = changed && problem == null, colors = ButtonDefaults.buttonColors(containerColor = ACCENT, contentColor = Color.Black)) {
                    Text(stringResource(R.string.blocks_save))
                }
            }
        }
    }
    when (dialog) {
        BlockDialog.APPS -> BlockAppsDialog(l, draft.packages, kept?.packages.orEmpty(), { pkg ->
            draft = draft.copy(packages = if (pkg in draft.packages) draft.packages - pkg else draft.packages + pkg)
        }) { dialog = null }
        BlockDialog.HOURS -> SpanDialog(l, { s ->
            draft = draft.copy(spans = draft.spans + s)
            dialog = null
        }) { dialog = null }
        null -> Unit
    }
}

/** The apps a block can hold, by package (every activity and profile of it), ticked first; [kept] cannot be unticked. */
@Composable
private fun BlockAppsDialog(l: Launcher, chosen: Set<String>, kept: Set<String>, onToggle: (String) -> Unit, onDismiss: () -> Unit) {
    var query by remember { mutableStateOf("") }
    val safe = remember { l.unblockable() }
    val first = remember { chosen } // ticked first, in the order the list opened with, so a tap does not move the row away
    val apps = remember(l.apps) { l.apps.distinctBy { it.info.componentName.packageName }.filter { it.info.componentName.packageName !in safe } }
    PickerDialog(l, stringResource(R.string.blocks_pick_apps), query, { query = it }, onDismiss) {
        items(search(apps.sortedByDescending { it.info.componentName.packageName in first }, query) { it.label }, key = { it.id }) { app ->
            val pkg = app.info.componentName.packageName
            PickerRow(app.icon, app.label, checked = pkg in chosen) { if (pkg in kept) l.toast(l.getString(R.string.blocks_kept)) else onToggle(pkg) }
        }
    }
}

/** A new range: days to tick, then a start and an end, each through a clock; an end before the start is the next day. */
@Composable
private fun SpanDialog(l: Launcher, onDone: (Span) -> Unit, onDismiss: () -> Unit) {
    var days by remember { mutableStateOf(setOf(DayOfWeek.MONDAY, DayOfWeek.TUESDAY, DayOfWeek.WEDNESDAY, DayOfWeek.THURSDAY, DayOfWeek.FRIDAY)) }
    var start by remember { mutableIntStateOf(9 * 60) }
    var end by remember { mutableIntStateOf(18 * 60) }
    var picking by remember { mutableStateOf<Boolean?>(null) } // true: the start, false: the end
    val haptic = rememberHaptic(l)
    val span = span(days, start, end)
    val locale = LocalConfiguration.current.locales[0] // observed: the day names follow a language change
    @Composable
    fun TimeRow(label: Int, minutes: Int, onClick: () -> Unit) = Row(
        Modifier.padding(top = 8.dp).fillMaxWidth().clip(RoundedCornerShape(12.dp)).clickable(onClick = onClick).padding(vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(stringResource(label), Modifier.weight(1f), fontSize = 16.sp)
        Text(minuteText(l, minutes), color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 16.sp)
    }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { DialogTitle(stringResource(R.string.blocks_add_hours)) },
        text = {
            Column {
                Row(Modifier.fillMaxWidth(), Arrangement.SpaceBetween) {
                    for (d in DayOfWeek.entries) {
                        val on = d in days
                        Box(
                            Modifier
                                .size(36.dp)
                                .clip(CircleShape)
                                .background(if (on) ACCENT else TAB_OFF)
                                .toggleable(on, role = Role.Checkbox) {
                                    days = if (it) days + d else days - d
                                    haptic(if (it) HapticFeedbackType.ToggleOn else HapticFeedbackType.ToggleOff)
                                }
                                .semantics { contentDescription = d.getDisplayName(DayStyle.FULL_STANDALONE, locale) },
                            contentAlignment = Alignment.Center,
                        ) {
                            Text(d.getDisplayName(DayStyle.NARROW_STANDALONE, locale), Modifier.clearAndSetSemantics {}, color = if (on) Color.Black else TAB_OFF_TEXT, fontSize = 14.sp)
                        }
                    }
                }
                TimeRow(R.string.blocks_from, start) { picking = true }
                TimeRow(R.string.blocks_to, end % DAY_MINUTES) { picking = false }
                if (span.end < span.start) Text(stringResource(R.string.blocks_next_day), color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 14.sp)
            }
        },
        confirmButton = { TextButton({ onDone(span) }, enabled = span.valid) { Text(stringResource(R.string.add)) } },
        dismissButton = { TextButton(onDismiss) { Text(stringResource(R.string.cancel)) } },
    )
    picking?.let { first ->
        TimeDialog(if (first) start else end % DAY_MINUTES, { m ->
            if (first) start = m else end = m
            picking = null
        }) { picking = null }
    }
}

/** Material's clock face, in the phone's 12- or 24-hour format. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun TimeDialog(minutes: Int, onDone: (Int) -> Unit, onDismiss: () -> Unit) {
    val state = rememberTimePickerState(minutes / 60, minutes % 60, android.text.format.DateFormat.is24HourFormat(LocalContext.current))
    AlertDialog(
        onDismissRequest = onDismiss,
        text = { TimePicker(state) },
        confirmButton = { TextButton({ onDone(state.hour * 60 + state.minute) }) { Text(stringResource(R.string.done)) } },
        dismissButton = { TextButton(onDismiss) { Text(stringResource(R.string.cancel)) } },
    )
}

/** Shown by [PinActivity] over whatever app asked to add a shortcut. */
@Composable
fun PinDialog(label: String, from: String, onCancel: () -> Unit, onAdd: () -> Unit) {
    MaterialTheme(colorScheme = novaColors()) {
        AlertDialog(
            onDismissRequest = onCancel,
            title = { DialogTitle(stringResource(R.string.add_to_home_screen)) },
            text = { Text(stringResource(R.string.pin_text, label, from)) },
            confirmButton = { TextButton(onAdd) { Text(stringResource(R.string.add)) } },
            dismissButton = { TextButton(onCancel) { Text(stringResource(R.string.cancel)) } },
        )
    }
}

/** Runs [up] or [down] when a vertical drag ends at least SWIPE_MIN that way. */
private fun Modifier.onVerticalSwipe(up: () -> Unit = {}, down: () -> Unit = {}) = pointerInput(Unit) {
    var total = 0f
    detectVerticalDragGestures(
        onDragStart = { total = 0f },
        onDragEnd = {
            when (swipeOf(0f, total, SWIPE_MIN.toPx())) {
                Swipe.UP -> up()
                Swipe.DOWN -> down()
                else -> Unit
            }
        },
    ) { change, dy ->
        change.consume()
        total += dy
    }
}

/**
 * A long press, then optionally a drag past the touch slop ([onDragStart] gets the press position, local; [onDrag]
 * each change after it, release included). A cancel (the screen turned off, a call took the window) is [onCancel], not
 * a drop. It watches the Initial pass, so it wins over a hosted View (a widget), before the View gets the touch.
 */
private fun Modifier.longPressDrag(
    key: Any?,
    onLongPress: () -> Unit,
    onDragStart: (Offset) -> Unit,
    onDrag: (PointerInputChange) -> Unit,
    onDrop: () -> Unit,
    onCancel: () -> Unit,
) = pointerInput(key) {
    awaitEachGesture {
        val down = awaitFirstDown(requireUnconsumed = false, pass = PointerEventPass.Initial)
        val released = withTimeoutOrNull(viewConfiguration.longPressTimeoutMillis) {
            while (true) {
                val change = awaitPointerEvent(PointerEventPass.Initial).changes.firstOrNull { it.id == down.id } ?: break
                if (!change.pressed || (change.position - down.position).getDistance() > viewConfiguration.touchSlop) break
            }
        }
        if (released != null) return@awaitEachGesture
        onLongPress()
        var dragging = false
        try {
            // Swallow the rest of the gesture, so the item gets a cancel instead of a click and the pager stays put.
            do {
                val event = awaitPointerEvent(PointerEventPass.Initial)
                event.changes.firstOrNull { it.id == down.id }?.let { change ->
                    // Compose turns a cancel into a release consumed already; nothing ahead of this consumes a real one.
                    if (!change.pressed && change.isConsumed) {
                        if (dragging) onCancel()
                        return@awaitEachGesture
                    }
                    if (!dragging && change.pressed && (change.position - down.position).getDistance() > viewConfiguration.touchSlop) {
                        dragging = true
                        onDragStart(down.position)
                    }
                    if (dragging) onDrag(change)
                }
                event.changes.forEach { it.consume() }
            } while (event.changes.any { it.pressed })
        } catch (e: CancellationException) { // the item left the screen mid-drag (e.g. its app was uninstalled)
            if (dragging) onCancel()
            throw e
        }
        if (dragging) onDrop()
    }
}
