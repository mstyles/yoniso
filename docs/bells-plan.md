# Mindfulness bells: plan

A bell that rings on its own through the day, at random or at set intervals, as a reminder to stop and breathe. The user can pick a bundled bell or upload their own, choose to vibrate instead (or as well), and shape the vibration.

This extends Yoniso beyond the PRD, whose principles still apply: mindfulness over restriction, gentle, private, simple. [plan.md](plan.md) covers the pause feature.

## Status

- [ ] **Phase 5: platform prototype.** Can an unprompted bell and vibration play reliably on Android 17?
- [ ] **Phase 6: schedule core and tests.**
- [ ] **Phase 7: ringing in the background.** Alarms, the ring service, reboots.
- [ ] **Phase 8: settings UI.** Bells screen, bundled bells, shared bell setting.
- [ ] **Phase 9: custom bells and vibration.** Upload, vibration editor, previews.
- [ ] **Phase 10: extras, testing and Play.** Tile, snooze, device testing, disclosures.

## Decisions

Made while brainstorming on 2026-11-10.

- **Rings any time,** including with the screen off and after a reboot. Bells use `AlarmManager`, not the accessibility service, so they work even when the pause feature is off.
- **Follows the phone's sound mode.** Ringer on: as configured. Vibrate mode: vibrate only (a "Bell" setting is downgraded to vibration). Silent or Do Not Disturb: skip completely. The bell is never louder than the user's own settings allow.
- **Two timing modes:**
  - **Interval:** every N minutes (5, 10, 15, 20, 30, 45, 60, 90, 120), **aligned to the clock**: "every 15 minutes" rings at :00, :15, :30, :45, not 15 minutes after turning it on. Intervals that don't divide an hour (45, 90) align to midnight.
  - **Random:** each gap is picked evenly between a minimum and maximum (e.g. "every 10 to 30 minutes"). Minimum is at least 5 minutes, and the maximum is at least the minimum.
- **Active hours and days,** e.g. 08:00–21:00, Mon–Fri. Nothing rings outside them. Default: 08:00–21:00, every day. A window that crosses midnight (22:00–02:00) belongs to the day it starts on.
- **Output: Bell, Vibrate, or Both.**
- **One shared bell.** The bell chosen here is also what "Listen to a bell" plays during a pause.
- **Bundled bells:** the Plum Village bell (current `bell.mp3`) plus a few CC0 bells (e.g. from Freesound: a singing bowl, a small chime, tingsha). Each needs a recorded source and licence (see below).
- **Extras in scope:** a Quick Settings tile, snooze, and skipping during calls and media.

## How it works

### Scheduling

- Only one alarm is ever set: the next ring. When it fires, Yoniso rings (or skips), then works out and sets the next one. The next ring time is stored in DataStore so it can be restored.
- The next ring is computed by a pure function in `core/`, `BellSchedule.nextRing(after, settings, zone, random)`, using `java.time` (available since minSdk 26). It handles interval vs random, clock alignment, active hours and days, snooze, and DST.
  - Random mode starts a new random gap at the beginning of each active window, so the first bell of the day isn't always at the window's start.
  - DST: compute in local time, then convert. If a ring time falls in a skipped hour, move it forward; in a repeated hour, ring once.
- **Exact vs inexact alarms:**
  - By default, inexact `setAndAllowWhileIdle`. In Doze, Android allows these at most about once every 9 minutes per app and may delay them. That's fine for random bells, where nobody can tell.
  - For interval mode, offer "Ring on time" which asks for `SCHEDULE_EXACT_ALARM` (a user-granted special access on Android 14+, checked with `canScheduleExactAlarms()`) and uses `setExactAndAllowWhileIdle`. If the grant is missing or revoked, fall back to inexact and tell the user on the bells screen.
  - Don't use `USE_EXACT_ALARM`: Play restricts it to alarm-clock and calendar apps.
  - Don't use `setAlarmClock`: it shows an alarm icon in the status bar.
- **Late alarms:** if an alarm fires more than about 2 minutes late (device off, deep Doze), skip it and schedule the next one, so the user never gets a burst of stale bells.
- **Rescheduling** on `BOOT_COMPLETED` (`RECEIVE_BOOT_COMPLETED`), `MY_PACKAGE_REPLACED`, `TIME_SET`, `TIMEZONE_CHANGED`, `SCHEDULE_EXACT_ALARM_PERMISSION_STATE_CHANGED`, and any change to the bell settings.

### Ringing

