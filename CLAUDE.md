# Islauncher

Private Android home-screen app that replaces Nova Launcher. Kotlin + Jetpack Compose, single Gradle module at the repo root.

## Invariant: no network

A launcher sees every app the owner opens, so it must never be able to send data anywhere.
`src/main/AndroidManifest.xml` strips `android.permission.INTERNET` with `tools:node="remove"`, which also
blocks libraries from adding it. Never remove that line or add a feature that needs the network. After adding
a dependency, confirm with `apkanalyzer manifest permissions build/outputs/apk/release/islauncher-release.apk`.
Cloud backup is off too (`allowBackup="false"`). The layout backups (manual and weekly) go through the system file
picker with `EXTRA_LOCAL_ONLY`, so a cloud provider cannot be chosen: keep that extra on any new picker that writes.
Blocks see more than any other part (an accessibility service can read the screen, notification access every
notification): they read only the app in front, a browser's own views (never a page: `barNodes` skips its subtree)
and a notification's package and key, and log or keep none of it beyond the blocks and each browser's last read day.

## Commands

- Build and install on the USB-connected phone: `scripts/install.sh`. Not `./gradlew installRelease`: an update that kills
  the running home app can make Android drop the "always" home choice (the HOME role stays, the choice doesn't, so
  Home asks which launcher to use). The script stops the app first and warns if the choice was lost anyway.
- Unit tests: `./gradlew testDebugUnitTest`
- Build a GitHub release APK: `scripts/release.sh` (tests, signature and no-INTERNET checks, SHA-256 for the notes).
- Optional reinforced block setup on the USB-connected phone: `scripts/blocks-setup.sh` (secure settings permission, device admin,
  notification access, accessibility service; no account removed, unlike a device owner).
- Build machine: JDK 21 (`JAVA_HOME`) and the SDK in `~/Android/Sdk` (`local.properties`), platform 37.0.
- Make it the default: Ajustes → Apps → Apps predeterminadas → App de inicio.

Release builds are signed with the key in `~/.config/islauncher/` (`release.p12`, `password`), which never enters the
repo; losing it means users must uninstall to update, which wipes their layout. Without it they fall back to this
machine's debug key, so anyone can build from source, and `scripts/release.sh` refuses to ship that build.

A restored backup or Nova import goes through `restored`: past `MAX_ITEMS`, `MAX_TABS` or `MAX_TEXT` the whole file is
rejected before anything is saved, because a saved layout that cannot render would fail on every start.

