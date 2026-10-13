# Dot Recorder

A minimal Android screen recorder with a clean, unobtrusive control: instead of
the bulky overlay the built-in recorder drops on your screen, this shows a small
draggable **black dot**. Tap it to expand a compact Stop control; tap again to
collapse back to the dot. Drag it anywhere. That keeps your footage clean — handy
for recording tutorials or app demos without a cluttered control bar in frame.

## What this does and does not do

Android deliberately makes screen capture visible, and this app does **not** work
around that:

- Every time recording starts, the **OS shows its own consent dialog**
  ("Start recording or casting?"). The app cannot suppress or auto-dismiss it.
- While recording, Android keeps a **recording indicator** in the status bar /
  privacy chip. The app cannot hide it.

The only thing this app controls is *its own* on-screen UI — the dot — which is
what ends up in your recording. It is not a hidden or covert recorder, and it
can't be used as one.

## Features

- Tap-to-record, foreground-service capture to an MP4.
- Minimal draggable dot overlay; tap to expand/collapse controls.
- H.264 / MP4, screen resolution, 30 fps, 8 Mbps (tweak in `ScreenRecordService.buildRecorder`).
- Recordings saved to the app's private external dir:
  `Android/data/com.example.dotrecorder/files/recordings/`.

## Requirements

- Android 10 (API 29) or newer.
- Android Studio (Koala or newer) **or** JDK 17 + the included Gradle wrapper.

## Build

```bash
./gradlew assembleDebug
```

The APK is written to `app/build/outputs/apk/debug/app-debug.apk`.

Install it on a device with USB debugging enabled:

```bash
adb install -r app/build/outputs/apk/debug/app-debug.apk
```

Or open the project in Android Studio and hit Run.

> Note: the debug APK is signed with the debug keystore. For sharing/installing
> without Android Studio you may need to allow "install from unknown sources".
> For a release build, configure your own signing config.

## First run

1. Launch the app, tap **Start recording**.
2. Grant **"Display over other apps"** when prompted (needed for the dot), then
   tap Start again.
3. Approve the system **screen-capture consent** dialog.
4. The app drops you to the home screen with the dot showing. Record away.
5. Tap the dot → **Stop**, or use the Stop action in the notification.

## Permissions

| Permission | Why |
|---|---|
| `SYSTEM_ALERT_WINDOW` | Draw the floating dot over other apps |
| `FOREGROUND_SERVICE` + `FOREGROUND_SERVICE_MEDIA_PROJECTION` | Required to hold a capture session |
| `POST_NOTIFICATIONS` | The ongoing-recording notification (Android 13+) |
| `RECORD_AUDIO` | Declared for optional mic capture (not wired up in this basic build) |

## Project layout

```
app/src/main/java/com/example/dotrecorder/
  MainActivity.kt        — permissions + triggers the OS consent dialog
  ScreenRecordService.kt — foreground service: MediaProjection + MediaRecorder
  FloatingControl.kt     — the draggable dot overlay
```
