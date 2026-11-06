# Yoniso

An Android app that adds a short breathing pause before selected apps open. See [prd.md](prd.md).

**Status:** MVP feature-complete (Phase 4: testing and polish). Not yet published. See [docs/plan.md](docs/plan.md).

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
- The overlay is removed when another app comes to the front, the screen turns off, a phone call starts ringing, or the service is disabled or destroyed.
- Decisions live in a pure state machine (`core/PauseMachine`) with unit tests (`./gradlew testDebugUnitTest`).

## Platform limitations and assumptions

- **Sideloaded apps (Android 13+):** Android blocks enabling accessibility for apps not installed from a store until the user allows it under *App info > ⋮ > Allow restricted settings*. The settings screen explains this.
- **OEM battery managers:** Some manufacturers kill or freeze accessibility services in the background. Users may need to exempt Yoniso from battery optimisation.
- **Lock screen:** The overlay never appears over the lock screen. It is dismissed when the screen turns off. Unlocking back into a selected app pauses again only if its cooldown has expired, and never right after the user chose Continue.
- **The app is briefly visible:** Android reports the new window after it is drawn, so the selected app shows for a moment before the pause covers it.
- **Status bar:** The pause covers the app but not the status bar. While it shows, the notification shade can't be pulled down. Home and Recents always work, and leave the pause.
- **Phone calls:** A ringing call dismisses the pause, and no pause starts during a call. On Android 12+ this uses the audio mode (carrier calls and most VoIP apps). On Android 8–11 it uses the call state, which covers carrier calls only, because VoIP calls don't change it.
- **Audio:** During a pause, Yoniso takes audio focus and mutes media, because many games ignore audio focus. Background music also pauses or mutes for the length of the pause. The media volume is restored afterwards, even if Yoniso was killed mid-pause.
- **Popups:** A window that doesn't fill the screen (e.g. a Play Store or Play Games prompt) from an unselected app is not treated as leaving the app. A full-screen window from another package (e.g. a share sheet or the permission controller) is, and the cooldown stops this from causing repeated pauses.
- **Reduced motion:** Android has no dedicated setting, so Yoniso treats *Remove animations* (animator duration scale 0) as reduced motion: the circle is still and nothing fades.
- **Another pause app:** Apps that also put up an overlay when an app opens (e.g. one sec) may stack their overlay with Yoniso's if both watch the same app.

## Before publishing on Google Play

Yoniso is not an accessibility tool, so it must not set `isAccessibilityTool`. Play's AccessibilityService policy then requires:

- **In-app prominent disclosure and consent (done):** shown in the onboarding, and again on the settings screen while access is off. It says what is accessed, how it is used, and that nothing is shared, then asks for an explicit "Agree and open settings". "Not now" declines. Leaving the screen is never treated as consent. The service's description string does not count as the disclosure.
- **Play Console Accessibility API declaration (to do):** the reason for use, and a video showing the disclosure, accepting, declining (and the disclosure reappearing), and a pause.
- **Privacy policy (done):** [PRIVACY.md](PRIVACY.md), published at https://github.com/mstyles/yoniso/blob/main/PRIVACY.md and linked from the bottom of the settings screen. Put the same URL in the Play Console.
- **Data safety form (to do):** keep it consistent with the declaration. Check whether on-device-only processing of foreground-app data counts as "collected".
- **targetSdk:** currently 36. Raise it to meet Play's target API deadline when publishing, and retest.

Sources: Play Console Help on [AccessibilityService API use](https://support.google.com/googleplay/android-developer/answer/10964491), [best practices for prominent disclosure](https://support.google.com/googleplay/android-developer/answer/11150561) and the [User Data policy](https://support.google.com/googleplay/android-developer/answer/10144311).

## License

[MIT](LICENSE)
