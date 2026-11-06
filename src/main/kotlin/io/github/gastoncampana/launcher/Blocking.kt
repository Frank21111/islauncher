package io.github.gastoncampana.launcher

import android.Manifest
import android.accessibilityservice.AccessibilityService
import android.app.NotificationManager
import android.app.admin.DeviceAdminReceiver
import android.app.admin.DevicePolicyManager
import android.content.BroadcastReceiver
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.SharedPreferences
import android.content.pm.PackageManager
import android.database.ContentObserver
import android.graphics.Color
import android.graphics.PixelFormat
import android.graphics.Rect
import android.graphics.drawable.GradientDrawable
import android.media.session.MediaController
import android.media.session.MediaSession
import android.media.session.MediaSessionManager
import android.media.session.PlaybackState
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.provider.Settings
import android.provider.Telephony
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import android.telecom.TelecomManager
import android.text.format.DateFormat
import android.util.Log
import android.view.Gravity
import android.view.View
import android.view.WindowManager
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import android.view.accessibility.AccessibilityWindowInfo
import android.widget.TextView
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.core.content.edit
import androidx.core.net.toUri
import java.time.Duration
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.format.TextStyle
import java.util.Date
import java.util.Locale
import java.util.UUID
import java.util.concurrent.CopyOnWriteArraySet

private const val TAG = "Blocks"
private const val RULES_KEY = "rules"
private const val ANCHOR_KEY = "anchor"
private const val READ_PREFIX = "read." // + package: the day its address bar was last read
private const val CONTENT_DELAY_MS = 250L // window content changes come in bursts: one look after them
private const val BOUNCE_GAP_MS = 700L // after sending an app away, before doing it again
private const val BACK_AGAIN_MS = 2000L // a blocked site still there this soon after going back goes home instead
private const val MAX_NODES = 3000 // of a browser's own views, outside its page; a page's are never walked
private const val BACK_SOON_MS = 15_000L // a browser back in front this soon (a dialog was over it) keeps what was read
private const val NOTICE_MS = 3000L // a notice of why an app or site went, as long as a short toast

/**
 * The blocks ([Rule]) and the clock they go by, shared by the services and Ajustes in the home screen's process. Prefs
 * "blocks", apart from the layout: a restored backup or a Nova import never touches them.
 */
object BlockStore {
    private lateinit var prefs: SharedPreferences
    var rules by mutableStateOf(emptyList<Rule>())
        private set
    private val listeners = CopyOnWriteArraySet<() -> Unit>()
    private var anchor: Anchor? = null
    private var anchorSavedAt = -1L // elapsed time of the last anchor saved; -1: none yet
    private var boot = 0

    fun init(context: Context) {
        if (::prefs.isInitialized) return
        prefs = context.applicationContext.getSharedPreferences("blocks", Context.MODE_PRIVATE)
        rules = prefs.getString(RULES_KEY, null)?.let { json ->
            try {
                parseRules(json)
            } catch (e: Exception) {
                // Kept aside, as an unreadable layout is: the next save would overwrite it for good.
                Log.e(TAG, "Unreadable blocks", e)
                prefs.edit { putString("$RULES_KEY.corrupt", json) }
                null
            }
        }.orEmpty()
        anchor = prefs.getString(ANCHOR_KEY, null)?.let { runCatching { parseAnchor(it) }.getOrNull() }
        boot = Settings.Global.getInt(context.contentResolver, Settings.Global.BOOT_COUNT, 0) // fixed while the process lives
    }

    /** Saves [next], which Ajustes checked with [problemSaving]; the services follow at once. */
    fun save(next: List<Rule>) {
        val json = rulesToJson(next) // first: if it throws, what runs and what is saved still agree
        rules = next
        prefs.edit { putString(RULES_KEY, json) }
        listeners.forEach { it() }
    }

    fun listen(f: () -> Unit) = listeners.add(f)

    fun unlisten(f: () -> Unit) = listeners.remove(f)

