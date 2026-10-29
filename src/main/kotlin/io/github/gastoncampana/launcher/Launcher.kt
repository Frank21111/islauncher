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
import android.graphics.drawable.Drawable
import android.net.Uri
import android.os.Bundle
import android.os.Process
import android.os.UserHandle
import android.os.UserManager
import android.provider.DocumentsContract
import android.text.format.DateFormat
import android.util.Log
import android.view.ViewGroup
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
import kotlin.math.ceil

private const val TAG = "Launcher"
private const val LAYOUT_KEY = "layout"
private const val HOST_ID = 1024
private const val REQUEST_CONFIGURE = 1
private const val FOLDER_ACCESS = Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION
private const val MAX_BACKUP_BYTES = 1 shl 20 // a layout is a few KB; anything bigger is the wrong file

class App(val id: AppId, val label: String, val icon: ImageBitmap, val info: LauncherActivityInfo, val suspended: Boolean)

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
    File(filesDir, "shortcut-" + "${t.pkg}-${t.id}".replace(Regex("[^A-Za-z0-9._-]"), "_") + ".png")

fun Context.toast(text: String) = Toast.makeText(this, text, Toast.LENGTH_SHORT).show()

class Launcher : ComponentActivity() {
    private val launcherApps by lazy { getSystemService(LauncherApps::class.java) }
    private val users by lazy { getSystemService(UserManager::class.java) }
    private val widgetManager by lazy { AppWidgetManager.getInstance(this) }
    private val host by lazy { AppWidgetHost(this, HOST_ID) }
    val userSerial by lazy { users.getSerialNumberForUser(Process.myUserHandle()) }

    // Only touched from [loader], which runs one task at a time.
    private val icons = HashMap<AppId, ImageBitmap>()
    private val loader = Dispatchers.Default.limitedParallelism(1)
    private val widgetViews = HashMap<Int, AppWidgetHostView>()

    var apps by mutableStateOf(emptyList<App>())
        private set
    val find: (AppId) -> App? by derivedStateOf { finder(apps) { it.id } }
    val layout get() = Layouts.current
    var drawerOpen by mutableStateOf(false)
    var focusSearch by mutableStateOf(false) // drawer opened from the search bar
    var popup by mutableStateOf<Popup?>(null)
    var homePresses by mutableIntStateOf(0) // home pressed while already on the home screen
        private set
    var drawerResets by mutableIntStateOf(0) // bumped to close the drawer without its exit animation
        private set
    var today by mutableStateOf("")
        private set
    var cellSize = DpSize(90.dp, 150.dp) // measured by the desktop; sizes new widgets

    private val packageCallback = object : LauncherApps.Callback() {
        override fun onPackageAdded(packageName: String, user: UserHandle) = reload(packageName)
        override fun onPackageRemoved(packageName: String, user: UserHandle) = reload(packageName)
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
        backupFolder = backups.getString("folder", null)?.toUri()
        setContent { LauncherScreen(this) }
    }

    override fun onStart() {
        super.onStart()
        host.startListening()
        autoBackup()
    }

    override fun onResume() {
        super.onResume()
        updateToday() // the midnight broadcast is missed while the screen is off
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        val alreadyHome = hasWindowFocus() && intent.flags and Intent.FLAG_ACTIVITY_BROUGHT_TO_FRONT == 0
        if (alreadyHome && !drawerOpen) homePresses++
        closeAll()
    }

    override fun onStop() {
        super.onStop()
        host.stopListening()
        closeAll() // come back to a clean home screen after opening an app
        hideDrawer() // gone on return, rather than fading out then
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
        val size = (56 * resources.displayMetrics.density).toInt()
        return launcherApps.profiles
            .flatMap { launcherApps.getActivityList(null, it) }
            .map { info ->
                val id = "${info.componentName.flattenToString()}#${users.getSerialNumberForUser(info.user)}"
                val icon = icons.getOrPut(id) { info.getBadgedIcon(0).toBitmap(size, size).asImageBitmap() }
                App(id, info.label.toString(), icon, info, info.applicationInfo.flags and ApplicationInfo.FLAG_SUSPENDED != 0)
            }
            .sortedBy { fold(it.label) }
    }

    fun appOf(target: Target?): App? = when (target) {
        is Target.App -> find(target.id)
        is Target.Shortcut -> find("${target.pkg}/#$userSerial") // any activity of the package
        null -> null
    }

    fun labelOf(target: Target?): String = when (target) {
        is Target.App -> appOf(target)?.label ?: "no instalada"
        is Target.Shortcut -> target.label.ifEmpty { target.id }
        null -> "nada"
    }

