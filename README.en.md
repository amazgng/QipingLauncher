# Qiping Launcher 1.0.0 — Android TV Home Screen

[Privacy review and redaction notes](docs/PRIVACY_REVIEW.md)

[简体中文](README.md) | **English**

Qiping (栖屏) is a native Java launcher for TVs running Android 9 or later. It offers a translucent glass interface, remote-control navigation, app shortcuts, local wallpapers and family settings. It opens installed apps; it does not provide movies, live TV streams or other media content.

This English document describes the project and its operation. It does **not** mean the application has an English interface; this documentation update does not change application resources or behavior.

## Version and build requirements

| Item | Value |
| --- | --- |
| Application | 栖屏 / Qiping Launcher |
| Package / application ID | `cn.wengmang.qiping` |
| Version name / code | `1.0.0` / `22` |
| Minimum Android version | Android 9 / API 28 |
| Compile SDK / target SDK | 35 / 35 |
| Implementation | Java and native Android Views; Java 8 source compatibility |
| Android Gradle Plugin / Gradle | 8.7.3 / 8.9 |
| Build JDK | 17 or 21 |
| SDK Build Tools | 35.0.0 |
| Target TV in the handoff | Android 9 TV; personal device details redacted |

The repository contains the expanded Android Studio project, bundled resources, tests, design references and original handoff documents. Open the **repository root containing `settings.gradle`** in Android Studio. The original source ZIP is not duplicated in the repository; its hash in the handoff documents refers to the original delivery archive.

## Features

- Four configurable shortcuts: movies, live TV, music and photos, plus an all-apps screen. Install an external app before binding it to a shortcut.
- Remote navigation, app-grid paging at the left/right edges, focus restoration on return, and app management through the menu key or a long press on OK.
- Recently opened apps, an independent favorites collection, app ordering and hiding. Hiding only changes the ordinary app list; it is not an access restriction.
- Six built-in wallpaper scenes, local image import and phone-to-TV wallpaper transfer with a 16:9 crop preview.
- Glass panels and text contrast that adapt to wallpaper brightness, adjustable text size and screen margins, and optional animations.
- An elder-friendly layout with three large entries, plus layout locking to protect bindings, favorites and related layout edits.
- Separate switches for opening the bound live-TV app after system boot and after standby wake-up.
- A launcher-level child mode with parental verification, an allowed-app list and an optional daily opening window.
- Configuration export, preview before restore, rollback on failure and one-level undo.
- Optional home-screen idle clock, device information, lightweight performance settings and local diagnostics.

Home-screen ordering and hiding apply to the standard layout; the elder-friendly layout keeps its three entries. Settings and all apps remain reachable. Backgrounds are still images, not live output from the bound apps.

## Settings and remote control

The home-screen gear opens quick settings. Choose **全部设置 (All settings)** for the full settings screen.

| Chinese UI entry | English meaning and contents |
| --- | --- |
| 开机与桌面 | Startup and home: default launcher, boot/wake live-TV switches, target app, test launch and startup status |
| 家庭与配置 | Family and configuration: elder-friendly layout, layout lock, child mode and desktop backups |
| 网络设置 | Device network settings |
| 系统设置 | Device system settings |
| 其他桌面 | Open a vendor or another installed launcher |
| 设备与性能 | Device information, automatic/lightweight mode and diagnostics |
| 应用更新 | Reserved application-update interface; no live update service is configured |
| 个性设置 | Animations, margins, text size, home/app organization, idle clock and local wallpapers |
| 使用说明 | Usage instructions and device limitations |

Use the direction keys to move, OK to activate and Back to return. On the standard home screen, Down or Right beyond the last entry opens all apps. At the app-grid edges, Left/Right changes pages. Menu or a long press on OK opens app management. Dialogs and long explanations also support remote operation.

## Changes in 1.0.0

Version 1.0.0 is based on 0.2.19 and contains three maintenance fixes:

1. **Password cooldown:** on the first verification after reboot, the estimated remaining cooldown is bounded to 30 seconds and persisted against the current boot's monotonic clock. Reopening a screen or recreating the process does not keep extending that wait. An active cooldown is not simply cleared by another reboot. Storage failures do not grant access.
2. **Configuration export URI validation:** external picker results must be hierarchical `content://` URIs with a provider authority. `file://`, HTTPS and malformed content URIs are rejected before opening an output stream. The fixed application-owned export fallback remains available.
3. **Malformed child-mode time windows:** wrong types, out-of-range values or a missing endpoint deny new app launches when scheduling is enabled. Repair requires entering both start and end times before saving. Cancellation preserves the restriction. Legacy records with both endpoints absent retain the default 19:00–20:00 window.