    /** Now, as blocks read it: the wall clock, unless it was moved during a block ([trustedTime]). */
    @Synchronized
    fun now(): LocalDateTime {
        val elapsed = SystemClock.elapsedRealtime()
        val next = trustedTime(System.currentTimeMillis(), elapsed, boot, anchor) { ms -> rules.any { it.activeAt(local(ms)) } }
        anchor = next
        if (anchorSavedAt < 0 || elapsed - anchorSavedAt > 60_000 || elapsed < anchorSavedAt) { // a minute apart is close enough after a reboot
            prefs.edit { putString(ANCHOR_KEY, anchorToJson(next)) }
            anchorSavedAt = elapsed
        }
        return local(next.epochMs)
    }

    private fun local(ms: Long) = LocalDateTime.ofInstant(Instant.ofEpochMilli(ms), ZoneId.systemDefault())

    /** Until when the app of [pkg] cannot open, or null when it can. */
    fun appBlockedUntil(pkg: String, t: LocalDateTime = now()) = blockedUntil(rules, t) { pkg in it.packages }

    /** Until when a page on one of [hosts] cannot show, or null when it can. */
    fun siteBlockedUntil(hosts: Collection<String>, t: LocalDateTime) = blockedUntil(rules, t) { r -> hosts.any { blockedSite(it, r.sites) } }

    /** Notes that [pkg]'s address bar was read today, for Ajustes to tell which browsers can be blocked site by site. */
    fun noteRead(pkg: String) {
        val today = LocalDate.now().toEpochDay()
        if (prefs.getLong(READ_PREFIX + pkg, 0) != today) prefs.edit { putLong(READ_PREFIX + pkg, today) }
    }

    fun lastRead(pkg: String): LocalDate? = prefs.getLong(READ_PREFIX + pkg, 0).takeIf { it > 0 }?.let(LocalDate::ofEpochDay)

    fun newId() = UUID.randomUUID().toString()
}

/** When a block ends, as a toast says it: "18:00" today, else with the day ("lun 07:00"). */
fun Context.untilText(t: LocalDateTime): String {
    val time = DateFormat.getTimeFormat(this).format(Date.from(t.atZone(ZoneId.systemDefault()).toInstant()))
    return if (t.toLocalDate() == LocalDate.now()) time else t.dayOfWeek.getDisplayName(TextStyle.SHORT, Locale.getDefault()) + " " + time
}

/**
 * Apps no block may hold: this one, Ajustes, the phone and SMS apps, so the phone stays usable for anything urgent, and
 * the home app in use, where a blocked app is sent.
 */
fun Context.unblockable(): Set<String> = setOfNotNull(
    packageName,
    "com.android.settings",
    getSystemService(TelecomManager::class.java)?.defaultDialerPackage,
    Telephony.Sms.getDefaultSmsPackage(this),
    packageManager.resolveActivity(Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME), PackageManager.MATCH_DEFAULT_ONLY)?.activityInfo?.packageName,
)

/** The packages that open any web address: browsers, whatever their make. A site block reads their address bar. */
fun Context.browserPackages(): Set<String> = packageManager.queryIntentActivities(
    Intent(Intent.ACTION_VIEW, "https://example.com".toUri()).addCategory(Intent.CATEGORY_BROWSABLE),
    PackageManager.MATCH_ALL,
).mapTo(HashSet()) { it.activityInfo.packageName }

/** Required user-granted access; secure settings adds optional protection against disabling the service. */
class BlockSetup(val secureSettings: Boolean, val accessibility: Boolean, val notifications: Boolean, val admin: Boolean) {
    val complete get() = accessibility && notifications && admin
}

fun Context.blockSetup() = BlockSetup(
    checkSelfPermission(Manifest.permission.WRITE_SECURE_SETTINGS) == PackageManager.PERMISSION_GRANTED,
    accessibilityOn(),
    getSystemService(NotificationManager::class.java).isNotificationListenerAccessGranted(ComponentName(this, BlockListener::class.java)),
    getSystemService(DevicePolicyManager::class.java).isAdminActive(ComponentName(this, BlockAdmin::class.java)),
)

