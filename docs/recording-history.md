# CallReplay: recorded call history

CallReplay is an independent fork of [ShizuCallRecorder](https://github.com/kitsumed/ShizuCallRecorder).
It adds a Recents home screen while retaining the original recording engine and settings.
The installable package is `io.github.uriel1010.callreplay`, so it can coexist with the original app.
The original license and attribution remain in place. This feature was implemented with OpenAI Codex assistance.

## Using Recents

1. Complete the existing setup and choose a recording folder in Settings.
2. Open Recents to browse recordings, newest first, grouped by day.
3. Search by caller name, phone number, or filename. Filter incoming or outgoing recordings.
4. Tap play to listen in the app. Pause or seek using the controls beneath the selected call.
   Use the share button beside a recording to open Android's share menu with the audio attached.
   The chosen recipient receives temporary read access to that recording.
5. Refresh after adding or deleting files outside the app. Recents also refreshes on resume.

Recents displays recorded calls from the selected folder, including nested folders. It does not
request system call-log access or display missed and unrecorded calls. Existing audio files appear
under their filenames with an “Existing recording” label; their callers and directions are not guessed.
New recordings retain caller, number, direction, and recording start time in a private SQLite index.
The timestamp is the start of recording, which can be later than the start of the phone call.
The progress control shows recording duration rather than total phone-call duration.

Only one recording plays at a time. Playback stops when leaving Recents, backgrounding the app,
losing audio focus (including interruptions), or disconnecting headphones. It does not resume automatically.
Unsupported, incomplete, or inaccessible files produce an inline error and can be retried.
No playback library or runtime dependency was added.

## Storage and migration

Audio stays in the chosen folder. No files are renamed or moved. Empty files are excluded.
Deleting a file outside the app removes it from Recents after refresh. Switching folders lists the
new folder; selecting the old folder again restores that folder's recorded caller information.
Metadata remains private to this installation, is not written into audio files, and is lost if app
data is cleared. Files renamed or moved externally may receive a new URI and appear as existing recordings.
After installing alongside the original app, select its recording folder to browse its audio files.
Use one recorder at a time to avoid competing recording services.

## Device verification

Build with the project's configured Java/Gradle/Android SDK versions:

```sh
gradle :app:assembleDebug :app:assembleDebugAndroidTest :app:lintDebug
```

On a shared Linux machine, use `scripts/build-local-limited.sh` instead of invoking Gradle directly.
Set `JAVA_HOME`, `ANDROID_HOME`, and `CALLREPLAY_GRADLE` first. The script requires a working user
systemd session and at least 4 GiB of available memory; it refuses to run without those protections.
It caps the entire build at 2.5 GiB, disables build swap, limits CPU to two cores' worth of processing,
and uses one Gradle worker. The Kotlin compiler runs inside the capped Gradle process.

```sh
bash scripts/build-local-limited.sh :app:assembleDebug :app:assembleDebugAndroidTest :app:lintDebug
adb install -r app/build/outputs/apk/debug/app-debug.apk
adb install -r app/build/outputs/apk/androidTest/debug/app-debug-androidTest.apk
adb shell am instrument -w io.github.uriel1010.callreplay.test/androidx.test.runner.AndroidJUnitRunner
```

Device tests use silent WAV files and synthetic caller details. They verify playback, pause/seek,
recording switching, replay, missing-file recovery, metadata persistence, search, filters, settings
recovery, landscape/large-text layouts, and phone-number direction in RTL layouts. The UI tests
exercise the production Recents content without changing onboarding preferences. They save
screenshots under the app's private `files/device-test-screenshots` directory.

Before relying on the fork on a phone, check:

- Record incoming and outgoing calls, including an anonymous caller and a caller with Unicode characters.
- Restart the app and verify caller metadata and newest-first ordering survive.
- Include older audio files and nested filename-template folders; exclude empty and non-audio files.
- Search and filter; verify empty results, an empty folder, and revoked folder access.
- Play, pause, seek, replay after completion, and rapidly switch recordings.
- Interrupt playback with a call, another audio app, headphone removal, backgrounding, and navigation.
- Rotate the screen; verify playback stops and only one player can run afterward.
- Check small phones, landscape, large text, TalkBack, and both app themes.

## Implementation references

- [Android document storage](https://developer.android.com/training/data-storage/shared/documents-files)
- [MediaPlayer states and resource lifecycle](https://developer.android.com/media/platform/mediaplayer/state-resources)
- [Compose list keys](https://developer.android.com/develop/ui/compose/lists#item-keys)
