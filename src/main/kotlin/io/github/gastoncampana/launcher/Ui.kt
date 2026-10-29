package io.github.gastoncampana.launcher

import android.app.SearchManager
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Intent
import android.os.Bundle
import android.provider.DocumentsContract
import android.provider.Settings
import android.util.SizeF
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectVerticalDragGestures
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
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
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.layer.GraphicsLayer
import androidx.compose.ui.graphics.layer.drawLayer
import androidx.compose.ui.graphics.rememberGraphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.font.Typeface
import androidx.compose.ui.text.input.ImeAction
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
import androidx.core.net.toUri
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.delay
import java.text.NumberFormat
import kotlinx.coroutines.launch
import kotlin.math.abs
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
private const val DOCK_LOOPS = 1000 // virtual dock pages, so it scrolls around in both directions
private const val DRAWER_COLUMNS = 5
private const val EDGE_HOLD_MS = 600L // holding a dragged item at a side this long flips the page
private const val PAUSE_SECONDS = 10 // before a poison app opens
private const val PAUSE_STEP_SECONDS = 5 // added for each time it was opened today

/** The one open menu or dialog; the activity clears it on home, back and when leaving. */
sealed interface Popup {
    data class DesktopMenu(val page: Int, val at: Offset) : Popup
    data class ItemMenu(val page: Int, val item: Item) : Popup
    data class DockMenu(val virtualPage: Int, val slot: Int) : Popup
    data class AppMenu(val id: AppId) : Popup
    data class TabMenu(val index: Int) : Popup
    class PickApp(val title: String, val onPick: (App?) -> Unit) : Popup
    class Rename(val title: String, val initial: String, val onDone: (String) -> Unit) : Popup
    data class Widgets(val page: Int) : Popup
    class Pause(val app: App, val open: () -> Unit) : Popup
    data object Backup : Popup
}

@Composable
fun LauncherScreen(l: Launcher) {
    val mover = remember { Mover(l) }
    MaterialTheme(colorScheme = dynamicDarkColorScheme(LocalContext.current)) {
        Box(Modifier.fillMaxSize()) {
            Home(l, mover)
            key(l.drawerResets) {
                AnimatedVisibility(l.drawerOpen, enter = slideInVertically { it / 4 } + fadeIn(), exit = fadeOut()) {
                    // While one of its apps is dragged to the home screen, the drawer stays (the gesture lives in it) but unseen.
                    Box(Modifier.graphicsLayer { alpha = if (mover.drag?.page == null && mover.drag != null) 0f else 1f }) { Drawer(l, mover) }
                }
            }
            when (val p = l.popup) {
                is Popup.PickApp -> AppPicker(l, p)
                is Popup.Rename -> NameDialog(l, p)
                is Popup.Widgets -> WidgetPicker(l, p.page)
                is Popup.Pause -> PauseScreen(l, p)
                Popup.Backup -> BackupDialog(l)
                else -> Unit // menus render next to what they belong to
            }
        }
    }
}

// --- Home: desktop pages, dock, search bar ---

@Composable
private fun Home(l: Launcher, mover: Mover) {
    val layout = l.layout
    val pages = layout.desktop.size
    val pager = key(pages, layout.defaultPage) { rememberPagerState(initialPage = layout.defaultPage) { pages } }
    SideEffect { mover.pager = pager }
    val haptic = LocalHapticFeedback.current
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
            pager.animateScrollToPage(next)
        }
    }
    Box(Modifier.fillMaxSize()) {
        Column(
            Modifier
                .fillMaxSize()
                .onVerticalSwipe(
                    up = { l.drawerOpen = true },
                    down = {
                        haptic.performHapticFeedback(HapticFeedbackType.GestureThresholdActivate)
                        l.expandNotifications()
                    },
                )
                .systemBarsPadding(),
        ) {
            HorizontalPager(
                pager,
                Modifier.weight(1f).onGloballyPositioned { mover.bounds = it.boundsInRoot() },
                beyondViewportPageCount = pages,
            ) { page -> DesktopPage(l, mover, page) }
            PageLine(pager)
            Dock(l, mover)
            SearchPill(l)
        }
        mover.drag?.let { d ->
            val size = with(LocalDensity.current) { d.size.toSize().toDpSize() }
            Box(Modifier.offset { (d.pointer - d.grab).round() }.size(size).drawBehind { drawLayer(d.layer) })
        }
    }
}

