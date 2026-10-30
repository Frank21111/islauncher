# Launcher

Personal Android home-screen app that replaces Nova Launcher. Kotlin + Jetpack Compose, single Gradle module at the repo root.

## Invariant: no network

A launcher sees every app the owner opens, so it must never be able to send data anywhere.
`src/main/AndroidManifest.xml` strips `android.permission.INTERNET` with `tools:node="remove"`, which also
blocks libraries from adding it. Never remove that line or add a feature that needs the network. After adding
a dependency, confirm with `apkanalyzer manifest permissions build/outputs/apk/release/launcher-release.apk`.
Cloud backup is off too (`allowBackup="false"`). The layout backups (manual and weekly) go through the system file
picker with `EXTRA_LOCAL_ONLY`, so a cloud provider cannot be chosen: keep that extra on any new picker that writes.

## Commands

- Build and install on the USB-connected phone: `scripts/install.sh`. Not `./gradlew installRelease`: an update that kills
  the running home app can make Android drop the "always" home choice (the HOME role stays, the choice doesn't, so
  Home asks which launcher to use). The script stops the app first and warns if the choice was lost anyway.
- Unit tests: `./gradlew testDebugUnitTest`
- Make it the default: Ajustes → Apps → Apps predeterminadas → App de inicio.

Release builds are signed with this machine's debug key (`~/.android/debug.keystore`). Losing it means
uninstalling to update, which wipes the saved layout.

