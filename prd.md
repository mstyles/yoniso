# Product Requirements Document: Yoniso

**Version:** 0.1 (MVP)  
**Platform:** Android  
**Status:** Ready for implementation

## 1. Product Overview

Yoniso is an Android application designed to encourage mindfulness during everyday smartphone use.

The app allows users to select installed applications on their phone. Whenever a selected application enters the foreground, Yoniso displays a brief interruption that encourages the user to stop and breathe before proceeding.

After the pause, the user can choose to continue into the application or exit back to the home screen.

The goal is not to restrict screen time but to help users become more intentional about how they use their devices.

### Guiding Principles

- **Mindfulness over restriction:** Encourage awareness rather than enforce limits.
- **Minimal friction:** The experience should be simple, gentle, and unobtrusive.
- **User autonomy:** Users always retain the choice to continue.
- **Privacy first:** All data remains on the device.
- **Simplicity:** Avoid unnecessary features, dependencies, and complexity.

## 2. Target Platform & Technology

The MVP will target Android only.

Recommended technology stack:

- **Language:** Kotlin
- **UI:** Jetpack Compose
- **Foreground app detection:** Android AccessibilityService
- **Interruption UI:** Accessibility overlay using WindowManager and ComposeView
- **Local persistence:** Jetpack DataStore
- **Architecture:** Simple separation of UI, service, business logic, and persistence

Use current stable Android SDKs and libraries where practical.

No backend, authentication, analytics service, or internet connection is required.

## 3. Functional Requirements

### FR-1: Application Selection

Users must be able to:

- View a list of installed, launchable applications.
- See each application's name and icon.
- Select or deselect applications using toggles or checkboxes.
- Search or filter the application list.
- Have their selections persist across app restarts and device reboots.

The application must exclude itself and critical system applications where interruptions could cause problems.

### FR-2: Foreground Application Detection

The app must monitor foreground application changes using Android's AccessibilityService.

When an application enters the foreground:

1. Determine the foreground application's package name.
2. Check whether the application is selected.
3. Check whether Yoniso is enabled.
4. Check whether the application's cooldown has expired.
5. If all conditions are satisfied, display the mindfulness overlay.

Repeated accessibility events for the same foreground transition must not generate duplicate interruptions.

### FR-3: Mindfulness Pause Screen

When triggered, display a full-screen interruption above the selected application.

The screen should:

- Have a minimal, calming appearance.
- Display a message such as "Take a breath."
- Show a gentle, animated breathing indicator.
- Guide the user through a brief pause.
- Prevent interaction with the underlying application while visible.

**Default pause duration:** 10 seconds.

The pause duration should be configurable to 5, 10, or 15 seconds.

After the timer completes, display two buttons:

**Continue**
- Dismiss the overlay.
- Allow the user to interact with the underlying application.
- Do not relaunch the application unnecessarily.

**Exit**
- Dismiss the overlay.
- Navigate to the Android home screen.

The countdown must not depend on the app being in its own foreground activity.

### FR-4: Per-App Cooldown

Each selected application must have an independent cooldown.

**Default cooldown:** 5 minutes.

Available cooldown options:

- No cooldown
- 1 minute
- 5 minutes
- 15 minutes
- 30 minutes

The cooldown begins when the mindfulness overlay is successfully displayed.

If an application reenters the foreground during its cooldown, it must not trigger another interruption.

Cooldown behavior must avoid immediate retriggering when the user taps Continue.

Cooldown timestamps should be handled correctly across service restarts and device reboots.

### FR-5: Settings

The main application screen must provide:

- Master enable/disable toggle.
- Navigation to application selection.
- Pause duration selection.
- Cooldown duration selection.
- Accessibility permission status.
- Instructions for enabling the accessibility service if necessary.

All settings must persist locally.

### FR-6: Permission Onboarding

On first launch, guide the user through enabling the required AccessibilityService.

Explain clearly:

- Why accessibility access is needed.
- That the app detects foreground application changes.
- That it uses this information only to display mindfulness interruptions.
- That app usage information is not transmitted or shared.

Provide a clear action to open Android's accessibility settings.

The application must handle permission denial or revocation gracefully.

## 4. Interruption Lifecycle

The interruption logic must be implemented as a predictable state machine.

Suggested states:

- `IDLE`: No interruption active.
- `PAUSING`: Mindfulness overlay visible; timer running.
- `AWAITING_CHOICE`: Timer complete; Continue and Exit available.
- `DISMISSING`: Overlay being removed.

### Key Rules

1. Only one mindfulness overlay may exist at a time.
2. The app must not interrupt itself.
3. Overlay-generated accessibility events must not cause additional interruptions.
4. Continue must not immediately retrigger a pause.
5. If the foreground application changes while an overlay is active, the overlay must be safely dismissed or reconciled.
6. Screen locking, phone calls, and critical system interactions must not become blocked by the overlay.
7. If the accessibility service is disabled or destroyed, all overlay resources must be cleaned up.
8. Rapid application switching must not cause duplicate or stranded overlays.