    fun open(target: Target?) {
        when (target) {
            null -> Unit
            is Target.App -> appOf(target)?.let(::start) ?: toast("La app no está instalada o está desactivada")
            is Target.Shortcut -> pauseIfPoison(appOf(target)) { startShortcut(target) }
        }
    }

    private fun startShortcut(target: Target.Shortcut) = try {
        launcherApps.startShortcut(target.pkg, target.id, null, null, Process.myUserHandle())
    } catch (e: RuntimeException) { // not the default home app, shortcut removed, or user locked
        Log.w(TAG, "Cannot start shortcut ${target.pkg}/${target.id}", e)
        toast(
            if (!launcherApps.hasShortcutHostPermission()) "Los atajos funcionan cuando Launcher es la app de inicio"
            else "No se pudo abrir «${labelOf(target)}». Volvé a agregarlo desde su app",
        )
    }

    fun start(app: App) = pauseIfPoison(app) {
        orToast(app.label) { launcherApps.startMainActivity(app.info.componentName, app.info.user, null, null) }
    }

    // By package: an app that switches launcher aliases (Mercado Pago) stays poison under either activity.
    fun isPoison(app: App) = layout.poisons.any { find(it)?.let(::packageOf) == packageOf(app) }

    private fun packageOf(app: App) = app.info.componentName.packageName to app.info.user

    /** Opens a poison app (or one of its shortcuts) only through the pause screen. */
    private fun pauseIfPoison(app: App?, open: () -> Unit) {
        if (app != null && isPoison(app)) popup = Popup.Pause(app, open) else open()
    }

    // Opens of each poison app today, as "<date>|<package>#<user>" counts; older days are dropped on the next write.
    private val opens by lazy { getSharedPreferences("opens", MODE_PRIVATE) }

    private fun opensKey(app: App) = "${LocalDate.now()}|${app.id.substringBefore('/')}#${app.id.substringAfterLast('#')}"

    fun opensToday(app: App) = opens.getInt(opensKey(app), 0)

    fun countOpen(app: App) {
        val today = LocalDate.now().toString()
        opens.edit {
            opens.all.keys.filterNot { it.startsWith("$today|") }.forEach(::remove)
            putInt(opensKey(app), opensToday(app) + 1)
        }
    }

    fun showInfo(app: App) = orToast(app.label) { launcherApps.startAppDetailsActivity(app.info.componentName, app.info.user, null, null) }

    /** Opens another app's screen (search providers, assistant, Lens, calendar), behind the pause if that app is poison. */
    fun startIntent(intent: Intent, what: String) {
        val pkg = intent.`package` ?: intent.resolveActivity(packageManager)?.packageName
        val app = apps.firstOrNull { it.info.componentName.packageName == pkg && it.info.user == Process.myUserHandle() }
        pauseIfPoison(app) { orToast(what) { startActivity(intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) } }
    }

    private inline fun orToast(what: String, action: () -> Unit) {
        try {
            action()
        } catch (e: RuntimeException) { // disabled, uninstalled, quiet-mode profile or no app for the intent
            Log.w(TAG, "Cannot open $what", e)
            toast("No se pudo abrir $what")
        }
    }

