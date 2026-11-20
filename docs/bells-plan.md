# Mindfulness bell: plan

A bell that rings on its own through the day, at random or at set intervals, as a reminder to stop and breathe. The user can pick a bundled bell or upload their own, choose to vibrate instead (or as well), and shape the vibration.

This extends Yoniso beyond the PRD, whose principles still apply: mindfulness over restriction, gentle, private, simple. [plan.md](plan.md) covers the pause feature.

## Status

- [x] **Phase 5: platform prototype.** Done 2026-11-10 on the emulator (API 36) and the Pixel 8 (Android 17). See "Phase 5: results". A bell can play from the background, but a non-exact alarm can't start it while the accessibility service is off.
- [x] **Phase 6: schedule core and tests.** 59 new unit tests (114 in all) pass. See "Phase 6: as built".
- [x] **Phase 7: ringing in the background.** 117 unit tests pass. Tested on the emulator (API 36), driven over adb. See "Phase 7: as built".
- [x] **Phase 8: settings UI.** 122 unit tests pass. Tested on the emulator. Six CC0 bells added. See "Phase 8: as built".
- [x] **Phase 9: custom bells and vibration.** 136 unit tests pass. Tested on the emulator. See "Phase 9: as built".
- [ ] **Phase 10: extras, testing and Play.** Tile, snooze, device testing, disclosures. Tile and snooze done (140 unit tests pass, tested on the emulator); device tests and Play still to do. See "Phase 10: as built".

## Decisions

Made while brainstorming on 2026-11-10.

- **Rings any time,** including with the screen off and after a reboot. Bells use `AlarmManager`, not the accessibility service, so they work even when the pause feature is off.
- **Rings in vibrate mode.** Sound and vibrate modes: as configured, because many people keep their phone on vibrate all day only to silence calls (changed on 2026-11-10; the first draft vibrated only). Silent or Do Not Disturb: skip completely. The bell's loudness follows the alarm volume.
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
  - Exact `setExactAndAllowWhileIdle` for both modes whenever `SCHEDULE_EXACT_ALARM` is granted (a user-granted special access, denied by default on Android 14+, checked with `canScheduleExactAlarms()`). Yoniso asks for it when bells are first turned on. Phase 5 showed that only an exact alarm can start the bell service while the accessibility service is off.
  - Without the grant: inexact `setAndAllowWhileIdle`. In Doze, Android allows these at most about once every 9 minutes per app and may delay them. They can start the bell only while the accessibility service is on. If it's off too, the receiver vibrates instead (if vibration is on), and the bells screen explains why the bell can't ring.
  - If the grant is revoked, fall back to inexact and tell the user on the bells screen.
  - Don't use `USE_EXACT_ALARM`: Play restricts it to alarm-clock and calendar apps.
  - Don't use `setAlarmClock`: it shows an alarm icon in the status bar.
- **Late alarms:** if an alarm fires more than about 2 minutes late (device off, deep Doze), skip it and schedule the next one, so the user never gets a burst of stale bells.
- **Rescheduling** on `BOOT_COMPLETED` (`RECEIVE_BOOT_COMPLETED`), `MY_PACKAGE_REPLACED`, `TIME_SET`, `TIMEZONE_CHANGED`, `SCHEDULE_EXACT_ALARM_PERMISSION_STATE_CHANGED`, and any change to the bell settings.

### Ringing

- The alarm goes to a `BroadcastReceiver`, which decides whether to ring:
  - Skip outside active hours or while snoozed (a safety check, because the schedule should already avoid these).
  - Skip on silent (`RINGER_MODE_SILENT`) or DND (`currentInterruptionFilter != INTERRUPTION_FILTER_ALL`). Yoniso must check these itself, because the alarm stream ignores both. Vibrate mode rings as configured.
  - Skip during a call (`AudioManager.mode != MODE_NORMAL`), reusing the idea behind `CallWatcher`.
  - While media is playing (`isMusicActive`), follow a setting: **vibrate only** (default), ring anyway, or skip.
  - Skip while a Yoniso pause is showing. The pause already is the reminder.
