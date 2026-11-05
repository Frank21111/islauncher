package io.github.gastoncampana.launcher

import android.app.StatusBarManager
import android.appwidget.AppWidgetHost
import android.appwidget.AppWidgetHostView
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProviderInfo
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.SharedPreferences
import android.content.pm.ApplicationInfo
import android.content.pm.LauncherActivityInfo
import android.content.pm.LauncherApps
import android.content.pm.PackageManager
import android.content.pm.ShortcutInfo
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Color
import android.icu.text.DecimalFormatSymbols
import android.icu.util.ULocale
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Process
import android.os.UserHandle
import android.os.UserManager
import android.provider.DocumentsContract
import android.telephony.TelephonyManager
import android.text.format.DateFormat
import android.util.Log
import android.view.ViewGroup
import android.widget.RemoteViews
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.addCallback
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.core.content.edit
import androidx.core.net.toUri
import androidx.core.graphics.drawable.toBitmap
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.io.IOException
import java.text.SimpleDateFormat
import java.time.LocalDate
import java.util.Date
import java.util.Locale
import kotlin.math.roundToInt

private const val TAG = "Launcher"
private const val LAYOUT_KEY = "layout"
private const val LAUNCHED_KEY = "launched" // in prefs "launcher": set on the first launch, for the guide (opensGuide)
private const val HOST_ID = 1024
private const val REQUEST_CONFIGURE = 1
private const val FOLDER_ACCESS = Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION
private const val MAX_BACKUP_BYTES = 1 shl 20 // a layout is a few KB; anything bigger is the wrong file
private const val TRACKED_KEY = "tracked" // in prefs "usage": the day opens began to be counted
const val DAY_MS = 86_400_000.0

/** [installed]: the day it was first installed (days since 1970), or null when unknown, as for a work profile's app. */
class App(val id: AppId, val label: String, val icon: ImageBitmap, val info: LauncherActivityInfo, val suspended: Boolean, val installed: Double?) {
    /** Can be uninstalled: not a system app, updated or not. */
    val removable get() = info.applicationInfo.flags and (ApplicationInfo.FLAG_SYSTEM or ApplicationInfo.FLAG_UPDATED_SYSTEM_APP) == 0
}

/** One of an app's own shortcuts, as its menu lists it; [icon] is null when the app gives it none. */
class AppShortcut(val info: ShortcutInfo, val icon: ImageBitmap?) {
    val target = Target.Shortcut(info.`package`, info.id, info.label)
}

/** What a shortcut shows: its short label, else its long one. */
val ShortcutInfo.label get() = (shortLabel ?: longLabel ?: "").toString()

/** The saved layout, shared by the home screen and [PinActivity]. */
object Layouts {
    private lateinit var prefs: SharedPreferences
    var current by mutableStateOf(Layout())
        private set

    fun init(context: Context) {
        if (::prefs.isInitialized) return
        prefs = context.applicationContext.getSharedPreferences("launcher", Context.MODE_PRIVATE)
        current = load()
    }

    val hasCorruptCopy get() = prefs.contains("$LAYOUT_KEY.corrupt")

    /** Whether this launch opens the guide by itself ([opensGuide]): true once at most, as it notes the first launch. */
    fun guideOnLaunch(): Boolean {
        val launchedBefore = prefs.contains(LAUNCHED_KEY)
        if (!launchedBefore) prefs.edit { putBoolean(LAUNCHED_KEY, true) }
        return opensGuide(prefs.contains(LAYOUT_KEY), launchedBefore)
    }

    fun update(f: (Layout) -> Layout) {
        val next = f(current)
        val json = next.toJson() // first: if it throws, screen and saved layout still agree
        current = next
        prefs.edit { putString(LAYOUT_KEY, json) }
    }

    private fun load(): Layout {
        val json = prefs.getString(LAYOUT_KEY, null) ?: return Layout()
        return try {
            parseLayout(json)
        } catch (e: Exception) {
            // Keep the unreadable copy: the next save would otherwise overwrite it for good.
            Log.e(TAG, "Unreadable layout, starting empty", e)
            prefs.edit { putString("$LAYOUT_KEY.corrupt", json) }
            Layout()
        }
    }
}

/** Where a shortcut's icon is kept: shortcuts cannot be queried until this is the default home app. */
fun Context.shortcutIconFile(t: Target.Shortcut) =
    File(filesDir, "shortcut-" + "${t.pkg}-${t.id}".replace(Regex("[^A-Za-z0-9._-]"), "_").take(242) + ".png") // 255 bytes at most

fun Context.toast(text: String) = Toast.makeText(this, text, Toast.LENGTH_SHORT).show()

/**
 * Puts shortcut [info] on the main page, or the first page with room, once [pin] has pinned it (false: it was not, and a toast says so),
 * and keeps its icon. Used by [PinActivity] and by app menus. This runs in the home screen's process: an uncaught throw
 * would take it down too.
 */
fun Context.addShortcut(info: ShortcutInfo, pin: () -> Boolean) {
    Layouts.init(this)
    val layout = Layouts.current
    val page = (listOf(layout.defaultPage) + layout.desktop.indices).firstOrNull { layout.freeCell(it, 1f, 1f) != null }
        ?: return toast(getString(R.string.pin_no_room))
    val label = info.label
    val pinned = try {
        pin()
    } catch (e: RuntimeException) { // answered elsewhere, its sender or app is gone, or no longer the home app
        Log.w(TAG, "Pin request failed", e)
        false
    }
    if (!pinned) return toast(getString(R.string.pin_add_failed, label))
    val target = Target.Shortcut(info.`package`, info.id, label)
    try {
        val icon = getSystemService(LauncherApps::class.java).getShortcutIconDrawable(info, resources.displayMetrics.densityDpi)
        val size = (56 * resources.displayMetrics.density).toInt()
        icon?.let { shortcutIconFile(target).outputStream().use { out -> it.toBitmap(size, size).compress(Bitmap.CompressFormat.PNG, 100, out) } }
    } catch (e: Exception) { // the app's icon stands in
        Log.w(TAG, "Cannot save the icon of $label", e)
    }
    Layouts.update { l -> l.copy(desktop = l.desktop.replaced(page) { it + Item.Icon(target, label, l.freeCell(page, 1f, 1f)!!) }) }
    toast(getString(R.string.pin_added))
}