/** The setting may name a service in its short form (pkg/.Cls) or its long one: compared as components. */
private fun Context.accessibilityOn() = Settings.Secure.getString(contentResolver, Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES).orEmpty()
    .split(':').mapNotNull(ComponentName::unflattenFromString).any { it == ComponentName(this, BlockService::class.java) }

private var lastGuardWrite = Long.MIN_VALUE / 2
private var guardPending = false

/**
 * While there are blocks, puts the blocking service back when it was turned off: in Ajustes, by its shortcut, or by a
 * force stop, which turns it off too (the notification listener reconnects within a second and calls this). During a
 * block it also keeps the time and time zone automatic. Writes only what is missing, at most once a second: each write
 * calls the settings observers that call this again. Needs WRITE_SECURE_SETTINGS, which only adb can give.
 */
fun Context.guardBlocks() {
    BlockStore.init(this)
    if (BlockStore.rules.isEmpty() || checkSelfPermission(Manifest.permission.WRITE_SECURE_SETTINGS) != PackageManager.PERMISSION_GRANTED) return
    val now = SystemClock.elapsedRealtime()
    if (now - lastGuardWrite < 1000) {
        // Not dropped: another setting may have changed meanwhile (time zone after time). Once, when the second is up.
        if (!guardPending) {
            guardPending = true
            Handler(Looper.getMainLooper()).postDelayed({
                guardPending = false
                guardBlocks()
            }, 1000 - (now - lastGuardWrite))
        }
        return
    }
    try {
        if (!accessibilityOn()) {
            lastGuardWrite = now
            enableBlockService()
            Log.i(TAG, "Blocking service turned back on")
        }
        val t = BlockStore.now()
        if (BlockStore.rules.any { it.activeAt(t) }) {
            for (key in listOf(Settings.Global.AUTO_TIME, Settings.Global.AUTO_TIME_ZONE)) {
                if (Settings.Global.getInt(contentResolver, key, 1) == 0) {
                    lastGuardWrite = now
                    Settings.Global.putInt(contentResolver, key, 1)
                }
            }
        }
    } catch (e: SecurityException) {
        Log.w(TAG, "Cannot guard the blocks", e)
    }
}

/** Turns the blocking service on, keeping the other services on; false when it cannot (no WRITE_SECURE_SETTINGS). */
fun Context.enableBlockService(): Boolean {
    if (checkSelfPermission(Manifest.permission.WRITE_SECURE_SETTINGS) != PackageManager.PERMISSION_GRANTED) return false
    if (accessibilityOn()) return true
    return try {
        val enabled = Settings.Secure.getString(contentResolver, Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES).orEmpty()
        val me = ComponentName(this, BlockService::class.java).flattenToShortString()
        Settings.Secure.putString(contentResolver, Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES, if (enabled.isBlank()) me else "$enabled:$me")
        Settings.Secure.putInt(contentResolver, Settings.Secure.ACCESSIBILITY_ENABLED, 1)
        true
    } catch (e: SecurityException) {
        Log.w(TAG, "Cannot turn the blocking service on", e)
        false
    }
}

/**
 * With no block left, turns the device admin and the blocking service off. Notification access only the owner can turn
 * off, in Ajustes: Android takes no setting for it (phase 0).
 */
fun Context.turnOffBlocks() {
    if (BlockStore.rules.isNotEmpty()) return
    getSystemService(DevicePolicyManager::class.java).removeActiveAdmin(ComponentName(this, BlockAdmin::class.java))
    if (checkSelfPermission(Manifest.permission.WRITE_SECURE_SETTINGS) != PackageManager.PERMISSION_GRANTED) return
    try {
        val me = ComponentName(this, BlockService::class.java)
        val left = Settings.Secure.getString(contentResolver, Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES).orEmpty()
            .split(':').filter { it.isNotBlank() && ComponentName.unflattenFromString(it) != me }
        Settings.Secure.putString(contentResolver, Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES, left.joinToString(":"))
        if (left.isEmpty()) Settings.Secure.putInt(contentResolver, Settings.Secure.ACCESSIBILITY_ENABLED, 0)
    } catch (e: SecurityException) {
        Log.w(TAG, "Cannot turn the blocking service off", e)
    }
}