- The alarm goes to a `BroadcastReceiver`, which decides whether to ring:
  - Skip outside active hours or while snoozed (a safety check, because the schedule should already avoid these).
  - Skip on silent (`RINGER_MODE_SILENT`) or DND (`currentInterruptionFilter != INTERRUPTION_FILTER_ALL`). On vibrate mode, vibrate only.
  - Skip during a call (`AudioManager.mode != MODE_NORMAL`), reusing the idea behind `CallWatcher`.
  - While media is playing (`isMusicActive`), follow a setting: **vibrate only** (default), ring anyway, or skip.
  - Skip while a Yoniso pause is showing. The pause already is the reminder.
- If it rings, the receiver starts a short **foreground service** (`BellService`, type `mediaPlayback`) that plays the bell and/or vibration and stops itself afterwards. A receiver alone only has about 10 seconds, and the bell lasts 22.
  - Background start: an exact alarm exempts the app from Android 12+'s background foreground-service limits, and so does a bound accessibility service. **An inexact alarm with the accessibility service off has no exemption.** Phase 5 must find a path that works in that case, or random mode will need exact alarms too.
  - The notification is quiet ("Mindfulness bell" with a Stop action) on its own low-importance channel. On Android 13+, without `POST_NOTIFICATIONS`, the service still runs; the notification only shows in the task manager.
  - Audio uses `USAGE_NOTIFICATION_EVENT`, so the bell follows the notification volume and DND, plus a bell volume slider (0.1 to 1.0 of that stream). It takes `AUDIOFOCUS_GAIN_TRANSIENT_MAY_DUCK`, so music ducks rather than stops.
  - The pause's "Listen to a bell" keeps `USAGE_ALARM`, because the user asked for it. Only the sound file is shared.
- **Vibration** uses `VibratorManager` (31+) / `Vibrator` with `VibrationEffect.createWaveform(timings, amplitudes, -1)` and notification usage attributes. Amplitude is used only if `hasAmplitudeControl()` is true. Otherwise the intensity setting is hidden and pulses play at the default strength.

### Bell sounds

- Bundled bells live in `res/raw/` and have stable IDs (`plum_village`, `singing_bowl`, ...). Stored settings refer to the ID, never to the resource number.
- **Custom bells:**
  - Picked with `ACTION_OPEN_DOCUMENT` (`audio/*`), so no storage permission is needed.
  - The file is **copied** into `filesDir/bells/<uuid>`, so it keeps working if the original is moved, and no persistable URI grant is needed.
  - Checked on import: it must decode (`MediaMetadataRetriever` / a test `MediaPlayer` prepare), be at most 5 MB and at most 60 seconds long. Rejected files get a plain-language message.
  - The user names it (the file name is the default), and can rename or delete it. Deleting the bell in use switches back to the Plum Village bell.
  - If a custom file goes missing or fails to play, the bundled default rings instead, and the error is logged.
- Custom files are already excluded from backup and transfer by the existing `dataExtractionRules`.

### Vibration

- **Presets:** Gentle tap, Double tap, Heartbeat, Three slow, Swell.
- **Custom:** number of pulses (1–6), pulse length (50–1000 ms), gap (100–1500 ms), intensity (low / medium / high, or a slider), and shape (steady, or swell, where amplitude ramps up through each pulse; amplitude-control devices only). Repeat the pattern 1–3 times.
- The total is capped at 6 seconds. Editing a preset turns it into Custom.
- A **Preview** button plays the current pattern straight away.
- Stored as the parameters, not a raw waveform, so the editor can always show them again. `core/VibrationPattern.toWaveform()` turns them into timings and amplitudes, and is unit tested.

### Settings and data

- A new DataStore file, `bells`, kept separate from `settings` so recording the next ring doesn't re-emit everything:
  - enabled, mode (interval / random), interval minutes, random min/max, active start/end, active days, output (bell / vibrate / both), bell ID, bell volume, vibration pattern parameters, during-media behaviour, ring on time, snoozed until, next ring at.
  - Custom bell names and file names (a small JSON string or a string set).
- The shared bell ID is read by `PauseBell` too.

### UI

- The main settings screen gets a "Mindfulness bells" row (on/off and a summary, e.g. "Every 10–30 min, 08:00–21:00") that opens a **Bells screen**:
  - Master toggle and snooze ("Pause bells for 1 hour / until tomorrow").
  - Timing: interval or random chips, then the interval chips or a min/max range slider.
  - Active hours (two time pickers) and day chips.
  - Output: Bell / Vibrate / Both.
  - Bell: a list with a play button per bell, "Add your own…", and a volume slider.
  - Vibration: presets, a "Customise" section, and Preview.
  - "While media is playing": vibrate only / ring / skip.
  - "Ring on time" (interval mode), with the exact-alarm grant flow.
  - A status line: "Next bell around 10:40", or the reason it won't ring (notifications blocked, exact alarms off, DND).