The original 1.0.0 handoff records production changes relative to 0.2.19 in `MainActivity.java`, `ParentalStore.java` and version fields, with accompanying build, test and documentation updates. Existing boot/wake state machines and visual resources were retained. Importing the project into GitHub and adding bilingual documentation did not modify application code.

## Boot and standby behavior

Set Qiping as the default HOME launcher, bind a live-TV app and use **立即试播 (Test now)** to check that the app opens. System boot and standby wake-up use separate saved switches.

When the default home screen is visible, focused and ready, the launcher displays a three-second countdown. User input cancels that round. Each round sends at most one launch request. Boot and wake-up handling avoid duplicate requests.

If another app was active before the screen turned off, the launcher prefers to keep that app. Qiping does not force an app switch from the background. Returning to the launcher more than 60 seconds after wake-up does not trigger a delayed launch. After process death, an old screen-off record alone is not treated as proof of a new wake-up.

Child-mode app and time rules also apply to automatic launch requests. A blocked round is skipped rather than postponed until an allowed time. “Launch request sent” means Android received the request; it does not prove that the external app started playback.

The user previously confirmed boot-to-bound-app behavior on version 0.2.19. Version 1.0.0 still requires confirmation of signed in-place upgrade and vendor-specific power behavior on the target TV.

## Child mode and parental credentials

Open **家庭与配置 → 未成年模式 (Family and configuration → Child mode)**. The mode is off by default. Set and repeat a six-digit PIN, save and confirm the generated 12-digit recovery code, and select at least one allowed app before enabling it.

The child home screen shows allowed installed apps and a parent-management entry. Back does not exit the mode. Changing rules or exiting requires parental verification. Authorization is limited to the current parent-management flow and is cleared when leaving the launcher or returning to the child home screen.

The optional schedule is one daily time window, including overnight windows. The start is inclusive and the end exclusive. It is **not** a daily accumulated viewing-time limit. Disabling the schedule allows permitted apps to be opened throughout the day.

This mode controls only launch requests made by Qiping. It cannot stop video already playing in another app or filter content inside that app. System shortcuts, voice assistants, other launchers, app-to-app navigation, changing system time, uninstalling or clearing data can bypass launcher-level controls.

PIN and recovery-code derivatives are stored in separate private `parental_control` preferences using random salt and PBKDF2-HMAC-SHA256. Plaintext credentials are not saved in ordinary backups or diagnostics. PIN and recovery-code failures share a counter: five consecutive failures trigger a 30-second cooldown. There is no universal unlock PIN.

Exiting child mode preserves credentials, allowed apps and the schedule. Ordinary configuration restore and undo never replace parental rules. Keep the recovery code privately; do not add it to this repository.

## Phone wallpaper transfer

1. Connect the phone and TV to the same router; the TV may use Ethernet.
2. Open **个性设置 → 本地壁纸 → 手机传壁纸 (Personal settings → Local wallpapers → Transfer from phone)** and keep the TV transfer page open.
3. Scan the QR code, or enter the displayed HTTP address in a browser and provide the six-digit pairing code.
4. Select a scene and an image, then adjust the 16:9 crop using drag, zoom and position controls.
5. Upload the image. On the TV, choose **应用壁纸 (Apply wallpaper)** or **放弃图片 (Discard image)**. The old wallpaper remains until confirmation.

Sessions last at most ten minutes. Leaving or pausing the transfer page, losing the network or changing the network address ends reception; the next session receives fresh credentials. The page is bundled with the launcher, requires no companion phone app and does not upload images to a cloud service. Transfer uses local HTTP on a trusted home network. Guest Wi-Fi or AP isolation can block access.

Supported inputs are JPG, PNG and WebP, up to 20 MB, 48 million pixels and a 16,000-pixel longest edge. The phone exports a JPEG crop with a longest edge of at most 1920 pixels; the TV validates and resizes it again. Transparent areas use a dark background. Imported wallpapers are copied to private storage and survive removal of the USB drive, but are deleted if the app is uninstalled or its data is cleared.

Android emulators use a virtual network; a phone usually cannot reach the displayed emulator address directly. For desktop-browser testing, substitute the displayed port below:

```sh
adb forward tcp:8765 tcp:<displayed-port>
```

Then open `http://127.0.0.1:8765/` and enter the TV pairing code.

## Idle clock and performance

The desktop idle clock is off by default. It supports 5, 10, 15 or 30 minutes and an immediate preview. It appears only while the home screen is in the foreground and idle, showing the current wallpaper, date and clock. The key used to dismiss it is consumed rather than also opening an app. It does not cover external video apps or change Android sleep settings.