/** Calls [guardBlocks] whenever the enabled accessibility services or the automatic time settings change. */
class BlockGuard(private val context: Context) : ContentObserver(Handler(Looper.getMainLooper())) {
    override fun onChange(selfChange: Boolean) = context.guardBlocks()

    fun register() {
        val resolver = context.contentResolver
        resolver.registerContentObserver(Settings.Secure.getUriFor(Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES), false, this)
        resolver.registerContentObserver(Settings.Global.getUriFor(Settings.Global.AUTO_TIME), false, this)
        resolver.registerContentObserver(Settings.Global.getUriFor(Settings.Global.AUTO_TIME_ZONE), false, this)
    }

    fun unregister() = context.contentResolver.unregisterContentObserver(this)
}

/**
 * Keeps blocked apps and sites closed. A blocked app goes home as soon as its window shows, whatever opened it (the
 * launcher, Recents, a notification, a call, another app). A browser on a blocked site goes back, or home when that
 * leaves it there; a browser whose address bar cannot be read goes home too. During a block Ajustes' pages that would
 * undo it (this app's info, its accessibility and admin pages, notification access, date and time) go home as well.
 * It reads which app is in front and, during a block with sites, a browser's own views: never a page, and keeps nothing.
 */
class BlockService : AccessibilityService() {
    private val handler = Handler(Looper.getMainLooper())
    // Separate, so one pending never holds another back: a look after window changes, at a block's start or end, and
    // when a browser's moment to show its address bar is up.
    private val check = Runnable { check() }
    private val atChange = Runnable { check() }
    private val grace = Runnable { check() }
    private val onRules: () -> Unit = { handler.post(check) }
    private var running = false // a block is running: only then do window changes matter
    private var front: String? = null
    private var enteredAt = 0L // uptime the app in front came there
    private var hosts: Set<String>? = null // what the browser in front last showed, since it came there
    private var read = false // its address bar was read since it came there
    private var left: Triple<String, Set<String>?, Boolean>? = null // the app last in front, what was read in it
    private var leftAt = 0L
    private var lastBounce = Long.MIN_VALUE / 2
    private var lastBack: Pair<String, Long>? = null
    private var settingsPage: CharSequence? = null // the class of Ajustes' latest window
    private var browsers: Set<String>? = null
    private var safe: Set<String>? = null // [unblockable], read again when packages change
    private var guard: BlockGuard? = null
    private var notice: View? = null

