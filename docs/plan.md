# Yoniso implementation plan

[prd.md](../prd.md) is the source of truth. This file records the design decisions made while planning, and how far the work has got.

## Status

- [x] **Phase 1: proof of concept.** Commit `08a133f`. Tested on the emulator (API 36) and a Pixel 8 (Android 17): show, Continue, Exit, screen off, and service disable all work.
- [ ] Phase 2: core logic and tests
- [ ] Phase 3: app UI and persistence
- [ ] Phase 4: testing and polish

## Project setup (done)

- Single `app` module, package `app.yoniso`. minSdk 26, targetSdk 36, compileSdk 37.2 (current AndroidX libraries need 37).
- AGP 9.5.1 (built-in Kotlin, no `kotlin-android` plugin), Kotlin 2.4.21 Compose plugin, Gradle 9.9 wrapper, version catalog in `gradle/libs.versions.toml`.
- Dependencies are limited to Compose, DataStore (to be added in Phase 3) and JUnit. No `INTERNET` permission.
- Package layout:
  - `core/`: pure Kotlin with no Android imports. State machine, cooldown policy and event filtering. Unit tested.
  - `service/`: AccessibilityService and overlay (`OverlayController`, `OverlayLifecycleOwner`, `PauseOverlay`).
  - `data/`: DataStore settings repository.
  - `ui/`: screens.

## Phase 1: as built

- The service subscribes only to `typeWindowStateChanged`, with `canRetrieveWindowContent="false"`. It reads only `event.packageName`.
- Ignored packages: Yoniso itself, `com.android.systemui`, and all IME packages (`InputMethodManager.inputMethodList`).
- A pause triggers only when the foreground package **changes**. Later events from the same package are duplicates, so this already stops Continue from retriggering.
- The overlay is a `TYPE_ACCESSIBILITY_OVERLAY` window (no `SYSTEM_ALERT_WINDOW` needed). It is focusable and touchable, so it takes taps and Back. A `ComposeView` needs `OverlayLifecycleOwner` set as its view-tree lifecycle and saved-state owner.
- The overlay is hidden on: any foreground change, `ACTION_SCREEN_OFF`, `onUnbind`, and `onDestroy`. `hide()` is safe to call when nothing is showing.
- Exit hides the overlay, then `performGlobalAction(GLOBAL_ACTION_HOME)`.
- The target is hardcoded as `TARGET_PACKAGE = "com.android.chrome"` in `YonisoAccessibilityService`, and the pause is a hardcoded 5s countdown in `PauseOverlay`.
- Known behaviour: on screen off, `foregroundPackage` is reset, so unlocking back into the target app pauses again. The Phase 2 cooldown suppresses this.

## Phase 2: core logic and tests (next)

1. **State machine** in `core/`: `IDLE → PAUSING → AWAITING_CHOICE → DISMISSING → IDLE`.
   - Inputs: foreground changed(pkg), timer finished, Continue, Exit, screen off, service stopped.
   - Outputs: actions (show overlay, hide overlay, go home, record cooldown).
   - The clock is injected so tests can control it. The service becomes a thin adapter that feeds events in and carries actions out.
2. **Event filtering** (pure function): ignore Yoniso itself, system UI and IMEs, and repeats of the current foreground package. The home screen or any other app counts as leaving.
   - Watch for transient system dialogs (e.g. the `android` package resolver, the permission controller) returning to the target app. The cooldown covers this; consider ignoring known transient packages if it still feels noisy.
3. **Anti-loop rule:** after Continue, suppress that package until a *different* package has come to the front. This holds even with "No cooldown" and is separate from the cooldown.
4. **Per-app cooldown:**
   - Starts when the overlay is *shown*.
   - Stored as wall-clock `System.currentTimeMillis()` per package, so it survives reboots. `elapsedRealtime` resets on boot, so it can't be used.
   - A stored timestamp in the future (clock changed) is treated as expired.
   - Options: none / 1 / 5 / 15 / 30 min, default 5. The setting is global, not per app (per-app durations are out of scope).
   - In Phase 2, persistence can be in-memory or SharedPreferences behind a small interface. DataStore arrives in Phase 3.
5. **App switching:** if the foreground changes mid-pause, hide the overlay, then evaluate the new package. Only one overlay ever exists.
6. **Timer:** runs in the service (coroutine/Handler), not in an Activity. The pause length is a setting: 5 / 10 / 15s, default 10.
7. **Breathing animation:** a slow expanding/contracting circle. If `Settings.Global.ANIMATOR_DURATION_SCALE == 0` (reduced motion), show a static indicator instead. No flashing.
8. **Copy:** "Take a breath." / "Notice this moment." during the pause; "Continue mindfully?" afterwards; buttons Continue / Exit. No guilt-inducing language.
9. **Unit tests (JUnit):**
   - Cooldown expiry and the clock going backwards.
   - Duplicate events.
   - Continue does not retrigger.
   - Rapid switching A→B→A.
   - Switching mid-pause.
   - Screen off mid-pause.
   - Service stopped mid-pause.
   - Self and system UI are ignored.

## Phase 3: app UI and persistence

- **DataStore preferences:** enabled flag, selected packages, pause duration, cooldown duration, cooldown timestamps. The service observes them as a Flow.
- **App discovery:** query `MAIN`/`LAUNCHER` activities, declared with `<queries>` in the manifest. Do **not** use `QUERY_ALL_PACKAGES`.
- **Never selectable:**
  - Yoniso itself.
  - All launchers (resolve the `HOME` intent).
  - The default dialer (`TelecomManager.defaultDialerPackage`).
  - Android Settings, so the user is never stalled while trying to disable the service.
- **Screens:**
  - Main settings: master toggle, selected count, manage-apps button, pause and cooldown pickers, accessibility status.
  - Searchable app list with icons and toggles.
  - First-launch onboarding with the disclosure from PRD FR-6 and a button opening `Settings.ACTION_ACCESSIBILITY_SETTINGS`.
- Re-check service status in `onResume` to handle revocation.

## Phase 4: testing and polish

- Follow PRD Phase 4 on the emulator and the Pixel 8.
- One case still unverified on a physical device: swiping home *during* a pause should close the overlay. It works on the emulator.
- Keep the README's platform limitations up to date, and review Google Play's accessibility disclosure requirements before distribution.