/**
 * An item being dragged, from desktop [page] or (null) from the drawer. Home draws [layer], the item's last frame,
 * under the finger.
 */
private class Drag(val page: Int?, val item: Item, val layer: GraphicsLayer, val size: IntSize, val grab: Offset) {
    var pointer by mutableStateOf(Offset.Zero) // root coordinates
}

/** Drags items onto the desktop page shown (the nearest free spot) or into an empty dock slot. */
private class Mover(private val l: Launcher) {
    var drag by mutableStateOf<Drag?>(null)
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

    fun drop() {
        val d = drag ?: return
        drag = null
        if (d.page == null) l.hideDrawer()
        val app = (d.item as? Item.Icon)?.target as? Target.App
        if (app != null && d.pointer in dock) return dropOnDock(d, app)
        if (d.pointer !in bounds) return // released over the search bar or page line: cancelled
        val page = pager?.currentPage ?: return
        val at = d.pointer - d.grab - bounds.topLeft
        val want = d.item.cell.copy(x = at.x / bounds.width * GRID, y = at.y / bounds.height * GRID)
        val before = l.layout
        l.update { it.moved(d.page, d.item, page, want) }
        if (l.layout === before) l.toast("No hay lugar en esta página")
    }

    // Only an empty slot: replacing a dock icon by accident would lose its swipes' setup with no undo.
    private fun dropOnDock(d: Drag, app: Target.App) {
        val page = dockPage()
        val slot = ((d.pointer.x - dock.left) / dock.width * DOCK_SIZE).toInt().coerceIn(0, DOCK_SIZE - 1)
        if (l.layout.dock[page][slot].tap != null) return l.toast("Ese lugar del dock está ocupado")
        l.update { layout ->
            layout.copy(
                dock = layout.dock.replaced(page) { it.replaced(slot) { icon -> icon.copy(tap = app) } },
                desktop = if (d.page == null) layout.desktop else layout.desktop.replaced(d.page) { it - d.item },
            )
        }
    }
}