    private val receiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            if (intent.action?.startsWith("android.intent.action.PACKAGE_") == true) {
                browsers = null
                safe = null
            }
            handler.post(check)
        }
    }

    override fun onServiceConnected() {
        BlockStore.init(this)
        BlockStore.listen(onRules)
        guard = BlockGuard(this).also { it.register() }
        registerReceiver(receiver, IntentFilter().apply {
            addAction(Intent.ACTION_SCREEN_ON)
            addAction(Intent.ACTION_USER_PRESENT)
            addAction(Intent.ACTION_TIME_CHANGED)
            addAction(Intent.ACTION_TIMEZONE_CHANGED)
        }, RECEIVER_NOT_EXPORTED)
        registerReceiver(receiver, IntentFilter().apply {
            addAction(Intent.ACTION_PACKAGE_ADDED)
            addAction(Intent.ACTION_PACKAGE_REMOVED)
            addAction(Intent.ACTION_PACKAGE_CHANGED)
            addDataScheme("package")
        }, RECEIVER_NOT_EXPORTED)
        handler.post(check)
    }

    override fun onDestroy() {
        BlockStore.unlisten(onRules)
        guard?.unregister()
        runCatching { unregisterReceiver(receiver) }
        handler.removeCallbacksAndMessages(null)
        notice?.let { runCatching { getSystemService(WindowManager::class.java).removeView(it) } }
        super.onDestroy()
    }

    override fun onInterrupt() = Unit

    override fun onAccessibilityEvent(event: AccessibilityEvent) {
        if (event.eventType == AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED) {
            if (event.packageName?.toString() == SETTINGS) settingsPage = event.className
            if (!running) return
            handler.removeCallbacks(check)
            handler.post(check)
        } else if (running && !handler.hasCallbacks(check)) {
            handler.postDelayed(check, CONTENT_DELAY_MS)
        }
    }

    private fun check() {
        handler.removeCallbacks(check)
        handler.removeCallbacks(atChange)
        handler.removeCallbacks(grace)
        val rules = BlockStore.rules
        val now = BlockStore.now()
        // Look again when a block starts or ends: what is already open when one starts is caught then.
        nextChange(rules, now)?.let { handler.postDelayed(atChange, Duration.between(now, it).toMillis().coerceAtLeast(1000)) }
        val runs = rules.filter { it.activeAt(now) }
        running = runs.isNotEmpty()
        if (!running) {
            front = null // so the app in front when one starts gets its moment from then
            return
        }
        applicationContext.guardBlocks() // the time settings, now that one runs
        val windows = try {
            windows.filter { it.type == AccessibilityWindowInfo.TYPE_APPLICATION }
        } catch (e: RuntimeException) { // the connection is going away
            return
        }
        val focused = windows.firstOrNull { it.isFocused } ?: windows.firstOrNull { it.isActive } ?: return
        val root = focused.root ?: return
        val pkg = root.packageName?.toString() ?: return
        if (pkg != front) {
            val t = SystemClock.uptimeMillis()
            // Back to the browser left a moment ago (a dialog of another app was over it): its page is the one read.
            val back = left?.takeIf { it.first == pkg && t - leftAt < BACK_SOON_MS }
            front?.let { left = Triple(it, hosts, read) }
            leftAt = t
            front = pkg
            enteredAt = t
            hosts = back?.second
            read = back?.third == true
        }
        if (pkg == SETTINGS) return protectSettings(focused, root, rules, now)
        if (pkg in (safe ?: unblockable().also { safe = it })) return
        BlockStore.appBlockedUntil(pkg, now)?.let { return bounce(GLOBAL_ACTION_HOME, R.string.blocked_app_until, it) }
        if (runs.any { it.sites.isNotEmpty() } && pkg in (browsers ?: browserPackages().also { browsers = it })) {
            checkSite(pkg, windows.mapNotNull { it.root }.filter { it.packageName?.toString() == pkg }, rules, now)
        }
    }

    private fun checkSite(pkg: String, roots: List<AccessibilityNodeInfo>, rules: List<Rule>, now: LocalDateTime) {
        val height = getSystemService(WindowManager::class.java).maximumWindowMetrics.bounds.height()
        val address = readAddress(barNodes(roots), height)
        when (address) {
            is Address.Site -> {
                hosts = address.hosts
                read = true
                BlockStore.noteRead(pkg)
            }
            Address.Own -> read = true
            Address.Typing, Address.Unknown -> Unit // the page is still the one last read
        }
        hosts?.let { shown ->
            val until = BlockStore.siteBlockedUntil(shown, now) ?: return@let
            hosts = null
            // Back leaves the page; if a blocked one is still there soon after (its tab opened on it), home.
            val again = lastBack?.let { (p, at) -> p == pkg && SystemClock.uptimeMillis() - at < BACK_AGAIN_MS } == true
            if (!again) lastBack = pkg to SystemClock.uptimeMillis()
            return bounce(if (again) GLOBAL_ACTION_HOME else GLOBAL_ACTION_BACK, R.string.blocked_site_until, until)
        }
        if (read) return
        // No address bar since it came to the front (hidden while scrolling, or one this cannot read): a moment, then home.
        val waited = SystemClock.uptimeMillis() - enteredAt
        if (waited < UNREAD_GRACE_MS) {
            handler.postDelayed(grace, UNREAD_GRACE_MS - waited)
            return
        }
        val until = blockedUntil(rules, now) { it.sites.isNotEmpty() } ?: return
        bounce(GLOBAL_ACTION_HOME, R.string.blocked_browser_unread, until)
    }

    /** The browser's own views in [roots]: the page's are skipped whole, so a long page costs nothing. */
    private fun barNodes(roots: List<AccessibilityNodeInfo>): List<BarNode> {
        val out = ArrayList<BarNode>()
        val queue = ArrayDeque(roots)
        val bounds = Rect()
        while (queue.isNotEmpty() && out.size < MAX_NODES) {
            val n = queue.removeFirst()
            val web = n.className?.contains("WebView") == true || n.extras?.containsKey("AccessibilityNodeInfo.chromeRole") == true
            if (web) continue
            n.getBoundsInScreen(bounds)
            out += BarNode(n.viewIdResourceName?.substringAfter('/'), n.text, n.contentDescription, n.isEditable, n.isFocused, bounds.exactCenterY(), false)
            for (i in 0..<n.childCount) n.getChild(i)?.let(queue::addLast)
        }
        return out
    }

    /**
     * Sends Ajustes home from a page that would undo a block: any page about this app (its header names it: info,
     * storage, notifications), the page that turns its admin off (Desinstalar leads there), the pages of its own
     * services, notification access, and date and time. Found by window class and title; other makers may differ.
     */
    private fun protectSettings(window: AccessibilityWindowInfo, root: AccessibilityNodeInfo, rules: List<Rule>, now: LocalDateTime) {
        val page = settingsPage?.toString().orEmpty()
        val me = getString(R.string.app_name)
        fun shows(id: String) = root.findAccessibilityNodeInfosByViewId("$SETTINGS:id/$id").any { it.text?.toString() == me }
        val locked = shows("entity_header_title") || shows("admin_name") ||
            (page.endsWith("SubSettings") && window.title?.toString() in setOf(me, getString(R.string.blocks_service))) ||
            "NotificationAccessSettings" in page || "DateTimeSettings" in page
        if (!locked) return
        val until = blockedUntil(rules, now) ?: return
        bounce(GLOBAL_ACTION_HOME, R.string.blocked_settings_until, until)
    }

    private fun bounce(action: Int, text: Int, until: LocalDateTime) {
        val t = SystemClock.uptimeMillis()
        if (t - lastBounce < BOUNCE_GAP_MS) return
        lastBounce = t
        performGlobalAction(action)
        notice(getString(text, untilText(until)))
    }

    /**
     * Says why, as a toast would: Android drops a background app's toasts while its notifications are off (Android 13
     * on, this app asks for none), so it is an accessibility overlay, which needs no permission. Read out by TalkBack.
     */
    private fun notice(text: String) {
        val wm = getSystemService(WindowManager::class.java)
        notice?.let { runCatching { wm.removeView(it) } }
        val dp = resources.displayMetrics.density
        val view = TextView(this).apply {
            this.text = text
            setTextColor(Color.WHITE)
            textSize = 15f
            gravity = Gravity.CENTER
            setPadding((20 * dp).toInt(), (12 * dp).toInt(), (20 * dp).toInt(), (12 * dp).toInt())
            background = GradientDrawable().apply {
                cornerRadius = 24 * dp
                setColor(0xEE202020.toInt())
            }
            accessibilityLiveRegion = View.ACCESSIBILITY_LIVE_REGION_POLITE
            maxWidth = minOf(resources.displayMetrics.widthPixels - (32 * dp).toInt(), (480 * dp).toInt())
        }
        val params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE,
            PixelFormat.TRANSLUCENT,
        ).apply {
            this.gravity = Gravity.BOTTOM or Gravity.CENTER_HORIZONTAL
            y = (96 * dp).toInt()
        }
        try {
            wm.addView(view, params)
        } catch (e: RuntimeException) { // the connection is going away
            Log.w(TAG, "Cannot show a notice", e)
            return
        }
        notice = view
        handler.postDelayed({
            if (notice === view) {
                runCatching { wm.removeView(view) }
                notice = null
            }
        }, NOTICE_MS)
    }

    private companion object {
        const val SETTINGS = "com.android.settings"
    }
}

