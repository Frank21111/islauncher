# Launcher

Personal Android home-screen app that replaces Nova Launcher. Kotlin + Jetpack Compose, single Gradle module at the repo root.

## Invariant: no network

A launcher sees every app the owner opens, so it must never be able to send data anywhere.
`src/main/AndroidManifest.xml` strips `android.permission.INTERNET` with `tools:node="remove"`, which also
blocks libraries from adding it. Never remove that line or add a feature that needs the network. After adding
a dependency, confirm with `apkanalyzer manifest permissions build/outputs/apk/release/launcher-release.apk`.
Cloud backup is off too (`allowBackup="false"`).

## Commands

- Build and install on the USB-connected phone: `./gradlew installRelease`
- Unit tests: `./gradlew testDebugUnitTest`
- Make it the default: Ajustes → Apps → Apps predeterminadas → App de inicio.

Release builds are signed with this machine's debug key (`~/.android/debug.keystore`). Losing it means
uninstalling to update, which wipes the saved layout.

Other permissions: `EXPAND_STATUS_BAR` (swipe down opens notifications) and `QUERY_ALL_PACKAGES` (the widget
picker must see non-exported widget receivers, which a `<queries>` block misses).

## Layout

- `Model.kt`: pure logic. `Layout` holds dock pages (5 `DockIcon`s each: tap/up/down targets), desktop pages of
  `Item`s (icons and widgets on a 5×5 grid with half cells), drawer tabs, hidden apps and the double-tap target.
  Also JSON persistence, `finder` (app lookup tolerant of launcher-alias changes), `tabApps`, `freeCell`,
  accent-insensitive search. Unit-tested in `src/test`.
- `Nova.kt`: pure converter from Nova's `favorites`/`drawer_groups`/`appgroups` rows and prefs to a `Layout`.
  `appgroups` group -1 is Nova's hidden apps. `NovaImport.kt` reads the `.novabackup` zip (nova.db + nova.xml).
- `Launcher.kt`: the activity (app list, widget host and bind/configure flow, Nova import), `Layouts` (the shared
  layout state, also written by `PinActivity`) and `PinActivity` (pinned shortcuts; asks the owner first, since any
  foreground app can send a request).
- `Ui.kt`: Compose UI. Desktop pager with icons and widgets (long press opens the item menu; dragging moves the
  item, drawn from its last frame, and holding it at a side flips the page), a wrap-around dock pager, Nova-style
  search pill. The drawer has search (with "Buscar con" providers), one swipeable page per tab and a fast scroller.
  All menus and dialogs go through one `Popup` state, so `closeAll()` closes everything.
- The layout is stored as JSON in SharedPreferences `launcher`, key `layout`. An unreadable layout is kept under
  `layout.corrupt` instead of being overwritten.

Imported widgets start unbound (`widgetId` 0) and bind when tapped. Pinned shortcuts belong to the home app
that pinned them: shortcuts Nova pinned must be re-added from their app once this launcher is the default.

UI strings are Spanish; code comments are English.