Automatic performance selection uses lightweight limits for low-memory devices, total RAM of at most 1.5 GiB, missing total-RAM readings, or an app heap of at most 128 MiB. Lightweight mode limits wallpaper decoding to a longest edge of 960 and caches at most two scenes within a 4 MiB budget; standard mode uses up to 1920, three scenes and 20 MiB. These are cache limits, not total process-memory limits. Transition frames and decoding also use memory.

Background decoding is bounded, and resources are released with lifecycle changes. Do not introduce continuous blur, background video wallpapers or forced background launches when maintaining this baseline.

## Configuration backup and upgrades

Open **家庭与配置 → 桌面配置 (Family and configuration → Desktop configuration)**. Exported `.qiping` v2 files have a 32 KiB limit and an integrity checksum. Restore shows a preview before confirmation, supports rollback on failure and one-level undo, and recovers unfinished restore transactions. The checksum detects damage; it does not encrypt the file.

| Included | Excluded |
| --- | --- |
| Four category bindings, favorite order, text size, margins, animation preference, performance mode, elder-friendly layout, layout lock, home/app order and hiding, idle-clock delay | Wallpaper image files, external-app data, recent history, legacy six-slot favorites, boot/wake switches and session state, child-mode rules, PIN and recovery code |

Version 1 backups can be imported while keeping local values for newer layout and idle-clock fields. Version 0.2.14 cannot import v2 backups. Restore does not immediately open the live-TV app; a future boot uses the restored binding if the local boot switch is enabled.

Upgrade the TV **in place using its original signing key and the same package name**. Version code 22 is higher than 0.2.19's 21. Do not uninstall first: uninstalling or clearing data loses private settings, wallpaper copies and parental rules. Ordinary configuration backup does not include all of that data.

Android Studio's debug key, `build_local.py`'s local test key and the key used for the TV installation may differ. Keep production signing keys and passwords on the owner's machine. Retaining older source does not guarantee that Android will accept installation of an APK with a lower version code.

## Build and run

1. Clone or download this repository and open its root in Android Studio.
2. Select Gradle JDK 17 or 21. Install SDK Platform 35 and Build Tools 35.0.0. Keep AGP 8.7.3 and Gradle 8.9 for the baseline.
3. Allow the first Gradle sync to download dependencies. Separate network/dependency failures from application-code failures.
4. Prefer a hardware-accelerated Android 9 / API 28 landscape emulator with a 1280×720 display area. Use arrow keys, Enter and Back to exercise remote navigation.
5. Complete settings and family-menu checks before upgrading the TV with the original signing key.

Windows:

```bat
gradlew.bat :app:assembleDebug
```

macOS / Linux:

```sh
./gradlew :app:assembleDebug
```

An alternative standalone SDK validation script is included. Strict API 28 checking also requires SDK Platform 28:

```sh
python3 build_local.py --sdk <SDK-path> --check-api28 --instrumentation
```

This script creates a local test signing key and test APKs. Those outputs are not production-signed updates and are excluded from version control.

## Verification status

The following results are from the supplied **October 22, 2026** records. The GitHub import and bilingual documentation update did not rerun builds or device tests.

| Scope | Recorded result |
| --- | --- |
| Standalone SDK build | Passed resource processing, strict API 28 Java compilation, D8, signing verification, zip alignment and instrumentation packaging |
| Host checks | 16 groups passed, including parental rules, configuration, navigation, personalization, boot/wake and wallpaper server checks |
| Android 9 maintenance suite | 20 passing records; no failed items in that suite |
| Complete settings/family UI regression | Not completed: System UI interruptions and a subsequent 480-second timeout; not counted as passed |
| Conventional Gradle build | Not rerun in the recorded validation round |
| Hisense TV running 1.0.0 | Signed in-place upgrade and vendor power behavior still need confirmation |
| Android 10 and later | Not individually exercised by this validation round |

Reboot/clock rollback boundaries were tested with injected boot identifiers and time inputs, without changing the emulator's actual date. Export checks injected URI results into the real callback; they were not an end-to-end test with a separately installed malicious picker. In-memory preference tests do not establish real disk-failure behavior.

Selected instrumentation commands:

```sh
adb shell am instrument -w -e suite maintenance cn.wengmang.qiping.tests/cn.wengmang.qiping.tests.LauncherSmoke
adb shell am instrument -w -e suite settings-family cn.wengmang.qiping.tests/cn.wengmang.qiping.tests.LauncherSmoke
adb shell am instrument -w -e suite phone cn.wengmang.qiping.tests/cn.wengmang.qiping.tests.LauncherSmoke
adb shell am instrument -w -e suite wake cn.wengmang.qiping.tests/cn.wengmang.qiping.tests.LauncherSmoke
node tests/crop_test.js
```

