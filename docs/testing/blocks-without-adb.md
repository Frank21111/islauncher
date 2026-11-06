# Blocks without secure settings: device validation

Tested on 2026-11-06 using a Motorola Moto G42, Android 13, in a disposable secondary user. The owner's existing rules and permissions were left unchanged.

APK SHA-256: `14e2bf49af4ed18d5e49edce1326efa2f70895baff68c389536c4082c984fac7`.

ADB drove the test device and installed a temporary probe app; **WRITE_SECURE_SETTINGS was not granted to Islauncher in the test user**. Accessibility, notification access and device admin were enabled through Android's UI. Browser onboarding was completed with accessibility temporarily disabled in the test user. The UI inspection helper kept other accessibility services running.

| Check | Observed result |
| --- | --- |
| Setup status after returning from Android | Each grant was detected; adding a block became available without secure settings. |
| Allowed website in Chrome and Firefox | `example.org` remained open during a site block. |
| Blocked website in Chrome and Firefox | Opening `example.com` returned to a previous allowed page or home. |
| Subdomain in Chrome and Firefox | `www.example.com` was also blocked. Firefox's default-browser prompt was dismissed before checking the result. |
| Domain boundary | `example.com.invalid` remained in Chrome with its expected DNS error; it was not treated as a subdomain of `example.com`. |
| App opened externally during a block | The probe activity returned to home. |
| Notification posted before the block | Synthetic notification 41 moved from the active list to Android's snoozed list when the rule was saved. |
| Notification posted during the block | Synthetic notification 42 also appeared in the snoozed list, with release scheduled for 13:00 local time. |
| Multimedia transport controls | A synthetic active MediaSession received `onPause`; playback state changed from PLAYING (3) to PAUSED (2). Repeating PLAY caused another pause callback. No actual audio was played. |
| Settings protection | Date/time, Islauncher app info and notification access screens returned to home during the block. |
| Editing a running rule | Removing its site left the site in place; the remove-rule row indicated it could only be removed after the end. |
| No automatic accessibility recovery | After the test harness disabled accessibility in the disposable user, it remained disabled and the probe activity could open. Restoring accessibility made the activity return home again. |
| Notification release after the end | At 13:00, notifications 41 and 42 returned to the active list and left the snoozed list. |
| New activity after the end | The probe activity remained open after its app block expired, while the separate site rule was still active. |
| New notification after the end | Notification 43 remained active. |
| New playback after the end | A fresh PLAY request remained in PLAYING (3), with no forced pause. |

Cleanup completed: synthetic notifications and media session cancelled, test-user device admin deactivated through Android, probe app uninstalled, temporary user removed, and owner user restored. Owner accessibility, notification access, device admin and secure settings permission were retained. No production rule was edited.

These checks cover the tested Moto and browser versions. They do not validate every browser, real audio playback, installation through a file manager/Play Protect, or all Android restricted-settings variants. Automatic time enforcement was not exercised because it would change a device-wide setting.