## 5. User Experience

### Main Screen

A minimal settings screen showing:

- Yoniso enabled/disabled.
- Number of selected applications.
- Button to manage selected applications.
- Pause duration.
- Cooldown duration.
- Accessibility service status.

### Application Selection Screen

A searchable list of installed applications with icons and selection controls.

### Mindfulness Overlay

The visual design should feel calm and uncluttered.

Suggested appearance:

- Neutral or muted background.
- Centered breathing animation.
- Short mindfulness prompt.
- Minimal text.
- No gamification, warnings, or guilt-inducing language.

Example copy:

"Take a breath."

"Notice this moment."

After the pause:

"Continue mindfully?"

Buttons:

- Continue
- Exit

Use subtle animation and respect the device's reduced-motion preferences.

## 6. Non-Functional Requirements

### Performance

- Foreground detection and overlay presentation should feel prompt and responsive.
- Avoid polling when Android system events can be used.
- Minimize battery consumption and background work.
- Avoid unnecessary wake locks or persistent foreground services.

### Reliability

- Handle repeated accessibility events.
- Handle service restarts.
- Handle rapid application switching.
- Avoid accidental interruption loops.
- Never leave the user trapped behind an unresponsive overlay.

### Privacy

- No backend or network calls.
- No account registration.
- No collection of screen content.
- Store only necessary settings and cooldown information locally.
- Request only the Android permissions necessary for the feature.

### Accessibility

- Support readable text and appropriate contrast.
- Ensure buttons have accessible labels.
- Respect reduced-motion preferences.
- Avoid flashing or aggressive animations.

## 7. Out of Scope

The following features are explicitly excluded from the MVP:

- iOS support.
- User accounts or cloud synchronization.
- Usage analytics and dashboards.
- Daily mindfulness streaks.
- Gamification or rewards.
- AI-generated mindfulness prompts.
- Scheduled blocking or app limits.
- Custom breathing exercises.
- Per-application pause durations or cooldown settings.
- Subscription or payment functionality.
- Social features.
- Notifications or reminders.

The MVP should remain focused on one primary experience: introducing a mindful pause before using selected applications.

## 8. Acceptance Criteria

The MVP is complete when:

- [ ] The user can select installed applications.
- [ ] Selections and settings persist across restarts.
- [ ] Launching a selected application triggers a pause overlay.
- [ ] Launching an unselected application does not trigger an overlay.
- [ ] The breathing animation and timer function correctly.
- [ ] Continue dismisses the overlay and reveals the requested app.
- [ ] Exit returns the user to the home screen.
- [ ] Each selected application has its own cooldown.
- [ ] Reopening an application during cooldown does not trigger another pause.
- [ ] The overlay never creates an immediate interruption loop.
- [ ] Disabling the accessibility service does not leave overlays visible.
- [ ] The experience works reliably during ordinary app switching.
- [ ] All application data is stored locally.

## 9. Implementation Priorities

Implement the application incrementally.

### Phase 1: Technical Proof of Concept

Before developing the full UI:

1. Create a minimal Android project.
2. Implement the AccessibilityService.
3. Detect and log foreground application changes.
4. Hardcode a single target application for testing.
5. Display a simple accessibility overlay when that application enters the foreground.
6. Implement Continue and Exit actions.

**Milestone:** Demonstrate that the app can reliably display and dismiss an overlay when a selected application enters the foreground.

### Phase 2: Core Functionality

1. Implement the interruption state machine.
2. Add the breathing timer and animation.
3. Implement per-application cooldowns.
4. Handle duplicate events and application switching.
5. Add automated tests for interruption decisions.

### Phase 3: Application UI

1. Build the main settings screen.
2. Implement installed-app discovery and selection.
3. Add persistent settings using DataStore.
4. Implement permission onboarding.

### Phase 4: Testing & Polish

1. Test on a physical Android device.
2. Verify rapid application switching.
3. Verify behavior when locking and unlocking the device.
4. Verify service restart and permission revocation handling.
5. Refine visual design and animation.
6. Review Google Play accessibility permission and disclosure requirements before distribution.

## 10. Implementation Guidance for Claude Code

Use this PRD as the source of truth.

Start by inspecting the repository and determining whether an Android project already exists.

Prioritize the Phase 1 proof of concept, since foreground application detection and overlay reliability are the greatest technical risks.

Follow these guidelines:

- Prefer simple, idiomatic Kotlin.
- Use established Android APIs rather than introducing unnecessary libraries.
- Keep interruption decision logic separate from Android UI and services so it can be unit tested.
- Avoid premature abstraction and complex architecture.
- Use modern stable dependencies compatible with the chosen Android SDK.
- Do not implement features outside the MVP scope.
- Do not introduce a backend or network dependency.
- Document any platform limitations or assumptions.
- Add focused tests for cooldowns, foreground transitions, and state changes.
- Ensure all overlays are safely removable.

**Primary objective:** Build a minimal, reliable Android application that helps users bring awareness to the moment they open a selected app.
