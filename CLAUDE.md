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

Other permissions: `EXPAND_STATUS_BAR` (swipe down opens notifications) and `QUERY_ALL_PACKAGES` (the widget
picker must see non-exported widget receivers, which a `<queries>` block misses).

## Layout

- `Model.kt`: pure logic. `Layout` holds dock pages (`DockIcon`s: tap/up/down targets), desktop pages of `Item`s
  (icons and widgets on half cells), drawer tabs, hidden apps, the double-tap target and the settings: desktop and
  drawer grids, dock icons per page, dock wrap-around. Every dock page keeps at least `dockSize` icons; those past
  it stay saved, hidden. Shrinking the grid (`withGrid`) or removing a page (`withoutPage`) moves what no longer
  fits to the nearest free spot, else another page, else a new one: nothing is dropped. Also JSON persistence,
  `finder` (app lookup tolerant of launcher-alias changes), `tabApps`, `freeCell`, accent-insensitive search.
  Unit-tested in `src/test`.
- `Nova.kt`: pure converter from Nova's `favorites`/`drawer_groups`/`appgroups` rows and prefs (grids included) to a
  `Layout`. `appgroups` group -1 is Nova's hidden apps. `NovaImport.kt` reads the `.novabackup` zip (nova.db + nova.xml).
- `Launcher.kt`: the activity (app list, widget host and bind/configure flow, backups, Nova import), `Layouts` (the shared
  layout state, also written by `PinActivity`) and `PinActivity` (pinned shortcuts; asks the owner first, since any
  foreground app can send a request).
- `Ui.kt`: Compose UI. Desktop pager with icons and widgets (long press opens the item menu; dragging moves the
  item, drawn from its last frame, and holding it at a side flips the page), a wrap-around dock pager, Nova-style
  search pill. The drawer has search (with "Buscar con" providers), one swipeable page per tab and a fast scroller.
  All menus and dialogs go through one `Popup` state, so `closeAll()` closes everything. Ajustes (desktop menu) is a
  full-screen overlay styled after Nova's settings (`novaColors()` greys the whole app's Material scheme; glyphs are
  Canvas line drawings, no icon library); it survives leaving (so the file pickers it opens come back to it) and
  closes on home. Home stays composed under it at alpha 0, so the clear preview strips show the wallpaper:
  removing Home would re-parent the cached widget views.
- The layout is stored as JSON in SharedPreferences `launcher`, key `layout`. An unreadable layout is kept under
  `layout.corrupt` instead of being overwritten. Prefs `opens` counts today's opens of poison apps; prefs `backup`
  holds the weekly backup folder (a persisted SAF tree URI) and when it last ran.

Features worth knowing before editing: "venenos" (`Layout.poisons`) open only after a pause that grows with each
open today; every way the launcher opens an app (`start`, `open`, `startIntent`) goes through `pauseIfPoison`, so
a new one must too. Known gaps: notifications, recents, and an app picked inside a system chooser. Search shows a calculator result (`calc`) when the query is a sum.

Ajustes → Copia de seguridad imports a Nova backup; imported widgets start unbound (`widgetId` 0) and bind when
tapped. Pinned shortcuts belong to the home app that pinned them: shortcuts Nova pinned must be re-added from their
app. Browsers bake their own badge into the web shortcuts they pin, so those get no second one.

UI strings live in `src/main/res`: `values/strings.xml` is English (the default), `values-es/strings.xml` Spanish
(rioplatense, voseo); new UI text needs both. Code comments are English.