/**
 * During a block, keeps its apps quiet: their notifications are snoozed until it ends, when they come back, and their
 * media sessions are paused, again if they resume. It reads a notification's package and key only, never its content.
 * Its reconnection after a force stop is also what turns the blocking service back on ([guardBlocks]).
 */
class BlockListener : NotificationListenerService() {
    private val handler = Handler(Looper.getMainLooper())
    private val recheck = Runnable { recheck() }
    private val onRules: () -> Unit = { handler.post(recheck) }
    // Held, not just their tokens: a controller nothing holds is collected, and its callback stops with it.
    private val watched = HashMap<MediaSession.Token, Pair<MediaController, MediaController.Callback>>()
    private var guard: BlockGuard? = null
    private val sessionsChanged = MediaSessionManager.OnActiveSessionsChangedListener { it?.let(::sessions) }

    override fun onListenerConnected() {
        BlockStore.init(this)
        guardBlocks()
        guard = BlockGuard(this).also { it.register() }
        BlockStore.listen(onRules)
        try {
            getSystemService(MediaSessionManager::class.java).addOnActiveSessionsChangedListener(sessionsChanged, ComponentName(this, BlockListener::class.java), handler)
        } catch (e: SecurityException) { // access revoked as it connected
            Log.w(TAG, "No media sessions", e)
        }
        recheck()
    }