- If it rings, the receiver starts a short **foreground service** (`BellService`, type `shortService` on Android 14+, and no type before that, where `shortService` doesn't exist) that plays the bell and/or vibration and stops itself afterwards. A receiver alone only has about 10 seconds, and the bell lasts 22.
  - Background start: an exact alarm exempts the app from Android 12+'s background foreground-service limits, and so does a bound accessibility service. **An inexact alarm with the accessibility service off has no exemption** (confirmed in Phase 5), so that case vibrates only.
  - The notification is quiet ("Mindfulness bell" with a Stop action) on its own low-importance channel. On Android 13+, without `POST_NOTIFICATIONS`, the service still runs; the notification only shows in the task manager.
  - Audio uses `USAGE_ALARM`, like the pause's "Listen to a bell". The notification stream would be muted in vibrate mode. The bell follows the alarm volume, with a bell volume slider (0.1 to 1.0 of that stream). It takes `AUDIOFOCUS_GAIN_TRANSIENT_MAY_DUCK`, so music ducks rather than stops.
  - Side effect: the bell is as loud as the user's alarm volume, which is often set high. The bell volume slider defaults to 0.5, and the bells screen says the loudness follows the alarm volume.
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
- **Custom:** number of pulses (1–6), pulse length (50–1500 ms), gap (100–1500 ms), intensity (low / medium / high, or a slider), and shape (steady, or swell, where amplitude ramps up through each pulse; amplitude-control devices only). Repeat the pattern 1–3 times.
- The total is capped at 6 seconds. Editing a preset turns it into Custom.
- A **Preview** button plays the current pattern straight away.
- Stored as the parameters, not a raw waveform, so the editor can always show them again. `core/VibrationPattern.toWaveform()` turns them into timings and amplitudes, and is unit tested.

### Settings and data

- A new DataStore file, `bells`, kept separate from `settings` so recording the next ring doesn't re-emit everything:
  - enabled, mode (interval / random), interval minutes, random min/max, active start/end, active days, output (bell / vibrate / both), bell ID, bell volume, vibration pattern parameters, during-media behaviour, snoozed until, next ring at.
  - Custom bell names and file names (a small JSON string or a string set).
- The shared bell ID is read by the pause's "Listen to a bell" too.

### UI

- The home screen has a square tile per feature, "Pause before apps" and "Mindfulness bell" (singular since 2026-11-20), showing only an icon (the Yoniso ripple, a singing bowl), filled when on and outlined when off. Tapping a tile opens that feature's screen, where it is switched on and off (changed on 2026-11-20; the bells were first a row on the pause settings). The **Bells screen**:
  - Master toggle and snooze ("Pause bells for 1 hour / until tomorrow").
  - Timing: interval or random chips, then the interval chips or a min/max range slider.
  - Active hours (two time pickers) and day chips.
  - Output: Bell / Vibrate / Both.
  - Bell: a list with a play button per bell, "Add your own…", and a volume slider.
  - Vibration: presets, a "Customise" section, and Preview.
  - "While media is playing": vibrate only / ring / skip.
  - The exact-alarm grant flow ("Allow Yoniso to ring on time"), shown when the grant is missing.
  - A status line: "Next bell around 10:40", or the reason it won't ring (notifications blocked, exact alarms off, DND).
- **Quick Settings tile** (`TileService`): tap to toggle bells. The subtitle shows "Snoozed" or the next bell time.
- Same chip and toggle styles as the existing settings, announced to TalkBack the same way.

### Permissions and Play

New manifest entries:

| Permission | Why | Notes |
|---|---|---|
| `RECEIVE_BOOT_COMPLETED` | Restore the next alarm after a reboot | Normal |
| `VIBRATE` | Vibration | Normal |
| `FOREGROUND_SERVICE` | Ring for the length of the bell | `shortService` on 14+, which needs no type permission; no type before 14 (decided in Phase 10, see there) |
| `POST_NOTIFICATIONS` | Show the ringing notification (13+) | Asked for when bells are first turned on; optional |
| `SCHEDULE_EXACT_ALARM` | Ring on time, and ring with the accessibility service off | Strongly recommended, user-granted |

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

### Phase 5: results

The prototype (`app/yoniso/proto/`, branch `bells-prototype`, not merged) scheduled test alarms over adb and logged what happened.

| Alarm | Accessibility service | Screen / Doze | Foreground service | Bell | Vibration |
|---|---|---|---|---|---|
| Inexact | On | On | Starts (`mediaPlayback` and `shortService`) | Plays | Plays |
| Inexact | On | Off, forced deep Doze | Starts (fired 22s late) | Plays | Plays |
| Inexact | **Off** | Off | **Refused** (`ForegroundServiceStartNotAllowedException`) | — | Plays from the receiver |
| Exact | Off | On and off | Starts (`mediaPlayback` and `shortService`) | Plays, full 22s | Plays |
| Inexact, played in the receiver | On and off | Off | Not used | Plays, but only for the receiver's ~10s | Plays |

- **Audio hardening isn't a problem.** On the Pixel, with accessibility on, Android logged nothing against Yoniso. With it off, it logs "would be muted ... level: full", the same entry it logs for Google Clock's alarms. Entries that say "would be" are not enforced, and the bell was audible.
- **Vibrate mode:** a `USAGE_ALARM` bell is audible on the Pixel with the ringer on vibrate (the user heard it at 20:48). So the bell switched from the notification stream to the alarm stream (see Decisions).
- **Ringer mode, DND, call state, idle and interactive** are all read correctly from the receiver.
- **Exact alarms** are denied by default on a fresh install (`canScheduleExactAlarms() == false`), and `setExactAndAllowWhileIdle` throws `SecurityException` until the user grants them.
- **Notification permission** doesn't matter: the service starts and plays either way.
- **Playing in the receiver** works for a few seconds, but once `goAsync()` finishes the process can be frozen or killed mid-bell. Not usable for a 22s bell.
- **`shortService`** behaves like `mediaPlayback` here, and needs no `FOREGROUND_SERVICE_MEDIA_PLAYBACK` permission. A bell is well under its ~3-minute limit.

**Decisions (agreed 2026-11-11, folded into the sections above):**
- Use exact alarms for both modes whenever granted, and ask for the grant when bells are first turned on. If it isn't granted, use inexact alarms, which work while the accessibility service is on. If both are off, vibrate only from the receiver, and the bells screen says why the bell can't ring.
- Use the `shortService` type rather than `mediaPlayback`. Check Play's foreground-service declaration rules for it in Phase 10.

### Phase 6: schedule core and tests

- `core/BellSettings`, `core/BellSchedule`, `core/VibrationPattern`, `core/RingDecision` (silent/DND/call/media/pause → ring, vibrate, or skip). All pure, with an injected clock, zone and `Random`.
- Tests: clock alignment (including 45 and 90 minutes), random gaps within min/max with a seeded `Random`, active-window starts and ends, windows crossing midnight, day filters, all days off (no next ring), snooze, DST forward and back, late alarms, and each `RingDecision` case. Waveform length cap and amplitude clamping.

### Phase 6: as built

- `core/BellSettings`: timing, interval, random bounds, `ActiveHours`, output, bell ID, volume, vibration, media behaviour and snooze. `sanitized()` makes stale stored values safe: an interval that isn't offered becomes 15, random bounds are clamped to 5–240 minutes and ordered, times are cut to whole minutes, and volume and vibration are clamped. Defaults: off, random 10–30 minutes, 08:00–21:00 every day, bell only, volume 0.5, Double tap, vibrate during media.
- `core/BellSchedule.nextRing(after, settings, zone, random)` returns the first ring strictly after `after`, or null if bells are off or no day is active. It looks up to a week ahead.
  - Interval slots are multiples of the interval from local midnight. All the options divide a day, so 15 minutes lands on the quarter hours and 45 and 90 minutes stay aligned every day.
  - **The window end is inclusive**, so "08:00–21:00" hourly rings at 21:00. A window with start equal to end lasts 24 hours.
  - Random mode merges touching windows into one span, so with "all day, every day" the gap doesn't restart at midnight. A random gap that would leave the span starts again from the next span's start, and a span shorter than the minimum gap never rings.
  - DST is handled by `java.time`: a slot in the skipped hour moves forward (02:30 rings at 03:30), and the repeated hour rings once (on its first pass). The tests check that rings stay unique and in order.
  - `isActive` (the receiver's safety check) and `isLate` (more than 2 minutes) are separate functions.
- `core/RingDecider.decide(due, now, settings, zone, phone)` returns `Ring(sound, vibrate)` or `Skip(reason)`. Skips are checked in this order: disabled, late, snoozed, outside hours (checked against the due time), DND, silent, call, pause showing, media (with Skip). When sound is blocked, either by media with Vibrate or because `canPlaySound` is false (inexact alarm with the accessibility service off), a bell-only setting vibrates instead.
- `core/VibrationPattern.toWaveform(amplitudeControl)` builds alternating off/on timings starting with off, with consecutive off segments merged so it still alternates after a cut. Swell is four rising steps per pulse. Without amplitude control, every pulse uses `DEFAULT_AMPLITUDE` (-1) and plays steady. The total is cut at 6 seconds, and repeats are 600 ms apart. Pulse length goes up to 1500 ms, because the Swell preset is 1200 ms.
- `VibrationPreset`: Gentle tap, Double tap (the default), Heartbeat (two quick pulses, repeated), Three slow, Swell. `VibrationPreset.of(pattern)` returns null for a custom pattern.
- Bug found by the tests: a time exactly on the boundary between two touching windows matched the earlier, ending window, so the next window was skipped and the gap was 24 hours. Merging touching windows fixed it, and a regression test covers it.

### Phase 7: ringing in the background

- `BellAlarms` (schedule/cancel, exact or inexact), `BellReceiver`, `BellService`, the boot/time receivers, the `bells` DataStore.
- `PauseBell` and `BellService` share a small `BellPlayer` that resolves the bell ID to a resource or file, with fallback.
- No UI yet: debug defaults (or an adb broadcast) to test on the emulator and Pixel.

### Phase 7: as built

- New package `bells/` for the Android side. `core/` stays pure.
- `data/BellRepository`: the `bells` DataStore. Every `BellSettings` field as its own key (times as minutes since midnight, enums by name, days as a set of names), plus `next_ring_at`. Values are sanitized when read and written; unknown names fall back to the defaults. `update { }` changes the settings, and the caller then calls `BellAlarms.reschedule()`.
- `bells/BellAlarms`:
  - `reschedule(after)` computes the next ring and sets it, or cancels the alarm if there is none. Used after a settings change, a clock or time zone change, and each bell.
  - `restore()` sets the stored ring again if it's still ahead, so a random bell already chosen doesn't move; otherwise it reschedules. Used on boot, app update, exact alarms being allowed, the accessibility service connecting and `MainActivity` starting. The last two cover exact alarms being revoked, which cancels them.
  - One `PendingIntent` (request code 0), so each alarm replaces the last. Its extras are the due time, when it was set and whether it is exact. A `Mutex` keeps scheduling one at a time.
- **Late tolerance for inexact alarms** (a change to Phase 6): Android may deliver an inexact alarm up to 75% of its lead time late (confirmed on the emulator: `window=+1m21s` for a 1m48s lead). With a fixed 2 minutes, most inexact bells would have been skipped. `BellSchedule.lateTolerance(setAt, due, exact)`: 2 minutes for exact alarms, and 2 minutes plus 75% of the lead for inexact ones, at most an hour. `RingDecider.decide` takes it as a parameter. A late inexact bell is still checked against active hours using its due time, so it can ring a little after the window ends.
- `bells/BellReceiver` sets the next alarm first, then reads the phone state and calls `RingDecider`. Vibration plays from the receiver. Sound starts `BellService`. If Android refuses the start, it vibrates instead.
- `bells/RunningState`: process-wide flags for "accessibility service connected" (so `canPlaySound` = exact alarm or connected) and "pause showing" (set by `OverlayController`).
- `bells/BellService`: `shortService` on 14+, `mediaPlayback` on 10–13, no type before that. A quiet, low-importance "Mindfulness bells" channel, with a Stop action. Transient may-duck focus. It stops when the bell ends. A new bell during a ring replaces it without stopping the service. `onTimeout` stops it. Declares `FOREGROUND_SERVICE_MEDIA_PLAYBACK` for 10–13; the Play declaration question is still for Phase 10.
- `bells/BellPlayer` replaces `PauseBell`: `BundledBell` maps IDs to raw resources, and an unknown bell or a load or playback failure falls back to the Plum Village bell. Optional volume. The pause still plays the default bell until Phase 8.
- `bells/BellVibrator`: `VibratorManager` / `Vibrator`, notification usage (`VibrationAttributes` on 13+, `AudioAttributes` before).
- `bells/BellRescheduleReceiver`: boot, app update, exact-alarm grant (restore); time set, time zone (reschedule).
- **Debug builds only:** `BellDebugReceiver` (in `src/debug`, guarded by the `DUMP` permission, which the adb shell holds): `adb shell am broadcast -n app.yoniso/.bells.BellDebugReceiver --es cmd set|test|status ...`. Its doc comment lists the extras. Results in `adb logcat -s Yoniso`.
- Emulator results:

| Case | Result |
|---|---|
| Inexact, accessibility off | Fired 38s late, vibrated only (`canPlaySound=false`) |
| Inexact, accessibility on | Fired 82s late (within its window), bell played through `shortService` |
| Exact, accessibility off | Fired 4ms late, bell played |
| Service | Quiet notification with Stop. Stops itself and releases focus when the bell ends (22s). Stop ends it at once. Without notification permission it still rings, with no notification |
| Exact alarms granted | The broadcast re-set the same ring as exact |
| DND / silent / vibrate mode | Skip / skip / rings |
| Reboot | Same ring time restored (to the millisecond) |
| Time zone change | New ring worked out |
| Bells turned off | Alarm cancelled |

- Not yet tested on a device: the Pixel 8, a pause showing, media playing, a call, Doze. These are in the Phase 10 device tests.

### Phase 8: settings UI

- The bells row and screen (timing, hours, days, output, bundled bell list with previews, volume, media behaviour, exact-alarm grant, status line).
- Notification permission request and exact-alarm grant flow.
- Add the CC0 bells (trim and normalise like `bell.mp3`) and their credits.
- "Listen to a bell" in the pause plays the shared bell.

### Phase 8: as built

- **Main screen:** a "Mindfulness bells" row below Cooldown. It shows a summary ("Every 10–30 min, 8:00 AM–9:00 PM, weekdays"). Tapping the row opens the bells screen, and its switch turns bells on and off.
- **Bells screen** (`ui/BellsScreen`), top to bottom:
  - A "Ring bells" switch and a status line: "Off. No bells ring.", "On. Next bell at 8:00 AM." for set times, or "around ..." for random ones, with "tomorrow" or a weekday when it isn't today.
  - Problems from `core/BellProblems` (unit tested): no days chosen, Do Not Disturb, silent, and a "Let Yoniso ring on time" card with "Open settings" when exact alarms aren't allowed. The card says bells will only vibrate if accessibility access is off too, and otherwise that they may come late.
  - Timing chips "At set times" or "At random". Set times show the interval chips. Random shows a range slider from 5 to 120 minutes in steps of 5, saved when the thumb is let go.
  - Hours: "From" and "To" buttons that open a Material time picker, in the phone's 12- or 24-hour style. It notes when the window crosses midnight or lasts all day.
  - Day chips in the locale's week order, announced with the full day name.
  - "Each bell": Rings / Vibrates / Rings and vibrates.
  - Bell (shown when it rings): radio rows with Play/Stop previews, and the volume slider. Previews stop when leaving the screen.
  - "While music or video plays": Vibrate only / Ring as usual / Skip the bell.
- **Turning bells on** (from either switch): on Android 13+, Android's notification prompt if it isn't granted. Then, if exact alarms aren't allowed, a "Let bells ring on time?" dialog with "Open settings" (Yoniso's "Alarms & reminders" page) or "Not now". Bells work either way.
- Every change goes through `MainActivity.updateBells`, which saves and reschedules on `BellAlarms.scope`, so it finishes even if the screen closes. The environment (exact alarms, accessibility, DND, silent) is checked again in `onResume`.
- **Shared bell:** the accessibility service observes the bell settings, and "Listen to a bell" plays the chosen bell at the bell volume.
- Shared UI pieces (`Section`, `Choices`, and a new `SubScreen` header) moved to `ui/Components.kt`.
- **Bundled bells:** the Plum Village bell plus six CC0 1.0 bells from Freesound: Singing bowl, Deep bowl, Bright bell, Tingsha, Harmony bell and Temple bell (`res/raw/bell_<id>.mp3`). Picked from the best-rated CC0 single strikes, with the licence checked on each sound's page. Each was made from Freesound's high-quality preview (about 180 kbps; originals need a login), trimmed to one strike, faded out, normalised to about −20.5 LUFS like `bell.mp3`, and saved as 44.1 kHz stereo 192 kbps MP3. They add about 2 MB. `BundledBell` holds each one's author and source URL. The About screen credits them with links, and the README lists them under "Sounds".
- Emulator: the whole turn-on flow (notification prompt, exact-alarm dialog, settings toggle, returning without the card, the same ring re-set as exact), timing, interval, time pickers, days, preview and stop, volume, a background bell playing the chosen bell (Tingsha), the About credits, and "Listen to a bell" in a pause.

### Phase 9: custom bells and vibration

- Import, validate, name, rename and delete custom bells.
- Vibration presets, editor, and preview. Intensity controls appear only on amplitude-control devices.

### Phase 9: as built

- **Core** (`core/CustomBell`, unit tested): a `CustomBell` is a file name (a random UUID) and a name; its bell ID is `custom_<uuid>`. `CustomBell.fileNameOf` only accepts a well-formed UUID, so a stored ID can't point outside the bells folder. `CustomBellRules.check(size, duration)` returns too large (over 5 MB, checked first), unreadable (empty or no duration) or too long (over 60 s). `defaultName` drops the extension and underscores ("temple_gong.mp3" → "temple gong"), and `cleanName` puts a name on one line, trims it and cuts it to 40 characters. The list is stored as one string, a line per bell ("uuid<TAB>name"), under `custom_bells` in the `bells` DataStore; malformed and repeated lines are dropped when read.
- `VibrationPattern.fullMillis` and `isCut`, so the editor can say how long a pattern lasts and when the 6-second cap cuts it.
- `data/BellRepository`: `customBells`, `addCustomBell` (also selects it), `renameCustomBell`, and `removeCustomBell`, which switches to the Plum Village bell in the same edit if the deleted bell was in use.
- `bells/CustomBellFiles`: `import(uri)` reads the display name and size, refuses a reported size over 5 MB before copying, copies to `filesDir/bells/<uuid>` (stopping a byte past the limit, since the reported size can be missing or wrong), then checks the copy with `MediaMetadataRetriever` (has audio, duration) and a test `MediaPlayer.prepare()`. A refused copy is deleted.
- `bells/CustomBellLibrary`: what the bells screen calls. Saves, discards, renames and deletes run on `BellAlarms.scope`. Each import first deletes files no bell refers to (e.g. imported, then the app closed before naming), except imports still waiting for a name.
- `BellPlayer` plays a custom bell from its file. A missing file or a load or playback failure falls back to the Plum Village bell and logs a warning.
- **Bells screen:**
  - Bell list: custom bells after the bundled ones, each with Edit (rename, or Delete with a confirmation that says when the Plum Village bell takes over) and Play. "Add your own…" opens the system file picker for `audio/*` ("Adding…" while it copies), then a "Name your bell" dialog with the suggested name. Cancel discards the copy; a tap outside doesn't. Refused files get "Can't use this sound" with the reason.
  - A "Vibration" section when the bell vibrates and the phone has a vibrator: preset chips, plus a "Custom" chip once the pattern is edited. Choosing a preset or finishing an edit plays the pattern. Preview, and "Customise" opens sliders for pulses, pulse length and gap (rounded to 10 ms; the gap shows only with more than one pulse), strength (5% steps) and Steady / Swells (both only with amplitude control), Repeat (once, twice, 3 times), and "Lasts 1.2 seconds", noting when the end is cut off at 6 seconds.
- Emulator results:

| Case | Result |
|---|---|
| Valid MP3 | Copied, named ("temple gong" suggested), selected, previews on the alarm stream |
| Background ring, screen off | The custom bell played |
| Custom file missing | Warning logged, the Plum Village bell played |
| 75 s MP3 / 5.5 MB WAV / random bytes as .mp3 | Refused as too long / too large / unreadable, nothing kept |
| Cancel at naming | The copy was deleted |
| Rename | Saved |
| Delete the bell in use | File deleted, Plum Village selected |
| Heartbeat preset | Played 1245 ms (1240 expected) |
| Edit to 5 × 1470 ms, twice | "Custom" chip, cut-off note, played 6008 ms |
| Choosing a preset after editing | "Custom" chip gone |

- **Home screen** (added after Phase 9, 2026-11-20): the pause and the bells no longer share a page. `ui/HomeScreen` shows two square tiles side by side, then About and Privacy policy. A tile shows only the feature's icon: the Yoniso ripple (`ic_pause_feature`, cropped from the launcher's monochrome icon) or a singing bowl on its cushion with sound arcs to each side (`ic_bell_feature`, drawn for Yoniso). It's filled with the accent colour when on, and outlined with a muted icon when off. TalkBack reads e.g. "Pause before apps, On". There's no switch on the home screen. Tapping a tile opens its page. The pause settings moved to `ui/PauseScreen` (was `SettingsScreen`), a sub-screen with the accessibility notice, on/off, apps, pause length and cooldown. The pause tile shows a red dot ("Needs access" to TalkBack) when the pause is on but accessibility access is off. The summary strings (`bellSummary`) were removed.
- **First-launch intro** (2026-11-20): `ui/IntroScreen` replaces the onboarding. It shows once (the same `onboarding_done` flag, so existing installs skip it): "Welcome to Yoniso", each feature with its tile icon and a sentence, and "Get started", which goes home. It no longer asks for accessibility access; the pause page asks, with the full disclosure, so someone who only wants bells is never asked. On a fresh install the pause is on, so its tile shows the red dot until access is granted. The Play disclosure video needs re-recording from the intro (see the README).
- Not tested yet: a device without amplitude control (the strength and shape controls should hide), and the Pixel. Both are in the Phase 10 device tests.