Run interactive suites only on a disposable emulator. They may clear test-instance parental credentials/rules, change settings or wallpapers, launch test apps and toggle the screen. The wake suite requires Qiping as default HOME. No suite argument and `suite all` do not mean every historical UI test. Older test paths and screenshots remain historical references, not proof of current-version coverage.

Prioritize normal settings-menu round trips, parental setup and recovery-code confirmation, allowed apps, schedule editing, enabling/exiting child mode and focus restoration. On the TV, confirm settings survive the original-signature upgrade, then check daily standby/wake, system restart when practical, wallpaper transfer and configuration-export preview. Do not corrupt TV data or change its clock merely to reproduce boundary tests.

## Source map

Main Java classes are under `app/src/main/java/cn/wengmang/qiping/`. Most screens are constructed in Java rather than layout XML.

| Area | Main files |
| --- | --- |
| Activity, navigation and lifecycle | `MainActivity.java`, `QipingApplication.java` |
| Layout and remote navigation | `StageLayout.java`, `GridNavigation.java`, `WorkspaceLayout.java`, `PageEdgeView.java` |
| Wallpaper and glass rendering | `SceneView.java`, `GlassPanel.java`, `GlassDialog.java`, `GlassButton.java`, `GlassContrast.java` |
| Apps, bindings and collections | `AppCatalog.java`, `ShortcutStore.java`, `Favorites.java`, `AppOrganizer.java` |
| Settings and display | `QuickSettingsDialog.java`, `DisplayPreferences.java`, `ExperiencePolicy.java` |
| Boot and wake | `BootReceiver.java`, `BootSession.java`, `StartupState.java`, `StartupFlow.java`, `WakeMonitor.java`, `WakeState.java`, `WakeFlow.java` |
| Parental rules and entry dialogs | `ParentalStore.java`, `NumberEntryDialog.java`, parent flows in `MainActivity.java` |
| Configuration persistence | `ConfigBackup.java`, `ConfigStore.java` |
| Wallpaper transfer | `WallpaperStore.java`, `PhoneWallpaperServer.java`, `PhoneWallpaperDialog.java`, `LanAddress.java`, `app/src/main/assets/phone-wallpaper/` |
| Performance and diagnostics | `DeviceInfo.java`, `PerformancePolicy.java`, `RunDiagnostics.java`, `DiagnosticJournal.java` |
| Reserved update integration | `UpdatePolicy.java`, `UpdateRepository.java`, `UpdateBadge.java`, `app/src/main/assets/update-source.json` |
| Tests and evidence | `tests/`, `app/src/androidTest/`, `docs/checks/` |
| Historical references | `design/`, `docs/history/` |

## Diagnostics, limits and maintenance

Diagnostics are available under **设备与性能 → 运行诊断 (Device and performance → Diagnostics)** and from startup status. Up to 100 records / 48 KiB are stored privately without automatic upload. They cover launcher lifecycle, memory samples, app-launch requests, startup and transfer state. They do not contain external-app logs, image contents, original photo filenames, pairing codes or QR URLs. Abrupt power loss, system termination, native crashes or hangs may leave no complete record. Memory figures can overlap and must not be added as independent totals.

The update interface exists, but `app/src/main/assets/update-source.json` has no configured service. Live update feeds and automatic APK download/installation are not enabled. Online wallpaper integration remains paused. Embedding external app windows as the desktop background is not part of this baseline.

The application declares Internet, network-state and boot-completed permissions. It adds no foreground service, wake lock or accessibility permission. QR generation uses the included Project Nayuki implementation; preserve [THIRD_PARTY_NOTICES.txt](THIRD_PARTY_NOTICES.txt).

Maintain the original package, Android 9 minimum and original-signature upgrade path. Increment version codes for later app releases. Keep the glass design and remote interactions unless a redesign is explicitly requested. Changes to ordinary configuration must not overwrite parental credentials or weaken malformed-rule handling.

Original Chinese delivery documents:

- [Handoff explanation](docs/handoff/1.0.0/交接说明.txt)
- [Quick start](docs/handoff/1.0.0/快速运行说明.txt)
- [Pending verification checklist](docs/handoff/1.0.0/待验证清单.txt)
- [Usage and acceptance](1.0.0_使用与验收.txt)
- [Validation record](验证记录.txt)
- [Change history](CHANGELOG.md)
