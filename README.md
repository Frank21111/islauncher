# Launcher

A private home-screen app for Android 13 and newer, inspired by Nova Launcher. It has no network permission at all,
so it cannot send anything over the network.

It is not affiliated with or endorsed by Nova Launcher or its developers. It is written in Kotlin with Jetpack Compose
and depends only on AndroidX (Compose Material 3, Activity, Core), with no analytics, crash reporting or accounts.

The app is available in English and Spanish. It follows the system language, or the language picked for it in
Android's per-app language settings (Settings → System → Languages → App languages, or the app's info page; the
names vary by phone).

## Privacy

A launcher sees every app you have and every app you open, so this one is built so that it cannot leak that:

- **No INTERNET permission.** `src/main/AndroidManifest.xml` declares `android.permission.INTERNET` with
  `tools:node="remove"`, which strips it from the merged manifest even when a library asks for it. Without that
  permission Android gives the app no network sockets.
- **Check any build yourself:**
  `apkanalyzer manifest permissions build/outputs/apk/release/launcher-release.apk` (apkanalyzer comes with the
  Android SDK command-line tools). It lists the three permissions under [Permissions](#permissions), and INTERNET
  is not one of them.
- **No cloud backup.** `android:allowBackup="false"` keeps the app's data out of Android's cloud backup. Since
  Android 12 that setting does not stop a device-to-device transfer, so setting up a new phone from this one can
  copy the layout across.
- **Layout backups stay local.** Manual and weekly backups are written through the system file picker with
  `EXTRA_LOCAL_ONLY`, so cloud providers are not offered as a destination. A local folder that another app syncs
  is still synced by that app.
- **"Search with" hands your query to the app you tap.** The launcher itself sends nothing.
- **Everything else stays in the app's private storage.** That covers the layout (JSON in SharedPreferences),
  today's open counts for poison apps (older days are dropped), the weekly backup folder and the icons of pinned
  shortcuts. Uninstalling deletes all of it, but not the backup files you saved elsewhere.

## Features

**Home screen**

- Desktop pages with apps, shortcuts and widgets on a grid that places items in half-cell steps.
- Long-press an item for its menu (app info and remove, plus "set up again" on a widget), or drag it to move it.
  Holding a dragged item at the side of the screen flips to the page on that side.
- Long-press an empty spot to add a widget, change the wallpaper or open the settings.
- Widgets come from a searchable picker that lists every provider. A widget takes the size its app asks for, fitted
  to the grid, at the first free spot of the page. If it needs setup, its setup screen runs when you add it.
- Gestures:
  - Swipe up opens the app drawer.
  - Swipe down opens the notification shade.
  - Double tap opens an app of your choice, set in Settings → Gestures.
- The Home button goes back to the main page, and pressing it on the main page opens search.
- The search bar has buttons for the voice assistant and Google Lens, and shows today's date, which opens your
  calendar. There is no weather, because it would need the network.
- Apps paused by Digital Wellbeing are greyed out.

**Dock**

- Several dock pages, which wrap around from the last page to the first (you can turn that off).
- Each icon opens one app on tap and can open other apps on a swipe up or down. Long-press a slot to choose its
  three apps. An icon without its own swipe actions opens the drawer or the notification shade, as a swipe does
  elsewhere on the home screen.
- Drop an app from the drawer or the desktop onto an empty dock slot.

**App drawer**

- Tabs, swiped sideways like pages:
  - an all-apps tab, plus tabs of apps you pick
  - a picked tab can take its apps out of the all-apps tabs, as new tabs do by default
  - long-press a tab to rename it, add a tab or remove a picked one; the settings also reorder them
- Hidden apps are left out of every tab, but search still finds them.
- Accent-insensitive search that ranks prefix matches first. The keyboard's Go key opens the first result.
- A calculator: a query that is a sum, such as `125*3` or `(2+3)^2`, shows its result, and tapping the result copies
  it. It handles `x × ÷ ^ %` and parentheses. Both `.` and `,` count as the decimal point.
- A "search with" row that hands the query to Google, DuckDuckGo, Play Store, Spotify or Firefox Focus.
- A fast scroller. Pulling down at the top of the list closes the drawer.
- Each app's menu can add it to the desktop, toggle its tabs, hide it, mark it as a poison or open its app info.
  You can also drag an app onto the desktop or the dock.

**Poisons**

- Apps you mark as poisons open only after a pause: 10 seconds, plus 5 seconds for each time you already opened that
  app today. The pause screen shows today's count, and "go back" is the main button.
- The pause applies to every way the launcher opens an app: the drawer, desktop and dock icons, swipes, double tap,
  its shortcuts on the desktop, search providers and the search bar buttons.

**Pinned shortcuts**

- Supports "Add to home screen" from other apps, such as a browser's web shortcuts, while this is the default home
  app. You confirm each one, because any app in the foreground can send the request. It goes on the main page, or
  the first page with room.
- Shortcuts carry the badge of their app, except browser shortcuts, which come with a badge of their own.

**Settings**

- You can set the desktop grid, the drawer grid (its columns and how many rows fit on screen) and the number of dock
  icons per page, each from 2 to 12.
- Shrinking the desktop grid never deletes anything. Items that no longer fit move to the nearest free spot, or to
  another page, or to a new page, and the grid dialog says how many will move before you apply it. Fewer dock icons
  per page hide the ones past the new size, kept until the dock grows again.
- Desktop pages can be added, removed (their items move to other pages) and set as the main page. Dock pages can be
  added and removed; removing one deletes its icons, after asking. The desktop and the dock take up to 20 pages each.
- The drawer section reorders, renames and removes tabs, sets whether a picked tab leaves the all-apps tabs, and
  lists the hidden apps and the poisons to tick or untick.

**Backups**

- Manual backups: save the whole layout (desktop, dock, tabs, hidden apps, poisons and settings) to a JSON file, or
  restore it from one. Restoring replaces the current layout.
- Weekly backups go to a folder you choose. The first copy is written right away, and then one whenever you come
  back to the home screen and seven days have passed since the last one, so no background scheduler is needed. A
  failed copy is retried the next day. Each copy is a new dated file; old ones are not deleted.
- Nova Launcher import from a `.novabackup` file. It replaces the current layout and brings in:
  - dock pages with their swipe actions
  - desktop pages with their apps, shortcuts and widgets
  - drawer tabs and hidden apps
  - grid sizes (except the drawer's rows), the main page, the double-tap gesture and dock wrap-around

  Your poisons are kept.

## Requirements

- **To run:** Android 13 (API 33) or newer.
- **To build:**
  - JDK 21. It has to be a full JDK, because the build needs `javac` and a JRE alone fails.
  - The Android SDK with platform 37 (compileSdk and targetSdk are 37).
  - Gradle comes with the wrapper.

## Building

1. Point the build at the SDK, either with `ANDROID_HOME` or with `sdk.dir` in `local.properties`. Android Studio
   writes that file, and git ignores it.
2. If the default `java` is not a JDK 21 with `javac`, set `JAVA_HOME` to one.
3. Run the build or the tests:

```sh
./gradlew assembleRelease     # writes build/outputs/apk/release/launcher-release.apk
./gradlew testDebugUnitTest   # unit tests: layout model, search, calculator, Nova converter
```

The build downloads Gradle and the dependencies. The app it produces has no network access.

**Signing:** release builds are minified with R8 and signed on purpose with the debug key of the machine that
builds them. That key is `~/.android/debug.keystore`, which the Android build tools create on first use. It spares a
sideloaded personal app any key management, and it means:

- **A build made on your machine is signed with your key.** Android installs an update only over an app signed with
  the same key. You cannot update an install that came from someone else's build without uninstalling it first. The
  same goes for your own installs if you lose that keystore.
- **Uninstalling wipes the layout.** Save a backup first and restore it after reinstalling. Pinned shortcuts do not
  come back that way (see [Known limitations](#known-limitations)).
- **To distribute APKs,** give the release build type a signing config with a key of your own.

## Installing

**First install:**

```sh
./gradlew assembleRelease
adb install build/outputs/apk/release/launcher-release.apk
```

You can also copy the APK to the phone and open it there, allowing installs from that app when Android asks.

**Updates,** with USB debugging on and the phone connected:

```sh
scripts/install.sh
```

The script needs `adb` on the `PATH` and a single connected phone. It builds the release APK, stops the running
launcher, installs the update and checks that the launcher is still the default home app. If the launcher is on
screen, it first brings up Settings, so Android does not restart it during the install, and goes back home after.

It exists because an update that kills the running home app can make Android drop the "always" home choice. The app
keeps the home role, but the next Home press asks which launcher to use. Stopping the app first avoids that. If the
choice is lost anyway, the script says so, and you pick the launcher again with "Always". `./gradlew installRelease`
does not stop the app first, so it can lose the choice.

## Making it the default home app

- Press Home and pick Launcher, then "Always".
- Or go to Settings → Apps → Default apps → Home app. The names vary by manufacturer.
- The launcher's own settings (long-press an empty spot on the desktop) have a row that opens that system screen.

Shortcuts (pinned ones and those imported from Nova) open only while this is the default home app, because Android
allows that only for the default home app.

## Permissions

These are the permissions `apkanalyzer manifest permissions` lists for a release build:

| Permission | Why |
|---|---|
| `android.permission.QUERY_ALL_PACKAGES` | Lists every installed app and every widget provider. That includes widget receivers that are not exported, which a `<queries>` block misses. Seeing every app is why the no-network rule matters. |
| `android.permission.EXPAND_STATUS_BAR` | Swiping down opens the notification shade. |
| `io.github.gastoncampana.launcher.DYNAMIC_RECEIVER_NOT_EXPORTED_PERMISSION` | Added by AndroidX Core for its receiver helper on older Android versions. It is a signature permission, and nothing in this app is guarded by it. |

There are no runtime permission prompts. Adding a widget may show Android's own dialog asking whether the launcher
may create widgets.

## Known limitations

- **Poisons only pause opens that go through the launcher.** An app opened from a notification, from Recents, or
  picked inside a system chooser (such as the wallpaper chooser) opens right away.
- **Shortcuts that Nova pinned belong to Nova.** After an import they appear but do not open, so add them again from
  their app.
- **Pinned shortcuts do not survive a reinstall.** They belong to the install that pinned them, and backups do not
  carry their icons. After restoring, add them again from their app.
- **Some widgets start as placeholders.** This happens after a Nova import, and after restoring a backup whose
  widgets no longer exist on the device. Tap each placeholder to bind it and run its setup.
- **Widgets cannot be resized.** They keep the size they were added with (or imported with from Nova).
- **There are no folders.** A Nova import skips Nova's folders, its private-space tab and Nova's own actions, such as
  "open drawer".
- **No app shortcut menu.** Long-pressing an app does not list its shortcuts (such as "New message"). Shortcuts come
  only from "Add to home screen" and from a Nova import.
- **No icon packs, and icons cannot be renamed.** A Nova import keeps the labels the icons had in Nova.
- **Other apps can pin shortcuts, but not widgets.**
- **The screen does not rotate.** It stays in the phone's natural orientation.
- **The "search with" providers and the Lens button are fixed.** The Lens button needs the Google Lens app.
- **Swipe down depends on a non-public Android API.** There is no public way to open the notification shade, so a
  future Android version may block it.

## Project layout

Kotlin sources are in `src/main/kotlin/io/github/gastoncampana/launcher/`, and the tests are in the matching
`src/test/kotlin/` folder.

| File | Contents |
|---|---|
| `build.gradle.kts`, `settings.gradle.kts`, `gradle.properties` | The single Gradle module and its AndroidX dependencies. |
| `src/main/AndroidManifest.xml` | Permissions (INTERNET removed), the home activity and the pinned-shortcut activity. |
| `src/main/res/values/`, `src/main/res/values-es/` | UI strings: English (the default) and Spanish. |
| `src/main/res/resources.properties` | Names English as the default locale for the generated per-app language list. |
| `Model.kt` | Pure logic: the layout model, grid placement, JSON persistence, app lookup, search and the calculator. |
| `Nova.kt` | A pure converter from a Nova backup's database rows and preferences to a layout. |
| `NovaImport.kt` | Reads the `.novabackup` zip (`nova.db` and `nova.xml`). |
| `Launcher.kt` | The home activity (app list, widget hosting, opening apps, poisons, backups, Nova import), the shared layout state and `PinActivity` for pinned shortcuts. |
| `Ui.kt` | The Compose UI: desktop, dock, search bar, drawer, menus and dialogs, the pause screen and the settings. |
| `ModelTest.kt`, `NovaTest.kt` | Unit tests for the model and the Nova converter. |
| `scripts/install.sh` | Builds and installs an update over adb without losing the default home choice. |
| `CLAUDE.md` | Invariants and architecture notes for contributors and coding agents. |

## License

MIT, see [LICENSE](LICENSE).