### Phase 10: extras, testing and Play

- Quick Settings tile and snooze.
- Device tests: a day of random bells on the Pixel with normal use; Doze overnight (nothing outside hours, nothing burst in the morning); reboot; time zone change; DST (emulator clock); silent, vibrate and DND; a call; music playing; a pause showing; accessibility service off; exact alarm revoked; a custom bell deleted from the source folder; a corrupt file; a vibration-only device without amplitude control (emulator).
- README, PRIVACY.md, About screen credits, Play foreground-service declaration and video.

### Phase 10: as built

- **Snooze** (`core/BellSchedule`, unit tested): `SNOOZE_HOUR`, and `snoozeUntilTomorrow(now, hours, zone)`, which is midnight after today. Inside an active window, "today" is the day the window starts on, and a window that crosses midnight is snoozed to its end (at 23:00 or 01:00 in 22:00–02:00, until 02:00).
- **Bells screen:** when bells are on, "Snooze 1 hour" and "Snooze until tomorrow" under the switch, or "End snooze" while snoozed. The status line reads "Snoozed. Next bell tomorrow around 8:26 AM."
- `BellAlarms.update(context, transform)` saves, reschedules and refreshes the tile; `MainActivity` and the tile both use it. `BellAlarms.setEnabled` clears any snooze, so bells turned off and on again start afresh.
- **Quick Settings tile** (`bells/BellTileService`, label "Bell" (was "Bells"), the bell feature icon, `TOGGLEABLE_TILE` so TalkBack reads it as a switch). A tap turns bells on or off. The subtitle (Android 10+) is "Off", "Snoozed", "Next 10:15" (set times) or "Around 10:09 AM" (random), or "On" when the next bell isn't today. It reads the stores while the panel is open. A long press opens the bells screen (`QS_TILE_PREFERENCES` on `MainActivity`). Turning bells on from the tile asks for no permissions; the bells screen shows what's missing.
- **No `mediaPlayback`** (Play's foreground-service rules, read 2026-11-20): an app targeting 14+ must declare each foreground-service type it uses in Play Console, with a video. For `mediaPlayback` the video must show "a user starting video or audio playback", and "Playback must be user-initiated". A bell started by an alarm isn't, so it risked rejection. Types are only required from Android 14, so `BellService` now has no type on 10–13 and `FOREGROUND_SERVICE_MEDIA_PLAYBACK` is gone. The manifest declares `shortService` only. Play's pages don't say whether `shortService` needs a declaration; check what Play Console asks for at upload. (Also noted: apps targeting 15+ can't start a `mediaPlayback` service from `BOOT_COMPLETED`; Yoniso doesn't.)
- Tested: a bell from an exact alarm in the background rang on the API 36 emulator (type `shortService`) and on a new API 33 emulator (`yoniso_api33`, no type).
- Fixed a lint error from Phase 9: the vibration editor read the locale in a way Compose can't observe.
- Emulator: tile added with `cmd statusbar add-tile`, tap on and off, the subtitle following the next ring, a long press opening the bells screen, snooze for an hour (next bell moved past it), end snooze, snooze until tomorrow (next bell tomorrow at 8:26), "Snoozed" on the tile.

## Open questions

- Should a bell show anything on screen (e.g. a brief "Breathe" notification or a soft overlay when the screen is on), or be sound/vibration only? Proposed: sound/vibration only, with the quiet service notification.
- Number of strikes per bell (1–3), as in Plum Village's practice of inviting the bell? Proposed: later.
- Should the random minimum go below 5 minutes? Doze limits make shorter gaps unreliable anyway.