@Composable
private fun DesktopPage(l: Launcher, mover: Mover, page: Int) {
    BoxWithConstraints(
        Modifier
            .fillMaxSize()
            .pointerInput(page) {
                detectTapGestures(
                    onDoubleTap = { l.open(l.layout.doubleTap) },
                    onLongPress = { at ->
                        // A widget that ignores touches lets the press through; the widget opens its own menu.
                        val point = Cell(at.x / size.width * GRID, at.y / size.height * GRID, 0f, 0f)
                        if (l.layout.desktop.getOrNull(page).orEmpty().none { it.cell.overlaps(point) }) l.popup = Popup.DesktopMenu(page, at)
                    },
                )
            },
    ) {
        val cell = DpSize(maxWidth / GRID, maxHeight / GRID)
        SideEffect { l.cellSize = cell }
        for (item in l.layout.desktop.getOrNull(page).orEmpty()) key(item) {
            val size = DpSize(cell.width * item.cell.w, cell.height * item.cell.h)
            val at = Modifier.offset(cell.width * item.cell.x, cell.height * item.cell.y).size(size)
            Movable(l, mover, page, item, { l.popup = Popup.ItemMenu(page, item) }, at) {
                when (item) {
                    is Item.Icon -> DesktopIcon(l, item)
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
    val haptic = LocalHapticFeedback.current
    val keyboard = LocalSoftwareKeyboardController.current // open when dragging from search results
    var coords by remember { mutableStateOf<LayoutCoordinates?>(null) }
    Box(
        modifier
            .onGloballyPositioned { coords = it }
            .drawWithContent {
                if (mover.drag?.item == item) return@drawWithContent // lifted: Home draws it under the finger
                layer.record { this@drawWithContent.drawContent() }
                drawLayer(layer)
            }
            .longPressDrag(
                item,
                onLongPress = {
                    haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                    onLongPress()
                },
                onDragStart = { grab ->
                    l.popup = null
                    keyboard?.hide()
                    coords?.let { mover.drag = Drag(page, item, layer, it.size, grab) }
                },
                onDrag = { at -> coords?.let { mover.drag?.pointer = it.localToRoot(at) } },
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
            MenuItem(l, "Agregar widget") { l.popup = Popup.Widgets(menu.page) }
            MenuItem(l, "Fondo de pantalla") {
                l.startIntent(Intent.createChooser(Intent(Intent.ACTION_SET_WALLPAPER), null), "el selector de fondos")
            }
            MenuItem(l, "App de inicio") { l.startIntent(Intent(Settings.ACTION_HOME_SETTINGS), "los ajustes") }
            MenuItem(l, "Copia del escritorio") { l.popup = Popup.Backup }
        }
    }
}

@Composable
private fun DesktopIcon(l: Launcher, item: Item.Icon) {
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Column(
            Modifier.clickable(interactionSource = null, indication = null) { l.open(item.target) },
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            TargetIcon(l, item.target)
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
    DropdownMenu(l.popup == Popup.ItemMenu(page, item), { l.popup = null }) {
        val app = (item as? Item.Icon)?.let { l.appOf(it.target) }
        if (app != null) MenuItem(l, "Info de la app") { l.showInfo(app) }
        if (item is Item.Widget && item.widgetId != 0) MenuItem(l, "Volver a configurar") { l.unbind(page, item) }
        MenuItem(l, "Quitar") { l.remove(page, item) }
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
    val pager = key(pages) { rememberPagerState(initialPage = pages * DOCK_LOOPS / 2) { pages * DOCK_LOOPS } }
    SideEffect { mover.dockPage = { pager.currentPage % pages } }
    HorizontalPager(
        pager,
        Modifier.fillMaxWidth().height(DOCK_HEIGHT).onGloballyPositioned { mover.dock = it.boundsInRoot() },
        beyondViewportPageCount = 1,
    ) { virtualPage ->
        Row(Modifier.fillMaxSize()) {
            l.layout.dock[virtualPage % pages].forEachIndexed { slot, icon -> DockSlot(l, virtualPage, slot, icon, Modifier.weight(1f)) }
        }
    }
}

@Composable
private fun DockSlot(l: Launcher, virtualPage: Int, slot: Int, icon: DockIcon, modifier: Modifier) {
    val page = virtualPage % l.layout.dock.size
    Box(
        modifier
            .fillMaxHeight()
            .pointerInput(icon) {
                var total = 0f
                detectVerticalDragGestures(
                    onDragStart = { total = 0f },
                    onDragEnd = {
                        // Without a target of its own, a swipe does what it does elsewhere on the home screen.
                        when (swipeOf(0f, total, SWIPE_MIN.toPx())) {
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
                indication = null,
                onClick = { l.open(icon.tap) },
                onLongClick = { l.popup = Popup.DockMenu(virtualPage, slot) },
            ),
        contentAlignment = Alignment.Center,
    ) {
        icon.tap?.let { TargetIcon(l, it) }
        DropdownMenu(l.popup == Popup.DockMenu(virtualPage, slot), { l.popup = null }) {
            fun pick(title: String, set: (DockIcon, Target?) -> DockIcon) {
                l.popup = Popup.PickApp(title) { app -> l.updateDock(page, slot) { set(it, app?.let { a -> Target.App(a.id) }) } }
            }
            MenuItem(l, "Tocar: ${l.labelOf(icon.tap)}") { pick("Al tocar") { d, t -> d.copy(tap = t) } }
            MenuItem(l, "Deslizar ↑: ${l.labelOf(icon.up)}") { pick("Al deslizar hacia arriba") { d, t -> d.copy(up = t) } }
            MenuItem(l, "Deslizar ↓: ${l.labelOf(icon.down)}") { pick("Al deslizar hacia abajo") { d, t -> d.copy(down = t) } }
            l.appOf(icon.tap)?.let { app -> MenuItem(l, "Info de la app") { l.showInfo(app) } }
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
        AssistantGlyph(Modifier.clip(CircleShape).clickable { l.startIntent(Intent(Intent.ACTION_VOICE_COMMAND), "el asistente") }.padding(4.dp))
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
                    l.startIntent(Intent.makeMainSelectorActivity(Intent.ACTION_MAIN, Intent.CATEGORY_APP_CALENDAR), "el calendario")
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

// --- Drawer: search, bubble tabs, 5-column grid ---

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
    val results = if (searching) search(l.apps, query) { it.label } else emptyList()
    fun appsOf(tab: Int) = tabs.getOrNull(tab)?.let { tabApps(it, tabs, l.apps, l.find, l.layout.hidden) } ?: l.apps

    // Pulling down past the top of the grid closes the drawer, as does flicking down from there.
    val closePx = with(LocalDensity.current) { SWIPE_MIN.toPx() }
    val pullToClose = remember {
        object : NestedScrollConnection {
            private var pulled = 0f
            override fun onPostScroll(consumed: Offset, available: Offset, source: NestedScrollSource): Offset {
                pulled = if (source == NestedScrollSource.UserInput && available.y > 0) pulled + available.y else 0f
                if (pulled > closePx) l.drawerOpen = false
                return Offset.Zero
            }

            // Runs when every drag ends, so the next gesture starts from zero.
            override suspend fun onPreFling(available: Velocity): Velocity {
                if (pulled > 0 && available.y > 0) l.drawerOpen = false
                pulled = 0f
                return Velocity.Zero
            }
        }
    }

    Column(
        Modifier
            .fillMaxSize()
            .background(Color.Black)
            .onVerticalSwipe(down = { l.drawerOpen = false })
            .systemBarsPadding()
            .imePadding(),
    ) {
        SearchField(query, { query = it }, focus) { results.firstOrNull()?.let(l::start) }
        if (searching) {
            val grid = rememberLazyGridState()
            Box(Modifier.weight(1f)) {
                val sum = calc(query)
                AppGrid(
                    l,
                    mover,
                    results,
                    grid,
                    pullToClose,
                    header = { sum?.let { item(span = { GridItemSpan(maxLineSpan) }) { CalcResult(it) } } },
                ) {
                    item(span = { GridItemSpan(maxLineSpan) }) { SearchProviders(l, query) }
                }
                FastScroller(grid, Modifier.align(Alignment.CenterEnd))
            }
        } else {
            Tabs(l, pager.currentPage) { scope.launch { pager.animateScrollToPage(it) } }
            val grids = remember(tabs.size) { List(tabs.size) { LazyGridState() } }
            Box(Modifier.weight(1f)) {
                HorizontalPager(pager, Modifier.fillMaxSize()) { page -> AppGrid(l, mover, appsOf(page), grids[page], pullToClose) }
                // One scroller over the pages, so it stays put while swiping between tabs.
                grids.getOrNull(pager.currentPage)?.let { FastScroller(it, Modifier.align(Alignment.CenterEnd)) }
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
    pullToClose: NestedScrollConnection,
    header: LazyGridScope.() -> Unit = {},
    footer: LazyGridScope.() -> Unit = {},
) =
    LazyVerticalGrid(
        GridCells.Fixed(DRAWER_COLUMNS),
        Modifier.fillMaxSize().nestedScroll(pullToClose),
        grid,
        contentPadding = PaddingValues(start = 8.dp, end = 8.dp, top = 25.dp, bottom = 16.dp),
    ) {
        header()
        items(apps, key = { it.id }) { app -> DrawerApp(l, mover, app) }
        footer()
    }

/** The result of a search that is a sum; a tap copies it. */
@Composable
private fun CalcResult(value: Double) {
    val context = LocalContext.current
    val text = remember(value) {
        // Scientific notation where ten decimals would round to 0 or the digits run off the screen.
        if (value != 0.0 && abs(value) < 1e-6 || abs(value) >= 1e15) "%.6g".format(value)
        else NumberFormat.getNumberInstance().apply { maximumFractionDigits = 10 }.format(value)
    }
    Row(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = 8.dp, vertical = 8.dp)
            .clip(RoundedCornerShape(16.dp))
            .background(TAB_OFF)
            .clickable { context.getSystemService(ClipboardManager::class.java).setPrimaryClip(ClipData.newPlainText("Resultado", text)) }
            .padding(horizontal = 20.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text("= $text", Modifier.weight(1f), color = Color.White, fontSize = 28.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
        Text("Copiar", color = TAB_OFF_TEXT, fontSize = 14.sp)
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
                    if (query.isEmpty()) Text("Buscar aplicaciones…", color = HINT, fontSize = 18.sp)
                    field()
                }
            },
        )
    }
}

@Composable
private fun Tabs(l: Launcher, selected: Int, onSelect: (Int) -> Unit) {
    LazyRow(
        Modifier.padding(top = 11.dp),
        contentPadding = PaddingValues(horizontal = 24.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        itemsIndexed(l.layout.tabs) { i, tab ->
            Box {
                Bubble(tab.name, i == selected, Modifier.combinedClickable(onClick = { onSelect(i) }, onLongClick = { l.popup = Popup.TabMenu(i) }))
                DropdownMenu(l.popup == Popup.TabMenu(i), { l.popup = null }) {
                    MenuItem(l, "Renombrar") {
                        l.popup = Popup.Rename("Renombrar pestaña", tab.name) { name ->
                            l.update { it.copy(tabs = it.tabs.replaced(i) { t -> t.copy(name = name) }) }
                        }
                    }
                    MenuItem(l, "Nueva pestaña") {
                        l.popup = Popup.Rename("Nueva pestaña", "") { name ->
                            l.update { it.copy(tabs = it.tabs + Tab(name, emptySet(), hideApps = true)) }
                        }
                    }
                    if (tab.apps != null) {
                        MenuItem(l, "Eliminar") {
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
            .height(40.dp)
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
private fun DrawerApp(l: Launcher, mover: Mover, app: App) {
    // Taking the app out of the tab shown removes it from under its menu; the menu must not reopen when it shows up again.
    DisposableEffect(app.id) { onDispose { if (l.popup == Popup.AppMenu(app.id)) l.popup = null } }
    val item = remember(app.id, app.label) { Item.Icon(Target.App(app.id), app.label, Cell(0f, 0f)) } // what a drag drops
    Movable(l, mover, null, item, { l.popup = Popup.AppMenu(app.id) }, Modifier) {
        Column(
            Modifier
                .fillMaxWidth()
                .height(129.dp)
                .clickable(interactionSource = null, indication = null) { l.start(app) },
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            AppImage(app, DRAWER_ICON)
            Text(app.label, Modifier.padding(top = 6.dp, start = 2.dp, end = 2.dp), style = LABEL, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        DropdownMenu(l.popup == Popup.AppMenu(app.id), { l.popup = null }) {
            MenuItem(l, "Agregar al escritorio") { l.addToDesktop(Target.App(app.id), app.label) }
            // Tab toggles leave the menu open so several can be set at once.
            l.layout.tabs.forEachIndexed { i, tab ->
                val members = tab.apps ?: return@forEachIndexed
                val inTab = members.any { l.find(it) == app }
                DropdownMenuItem({ Text("${if (inTab) "✓" else "○"} ${tab.name}") }, onClick = {
                    l.update {
                        it.copy(tabs = it.tabs.replaced(i) { t -> t.copy(apps = if (inTab) members.filterTo(HashSet()) { id -> l.find(id) != app } else members + app.id) })
                    }
                })
            }
            val hidden = l.layout.hidden.any { l.find(it) == app }
            MenuItem(l, if (hidden) "Mostrar en el cajón" else "Ocultar del cajón") {
                l.update { it.copy(hidden = if (hidden) it.hidden.filterTo(HashSet()) { id -> l.find(id) != app } else it.hidden + app.id) }
            }
            val poison = l.isPoison(app)
            MenuItem(l, if (poison) "Quitar de venenos" else "Marcar como veneno") {
                l.update { it.copy(poisons = if (poison) it.poisons.filterTo(HashSet()) { id -> l.find(id) != app } else it.poisons + app.id) }
            }
            MenuItem(l, "Info de la app") { l.showInfo(app) }
        }
    }
}

@Composable
private fun FastScroller(grid: LazyGridState, modifier: Modifier) {
    // In pixels: counting visible items made the thumb jump, as that count changes a whole row at a time.
    val info = grid.layoutInfo
    val row = info.visibleItemsInfo.firstOrNull()?.size?.height ?: return
    val rows = (info.totalItemsCount + DRAWER_COLUMNS - 1) / DRAWER_COLUMNS
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
                    scope.launch { grid.scrollToItem(target / row * DRAWER_COLUMNS, target % row) }
                }
            },
    ) {
        val scrolled = grid.firstVisibleItemIndex / DRAWER_COLUMNS * row + grid.firstVisibleItemScrollOffset
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
        Text("Buscar con", color = Color.White, fontSize = 16.sp)
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
private fun TargetIcon(l: Launcher, target: Target) {
    val app = l.appOf(target)
    when (target) {
        is Target.App -> AppImage(app)
        is Target.Shortcut -> Box(Modifier.size(ICON)) {
            val icon = remember(target) { l.shortcutIcon(target) }
            if (icon != null) Image(icon, target.label, Modifier.fillMaxSize()) else AppImage(app)
            // The publishing app's badge, as Nova draws it.
            if (app != null) Image(app.icon, null, Modifier.size(ICON * 0.4f).align(Alignment.BottomEnd).offset(4.dp, 2.dp))
        }
    }
}

@Composable
private fun AppImage(app: App?, size: Dp = ICON) {
    if (app == null) {
        Box(Modifier.size(size).clip(CircleShape).background(Color.White.copy(alpha = 0.15f)))
    } else {
        // Apps paused by Digital Wellbeing are greyed out, as in Nova.
        Image(app.icon, app.label, Modifier.size(size), alpha = if (app.suspended) 0.6f else 1f, colorFilter = if (app.suspended) GREY else null)
    }
}

/** A menu entry that closes the menu, then acts (the action may open another popup). */
@Composable
private fun MenuItem(l: Launcher, text: String, onClick: () -> Unit) = DropdownMenuItem(text = { Text(text) }, onClick = {
    l.popup = null
    onClick()
})

@Composable
private fun PickerDialog(l: Launcher, title: String, query: String, onQuery: (String) -> Unit, rows: LazyListScope.() -> Unit) {
    Dialog({ l.popup = null }) {
        Surface(shape = RoundedCornerShape(28.dp), color = MaterialTheme.colorScheme.surfaceContainerHigh) {
            Column(Modifier.padding(16.dp).heightIn(max = 560.dp)) {
                Text(title, Modifier.padding(start = 4.dp, bottom = 8.dp), style = MaterialTheme.typography.titleMedium)
                OutlinedTextField(query, onQuery, Modifier.fillMaxWidth(), placeholder = { Text("Buscar") }, singleLine = true)
                LazyColumn(content = rows)
            }
        }
    }
}

@Composable
private fun AppPicker(l: Launcher, p: Popup.PickApp) {
    var query by remember { mutableStateOf("") }
    PickerDialog(l, p.title, query, { query = it }) {
        fun choose(app: App?) {
            l.popup = null
            p.onPick(app)
        }
        item { PickerRow(null, "Nada") { choose(null) } }
        items(search(l.apps, query) { it.label }, key = { it.id }) { app -> PickerRow(app.icon, app.label) { choose(app) } }
    }
}

@Composable
private fun WidgetPicker(l: Launcher, page: Int) {
    val pm = LocalContext.current.packageManager
    val providers = remember {
        l.widgetProviders()
            .map { it to "${pm.getApplicationLabel(it.activityInfo.applicationInfo)} · ${it.loadLabel(pm)}" }
            .sortedBy { fold(it.second) }
    }
    var query by remember { mutableStateOf("") }
    PickerDialog(l, "Agregar widget", query, { query = it }) {
        items(search(providers, query) { it.second }) { (info, label) ->
            PickerRow(l.appOf(Target.Shortcut(info.provider.packageName, "", ""))?.icon, label) {
                l.popup = null
                l.addWidget(page, info)
            }
        }
    }
}

@Composable
private fun PickerRow(icon: ImageBitmap?, label: String, onClick: () -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.size(40.dp)) { icon?.let { Image(it, null, Modifier.fillMaxSize()) } }
        Spacer(Modifier.width(16.dp))
        Text(label)
    }
}

@Composable
private fun NameDialog(l: Launcher, p: Popup.Rename) {
    var name by remember { mutableStateOf(p.initial) }
    AlertDialog(
        onDismissRequest = { l.popup = null },
        title = { Text(p.title) },
        text = { OutlinedTextField(name, { name = it }, singleLine = true) },
        confirmButton = {
            TextButton({
                l.popup = null
                p.onDone(name.trim())
            }, enabled = name.isNotBlank()) { Text("Listo") }
        },
        dismissButton = { TextButton({ l.popup = null }) { Text("Cancelar") } },
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
            Text(if (left > 0) "Respirá un momento" else "¿La abrís igual?", Modifier.padding(top = 8.dp), color = TAB_OFF_TEXT, fontSize = 18.sp)
            val today = when (opens) {
                0 -> "Hoy no la abriste"
                1 -> "Hoy la abriste 1 vez"
                else -> "Hoy la abriste $opens veces"
            }
            Text(today, Modifier.padding(top = 4.dp), color = HINT, fontSize = 14.sp)
            Text("Cada vez que la abrís en el día, la espera suma $PAUSE_STEP_SECONDS segundos", color = HINT, fontSize = 14.sp, textAlign = TextAlign.Center)
            Spacer(Modifier.height(48.dp))
            Button({ l.popup = null }, colors = ButtonDefaults.buttonColors(containerColor = ACCENT, contentColor = Color.Black)) { Text("No, volver") }
            TextButton(
                {
                    l.popup = null
                    l.countOpen(p.app)
                    p.open()
                },
                Modifier.padding(top = 8.dp),
                enabled = left == 0,
            ) { Text(if (left > 0) "Abrir en $left" else "Abrir igual") }
        }
    }
}

@Composable
private fun BackupDialog(l: Launcher) = AlertDialog(
    onDismissRequest = { l.popup = null },
    title = { Text("Copia del escritorio") },
    text = {
        Column {
            Text(
                "Guardá en un archivo el escritorio, el dock, las pestañas y los venenos, o restauralos desde uno. " +
                    "Restaurar reemplaza lo que hay; los widgets que falten se vuelven a configurar tocándolos.",
            )
            Text("Copia automática semanal", Modifier.padding(top = 16.dp), style = MaterialTheme.typography.titleSmall)
            val folder = l.backupFolder
            Text(
                folder?.let { "En ${DocumentsContract.getTreeDocumentId(it).substringAfter(':').ifEmpty { "la carpeta elegida" }}" } ?: "Apagada",
                color = TAB_OFF_TEXT,
            )
            Row {
                TextButton({
                    l.popup = null
                    l.chooseBackupFolder()
                }) { Text(if (folder == null) "Elegir carpeta" else "Cambiar carpeta") }
                if (folder != null) TextButton(l::stopAutoBackup) { Text("Apagar") }
            }
        }
    },
    confirmButton = {
        Row {
            TextButton({
                l.popup = null
                l.restoreBackup()
            }) { Text("Restaurar") }
            TextButton({
                l.popup = null
                l.saveBackup()
            }) { Text("Guardar") }
        }
    },
    dismissButton = { TextButton({ l.popup = null }) { Text("Cancelar") } },
)

/** Shown by [PinActivity] over whatever app asked to add a shortcut. */
@Composable
fun PinDialog(label: String, from: String, onCancel: () -> Unit, onAdd: () -> Unit) {
    MaterialTheme(colorScheme = dynamicDarkColorScheme(LocalContext.current)) {
        AlertDialog(
            onDismissRequest = onCancel,
            title = { Text("Agregar al escritorio") },
            text = { Text("«$label», de $from") },
            confirmButton = { TextButton(onAdd) { Text("Agregar") } },
            dismissButton = { TextButton(onCancel) { Text("Cancelar") } },
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
 * A long press, then optionally a drag past the touch slop ([onDragStart] gets the press position, [onDrag] each new one,
 * both local). It watches the Initial pass, so it wins over a hosted View (a widget), before the View gets the touch.
 */
private fun Modifier.longPressDrag(
    key: Any?,
    onLongPress: () -> Unit,
    onDragStart: (Offset) -> Unit,
    onDrag: (Offset) -> Unit,
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
                event.changes.firstOrNull { it.id == down.id && it.pressed }?.let { change ->
                    if (!dragging && (change.position - down.position).getDistance() > viewConfiguration.touchSlop) {
                        dragging = true
                        onDragStart(down.position)
                    }
                    if (dragging) onDrag(change.position)
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