    override fun onListenerDisconnected() {
        BlockStore.unlisten(onRules)
        guard?.unregister()
        guard = null
        runCatching { getSystemService(MediaSessionManager::class.java).removeOnActiveSessionsChangedListener(sessionsChanged) }
        handler.removeCallbacksAndMessages(null)
        watched.values.forEach { (c, callback) -> c.unregisterCallback(callback) }
        watched.clear()
    }

    override fun onNotificationPosted(sbn: StatusBarNotification, rankingMap: RankingMap?) = snoozeIfBlocked(sbn, BlockStore.now())

    /** At connection, on each change of the blocks, and when one starts or ends: what was already there is caught too. */
    private fun recheck() {
        handler.removeCallbacks(recheck)
        val now = BlockStore.now()
        nextChange(BlockStore.rules, now)?.let { handler.postDelayed(recheck, Duration.between(now, it).toMillis().coerceAtLeast(1000)) }
        try {
            activeNotifications?.forEach { snoozeIfBlocked(it, now) }
            sessions(getSystemService(MediaSessionManager::class.java).getActiveSessions(ComponentName(this, BlockListener::class.java)))
        } catch (e: SecurityException) { // disconnected meanwhile
            Log.w(TAG, "Recheck failed", e)
        }
    }

    private fun snoozeIfBlocked(sbn: StatusBarNotification, now: LocalDateTime) {
        if (sbn.packageName == packageName) return
        val until = BlockStore.appBlockedUntil(sbn.packageName, now) ?: return
        snoozeNotification(sbn.key, Duration.between(now, until).toMillis().coerceAtLeast(1000))
    }

    private fun sessions(controllers: List<MediaController>) {
        for (c in controllers) {
            if (c.sessionToken !in watched) {
                val callback = object : MediaController.Callback() {
                    override fun onPlaybackStateChanged(state: PlaybackState?) = pauseIfBlocked(c)
                    override fun onSessionDestroyed() {
                        watched.remove(c.sessionToken)
                    }
                }
                watched[c.sessionToken] = c to callback
                c.registerCallback(callback, handler)
            }
            pauseIfBlocked(c)
        }
    }

    private fun pauseIfBlocked(c: MediaController) {
        if (c.playbackState?.state == PlaybackState.STATE_PLAYING && BlockStore.appBlockedUntil(c.packageName) != null) c.transportControls.pause()
    }
}

/** No policies: an app with an active device admin cannot be uninstalled until it is turned off, which a block keeps out of reach. */
class BlockAdmin : DeviceAdminReceiver() {
    override fun onDisableRequested(context: Context, intent: Intent): CharSequence = context.getString(R.string.blocks_admin_disable)
}