Other permissions: `EXPAND_STATUS_BAR` (swipe down opens notifications), `QUERY_ALL_PACKAGES` (the widget
picker must see non-exported widget receivers, which a `<queries>` block misses) and `REQUEST_DELETE_PACKAGES`
(Desinstalar: `ACTION_DELETE`, Android's own confirmation; a normal permission, granted at install).

## Layout

- `Model.kt`: pure logic. `Layout` holds dock pages (`DockIcon`s: tap/up/down targets), desktop pages of `Item`s
  (icons and widgets on half cells), drawer tabs, hidden apps, the double-tap target and the settings: desktop and
  drawer grids, dock icons per page, dock wrap-around, haptics, the app the poison pause offers `instead`, the number
  format (`decimal`; null follows the phone's country), `hideSuspended` (Apps pausadas: Atenuar / Ocultar). Every dock page keeps at least `dockSize` icons; those past
  it stay saved, hidden. Shrinking the grid (`withGrid`) or removing a page (`withoutPage`) moves what no longer fits
  to the nearest free spot, else another page, else a new one: nothing is dropped. `withPageMoved` reorders desktop
  pages and keeps `defaultPage` on the main one (`shifted` reorders any list: dock pages, tabs); the item menu's
  "Mover a otra página" is `moved` with the item's own cell. `audit` is Ajustes → Ordenar: broken icons, dock targets
  and widgets (by the `alive` checks it is given), the same app by `packageKey` across pages or dock and page, and
  empty pages, each a `Place`; it removes nothing (its `size` leaves twins out: they may be on purpose). `withoutApp`
  drops an uninstalled package's desktop icons and dock targets. Also JSON persistence,
  `finder` (app lookup tolerant of launcher-alias changes), `tabApps`, `freeCell`, accent-insensitive search (an
  optional `rank` orders matches within the same quality), the `decayed` use score (`useRank`: none below `MIN_USE`;
  `kept`; `countedSince`), `unused` (Sin abrir hace tiempo: null until opens have been counted `UNUSED_DAYS`),
  `widgetPackages`, `menuShortcuts` (Launcher3's
  pick of the shortcuts an app's menu lists) and search's `answer`: `calc`, unit conversions, phone numbers
  (`whatsAppNumber` adds Argentina's mobile 9), links and e-mail addresses, numbers read and shown by `formatNumber`
  with the chosen decimal mark.
  Unit-tested in `src/test`.
- `Nova.kt`: pure converter from Nova's `favorites`/`drawer_groups`/`appgroups` rows and prefs (grids included) to a
  `Layout`. `appgroups` group -1 is Nova's hidden apps. `NovaImport.kt` reads the `.novabackup` zip (nova.db + nova.xml).
- `Launcher.kt`: the activity (app list, app shortcuts, widget host and bind/configure flow, backups, Nova import),
  `Layouts` (the shared layout state, also written by `PinActivity`), `PinActivity` (pinned shortcuts; asks the owner
  first, since any foreground app can send a request) and `addShortcut`, which both it and the app menus' + use to
  place a pinned shortcut and save its icon.
- `Ui.kt`: Compose UI. Desktop pager with icons and widgets (long press opens the item menu; dragging moves the
  item, drawn from the layer it keeps recording, and holding it at a side flips the page), a wrap-around dock pager, Nova-style
  search pill. The drawer has search (with "Buscar con" providers), one swipeable page per tab and a fast scroller.
  All menus and dialogs go through one `Popup` state, so `closeAll()` closes everything. Every vibration goes
  through `rememberHaptic`, which checks `Layout.haptics` (so `combinedClickable` gets `hapticFeedbackEnabled = false`
  and text fields sit in `GatedHaptics`, as they vibrate on their own when selecting text). Ajustes (desktop menu) is a
  full-screen overlay styled after Nova's settings (`novaColors()` greys the whole app's Material scheme; glyphs are
  Canvas line drawings, no icon library); it survives leaving (so the file pickers it opens come back to it) and
  closes on home. Home stays composed under it at alpha 0, so the clear preview strips show the wallpaper:
  removing Home would re-parent the cached widget views. Ordenar is `Section.TIDY`, a sub-screen of Escritorio (back
  returns there; it is left off the main list). Its list comes from `Launcher.audit`, worked out off the main thread
  (`rememberAudit`: binder calls) on open and after each change; an app counts as gone only when the app list lacks it and `getApplicationInfo` fails for its user, so a
  paused work profile's apps (LauncherApps still lists them) and an app mid-update are never offered. Its Quitar goes
  through `remove(place)`: `remove(page, item)` (widgets free their ids) or `updateDock` (the target set to none).
  Sin abrir hace tiempo is `Section.UNUSED`, the same kind of sub-screen of Cajón de apps, from `Launcher.unusedApps`
  (removable apps, one per package, no poisons, none with a widget on the desktop; the install day is `App.installed`,
  read in `loadApps` off the main thread and only for the main profile, as `firstInstallTime` reads as this user).
  With `hideSuspended`, `Launcher.hides` keeps a paused app's desktop icons and dock taps from being drawn (the item
  stays saved, so its spot stays taken, though a long press there opens the desktop menu) and `Launcher.tabbed` leaves
  it out of the tabs; search lists `apps`, and one dragged from there is placed with a toast saying it is paused.
- The layout is stored as JSON in SharedPreferences `launcher`, key `layout`. An unreadable layout is kept under
  `layout.corrupt` instead of being overwritten. Prefs `opens` counts today's opens of poison apps; prefs `usage`
  holds each app's decayed use score (`<package>#<user>` to `<score> <day>`, the day of its last open), kept out of
  the layout so backups skip it; a faded entry is dropped only once `UNUSED_DAYS` old (`kept`), so a missing one means
  no open in that long, and key `tracked` is the day counting began (written once, by `countedSince`); prefs `backup` holds the weekly backup folder (a persisted SAF tree URI) and when it last ran.

Features worth knowing before editing: "venenos" (`Layout.poisons`) open only after a pause that grows with each
open today; every way the launcher opens an app (`start`, `open`, `startIntent`) goes through `pauseIfPoison`, so
a new one must too. The pause can offer `Layout.instead` (opened with `open`, so a poison there pauses as well;
`Popup.Pause` is keyed so that pause starts its own countdown). Known gaps: notifications, recents, and an app
picked inside a system chooser. `pauseIfPoison` is the one place an open is counted (`countUse`, only once `open`
returns true: it started), so a failed open never is; a poison's open keeps only its day (for Sin abrir hace tiempo),
never a score, and `usage` ranks a poison 0 anyway. Desinstalar (`Launcher.uninstall`, in the drawer, desktop and dock
app menus and Sin abrir hace tiempo; hidden for system apps by `App.removable`) is not an open: it skips
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
A tap goes through `open`, so a poison pauses. Its + pins through `Launcher.pin`: `pinShortcuts` replaces the
package's whole pinned list, so it passes the ids already pinned (a browser's web shortcuts) plus the new one, then
checks the new one took (it skips an id the app has dropped).

Ajustes → Copia de seguridad imports a Nova backup; imported widgets start unbound (`widgetId` 0) and bind when
tapped. Pinned shortcuts belong to the home app that pinned them: shortcuts Nova pinned must be re-added from their
app. Browsers bake their own badge into the web shortcuts they pin, so those get no second one.

UI strings live in `src/main/res`: `values/strings.xml` is English (the default), `values-es/strings.xml` Spanish
(rioplatense, voseo); new UI text needs both. Code comments are English.
