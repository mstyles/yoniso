# Yoniso

An Android app that adds a short breathing pause before selected apps open. See [prd.md](prd.md).

**Status:** Phase 1 proof of concept. Chrome (`com.android.chrome`) is hardcoded as the only app that triggers a pause.

## Build and run

Requirements: JDK 17+, an Android SDK with the `platforms;android-37.2` platform installed (to compile; the app targets API 36), and a device or emulator running Android 8.0 (API 26) or newer.

```sh
./gradlew installDebug
# Enable the accessibility service (or use Settings > Accessibility > Yoniso):
adb shell settings put secure enabled_accessibility_services \
  app.yoniso/app.yoniso.service.YonisoAccessibilityService
adb logcat -s Yoniso
```

## How it works

- `YonisoAccessibilityService` listens only for `TYPE_WINDOW_STATE_CHANGED` and reads only the event's package name. It never reads window content (`canRetrieveWindowContent="false"`).
- The overlay is a `TYPE_ACCESSIBILITY_OVERLAY` window holding a `ComposeView`, so it does not need the "display over other apps" permission.
- A pause is triggered only when the foreground package *changes*. Later events from the same app count as duplicates, so tapping Continue does not retrigger the pause.
- The overlay is removed when another app comes to the front, the screen turns off, or the service is disabled or destroyed.

## Platform limitations and assumptions

- **Sideloaded apps (Android 13+):** Android blocks enabling accessibility for apps not installed from a store until the user allows it under *App info > ⋮ > Allow restricted settings*.
- **OEM battery managers:** Some manufacturers kill or freeze accessibility services in the background. Users may need to exempt Yoniso from battery optimisation.
- **Lock screen:** The overlay never appears over the lock screen. It is dismissed when the screen turns off, and the pause shows again when the user unlocks back into a selected app. From Phase 2, the cooldown suppresses that repeat.
- **Detection is window-based:** A dialog from another package (e.g. a permission prompt) counts as an app switch. The per-app cooldown (Phase 2) stops this from causing repeated pauses.
- **Google Play:** Publishing an app that uses the Accessibility API requires a prominent disclosure and a Play Console declaration. This must be reviewed before distribution (PRD Phase 4).