    fun shortcutIcon(target: Target.Shortcut): ImageBitmap? =
        shortcutIconFile(target).takeIf { it.exists() }?.let { BitmapFactory.decodeFile(it.path)?.asImageBitmap() }

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
        val cell = freeCell(layout.desktop[page], 1f, 1f) ?: return toast("No hay lugar en la página principal")
        updatePage(page) { it + Item.Icon(target, label, cell) }
    }

    fun remove(page: Int, item: Item) {
        if (item is Item.Widget) release(item)
        updatePage(page) { it - item }
    }

    /** Turns a bound widget back into a placeholder, to set it up again (e.g. a habit widget on the wrong habit). */
    fun unbind(page: Int, item: Item.Widget) {
        release(item)
        updatePage(page) { items -> items.map { if (it == item) item.copy(widgetId = 0) else it } }
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

    fun addWidget(page: Int, info: AppWidgetProviderInfo) {
        fun cells(px: Int, target: Int, cellDp: Float): Float =
            if (target > 0) target.toFloat() else (ceil(px / resources.displayMetrics.density / cellDp * 2) / 2).coerceIn(0.5f, GRID.toFloat())
        val w = cells(info.minWidth, info.targetCellWidth, cellSize.width.value)
        val h = cells(info.minHeight, info.targetCellHeight, cellSize.height.value)
        val cell = freeCell(layout.desktop[page], w, h) ?: return toast("No hay lugar para este widget en la página")
        val placeholder = Item.Widget(info.provider.flattenToString(), 0, cell)
        updatePage(page) { it + placeholder }
        bindWidget(page, placeholder, isNew = true)
    }

    /** Binds [placeholder] (a widget imported or just added), asking permission and running its setup screen. */
    fun bindWidget(page: Int, placeholder: Item.Widget, isNew: Boolean = false) {
        if (binding != null) return // a second tap while the first setup is opening
        val info = providerOf(placeholder) ?: return toast("La app de este widget no está instalada")
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

    private fun finishBinding(ok: Boolean) {        val b = binding ?: return
        binding = null
        if (!ok) {
            host.deleteAppWidgetId(b.id)
            if (b.isNew) updatePage(b.page) { it - b.placeholder }
            return
        }
        if (b.placeholder.widgetId != 0) host.deleteAppWidgetId(b.placeholder.widgetId) // its app had been reinstalled
        updatePage(b.page) { items -> items.map { if (it == b.placeholder) b.placeholder.copy(widgetId = b.id) else it } }
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
                toast("Copia guardada")
            } catch (e: Exception) { // provider gone, no space, or no permission
                Log.w(TAG, "Backup failed", e)
                toast("No se pudo guardar la copia")
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
                val old = layout.desktop.flatten().filterIsInstance<Item.Widget>().map { it.widgetId }
                val live = host.appWidgetIds.associateWith { widgetManager.getAppWidgetInfo(it)?.provider?.flattenToString() }
                update { parseLayout(json).restored(live) }
                // Widgets the restored layout no longer holds would stay allocated forever.
                val kept = layout.desktop.flatten().filterIsInstance<Item.Widget>().map { it.widgetId }.toSet()
                old.filter { it != 0 && it !in kept }.forEach {
                    host.deleteAppWidgetId(it)
                    widgetViews.remove(it)
                }
                toast("Copia restaurada")
            } catch (e: Exception) { // not a backup of this launcher, or unreadable
                Log.w(TAG, "Restore failed", e)
                toast("No se pudo leer la copia")
            } catch (e: StackOverflowError) { // deeply nested JSON
                Log.w(TAG, "Restore failed", e)
                toast("No se pudo leer la copia")
            }
        }
    }

    fun saveBackup() = saveBackup.launch("launcher-${LocalDate.now()}.json")

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
                toast("No se pudo hacer la copia automática. Revisá la carpeta en Copia del escritorio")
            }
        }
    }

    fun restoreBackup() = openBackup.launch(arrayOf("application/json", "application/octet-stream", "text/plain"))
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
        val label = (info.shortLabel ?: info.longLabel ?: "").toString()
        val from = try {
            packageManager.getApplicationLabel(packageManager.getApplicationInfo(info.`package`, 0)).toString()
        } catch (e: PackageManager.NameNotFoundException) {
            info.`package`
        }
        setContent {
            PinDialog(label, from, onCancel = ::finish) {
                add(request, info, label)
                finish()
            }
        }
    }

    // This runs in the home screen's process: an uncaught throw would take it down too.
    private fun add(request: LauncherApps.PinItemRequest, info: ShortcutInfo, label: String) {
        Layouts.init(this)
        val layout = Layouts.current
        val page = (listOf(layout.defaultPage) + layout.desktop.indices).firstOrNull { freeCell(layout.desktop[it], 1f, 1f) != null }
            ?: return toast("No hay lugar en el escritorio")
        try {
            if (!request.accept()) return
        } catch (e: RuntimeException) { // answered elsewhere, or the sender is gone
            Log.w(TAG, "Pin request failed", e)
            return toast("No se pudo agregar «$label»")
        }
        val target = Target.Shortcut(info.`package`, info.id, label)
        try {
            launcherApps.getShortcutIconDrawable(info, resources.displayMetrics.densityDpi)?.let { saveIcon(shortcutIconFile(target), it) }
        } catch (e: Exception) { // the app's icon stands in
            Log.w(TAG, "Cannot save the icon of $label", e)
        }
        Layouts.update { l -> l.copy(desktop = l.desktop.replaced(page) { it + Item.Icon(target, label, freeCell(it, 1f, 1f)!!) }) }
        toast("Agregado al escritorio")
    }

    private fun saveIcon(file: File, icon: Drawable) {
        val size = (56 * resources.displayMetrics.density).toInt()
        file.outputStream().use { icon.toBitmap(size, size).compress(Bitmap.CompressFormat.PNG, 100, it) }
    }
}
