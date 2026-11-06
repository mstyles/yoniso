# Yoniso privacy policy

**Effective date:** 6 November 2026

Yoniso is an Android app that shows a short breathing pause when you open an app you have chosen. This policy explains what information Yoniso uses, and how.

**In short:** Yoniso uses only what it needs on your phone to show the pause. It has no internet access, so nothing it uses or stores ever leaves your device. There are no accounts, ads, analytics or tracking.

Yoniso is developed by Matt Styles ("we").

## What Yoniso uses, and why

### Which app is in front (accessibility service)

Yoniso uses Android's accessibility service to notice when an app comes to the front of the screen. For each change, it receives:

- the package name of the app whose window appeared (for example `com.android.chrome`), and
- whether that window fills the screen. This is used to tell a full app from a small popup.

Yoniso uses this only to decide whether to show the pause: whether you selected that app, whether Yoniso is turned on, and whether the app's cooldown has passed. These events are not saved or logged, and nothing about them is kept once that decision is made. The only exception is the time of the last pause, described below.

Yoniso **cannot** read what is on your screen, what you type, or any other content. It asks Android only for window-change events, and it is set up so it can't retrieve window content.

We use the accessibility service only for this purpose. Before you turn it on, Yoniso shows this explanation and asks you to agree. You can turn it off at any time in your phone's Settings → Accessibility.

### Your list of apps

To let you choose which apps get a pause, Yoniso asks Android for the apps that appear in your app drawer, with their names and icons. This list is shown on screen and is not stored. Only the apps you select are saved, as described below.

Yoniso also checks which app is your home screen, which is your phone app, and where Android Settings is, so that it never pauses them.

### Phone calls and sound

So that a pause never covers an incoming call, Yoniso checks whether a call is ringing or in progress:

- On Android 12 and later, it reads the phone's audio mode (for example "ringing" or "in a call").
- On Android 8 to 11, it reads the call state (idle, ringing or in a call).

Yoniso does not read phone numbers, contacts or call history, and it does not record calls.

While a pause is showing, Yoniso asks other apps to pause their sound and mutes the media volume, then restores it when the pause ends. It plays no sound and never uses the microphone.

## What Yoniso stores

Everything Yoniso stores stays on your phone, in storage that only Yoniso can access:

- your settings: whether Yoniso is on, which apps you selected (stored as package names), the pause length, the cooldown length, and whether you have seen the welcome screen;
- for each selected app, the time its last pause was shown, so the cooldown works even after a restart. Times that no longer matter are deleted automatically;
- a marker recording that Yoniso muted the media volume during a pause, so the volume can be restored if the app stops unexpectedly.

Yoniso does not store a history of which apps you opened or when, beyond the last-pause time above.

This data is excluded from Android's cloud backup and from device-to-device transfer, so it is not copied to Google or to a new phone.

## Sharing

Yoniso does not share, sell or send any information to us or to anyone else. The app does not have Android's internet permission, so it cannot connect to the internet. It contains no advertising, analytics, crash-reporting or tracking code.

## Keeping and deleting your data

Your data stays on your phone until you delete it. You can:

- change your settings or selected apps in Yoniso at any time;
- clear everything in Settings → Apps → Yoniso → Storage → Clear storage;
- uninstall Yoniso, which removes all of its data.

Because we never receive your data, we hold nothing about you that we could access, correct or delete.

## Security

Yoniso's data is kept in app-private storage that other apps cannot read, protected by Android's own security. Nothing is sent over a network, so nothing can be intercepted in transit.

## Children

Yoniso does not collect personal information from anyone, including children.

## Changes to this policy

If Yoniso changes how it uses information, we will update this policy and its effective date, and describe the change in the app's release notes. If a change means Yoniso uses information in a new way, the app will ask for your agreement again before doing so.

## Contact

Questions about this policy or about Yoniso's privacy: matt@mattstyles.dev