class Launcher : ComponentActivity() {
    private val launcherApps by lazy { getSystemService(LauncherApps::class.java) }
    private val users by lazy { getSystemService(UserManager::class.java) }
    private val widgetManager by lazy { AppWidgetManager.getInstance(this) }
    private val host by lazy { AppWidgetHost(this, HOST_ID) }
    val userSerial by lazy { users.getSerialNumberForUser(Process.myUserHandle()) }

    // Only touched from [loader], which runs one task at a time.
    private val icons = HashMap<AppId, ImageBitmap>()
    private val installTimes = HashMap<String, Long>() // by package, main profile only; 0 when not found
    private val loader = Dispatchers.Default.limitedParallelism(1)
    private val widgetViews = HashMap<Int, AppWidgetHostView>()

    var apps by mutableStateOf(emptyList<App>())
        private set
    val find: (AppId) -> App? by derivedStateOf { finder(apps) { it.id } }
    val layout get() = Layouts.current
    var drawerOpen by mutableStateOf(false)
    var focusSearch by mutableStateOf(false) // drawer opened from the search bar
    var popup by mutableStateOf<Popup?>(null)
    var settingsOpen by mutableStateOf(false) // kept when leaving, so the file pickers it opens return to it
    var welcome = false // Ajustes opened by itself on its guide (a fresh install's first launch); cleared when it closes
    var homePresses by mutableIntStateOf(0) // home pressed while already on the home screen
        private set
    var drawerResets by mutableIntStateOf(0) // bumped to close the drawer without its exit animation
        private set
    var dockResets by mutableIntStateOf(0) // bumped to put the dock back on its main page
        private set
    var today by mutableStateOf("")
        private set
    var resumes by mutableIntStateOf(0) // bumped on each return, so Ajustes reads again what may have changed meanwhile
        private set
    var cellSize = DpSize(90.dp, 150.dp) // measured by the desktop; sizes new widgets
    /** ISO code of the phone's country, upper case: its SIM's, else its network's, else the locale's; read on each return home. */
    var country = ""
        private set

    private val packageCallback = object : LauncherApps.Callback() {
        override fun onPackageAdded(packageName: String, user: UserHandle) = reload(packageName)
        override fun onPackageRemoved(packageName: String, user: UserHandle) {
            if (uninstalling == packageName to user) {
                uninstalling = null
                val without = { layout: Layout -> layout.withoutApp(packageName, users.getSerialNumberForUser(user), userSerial) }
                update(without)
                // A thrown icon of it does not come back on Deshacer either (an icon holds no widget id to free).
                thrown?.let { t -> if (without(Layout(desktop = listOf(listOf(t.item)))).desktop[0].isEmpty()) thrown = null }
            }
            reload(packageName)
        }
        override fun onPackageChanged(packageName: String, user: UserHandle) = reload(packageName)
        override fun onPackagesAvailable(packageNames: Array<String>, user: UserHandle, replacing: Boolean) = reload(*packageNames)
        override fun onPackagesUnavailable(packageNames: Array<String>, user: UserHandle, replacing: Boolean) = reload(*packageNames)
        override fun onPackagesSuspended(packageNames: Array<String>, user: UserHandle) = reload()
        override fun onPackagesUnsuspended(packageNames: Array<String>, user: UserHandle) = reload()
    }