Other permissions: `EXPAND_STATUS_BAR` (swipe down opens notifications), `QUERY_ALL_PACKAGES` (the widget
picker must see non-exported widget receivers, which a `<queries>` block misses), `REQUEST_DELETE_PACKAGES`
(Desinstalar: `ACTION_DELETE`, Android's own confirmation; a normal permission, granted at install) and
`WRITE_SECURE_SETTINGS` (blocks: only adb grants it; `guardBlocks` turns the blocking service back on with it and keeps
the time automatic during a block).

## Layout

- `Model.kt`: pure logic. `Layout` holds dock pages (`DockIcon`s: tap/up/down targets), desktop pages of `Item`s
  (icons and widgets on half cells), drawer tabs, hidden apps, the double-tap target and the settings: desktop and
  drawer grids, dock icons per page, dock wrap-around, the main dock page (`defaultDockPage`: the dock goes back to it
  whenever the home screen is left, `dockResets` in `onStop`), `showDock` and `showSearchBar` (a hidden dock takes no
  drops), haptics, the app the poison pause offers `instead`, the number
  format (`decimal`; null follows the phone's country), `hideSuspended` (Apps pausadas: Atenuar / Ocultar). Every dock page keeps at least `dockSize` icons; those past
  it stay saved, hidden. Shrinking the grid (`withGrid`) or removing a page (`withoutPage`) moves what no longer fits
  to the nearest free spot, else another page, else a new one: nothing is dropped. `withPageMoved` reorders desktop
  pages and keeps `defaultPage` on the main one, as `withDockPageMoved` and `withoutDockPage` keep `defaultDockPage`
  (`shifted` reorders any list, tabs too); the item menu's
  "Mover a otra página" is `moved` with the item's own cell. A widget's resize frame drags one `Edge` with `stretched`
  (half cells, within the provider's `Limits`, the page and the items beside it; a limit already broken only stops it
  getting worse) and commits with `resized` (refused off the page or on a new overlap: one it had already stays). `placed` puts a thrown item back. `audit` is Ajustes → Ordenar: broken icons, dock targets
  and widgets (by the `alive` checks it is given), the same app by `packageKey` across pages or dock and page, and
  empty pages, each a `Place`; it removes nothing (its `size` leaves twins out: they may be on purpose). `withoutApp`
  drops an uninstalled package's desktop icons and dock targets. Also JSON persistence, `opensGuide` (the guide
  opens by itself only on a fresh install's first launch),
  `finder` (app lookup tolerant of launcher-alias changes), `tabApps`, `freeCell`, accent-insensitive search (an
  optional `rank` orders matches within the same quality), the `decayed` use score (`useRank`: none below `MIN_USE`;
  `kept`; `countedSince`), `unused` (Sin abrir hace tiempo: null until opens have been counted `UNUSED_DAYS`),
  `widgetPackages`, `menuShortcuts` (Launcher3's
  pick of the shortcuts an app's menu lists) and search's `answer`: `calc`, unit conversions, phone numbers
  (`whatsAppNumber` adds Argentina's mobile 9), links and e-mail addresses, numbers read and shown by `formatNumber`
  with the chosen decimal mark.
  Unit-tested in `src/test`.
- `Blocks.kt`: pure logic of blocks ("bloqueos"). A `Rule` has packages, sites (hosts, `site` normalises what is typed,
  IDN to ASCII) and weekly `Span`s (days a span starts on, start and end minutes; an end before the start runs into the
  next day, midnight as an end is 1440). `activeAt`, `blockedUntil` (rules back to back count as one), `nextChange`,
  `problemSaving` (something to block, valid spans, `FREE_MINUTES` free in a row each week, and while running only
  `tightened`), `canRemove`. `readAddress` turns a browser's `BarNode`s into an `Address` (Site, Own, Typing, Unknown):
  bar ids (url, omnibar, location_bar, address, domain) in the top quarter or bottom fifth, text and description both,
  bidi marks stripped; only a focused field of the browser's own is typing. `trustedTime` ignores a wall-clock jump
  during a block. The fixtures in `BlocksTest` are real address bars of seven browsers (Chrome, Brave, Edge, Opera,
  DuckDuckGo, Samsung Internet, Firefox), read on a Motorola with Android 13.
- `Blocking.kt`: `BlockStore` (prefs `blocks`: key `rules`, `anchor` for the clock, `read.<pkg>` the day a browser's
  bar was last read; never in the layout, so backups and Nova imports leave blocks alone), `BlockService` (the
  accessibility service: a blocked app goes home whatever opened it; a browser on a blocked site goes back, or home if
  that leaves it there; a browser not read within `UNREAD_GRACE_MS` of coming to the front goes home; during a block,
  Ajustes' pages that would undo it go home, found by class and title), `BlockListener` (snoozes a blocked app's
  notifications until the block ends and pauses its media sessions, holding their controllers; its reconnection
  after a force stop is what turns the service back on), `BlockAdmin` (no policies: an active admin cannot be
  uninstalled), `guardBlocks`/`BlockGuard` (rewrite only what is missing, at most once a second, retried after; a write
  fires the observers again), `blockSetup`, `unblockable` (this app, Ajustes, the phone and SMS apps).
- `Nova.kt`: pure converter from Nova's `favorites`/`drawer_groups`/`appgroups` rows and prefs (grids included) to a
  `Layout`. `appgroups` group -1 is Nova's hidden apps. `NovaImport.kt` reads the `.novabackup` zip (nova.db + nova.xml).
- `Launcher.kt`: the activity (app list, app shortcuts, widget host and bind/configure flow, backups, Nova import),
  `Layouts` (the shared layout state, also written by `PinActivity`), `PinActivity` (pinned shortcuts; asks the owner
  first, since any foreground app can send a request) and `addShortcut`, which both it and the app menus' + use to
  place a pinned shortcut and save its icon.
- `Ui.kt`: Compose UI. Desktop pager with icons and widgets (long press opens the item menu; dragging moves the
  item, drawn from the layer it keeps recording, and holding it at a side flips the page), a wrap-around dock pager, Nova-style
  search pill. A drag's `VelocityTracker` follows the pointer in root coordinates (a page flip adds no speed) with
  Android's own rules: the release adds no sample, and one `HELD_STILL_MS` after the last move resets it, so a finger
  held still makes no throw; a cancel (`longPressDrag` tells Compose's synthetic one by its consumed release) is no
  drop. An app released over the dock docks however fast; otherwise, released over `THROW_DP_PER_S`, a desktop item is
  thrown (`Mover.flight` flies its layer out for `THROW_MS`, then `Launcher.throwAway`) and a drawer one cancelled.
  `Launcher.thrown` keeps it, its widget id still allocated, while Home's `SnackbarHost` (a polite live region with
  the accessibility timeout) offers Deshacer (`undoThrow`); `settle` frees it through `release` on the bar's timeout,
  the next throw, opening Ajustes (it can remove or reorder the pages) and `onStop`; uninstalling its app drops a
  thrown icon. "Cambiar tamaño" (bound widgets whose resizeMode is not none) is `Popup.Resize`:
  `ResizeFrame` covers all of Home, so nothing under it takes touches (Home's semantics are cleared under it), with
  handles only on the sides resizeMode allows, kept on the desktop, each with Agrandar/Achicar actions for TalkBack;
  `Launcher.resizeLimits` turns the provider's sizes into cells with `resizeSpan`, as `widgetCells` rounds. A bound widget is keyed by its
  id in `DesktopPage`, so a resize or a move on its page keeps its cached view in place. The drawer has search (with "Buscar con" providers), one swipeable page per tab and a fast scroller.
  All menus and dialogs go through one `Popup` state, so `closeAll()` closes everything. Every vibration goes
  through `rememberHaptic`, which checks `Layout.haptics` (so `combinedClickable` gets `hapticFeedbackEnabled = false`
  and text fields sit in `GatedHaptics`, as they vibrate on their own when selecting text). Ajustes (desktop menu) is a
  full-screen overlay styled after Nova's settings (`novaColors()` greys the whole app's Material scheme; glyphs are
  Canvas line drawings, no icon library); it survives leaving (so the file pickers it opens come back to it) and
  closes on home. Home stays composed under it at alpha 0, so the clear preview strips show the wallpaper:
  removing Home would re-parent the cached widget views. The sections opened stack up, each with its scroll, so back
  returns to the one before. Guía (`Section.GUIDE`, first on the main list) is `GuideSettings`: what the app does by
  part, a `Tip` each, whose feature's section (if any) it opens. It must say what the code does: a new or changed
  feature changes its entry, in both locales. On a fresh install's first launch (`Layouts.guideOnLaunch`) Ajustes
  opens by itself on it (`Launcher.welcome`: an intro line on top, and back goes home). Ordenar is `Section.TIDY`, a
  sub-screen of Escritorio (also opened from the guide; it is left off the main list). Its list comes from `Launcher.audit`, worked out off the main thread
  (`rememberAudit`: binder calls) on open and after each change; an app counts as gone only when the app list lacks it and `getApplicationInfo` fails for its user, so a
  paused work profile's apps (LauncherApps still lists them) and an app mid-update are never offered. Its Quitar goes
  through `remove(place)`: `remove(page, item)` (widgets free their ids) or `updateDock` (the target set to none).
  Sin abrir hace tiempo is `Section.UNUSED`, the same kind of sub-screen of Cajón de apps, from `Launcher.unusedApps`
  (removable apps, one per package, no poisons, none with a widget on the desktop; the install day is `App.installed`,
  read in `loadApps` off the main thread and only for the main profile, as `firstInstallTime` reads as this user).
  With `hideSuspended`, `Launcher.hides` keeps a paused app's desktop icons and dock taps from being drawn (the item
  stays saved, so its spot stays taken, though a long press there opens the desktop menu) and `Launcher.tabbed` leaves
  it out of the tabs; search lists `apps`, and one dragged from there is placed with a toast saying it is paused.
  Bloqueos is `Section.BLOCKS` (`BlocksSettings`): the three user-granted setup steps (read again on each `Launcher.resumes`), optional secure settings protection, the blocks,
  the browsers and whether their bar was read; `Popup.EditBlock` is the full-screen `BlockEditor`, whose trash on what
  a running block had only says so, and whose save checks `problemSaving` again as it saves.
- The layout is stored as JSON in SharedPreferences `launcher`, key `layout`; key `launched` marks the first launch, for
  `opensGuide`. An unreadable layout is kept under
  `layout.corrupt` instead of being overwritten. Prefs `opens` counts today's opens of poison apps; prefs `usage`
  holds each app's decayed use score (`<package>#<user>` to `<score> <day>`, the day of its last open), kept out of
  the layout so backups skip it; a faded entry is dropped only once `UNUSED_DAYS` old (`kept`), so a missing one means
  no open in that long, and key `tracked` is the day counting began (written once, by `countedSince`); prefs `backup` holds the weekly backup folder (a persisted SAF tree URI) and when it last ran.

Features worth knowing before editing: an app a running block holds does not open at all: `pauseIfPoison` checks
`Launcher.blocked` first (a toast says until when; never counted), and `startIntent` checks an app the list lacks by
package. "venenos" (`Layout.poisons`) open only after a pause that grows with each
open today; every way the launcher opens an app (`start`, `open`, `startIntent`) goes through `pauseIfPoison`, so
a new one must too. The pause can offer `Layout.instead` (opened with `open`, so a poison there pauses as well;
`Popup.Pause` is keyed so that pause starts its own countdown). Known gaps: notifications, recents, and an app
picked inside a system chooser. `pauseIfPoison` is the one place an open is counted (`countUse`, only once `open`
returns true: it started), so a failed open never is; a poison's open keeps only its day (for Sin abrir hace tiempo),
never a score, and `usage` ranks a poison 0 anyway. Desinstalar (`Launcher.uninstall`, in the drawer and desktop app
menus and Sin abrir hace tiempo; hidden for system apps by `App.removable`) is not an open: it skips
`pauseIfPoison` and is never counted. Once that package is removed (`onPackageRemoved`), `withoutApp` drops its icons
and dock targets; any other removal leaves them to Ordenar. `uses` is not snapshot state, so results do not reorder under the app opening. Only the drawer search
ranks by use; the tabs and the app pickers stay alphabetical. Search answers more than apps in one header,
`SearchAnswer`: a sum or a conversion (a tap copies it), or bubbles for a phone number (Call is `ACTION_DIAL`), link (only
when no app matches: "booking.com") or e-mail address, each opened through `startIntent`; nothing typed is kept. A phone-like query never also shows a sum, so
`answer` tries it first. `Launcher.decimal` is `Layout.decimal`, else the decimal mark of `Launcher.country` (SIM,
then network, then locale region; no permission needed).

Icons get their pressed feel from `PressScale`, passed as their clickable's `indication` (so long press, drag and
scrolling cancel it through the clickable); a new icon should take it too. Opens pass no `ActivityOptions`: an open
animation out of the icon was tried and removed, since from Android 15 QPR1 on a task launch takes them only with
`overrideTaskTransition` (`START_TASKS_FROM_RECENTS`, a system permission).

An app's long-press menu (desktop app icon, dock icon, drawer app) starts with `AppShortcuts`: `shortcutsOf` reads them
off the main thread, only while this is the home app and only for the main profile (`Target.Shortcut` keeps no user).
The menu opens once `rememberShortcuts` has them (it sits outside the `DropdownMenu`, whose content composes only while
shown), so rows never push the others down after it opens.
The dock's then has Configurar (`Popup.DockSetup`, `DockDialog`: a row each for the tap and the swipes, whose app
picker returns to the dialog after a pick or a cancel, `PickApp.back`; a slot with no app opens it straight away) and
Info de la app, no Desinstalar (Info de la app has it; an uninstall from there leaves the slot to Ordenar).
A tap goes through `open`, so a poison pauses. Its + pins through `Launcher.pin`: `pinShortcuts` replaces the
package's whole pinned list, so it passes the ids already pinned (a browser's web shortcuts) plus the new one, then
checks the new one took (it skips an id the app has dropped).

Ajustes → Copia de seguridad imports a Nova backup; imported widgets start unbound (`widgetId` 0) and bind when
tapped. Pinned shortcuts belong to the home app that pinned them: shortcuts Nova pinned must be re-added from their
app. Browsers bake their own badge into the web shortcuts they pin, so those get no second one.

UI strings live in `src/main/res`: `values/strings.xml` is English (the default), `values-es/strings.xml` Spanish
(rioplatense, voseo); new UI text needs both. Code comments are English.