- **Quick Settings tile** (`TileService`): tap to toggle bells. The subtitle shows "Snoozed" or the next bell time.
- Same chip and toggle styles as the existing settings, announced to TalkBack the same way.

### Permissions and Play

New manifest entries:

| Permission | Why | Notes |
|---|---|---|
| `RECEIVE_BOOT_COMPLETED` | Restore the next alarm after a reboot | Normal |
| `VIBRATE` | Vibration | Normal |
| `FOREGROUND_SERVICE`, `FOREGROUND_SERVICE_MEDIA_PLAYBACK` | Ring for the length of the bell | Play Console needs a foreground-service declaration with a description and video |
| `POST_NOTIFICATIONS` | Show the ringing notification (13+) | Asked for when bells are first turned on; optional |
| `SCHEDULE_EXACT_ALARM` | "Ring on time" | Optional, user-granted |

- Still no `INTERNET`. The Data safety answers don't change: custom bell files never leave the device. Add a line about custom bells to PRIVACY.md.
- Update the README: how bells work, the new permissions, limitations (Doze timing, OEM battery killers, the user's per-usage vibration intensity settings can silence vibration).
- Sound licences: record each bundled bell's source URL and licence in the README and the About screen. Confirm the Plum Village permission covers its use as a repeating bell, not only in the pause.

## Phases

### Phase 5: platform prototype

Answer the risky questions on the Pixel 8 (Android 17) before building anything else. Throwaway code on a branch.

1. Inexact `setAndAllowWhileIdle` → receiver → `mediaPlayback` foreground service → bell. Test with the accessibility service **on and off**, the screen off, and after `adb shell dumpsys deviceidle force-idle`.
2. The same with an exact alarm.
3. Check that Android 17's background-audio hardening doesn't block the bell or its focus request from the service (look for `AudioHardening` in logcat).
4. Vibration from the receiver and from the service, screen on and off, with notification usage attributes.
5. Check that ringer mode and DND are reported correctly.

Outcome: record what works in this file. If an inexact alarm can't start the service with accessibility off, choose between requiring exact alarms, requiring the accessibility service, or vibration-only from the receiver.

### Phase 6: schedule core and tests

- `core/BellSettings`, `core/BellSchedule`, `core/VibrationPattern`, `core/RingDecision` (silent/DND/call/media/pause → ring, vibrate, or skip). All pure, with an injected clock, zone and `Random`.
- Tests: clock alignment (including 45 and 90 minutes), random gaps within min/max with a seeded `Random`, active-window starts and ends, windows crossing midnight, day filters, all days off (no next ring), snooze, DST forward and back, late alarms, and each `RingDecision` case. Waveform length cap and amplitude clamping.

### Phase 7: ringing in the background

- `BellAlarms` (schedule/cancel, exact or inexact), `BellReceiver`, `BellService`, the boot/time receivers, the `bells` DataStore.
- `PauseBell` and `BellService` share a small `BellPlayer` that resolves the bell ID to a resource or file, with fallback.
- No UI yet: debug defaults (or an adb broadcast) to test on the emulator and Pixel.

### Phase 8: settings UI

- The bells row and screen (timing, hours, days, output, bundled bell list with previews, volume, media behaviour, ring on time, status line).
- Notification permission request and exact-alarm grant flow.
- Add the CC0 bells (trim and normalise like `bell.mp3`) and their credits.
- "Listen to a bell" in the pause plays the shared bell.

### Phase 9: custom bells and vibration

- Import, validate, name, rename and delete custom bells.
- Vibration presets, editor, and preview. Intensity controls appear only on amplitude-control devices.

### Phase 10: extras, testing and Play

- Quick Settings tile and snooze.
- Device tests: a day of random bells on the Pixel with normal use; Doze overnight (nothing outside hours, nothing burst in the morning); reboot; time zone change; DST (emulator clock); silent, vibrate and DND; a call; music playing; a pause showing; accessibility service off; exact alarm revoked; a custom bell deleted from the source folder; a corrupt file; a vibration-only device without amplitude control (emulator).
- README, PRIVACY.md, About screen credits, Play foreground-service declaration and video.

## Open questions

- Should a bell show anything on screen (e.g. a brief "Breathe" notification or a soft overlay when the screen is on), or be sound/vibration only? Proposed: sound/vibration only, with the quiet service notification.
- Number of strikes per bell (1–3), as in Plum Village's practice of inviting the bell? Proposed: later.
- Should the random minimum go below 5 minutes? Doze limits make shorter gaps unreliable anyway.
- Does Play's foreground-service policy accept `mediaPlayback` for a short bell, or should it be `shortService` (Android 14+, about 3 minutes)? Settle this in Phase 5.