    private val dateReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) = updateToday()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge(SystemBarStyle.dark(Color.TRANSPARENT), SystemBarStyle.dark(Color.TRANSPARENT))
        Layouts.init(this)
        BlockStore.init(this)
        onBackPressedDispatcher.addCallback(this) { closeAll() } // a launcher never finishes on back
        launcherApps.registerCallback(packageCallback)
        val dates = IntentFilter().apply {
            addAction(Intent.ACTION_DATE_CHANGED)
            addAction(Intent.ACTION_TIME_CHANGED)
            addAction(Intent.ACTION_TIMEZONE_CHANGED)
        }
        registerReceiver(dateReceiver, dates, RECEIVER_NOT_EXPORTED)
        // Free widget ids that no layout item holds (e.g. a setup interrupted by a crash), unless an unreadable
        // layout was set aside: its widgets would be lost for good.
        if (!Layouts.hasCorruptCopy) {
            val used = layout.desktop.flatten().filterIsInstance<Item.Widget>().map { it.widgetId }.toSet()
            host.appWidgetIds.filter { it !in used }.forEach(host::deleteAppWidgetId)
        }
        reload()
        uses = useScores.all.mapNotNull { (key, value) ->
            val (score, since) = value.toString().split(' ').mapNotNull(String::toDoubleOrNull).takeIf { it.size == 2 } ?: return@mapNotNull null
            key to (score to since)
        }.toMap()
        // Written once. The store predates this key, so counting may have begun before today: see countedSince.
        tracked = useScores.getString(TRACKED_KEY, null)?.toDoubleOrNull()
            ?: countedSince(uses.values.map { it.second }, now()).also { useScores.edit { putString(TRACKED_KEY, "$it") } }
        backupFolder = backups.getString("folder", null)?.toUri()
        welcome = Layouts.guideOnLaunch()
        settingsOpen = welcome
        setContent { LauncherScreen(this) }
    }

    override fun onStart() {
        super.onStart()
        host.startListening()
        autoBackup()
        guardBlocks() // as Home comes back: the blocking service may have been turned off meanwhile
        val phone = getSystemService(TelephonyManager::class.java) // neither country needs a permission
        country = listOfNotNull(phone?.simCountryIso, phone?.networkCountryIso, Locale.getDefault().country)
            .firstOrNull { it.isNotEmpty() }.orEmpty().uppercase(Locale.ROOT)
    }

    override fun onResume() {
        super.onResume()
        updateToday() // the midnight broadcast is missed while the screen is off
        resumes++
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        val alreadyHome = hasWindowFocus() && intent.flags and Intent.FLAG_ACTIVITY_BROUGHT_TO_FRONT == 0
        // In-window overlays (Ajustes, the widget picker) keep the focus: home only closes them, like a dialog.
        if (alreadyHome && !drawerOpen && !settingsOpen && popup == null) homePresses++
        settingsOpen = false
        closeAll()
    }

    override fun onStop() {
        super.onStop()
        host.stopListening()
        settle() // leaving ends the undo of a thrown item
        closeAll() // come back to a clean home screen after opening an app
        hideDrawer() // gone on return, rather than fading out then
        dockResets++ // and with the dock on its main page
    }

    override fun onDestroy() {
        launcherApps.unregisterCallback(packageCallback)
        unregisterReceiver(dateReceiver)
        super.onDestroy()
    }

    fun closeAll() {
        drawerOpen = false
        popup = null
    }

    fun hideDrawer() {
        drawerOpen = false
        drawerResets++
    }

    /** The decimal mark of the phone's [country] ("und_AR" is es_AR: ','). */
    val countryDecimal: Char get() = DecimalFormatSymbols.getInstance(ULocale.addLikelySubtags(ULocale("und_$country"))).decimalSeparator

    /** The decimal mark search reads and shows numbers with: the one picked in Ajustes, else the country's. */
    val decimal get() = layout.decimal ?: countryDecimal

    fun openSearch() {
        focusSearch = true
        drawerOpen = true
    }

    private fun updateToday() {
        today = SimpleDateFormat(DateFormat.getBestDateTimePattern(Locale.getDefault(), "dMMM"), Locale.getDefault()).format(Date())
    }

    // --- Apps ---

    /** Re-reads the app list, reusing cached icons except for the [changed] packages. */
    private fun reload(vararg changed: String) {
        lifecycleScope.launch {
            val list = withContext(loader) {
                try {
                    loadApps(changed.toSet())
                } catch (e: RuntimeException) { // a profile or package vanished mid-read; keep the current list
                    Log.w(TAG, "App list reload failed", e)
                    null
                }
            }
            if (list != null) apps = list
        }
    }

    private fun loadApps(changed: Set<String>): List<App> {
        icons.keys.removeAll { it.substringBefore('/') in changed }
        installTimes.keys.removeAll { it in changed }
        val size = (56 * resources.displayMetrics.density).toInt()
        return launcherApps.profiles
            .flatMap { launcherApps.getActivityList(null, it) }
            .map { info ->
                val id = "${info.componentName.flattenToString()}#${users.getSerialNumberForUser(info.user)}"
                val icon = icons.getOrPut(id) { info.getBadgedIcon(0).toBitmap(size, size).asImageBitmap() }
                // A binder call, read as this user: a work profile's copy would get the main profile's date, or none.
                val installed = if (info.user != Process.myUserHandle()) 0 else installTimes.getOrPut(info.componentName.packageName) { info.firstInstallTime }
                App(id, info.label.toString(), icon, info, info.applicationInfo.flags and ApplicationInfo.FLAG_SUSPENDED != 0, installed.takeIf { it > 0 }?.div(DAY_MS))
            }
            .sortedBy { fold(it.label) }
    }

    fun appOf(target: Target?): App? = when (target) {
        is Target.App -> find(target.id)
        is Target.Shortcut -> find("${target.pkg}/#$userSerial") // any activity of the package
        null -> null
    }

    fun labelOf(target: Target?): String = when (target) {
        is Target.App -> appOf(target)?.label ?: getString(R.string.target_not_installed)
        is Target.Shortcut -> target.label.ifEmpty { target.id }
        null -> getString(R.string.target_none)
    }

    fun open(target: Target?) {
        when (target) {
            null -> Unit
            is Target.App -> appOf(target)?.let(::start) ?: toast(getString(R.string.app_unavailable))
            is Target.Shortcut -> pauseIfPoison(appOf(target)) { startShortcut(target) }
        }
    }

    private fun startShortcut(target: Target.Shortcut) = try {
        launcherApps.startShortcut(target.pkg, target.id, null, null, Process.myUserHandle())
        true
    } catch (e: RuntimeException) { // not the default home app, shortcut removed, or user locked
        Log.w(TAG, "Cannot start shortcut ${target.pkg}/${target.id}", e)
        toast(
            if (!launcherApps.hasShortcutHostPermission()) getString(R.string.shortcut_needs_home)
            else getString(R.string.shortcut_open_failed, labelOf(target)),
        )
        false
    }

    fun start(app: App) = pauseIfPoison(app) {
        orToast(app.label) { launcherApps.startMainActivity(app.info.componentName, app.info.user, null, null) }
    }


    // By package: an app that switches launcher aliases (Mercado Pago) stays poison under either activity.
    fun isPoison(app: App) = layout.poisons.any { find(it)?.let(::packageOf) == packageOf(app) }

    fun packageOf(app: App) = app.info.componentName.packageName to app.info.user

    /**
     * Opens a poison app (or one of its shortcuts) only through the pause screen; [open] says whether it started. An
     * app a block holds does not open at all, and that is no open: never counted.
     */
    private fun pauseIfPoison(app: App?, open: () -> Boolean) {
        if (blocked(app?.info?.componentName?.packageName)) return
        if (app != null && isPoison(app)) {
            popup = Popup.Pause(app) { if (open()) countUse(app) } // only its day: a poison gains no rank
            return
        }
        if (open()) app?.let(::countUse) // only once it started, so a failed open never counts
    }

    /** Whether a running block holds [pkg] ([BlockStore]); a toast says until when. The blocking service would send it home anyway. */
    private fun blocked(pkg: String?): Boolean {
        val until = pkg?.let { BlockStore.appBlockedUntil(it) } ?: return false
        toast(getString(R.string.blocked_app_until, untilText(until)))
        return true
    }

    // How much each app is used, as "<package>#<user>" to "<score> <day>" (see [decayed]), so search can put it first
    // among equally good matches. Not in the layout, so backups leave it out; faded entries are dropped on a write (see [kept]).
    private val useScores by lazy { getSharedPreferences("usage", MODE_PRIVATE) }
    // Not snapshot state: results shown must not reorder under the app opening from them. The next query reads it.
    private var uses = emptyMap<String, Pair<Double, Double>>()
    /** The day opens began to be counted (see [unused]). */
    var tracked = 0.0
        private set

    private fun now() = System.currentTimeMillis() / DAY_MS // days since 1970

    /** The search rank of [app]: its decayed opens, none for a poison (it may have been used before it became one). */
    fun usage(app: App): Double =
        if (isPoison(app)) 0.0 else uses[packageKey(app.id)]?.let { (score, since) -> useRank(score, since, now()) } ?: 0.0

    /** The day [app] was last opened through the launcher, or null when none is kept: never, or [UNUSED_DAYS] ago at least. */
    fun lastOpen(app: App) = uses[packageKey(app.id)]?.second

    /** Records an open of [app] that started: one more use, or for a poison only the day, for [lastOpen]. */
    private fun countUse(app: App) {
        val now = now()
        val key = packageKey(app.id)
        val score = uses[key]?.let { (score, since) -> decayed(score, since, now) } ?: 0.0
        val next = (uses + (key to ((if (isPoison(app)) score else score + 1) to now))).filterValues { (score, since) -> kept(score, since, now) }
        uses = next
        useScores.edit {
            clear()
            putString(TRACKED_KEY, "$tracked")
            next.forEach { (key, use) -> putString(key, "${use.first} ${use.second}") }
        }
    }

    /**
     * Ajustes → Sin abrir hace tiempo ([unused]): removable apps, one per package, poisons (kept on purpose) and the
     * apps of the desktop's widgets left out. Null until opens have been counted for [UNUSED_DAYS].
     */
    fun unusedApps(): List<App>? {
        val widgeted = layout.widgetPackages
        val candidates = apps.distinctBy { packageKey(it.id) }.filter { it.removable && !isPoison(it) && it.info.componentName.packageName !in widgeted }
        return unused(candidates, tracked, now(), ::lastOpen, App::installed)
    }

    // The app [uninstall] last asked Android to remove: once it is gone, its icons go too, as in Nova.
    private var uninstalling: Pair<String, UserHandle>? = null

    /** Android's own uninstall confirmation for [app]. Not an open: no pause, and never counted as a use. */
    fun uninstall(app: App) {
        val pkg = app.info.componentName.packageName
        try {
            startActivity(Intent(Intent.ACTION_DELETE, Uri.fromParts("package", pkg, null)).putExtra(Intent.EXTRA_USER, app.info.user))
            uninstalling = pkg to app.info.user
        } catch (e: RuntimeException) {
            Log.w(TAG, "Cannot uninstall $pkg", e)
            toast(getString(R.string.uninstall_failed, app.label))
        }
    }

    /** Whether the desktop and dock leave [target] out: its app is paused and Ajustes hides paused apps. */
    fun hides(target: Target?) = layout.hideSuspended && appOf(target)?.suspended == true

    /** The apps the drawer tabs list from: without the paused ones when Ajustes hides them (search still shows them). */
    val tabbed get() = if (layout.hideSuspended) apps.filterNot { it.suspended } else apps

    // Opens of each poison app today, as "<date>|<package>#<user>" counts; older days are dropped on the next write.
    private val opens by lazy { getSharedPreferences("opens", MODE_PRIVATE) }

    private fun opensKey(app: App) = "${LocalDate.now()}|${packageKey(app.id)}"

    fun opensToday(app: App) = opens.getInt(opensKey(app), 0)

    fun countOpen(app: App) {
        val today = LocalDate.now().toString()
        opens.edit {
            opens.all.keys.filterNot { it.startsWith("$today|") }.forEach(::remove)
            putInt(opensKey(app), opensToday(app) + 1)
        }
    }

    fun showInfo(app: App) {
        orToast(app.label) { launcherApps.startAppDetailsActivity(app.info.componentName, app.info.user, null, null) }
    }

    /** Opens another app's screen (search providers, assistant, Lens, calendar), behind the pause if that app is poison. */
    fun startIntent(intent: Intent, what: String) {
        val pkg = intent.`package` ?: intent.resolveActivity(packageManager)?.packageName
        val app = apps.firstOrNull { it.info.componentName.packageName == pkg && it.info.user == Process.myUserHandle() }
        if (app == null && blocked(pkg)) return // an app the list lacks (no launcher entry) is held by its package too
        pauseIfPoison(app) { orToast(what) { startActivity(intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) } }
    }

    /** Runs [action], else toasts that [what] did not open; true if it ran. */
    private inline fun orToast(what: String, action: () -> Unit) = try {
        action()
        true
    } catch (e: RuntimeException) { // disabled, uninstalled, quiet-mode profile or no app for the intent
        Log.w(TAG, "Cannot open $what", e)
        toast(getString(R.string.open_failed, what))
        false
    }

    fun shortcutIcon(target: Target.Shortcut): ImageBitmap? {
        val path = shortcutIconFile(target).takeIf { it.exists() }?.path ?: return null
        val size = BitmapFactory.Options().apply { inJustDecodeBounds = true }.also { BitmapFactory.decodeFile(path, it) }
        val sampled = BitmapFactory.Options().apply { inSampleSize = iconSample(size.outWidth, size.outHeight) }
        return BitmapFactory.decodeFile(path, sampled)?.asImageBitmap()
    }

    /**
     * The shortcuts [app]'s menu lists ("New message", recent chats; see [menuShortcuts]), with icons [iconPx] square.
     * None unless this is the default home app, and none for a work profile's app: [Target.Shortcut] keeps no user.
     * Blocking: call it off the main thread.
     */
    fun shortcutsOf(app: App, iconPx: Int): List<AppShortcut> {
        if (app.info.user != Process.myUserHandle()) return emptyList()
        return try {
            if (!launcherApps.hasShortcutHostPermission()) return emptyList()
            val query = LauncherApps.ShortcutQuery()
                .setPackage(app.info.componentName.packageName)
                .setActivity(app.info.componentName)
                .setQueryFlags(LauncherApps.ShortcutQuery.FLAG_MATCH_DYNAMIC or LauncherApps.ShortcutQuery.FLAG_MATCH_MANIFEST)
            menuShortcuts(launcherApps.getShortcuts(query, app.info.user).orEmpty(), { it.isDeclaredInManifest }, { it.rank }).map {
                val icon = launcherApps.getShortcutIconDrawable(it, resources.displayMetrics.densityDpi)
                AppShortcut(it, icon?.toBitmap(iconPx, iconPx)?.asImageBitmap())
            }
        } catch (e: RuntimeException) { // the user is locked, or the app is gone
            Log.w(TAG, "Cannot read the shortcuts of ${app.label}", e)
            emptyList()
        }
    }

    /** Pins [shortcut], from an app's menu, to the desktop. */
    fun pin(shortcut: ShortcutInfo) = addShortcut(shortcut) {
        fun pinned(ids: List<String>?) = launcherApps.getShortcuts(
            LauncherApps.ShortcutQuery().setPackage(shortcut.`package`).setShortcutIds(ids)
                .setQueryFlags(LauncherApps.ShortcutQuery.FLAG_MATCH_PINNED or LauncherApps.ShortcutQuery.FLAG_GET_KEY_FIELDS_ONLY),
            shortcut.userHandle,
        ).orEmpty().map { it.id }
        // pinShortcuts replaces the package's pinned list: the ones already pinned (a browser's web shortcuts) must stay.
        launcherApps.pinShortcuts(shortcut.`package`, (pinned(null) + shortcut.id).distinct(), shortcut.userHandle)
        // It skips an id the app dropped since its menu opened (chat apps reshuffle recent chats), without a word.
        pinned(listOf(shortcut.id)).isNotEmpty()
    }

    /** Browsers draw their own badge on the web shortcuts they pin (Chrome does). Hostless, so app links don't match. */
    fun isBrowser(pkg: String) = packageManager.resolveActivity(
        Intent(Intent.ACTION_VIEW, Uri.parse("https://")).addCategory(Intent.CATEGORY_BROWSABLE).setPackage(pkg), 0,
    ) != null

    /** Hidden API; the status bar has no public way to open it. */
    fun expandNotifications() {
        try {
            StatusBarManager::class.java.getMethod("expandNotificationsPanel").invoke(getSystemService(StatusBarManager::class.java))
        } catch (e: ReflectiveOperationException) {
            Log.w(TAG, "Cannot expand notifications", e)
        }
    }

    fun update(f: (Layout) -> Layout) = Layouts.update(f)

    fun updatePage(page: Int, f: (List<Item>) -> List<Item>) = update { it.copy(desktop = it.desktop.replaced(page, f)) }

    fun updateDock(page: Int, slot: Int, f: (DockIcon) -> DockIcon) =
        update { it.copy(dock = it.dock.replaced(page) { icons -> icons.replaced(slot, f) }) }

    fun addToDesktop(target: Target, label: String) {
        val page = layout.defaultPage
        val cell = layout.freeCell(page, 1f, 1f) ?: return toast(getString(R.string.desktop_no_room_main))
        updatePage(page) { it + Item.Icon(target, label, cell) }
    }

    fun remove(page: Int, item: Item) {
        if (item is Item.Widget) release(item)
        updatePage(page) { it - item }
    }

    /**
     * The item last thrown off the desktop, while its bar offers Deshacer. A widget keeps its id until [settle], so
     * undoing brings the same one back. If the process dies first, the startup cleanup in [onCreate] frees it, unless
     * an unreadable layout was set aside (that skips the cleanup): then the id stays allocated, unused.
     */
    var thrown by mutableStateOf<Place.Desktop?>(null)
        private set

    /** Takes [item] off desktop [page], to undo with [undoThrow]; the one thrown before goes for good. */
    fun throwAway(page: Int, item: Item) {
        settle()
        if (item !in layout.desktop.getOrNull(page).orEmpty()) return
        updatePage(page) { it - item }
        thrown = Place.Desktop(page, item)
    }

    /** Puts the item last thrown back on its page and cell, else the nearest free spot, else another page. */
    fun undoThrow() {
        val t = thrown ?: return
        thrown = null
        update { it.placed(t.page, t.item) }
    }

    /** The undo bar is gone: the item last thrown goes for good, a widget's id freed as [remove] frees it. */
    fun settle() {
        val t = thrown ?: return
        thrown = null
        (t.item as? Item.Widget)?.let(::release)
    }

    /** Turns a bound widget back into a placeholder, to set it up again (e.g. a habit widget on the wrong habit). */
    fun unbind(page: Int, item: Item.Widget) {
        release(item)
        updatePage(page) { items -> items.map { if (it == item) item.copy(widgetId = 0) else it } }
    }

    /** Removes what sits at [place]: a desktop item as [remove] does, or a dock tap or swipe, set to none. */
    fun remove(place: Place) = when (place) {
        is Place.Desktop -> remove(place.page, place.item)
        is Place.Dock -> updateDock(place.page, place.slot) {
            when (place.swipe) {
                null -> it.copy(tap = null)
                Swipe.UP -> it.copy(up = null)
                else -> it.copy(down = null)
            }
        }
    }

    /**
     * What Ajustes → Ordenar lists ([Layout.audit]). A widget is gone when its provider is not installed and it is not
     * bound to a live one (a work profile's bound widget is not among [widgetProviders]). Blocking: the provider list
     * and each missing app's lookup are binder calls, so call it off the main thread.
     */
    fun audit(layout: Layout): Audit {
        val providers = widgetManager.installedProviders.mapTo(HashSet()) { it.provider.flattenToString() }
        return layout.audit(::installed) { it.provider in providers || (it.widgetId != 0 && widgetManager.getAppWidgetInfo(it.widgetId) != null) }
    }

    /**
     * Whether [target]'s app is still there: in the app list (which keeps a paused work profile's apps, as LauncherApps
     * lists them for launchers to grey out), else installed for its user, which covers a reload that ran mid-update.
     */
    private fun installed(target: Target): Boolean {
        if (appOf(target) != null) return true
        val (pkg, user) = when (target) {
            is Target.App -> target.id.substringBefore('/') to target.id.substringAfterLast('#').toLongOrNull()?.let(users::getUserForSerialNumber)
            is Target.Shortcut -> target.pkg to Process.myUserHandle()
        }
        return user != null && try {
            launcherApps.getApplicationInfo(pkg, 0, user)
            true
        } catch (e: PackageManager.NameNotFoundException) {
            false
        } catch (e: SecurityException) { // a user outside this profile group: a backup from another phone
            false
        }
    }

    private fun release(item: Item.Widget) {
        if (item.widgetId == 0) return
        host.deleteAppWidgetId(item.widgetId)
        widgetViews.remove(item.widgetId)
    }

    // --- Widgets ---

    private class Binding(val page: Int, val placeholder: Item.Widget, val id: Int, val info: AppWidgetProviderInfo, val isNew: Boolean)

    private var binding: Binding? = null

    private val bindRequest = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) {
        if (it.resultCode == RESULT_OK) configureWidget() else finishBinding(false)
    }

    fun widgetProviders(): List<AppWidgetProviderInfo> = widgetManager.installedProviders

    fun providerOf(item: Item.Widget) = widgetManager.installedProviders.find { it.provider.flattenToString() == item.provider }

    /** The host view of a bound widget, or null when its app is gone. Views are kept so pages can recompose. */
    fun widgetView(id: Int): AppWidgetHostView? {
        widgetViews[id]?.let { view ->
            (view.parent as? ViewGroup)?.removeView(view)
            return view
        }
        val info = widgetManager.getAppWidgetInfo(id) ?: return null
        return host.createView(this, id, info).also { widgetViews[id] = it }
    }

    /** The cells (width, height) a new widget of [info] takes, here and in the widget picker. */
    fun widgetCells(info: AppWidgetProviderInfo): Pair<Float, Float> {
        val density = resources.displayMetrics.density
        return widgetSpan(info.minWidth / density, info.targetCellWidth, cellSize.width.value, layout.columns) to
            widgetSpan(info.minHeight / density, info.targetCellHeight, cellSize.height.value, layout.rows)
    }

    /** Widget [item]'s provider while it is bound (not a Nova placeholder), else null. A binder call. */
    fun widgetInfo(item: Item.Widget): AppWidgetProviderInfo? = if (item.widgetId == 0) null else widgetManager.getAppWidgetInfo(item.widgetId)

    /** The sizes widget [info] may be resized to, in cells rounded as [widgetCells] rounds a new widget's. */
    fun resizeLimits(info: AppWidgetProviderInfo): Limits {
        val dp = resources.displayMetrics.density
        val (minW, maxW) = resizeSpan(info.minWidth / dp, info.minResizeWidth / dp, info.maxResizeWidth / dp, cellSize.width.value, layout.columns)
        val (minH, maxH) = resizeSpan(info.minHeight / dp, info.minResizeHeight / dp, info.maxResizeHeight / dp, cellSize.height.value, layout.rows)
        return Limits(minW, minH, maxW, maxH)
    }

    /**
     * The RemoteViews previews of [info], best first, as Launcher3 picks them: the one its app generated (Android 15+),
     * then its preview layout. Blocking: call it off the main thread.
     */
    fun widgetPreviews(info: AppWidgetProviderInfo): List<RemoteViews> {
        val home = AppWidgetProviderInfo.WIDGET_CATEGORY_HOME_SCREEN
        // Only for a launcher allowed to bind widgets ("always allow" in the bind dialog); else it throws.
        val generated = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.VANILLA_ICE_CREAM && info.generatedPreviewCategories and home != 0) {
            try {
                widgetManager.getWidgetPreview(info.provider, info.profile, home)
            } catch (e: RuntimeException) {
                null
            }
        } else {
            null
        }
        val layout = if (info.previewLayout == 0) null else try {
            RemoteViews(info.provider.packageName, info.previewLayout)
        } catch (e: RuntimeException) {
            null // its app was just removed
        }
        return listOfNotNull(generated, layout)
    }

    /** The preview image of [info], scaled to fit [width] × [height] px, or null. Blocking: call it off the main thread. */
    fun widgetPreviewImage(info: AppWidgetProviderInfo, width: Int, height: Int): ImageBitmap? = try {
        info.loadPreviewImage(this, 0)?.let { image ->
            val w = image.intrinsicWidth.takeIf { it > 0 } ?: width
            val h = image.intrinsicHeight.takeIf { it > 0 } ?: height
            val scale = minOf(width.toFloat() / w, height.toFloat() / h) // up too, so a small vector stays sharp
            image.toBitmap((w * scale).roundToInt().coerceAtLeast(1), (h * scale).roundToInt().coerceAtLeast(1)).asImageBitmap()
        }
    } catch (e: RuntimeException) {
        Log.w(TAG, "Widget preview image failed", e)
        null
    } catch (e: OutOfMemoryError) { // an app's huge image must not take the home screen down
        Log.w(TAG, "Widget preview image too big", e)
        null
    }

    fun addWidget(page: Int, info: AppWidgetProviderInfo) {
        val (w, h) = widgetCells(info)
        val cell = layout.freeCell(page, w, h) ?: return toast(getString(R.string.widget_no_room))
        val placeholder = Item.Widget(info.provider.flattenToString(), 0, cell)
        updatePage(page) { it + placeholder }
        bindWidget(page, placeholder, isNew = true)
    }

    /** Binds [placeholder] (a widget imported or just added), asking permission and running its setup screen. */
    fun bindWidget(page: Int, placeholder: Item.Widget, isNew: Boolean = false) {
        if (binding != null) return // a second tap while the first setup is opening
        val info = providerOf(placeholder) ?: return toast(getString(R.string.widget_app_missing))
        val id = host.allocateAppWidgetId()
        binding = Binding(page, placeholder, id, info, isNew)
        if (widgetManager.bindAppWidgetIdIfAllowed(id, info.profile, info.provider, null)) return configureWidget()
        bindRequest.launch(
            Intent(AppWidgetManager.ACTION_APPWIDGET_BIND)
                .putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, id)
                .putExtra(AppWidgetManager.EXTRA_APPWIDGET_PROVIDER, info.provider)
                .putExtra(AppWidgetManager.EXTRA_APPWIDGET_PROVIDER_PROFILE, info.profile),
        )
    }

    private fun configureWidget() {
        val b = binding ?: return
        // Launcher3's rule: setup may be skipped only when it is optional and can be reopened later.
        val skippable = AppWidgetProviderInfo.WIDGET_FEATURE_CONFIGURATION_OPTIONAL or AppWidgetProviderInfo.WIDGET_FEATURE_RECONFIGURABLE
        if (b.info.configure == null || b.info.widgetFeatures and skippable == skippable) return finishBinding(true)
        try {
            host.startAppWidgetConfigureActivityForResult(this, b.id, 0, REQUEST_CONFIGURE, null)
        } catch (e: RuntimeException) {
            Log.w(TAG, "Widget setup screen failed", e)
            finishBinding(true)
        }
    }

    @Deprecated("AppWidgetHost only reports the configure result here")
    @Suppress("DEPRECATION")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == REQUEST_CONFIGURE) finishBinding(resultCode == RESULT_OK)
    }

    private fun finishBinding(ok: Boolean) {
        val b = binding ?: return
        binding = null
        val (page, placeholder) = layout.locate(b.page, b.placeholder) ?: return host.deleteAppWidgetId(b.id) // removed meanwhile
        if (!ok) {
            host.deleteAppWidgetId(b.id)
            if (b.isNew) updatePage(page) { it - placeholder }
            return
        }
        if (placeholder.widgetId != 0) host.deleteAppWidgetId(placeholder.widgetId) // its app had been reinstalled
        updatePage(page) { items -> items.map { if (it == placeholder) placeholder.copy(widgetId = b.id) else it } }
    }

    // --- Backup: the layout as a JSON file the owner keeps, e.g. before reinstalling ---

    private val backups by lazy { getSharedPreferences("backup", MODE_PRIVATE) }

    /** The folder that gets a weekly copy, or null when that is off. */
    var backupFolder by mutableStateOf<Uri?>(null)
        private set

    private suspend fun writeBackup(uri: Uri, json: String) = withContext(Dispatchers.IO) {
        contentResolver.openOutputStream(uri, "wt")!!.use { it.write(json.toByteArray()) }
    }

    // Local storage only (EXTRA_LOCAL_ONLY): a backup lists every app the owner uses, and a cloud provider would
    // upload it, which the no-network rule forbids.
    private val saveBackup = registerForActivityResult(object : ActivityResultContracts.CreateDocument("application/json") {
        override fun createIntent(context: Context, input: String) = super.createIntent(context, input).putExtra(Intent.EXTRA_LOCAL_ONLY, true)
    }) { uri ->
        if (uri == null) return@registerForActivityResult
        val json = layout.toJson()
        lifecycleScope.launch {
            try {
                writeBackup(uri, json)
                toast(getString(R.string.backup_saved))
            } catch (e: Exception) { // provider gone, no space, or no permission
                Log.w(TAG, "Backup failed", e)
                toast(getString(R.string.backup_save_failed))
            }
        }
    }

    private val openBackup = registerForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri == null) return@registerForActivityResult
        lifecycleScope.launch {
            try {
                val json = withContext(Dispatchers.IO) {
                    val bytes = contentResolver.openInputStream(uri)!!.use { it.readNBytes(MAX_BACKUP_BYTES + 1) }
                    if (bytes.size > MAX_BACKUP_BYTES) throw IOException("Too big for a backup: $uri")
                    bytes.decodeToString()
                }
                val live = host.appWidgetIds.associateWith { widgetManager.getAppWidgetInfo(it)?.provider?.flattenToString() }
                replaceLayout(parseLayout(json).restored(live))
                toast(getString(R.string.backup_restored))
            } catch (e: Exception) { // not a backup of this launcher, or unreadable
                Log.w(TAG, "Restore failed", e)
                toast(getString(R.string.backup_read_failed))
            } catch (e: VirtualMachineError) { // deeply nested or crafted JSON
                Log.w(TAG, "Restore failed", e)
                toast(getString(R.string.backup_read_failed))
            }
        }
    }

    fun saveBackup() = saveBackup.launch("launcher-${LocalDate.now()}.json")

    /** Swaps in a restored or imported layout, then frees the widgets it no longer holds: they would stay allocated forever. */
    private fun replaceLayout(next: Layout) {
        val old = layout.desktop.flatten().filterIsInstance<Item.Widget>().map { it.widgetId }
        update { next } // first: if saving fails, the old widgets are still there
        val kept = next.desktop.flatten().filterIsInstance<Item.Widget>().map { it.widgetId }.toSet()
        old.filter { it != 0 && it !in kept }.forEach {
            host.deleteAppWidgetId(it)
            widgetViews.remove(it)
        }
    }

    private val pickFolder = registerForActivityResult(object : ActivityResultContracts.OpenDocumentTree() {
        override fun createIntent(context: Context, input: Uri?) = super.createIntent(context, input).putExtra(Intent.EXTRA_LOCAL_ONLY, true)
    }) { uri ->
        if (uri == null) return@registerForActivityResult
        contentResolver.takePersistableUriPermission(uri, FOLDER_ACCESS)
        backupFolder?.takeIf { it != uri }?.let(::releaseFolder)
        backups.edit {
            putString("folder", uri.toString())
            remove("last")
            remove("tried")
        }
        backupFolder = uri
        autoBackup() // the first copy right away
    }

    fun chooseBackupFolder() = pickFolder.launch(backupFolder)

    fun stopAutoBackup() {
        backupFolder?.let(::releaseFolder)
        backups.edit { clear() }
        backupFolder = null
    }

    private fun releaseFolder(uri: Uri) = try {
        contentResolver.releasePersistableUriPermission(uri, FOLDER_ACCESS)
    } catch (e: SecurityException) { // already gone
        Log.w(TAG, "Folder access was already released", e)
    }

    /** A weekly copy into [backupFolder], checked whenever the home screen comes back, so no scheduler is needed. */
    private fun autoBackup() {
        val folder = backupFolder ?: return
        val today = LocalDate.now().toEpochDay()
        if (today - backups.getLong("last", 0) < 7 || backups.getLong("tried", 0) == today) return
        backups.edit { putLong("tried", today) } // a failing copy is retried once a day, not on every return home
        val json = layout.toJson()
        lifecycleScope.launch {
            try {
                val file = withContext(Dispatchers.IO) {
                    val dir = DocumentsContract.buildDocumentUriUsingTree(folder, DocumentsContract.getTreeDocumentId(folder))
                    DocumentsContract.createDocument(contentResolver, dir, "application/json", "launcher-${LocalDate.now()}.json")
                } ?: throw IOException("Cannot create a file in $folder")
                writeBackup(file, json)
                backups.edit { putLong("last", today) }
            } catch (e: Exception) { // folder deleted, access revoked, or storage full
                Log.w(TAG, "Automatic backup failed", e)
                toast(getString(R.string.backup_auto_failed))
            }
        }
    }

    fun restoreBackup() = openBackup.launch(arrayOf("application/json", "application/octet-stream", "text/plain"))

    // --- Nova import: a .novabackup replaces the layout; the venenos (which Nova lacks), the app offered instead, the vibration setting, the number format and how paused apps show stay ---

    private val pickNova = registerForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri == null) return@registerForActivityResult
        lifecycleScope.launch {
            val imported = try {
                withContext(Dispatchers.IO) { readNovaBackup(this@Launcher, uri, userSerial) }
            } catch (e: Exception) { // not a Nova backup, or an unreadable one
                Log.w(TAG, "Nova import failed", e)
                return@launch toast(getString(R.string.backup_nova_read_failed))
            } catch (e: OutOfMemoryError) { // a crafted archive: only the import is lost
                Log.w(TAG, "Nova import failed", e)
                return@launch toast(getString(R.string.backup_nova_read_failed))
            }
            replaceLayout(imported.copy(poisons = layout.poisons, haptics = layout.haptics, instead = layout.instead, decimal = layout.decimal, hideSuspended = layout.hideSuspended, showDock = layout.showDock, showSearchBar = layout.showSearchBar))
            toast(getString(R.string.backup_nova_imported))
        }
    }

    fun importNova() = pickNova.launch(arrayOf("*/*"))
}

/**
 * "Add to home screen" requests (e.g. from Chrome) while this is the default home app. Any foreground app can send
 * one, so the owner confirms each.
 */
class PinActivity : ComponentActivity() {
    private val launcherApps by lazy { getSystemService(LauncherApps::class.java) }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val request = launcherApps.getPinItemRequest(intent)
        val info = request?.shortcutInfo
        if (request == null || info == null || !request.isValid) return finish()
        val from = try {
            packageManager.getApplicationLabel(packageManager.getApplicationInfo(info.`package`, 0)).toString()
        } catch (e: PackageManager.NameNotFoundException) {
            info.`package`
        }
        setContent {
            PinDialog(info.label, from, onCancel = ::finish) {
                addShortcut(info, request::accept)
                finish()
            }
        }
    }

}
