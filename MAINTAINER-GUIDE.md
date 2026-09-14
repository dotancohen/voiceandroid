# Voice for Android — a maintainer's guide

A map of the Android application `VoiceAndroid/`: what each part of the code is
for, how the parts call each other, and what is in the database. It is written
for a developer who is new to the project. A term in **bold** at its first use
is defined in the [glossary](#14-glossary) at the end.

Written on 2026-09-13 from the code itself (VoiceAndroid commit `9f1acd8` with
uncommitted changes, core checkout `987f769`). Checked against the code again
on 2026-09-14, on branch `accounts-pairing-sync`: VoiceAndroid commit `e86169a`
(uncommitted: this guide), the core submodule `submodules/voicecore` at
`abedc04` (the commit VoiceAndroid records), and `VoiceCore/` at `fe788d1`,
whose three commits after `abedc04` change only `README.md` and `CLAUDE.md`. Where an older document says something different
from the code, the code was taken as the truth, and the disagreements are listed
in [section 13](#13-where-older-documents-and-files-disagree-with-the-code).

## Contents

1. [Before anything else: the owner's phone](#1-before-anything-else-the-owners-phone)
2. [The Voice Family in one page](#2-the-voice-family-in-one-page)
3. [The layers of the Android application](#3-the-layers-of-the-android-application)
4. [Directory map](#4-directory-map)
5. [The Kotlin source, package by package](#5-the-kotlin-source-package-by-package)
6. [What happens when the application starts](#6-what-happens-when-the-application-starts)
7. [Files on the phone](#7-files-on-the-phone)
8. [The database](#8-the-database)
9. [How the parts work together](#9-how-the-parts-work-together)
10. [Building and testing](#10-building-and-testing)
11. [Rules to follow when changing the code](#11-rules-to-follow-when-changing-the-code)
12. [Where to look first](#12-where-to-look-first)
13. [Where older documents and files disagree with the code](#13-where-older-documents-and-files-disagree-with-the-code)
14. [Glossary](#14-glossary)

---

## 1. Before anything else: the owner's phone

On 2026-09-12 an instrumented test run (`./gradlew connectedDebugAndroidTest`)
replaced the application on the owner's phone. Android deletes an
application's data when the application is replaced, and a week of Notes and
Recordings was destroyed. There was no backup.

| Phone | What is on it | Processor type (**ABI**) |
|---|---|---|
| Samsung Galaxy S24 Ultra, the owner's phone | **Live data**: his Notes and Recordings, with no second copy | `arm64-v8a` |
| Samsung Galaxy A12 (SM-A12F), the owner's second phone | Treat it as live data too | **Not checked.** The APK packs `arm64-v8a` only (section 10.1), so it installs on the A12 only if `adb shell getprop ro.product.cpu.abilist` lists `arm64-v8a` |

Therefore:

- **Never** run `adb install`, `adb uninstall`, `pm clear`, or any Gradle task
  whose name contains `install` or `connected`, on either phone, unless the
  owner asked for that exact action in that message and a verified backup
  exists.
- **Make the backup first** with `tools/voice-phone-backup`, **as a command of
  its own**: alone, not piped into another command. Read its line
  `Backup verified.` and its listing; a backup that failed stops the work. Only
  then run the install, as a separate command:

```bash
cd /home/dotancohen/Projects/VoiceFamily/VoiceAndroid
tools/voice-phone-backup
# read "Backup verified." and the listing, then, as a separate command:
adb install -r app/build/outputs/apk/debug/app-debug.apk
```

- The backup copies `files/` (the database, `config.json`, the logs),
  `shared_prefs/` (the settings) and both Recording folders, and writes them to
  `~/voice-phone-backups/` by default. It leaves out the Whisper models unless
  `VOICE_BACKUP_MODELS=1` is set. It needs a debug build, because it uses
  `run-as`.
- Instrumented tests install as a separate application,
  `com.dotancohen.voiceandroid.uitest` (`testBuildType = "uitest"` in
  `app/build.gradle.kts`, TECHNICAL-DECISIONS 7.6), so they cannot reach the
  real application's private data. Do not remove this. A `connected` task
  still installs an APK, so the rules above still apply to it.
- **The separate application id does not protect the Recordings folder.**
  `VoiceRepository.defaultAudioFileDir` chooses
  `/storage/emulated/0/Recordings/Voice` whenever all-files access is granted,
  and that path does not contain the application id. A `.uitest` application
  given all-files access on the owner's phone would therefore read and write
  the owner's real Recordings.

---

## 2. The Voice Family in one page

| Directory | What it is | Language |
|---|---|---|
| `Voice/` | The desktop and server application | Python 3 |
| `VoiceAndroid/` | This application | Kotlin |
| `VoiceCore/` | The shared **core library**: the database, versioning, sync, cloud storage | Rust |
| `VoiceTranscription/` | Speech-to-text; on the phone, Whisper through whisper.cpp | Rust, C++ |

The most important fact for a maintainer: **almost every rule about data is
written once, in Rust, in VoiceCore.** The phone and the desktop call the same
Rust code. The Kotlin code draws the screens, records and plays audio, runs
Whisper, runs Android services and calls the core. When you ask "where is X
implemented?", check `VoiceAndroid/submodules/voicecore/src/` as well as the
Kotlin.

```
       VoiceAndroid (Kotlin)                        Voice (desktop, Python)
  Compose screens  (ui/screens, ui/components)      GUI  TUI  CLI  Web API
                |                                          |
  view models   (viewmodel/)                         src/core/*.py
                |                                          |
  VoiceRepository  (data/VoiceRepository.kt)               |
                |                                          |
  uniffi.voicecore.VoiceClient (generated Kotlin)    voicecore Python module (PyO3)
                |   JNA                                    |
  libvoicecore.so  ← submodules/voicecore/src/android.rs   |
                \                                          /
                 `----------- VoiceCore (Rust) -----------'
                                     |
                      notes.db (SQLite)  +  recording files
```

The core is checked out three times: `VoiceAndroid/submodules/voicecore`,
`Voice/submodules/voicecore` and the standalone `VoiceCore/`. The first two are
git **submodules**, and all three must point at the same commit
(`TECHNICAL-DECISIONS.md` 7.2).

### Where the rules are written

Since 2026-09-14 a document about more than one project is in the `VoiceFamily/`
directory, and a document about one project is in that project's directory.

| Document | What it holds |
|---|---|
| `VoiceFamily/CLAUDE.md` | Rules for every project: the owner's data, tests, naming, the three core checkouts, documentation |
| `VoiceAndroid/CLAUDE.md` | Rules and notes for this application: the owner's phone, the architecture, several features, the build as `build-app.sh` runs it. It holds what were the Android notes of `Voice/CLAUDE.md` |
| `VoiceFamily/TECHNICAL-DECISIONS.md` | Decisions shared by all projects, numbered (for example "3.1a") |
| `VoiceFamily/SYNC_SPECIFICATION.md` | Numbered rules of the data model, the sync protocol, files, accounts and pairing. An id such as `FILE-22`, `AUTH-9` or `PURGE-5` in a comment or a test name refers to a rule here. Until 2026-09-14 two rules had the id `FILE-12`; the second one (rust-s3 and TLS on Android) is now `FILE-24`, and `FILE-12` is send and fetch |
| `VoiceFamily/PLAN-ACCOUNTS-PAIRING-SYNC.md` | The plan for accounts, pairing and file transfer, in 16 stages. "Stage 9" in a comment refers to a stage here |
| `VoiceFamily/BUGS-THE-TESTS-MISSED.md` | Defects that reached the owner although tests existed, and the rule that would have caught each |
| `VoiceFamily/CLAUDE-VIOLATIONS.md` | Violations of the owner's instructions by Claude Code |
| `VoiceFamily/test-plans/` | The manual test plans, for the desktop, the server and the phones together |
| `VoiceAndroid/DEVELOPMENT.md` | Building, notes on selected parts, the two lanes of tests |
| `VoiceAndroid/USER_MANUAL.md` | How the application is used, screen by screen |
| `VoiceAndroid/README.md` | What the application is |
| `VoiceAndroid/PLAN-ANDROID.md` | The planning questions of December 2025, kept as history |

---

## 3. The layers of the Android application

Using "the user edits a Note and leaves the screen" as the example:

| Layer | File | What happens there |
|---|---|---|
| 1. Screen | `ui/screens/NoteDetailScreen.kt` | A **composable** function draws the Note from the view model's state and calls the view model when the user acts |
| 2. View model | `viewmodel/NoteDetailViewModel.kt` | Holds the screen's state in `StateFlow`s and starts work in `viewModelScope` |
| 3. Repository | `data/VoiceRepository.kt` | One method per operation; runs it on `Dispatchers.IO` and returns a Kotlin `Result` |
| 4. Generated binding | `app/src/main/java/uniffi/voicecore/voicecore.kt` | Kotlin generated by **UniFFI**; calls the native library through **JNA** |
| 5. Rust binding | `submodules/voicecore/src/android.rs`, `VoiceClient` | Converts arguments and errors, locks the database, calls the core |
| 6. Core | `submodules/voicecore/src/database.rs`, `versions.rs` | Writes the change, records its version, rebuilds caches |

This is the **MVVM** pattern (model, view, view model) with a **repository**.

### How Kotlin can call Rust

1. The core is compiled with the Cargo feature `uniffi` into
   `libvoicecore.so`, a native library for the phone's processor (ABI
   `arm64-v8a`). It is placed in `app/src/main/jniLibs/arm64-v8a/`, and Gradle
   packs it into the APK. `abiFilters` in `app/build.gradle.kts` packs
   `arm64-v8a` only; `-PtargetAbi=<abi>` asks for another ABI, whose native
   libraries must then be built first.
2. `uniffi-bindgen` reads the interface out of a desktop build of the same
   library and writes `voicecore.kt`: Kotlin classes such as `VoiceClient`,
   `NoteData`, `AudioFileData`, and the exception `VoiceCoreException`.
3. At start, `VoiceApplication` calls `System.loadLibrary("voicecore")`.

**The generated Kotlin file and the native library must come from the same
core code.** If a function exists in `voicecore.kt` but not in the `.so`, the
application crashes with `UnsatisfiedLinkError` the first time that function
is called. So they are always rebuilt together (section 10).

`VoiceClient` holds the database inside a `Mutex`, so the core runs one call at
a time, whichever thread calls it.

The JVM tests load the same core built for the computer, not for the phone
(section 10.3).

### Keep screens thin

The owner intends to replace Jetpack Compose with another toolkit. Logic
belongs in view models, `data/`, `audio/`, `transcription/` and `util/`; a
screen should only draw state and pass the user's actions to its view model.

### Adding a new core function

1. The function: `submodules/voicecore/src/database.rs` (or its module).
2. The Android binding: a method on `VoiceClient` in `submodules/voicecore/src/android.rs`.
3. The desktop binding: `Voice/rust/voice-python/src/lib.rs`, so the desktop does not fall behind.
4. Rebuild the bindings and the native library together; check that the new
   function appears in `voicecore.kt`.
5. A method in `VoiceRepository.kt`, then use it from a view model.
6. When it is a user action, add it to `AdbCommandReceiver`, its intent filter
   in `app/src/debug/AndroidManifest.xml`, and `tools/voice-adb`, so manual
   test plans can script it.

---

## 4. Directory map

```
VoiceAndroid/
├── app/
│   ├── build.gradle.kts            the application's build: SDK levels, abiFilters, build types, dependencies, test tasks
│   └── src/
│       ├── main/
│       │   ├── AndroidManifest.xml permissions, the activity, five services, the file provider
│       │   ├── java/com/dotancohen/voiceandroid/   the application (section 5)
│       │   ├── java/uniffi/voicecore/voicecore.kt  GENERATED binding to the core (8,978 lines)
│       │   ├── java/uniffi/voice_transcription/    GENERATED binding to Whisper
│       │   ├── java/androidx/compose/material/icons/  25 icons copied from material-icons-extended 1.7.6 (Apache 2.0)
│       │   ├── jniLibs/arm64-v8a/  libvoicecore.so, libvoice_transcription_android.so, libc++_shared.so (not in git)
│       │   └── res/                strings, theme, launcher icons, xml/shared_files.xml
│       ├── debug/                  debug build only: AdbCommandReceiver and its manifest
│       ├── test/                   JVM tests, including Compose screens with Robolectric
│       │   └── java/.../network/   LocalS3.kt and FaultyLink.kt, helpers for the storage tests
│       └── androidTest/            instrumented tests that run on a device (TagTreeItemTest)
├── submodules/
│   ├── voicecore/                  the Rust core (git submodule)
│   ├── voicetranscription/         Whisper bindings (git submodule)
│   └── jniLibs/, kotlin-bindings/, uniffi-generated/   old copies; no build reads them (section 13)
├── tools/
│   ├── voice-adb                   drives the debug application from a computer
│   └── voice-phone-backup          copies and verifies the phone's data
├── gradle/libs.versions.toml       library versions
├── build-app.sh                    the full build: bindings, native library, APK, JVM tests
├── settings.gradle.kts, build.gradle.kts, gradle.properties, gradlew
├── CLAUDE.md, MAINTAINER-GUIDE.md, PLAN-ANDROID.md
├── README.md, DEVELOPMENT.md, USER_MANUAL.md
└── build/, .gradle/, .kotlin/      build output; app/build/ also holds host-core/ and s3-test-venv/ (section 10.3)
```

`app/src/main/jniLibs/` is listed in `.gitignore`: the native libraries are
build output, rebuilt by `build-app.sh`.

**The icons.** The application depends on `material-icons-core` only. The 25
icons it draws that are not in the core set (for example `Mic`, `Transcribe`,
`CloudDownload`, `Merge`) are Kotlin files copied unchanged from the sources of
`material-icons-extended` 1.7.6, each with its Apache 2.0 header, under
`app/src/main/java/androidx/compose/material/icons/` in the packages `filled`,
`outlined` and `automirrored.filled`. They keep the library's package names, so
`Icons.Filled.Mic` works as before. To use another extended icon, copy its file
from the same sources in the same way.

**Versions** (`gradle/libs.versions.toml`, `app/build.gradle.kts`): Android
Gradle Plugin 8.7.3, Kotlin 2.0.21, Compose BOM 2024.12.01 (which also sets the
version of `material-icons-core`), Media3 1.5.1, Jellyfin's
`media3-ffmpeg-decoder` 1.5.0+1, CameraX 1.4.1, ZXing 3.5.3, Robolectric 4.14.1,
JNA 5.14.0; for the tests, moto 5.2.3. `minSdk = 29` (Android 10), `compileSdk`
and `targetSdk` 35. Java bytecode target 17; Gradle must run on JDK 21.

---

## 5. The Kotlin source, package by package

All paths below are under `app/src/main/java/com/dotancohen/voiceandroid/`.

### 5.1 The package root

| File | Purpose |
|---|---|
| `VoiceApplication.kt` | Runs once per process start: starts the two logs, loads `libvoicecore.so`, reports timezone changes to the core |
| `MainActivity.kt` | The one **activity**. Asks for the notification permission, sets the Compose content `VoiceApp`, accepts a navigation route from outside (ADB) and `voice://pair` links, and restarts the transcription service when the application comes to the screen |

### 5.2 `ui/` — navigation and drawing

`ui/VoiceApp.kt` holds the navigation graph (`NavHost`) and the list of routes
(`sealed class Screen`). There is no bottom bar: every screen is reached from
the toolbar of another. It also applies the "large interface" size to every
screen.

| Route | Screen file | Purpose |
|---|---|---|
| `notes` (start) | `NotesScreen.kt` | The Note list: search, filter, star, select several, merge, new Note, new recording |
| `note/{noteId}?record={record}` | `NoteDetailScreen.kt` | One Note: text, Recordings with player and recorder, Transcriptions, Tags, conflicts, previous and next Note; the dialogs "Where the copies are" and "Remove from this phone" |
| `tags/{noteId}` | `TagManagementScreen.kt` | The Tags of one Note |
| `tag_hierarchy` | `TagHierarchyScreen.kt` | Create, rename, move, delete Tags |
| `settings` | `SettingsScreen.kt` | The settings menu |
| `sync_settings` | `SyncSettingsScreen.kt` | Peers, the device, the account's upload limit, listening, the operation buttons, pairing codes |
| `import_audio` | `ImportAudioScreen.kt` | Importing a folder of existing recordings |
| `recorder_settings`, `microphone_settings`, `playback_settings`, `transcription_settings`, `advanced_settings` | `...SettingsScreen.kt` | One settings page each |
| `transcription_queue` | `TranscriptionQueueScreen.kt` | What is being transcribed and what waits |
| `trash` | `TrashScreen.kt` | Deleted Notes: recover or purge |
| `issues` | `IssuesScreen.kt` | What the user should know about (ISSUE-1), as plain lines with no action; a refresh icon reads them again |
| `missing_data` | `MissingDataScreen.kt` | Survey and calculation of facts that were never recorded |

`ui/screens/FilterScreen.kt` exists but is not in the navigation graph, so no
user can reach it.

`ui/components/` holds pieces used by several screens: `AudioPlayerWidget`
(the player in a Note, with waveform; each file's row has a ⋮ menu with "Where
are the copies?" and "Remove from this phone") and `CompactAudioPlayer` (the
player in the Note list, with no ⋮ menu), `AudioRecorderWidget`,
`PlaybackSpeedControl`, `TranscriptionCard`, `TranscriptionDetailsDialog`,
`TranscribeDialog`, `PendingTranscriptionIcon`, `TagChip`, `TagTreeItem`,
`CreateTagDialog`, `MultiTagDialog` (Tags for several Notes), `TagColourDialog`,
`ShareNoteDialog`, `QrCode` and `QrReader` (pairing), `Spotlight` (marks the
row of the Note just left), `StampText` (a timestamp at its own offset),
`AutoFocus`. `ui/theme/Theme.kt` is the Material 3 theme; `ui/UiSize.kt` the
large-interface scaling.

### 5.3 `viewmodel/` — the state of each screen

Each view model extends `AndroidViewModel`, keeps state in private
`MutableStateFlow`s exposed as `StateFlow`s, and calls `VoiceRepository`
inside `viewModelScope.launch`. A screen reads a flow with `collectAsState()`.

| File | Screen |
|---|---|
| `NotesViewModel.kt` | Note list, selection, merge, bulk transcription |
| `NoteDetailViewModel.kt` | One Note, saving, neighbours, transcriptions; `showLocations` and `removeLocalCopy` for a Recording |
| `SharedFilterViewModel.kt` | The current search and filter, shared by the list and the Note screen (scoped to the activity) |
| `FilterViewModel.kt` | Tag filter tree |
| `TagManagementViewModel.kt`, `TagHierarchyViewModel.kt` | Tags |
| `SettingsViewModel.kt` | Settings and sync screen, including `maxUploadMb`, `saveMaxUploadMb` and `listenAddresses` |
| `IssuesViewModel.kt` | Issues: `load()` reads the device names, this device's id and the issues, and turns them into sections with `IssuesText` |
| `ImportAudioViewModel.kt` | Import |
| `RecorderSettingsViewModel.kt`, `TranscriptionSettingsViewModel.kt` | Settings pages |
| `TranscriptionQueueViewModel.kt`, `TrashViewModel.kt`, `MissingDataViewModel.kt`, `SnapshotsViewModel.kt` | Their screens |

### 5.4 `data/` — the repository, data classes, sync services

| File | Purpose |
|---|---|
| `VoiceRepository.kt` | The only class that calls `VoiceClient`. A process-wide **singleton** (`VoiceRepository.getInstance(context)`). Chooses the audio directory, creates the client, reports the timezone. About 125 methods, grouped by Notes, Tags, Recordings, Transcriptions, conflicts and history, peers and operations, pairing, snapshots, bucket and encryption, and (since 2026-09-14) `fileLocations`, `checkFilesHere`, `madeHereButMissing`, `removeLocalCopy`, `listenAddresses`, `getMaxUploadMb`, `setMaxUploadMb`, `issues` and `deviceNames` |
| `Note.kt` | Kotlin data classes that mirror the generated ones: `Note`, `AudioFile`, `NoteAttachment`, `Transcription`, `Tag`, `SyncResult`, `Peer`, `CheckRow` ... |
| `OperationService.kt` | Foreground service in which every sync operation runs, with progress and Cancel in its notification; `OperationState` publishes progress to the screens |
| `SyncListenerService.kt` | Foreground service that keeps the phone listening for peers while the switch is on |
| `PeerDiscovery.kt` | Finding peers on the local network |
| `PairingRequests.kt` | A `voice://pair` link or a request to open the code reader, passed from the activity to the sync screen |
| `MissingData.kt`, `RepositoryMissingDataStore.kt` | The missing-data survey; the `Store` interface lets tests supply rows without a database |
| `TranscriptionFlags.kt` | The five flags and their wording; must agree with the desktop |
| `TagTree.kt` | Tag paths and depths, safe against a parent chain that loops |
| `TagColours.kt` | A Tag's colour: chosen one from the synced setting `tag_color.<name>`, else from the MD5 of the name |

### 5.5 `audio/` — recording and playback

| File | Purpose |
|---|---|
| `VoiceRecorder.kt` | The single recorder, outside any screen, so a recording continues when the user leaves the application. `save()` attaches the file to its Note |
| `RecordingService.kt` | Foreground service (type `microphone`) that keeps the process alive while recording |
| `WavRecorder.kt` | 16 kHz mono WAV through `AudioRecord` |
| `RecorderPreferences.kt` | Format (Opus in Ogg by default, Opus for speech, AAC, WAV), microphone, behaviour during a telephone call, the New button's default |
| `MicLevelMeter.kt` | Level stream for the microphone test |
| `AudioPlayerManager.kt` | The single ExoPlayer of the application (`AudioPlayerManager.shared(context)`), never released, so playback survives moving between screens. It uses the phone's decoders first and FFmpeg's (Jellyfin's `media3-ffmpeg-decoder`) for a format the phone cannot decode (FILE-21) |
| `PlaybackService.kt` | Foreground service (type `mediaPlayback`) with Pause and Stop in the notification |
| `PlaybackPreferences.kt` | One speed for every player, 0.5× to 3× |
| `WaveformExtractor.kt` | Draws a waveform from the levels a device kept with the Recording (FILE-20). Only when no levels are kept does it decode the file, one decoder at a time (TECHNICAL-DECISIONS 3.6), and then it stores the levels with the Recording for every other device |
| `WaveformCache.kt`, `LargeRecording.kt` | Local waveform cache; the "large recording" test (60 minutes or 100 MiB) |

### 5.6 `transcription/` — Whisper on the phone

| File | Purpose |
|---|---|
| `OnDeviceTranscriber.kt` | The queue and the job: creates a `Pending...` Transcription, converts the audio, runs Whisper, stores text, segments and the cost of the run. Service name `local_whisper` |
| `TranscriptionService.kt` | Foreground service that runs the queue (type `mediaProcessing`, or `dataSync` on API 34) |
| `JobQueue.kt` | The in-process queue: one job at a time; "transcribe this one next" never interrupts the running job |
| `AudioToWav.kt` | Decodes any Recording with `MediaCodec` and resamples it to 16 kHz mono WAV, which Whisper needs |
| `WhisperModels.kt` | Model catalogue; models are downloaded with resume into `files/whisper-models/` |
| `TranscriptionPreferences.kt` | Model, language, beam size |
| `TranscriptionWork.kt` | Reads the cost of a finished run from `service_response`, for estimates |

The phone refuses to transcribe more than ten minutes
(`Magic.TRANSCRIBE_MAX_SECONDS_ON_PHONE`, TECHNICAL-DECISIONS 3.4). The desktop
transcribes longer Recordings.

### 5.7 `util/`

| File | Purpose |
|---|---|
| `MagicNumbers.kt` | `object Magic`: every number chosen by looking at a screen, in one place (TECHNICAL-DECISIONS 3.7) |
| `AppLogger.kt` | Log to Logcat and to `files/voice.log` (bounded at 1 MiB) |
| `CriticalLog.kt` | `files/critical.log`: failures the user must be able to find, such as a failed import |
| `SecretWrap.kt` | Encrypts the device key and the recording key with an AES key that stays in the Android **Keystore**, before the core writes them to `config.json` |
| `Stamps.kt`, `TimeFormat.kt`, `Durations.kt` | Timestamps at their own offset; the date format presets and the custom pattern |
| `UiPreferences.kt` | Interface size, lines per row, spotlight duration |
| `NoteSharing.kt` | Sharing a Note or a Recording through the `FileProvider` |
| `IssuesText.kt` | The words of the Issues screen (sections, sizes, the reason a Recording is not in the bucket) and of the "Where the copies are" lines, in the desktop's words (`IssuesTextTest`) |
| `AddressText.kt` | The words after "Address" on Sync Settings (LISTEN-4): the address found through the phone's route, alone; or every candidate followed by the sentence that only one of them is correct; or the sentence that no address was found. The same as the desktop's `src/core/addresses_text.py` (`AddressTextTest`) |

### 5.8 `app/src/debug/.../automation/AdbCommandReceiver.kt`

A **broadcast receiver** that exists only in the debug build. It turns user
actions into **intents** that a computer can send over ADB, and replies with a
line starting `OK` or `ERROR`. The broadcast ends at once and the action runs
in the receiver's own coroutine scope, so a caller waits for the reply line in
logcat, never for `am broadcast` to return: Android declares an application not
responding when a broadcast is still open after 60 seconds, and a fetch or a
model download takes longer. `tools/voice-adb` sends them:

```bash
tools/voice-adb ping
tools/voice-adb create-note "פתק מהטלפון"
tools/voice-adb list-notes
tools/voice-adb help
```

The actions added on 2026-09-14 ("Where are the copies?", "Remove from this
phone", the upload limit, Issues) have no ADB action yet (section 13.2).

---

## 6. What happens when the application starts

1. Android creates `VoiceApplication`: logs start, `libvoicecore.so` is loaded,
   a receiver for `ACTION_TIMEZONE_CHANGED` is registered.
2. `MainActivity.onCreate`: edge-to-edge drawing, a route or pairing link from
   the intent is kept, the notification permission is requested, `VoiceApp`
   is set as the content.
3. The first call to `VoiceRepository` creates `VoiceClient(filesDir,
   SecretWrap.keystore())`. In Rust (`android.rs`, `VoiceClient::new`):
   - `Config::new` reads or creates `files/config.json`;
   - `Database::new(files/notes.db)` opens the database: it makes the schema
     in an empty file, and refuses a file of another schema (section 8.1);
   - `ensure_own_device_card` makes the device key and the device card once.
4. `VoiceRepository.initialize()` sets the device name from the phone model
   when it is still the core's placeholder, reports the timezone, tells the core
   the audio directory, and rebuilds the Note list caches.
5. `MainActivity.onStart` restarts the transcription service when jobs wait.

---

## 7. Files on the phone

### 7.1 The application's private directory

`context.filesDir`, which is `/data/data/com.dotancohen.voiceandroid/files/`:

```
files/
├── notes.db            the database (plus notes.db-wal and notes.db-shm while open)
├── config.json         device id and name, peers, device key (wrapped by the Keystore), sync options
├── snapshots/          up to five copies of notes.db
├── certs/              the listener's TLS certificate
├── whisper-models/     downloaded Whisper models (a gigabyte or more each)
├── voice.log           the log
└── critical.log        failures shown to the user
```

The phone has one account, and this directory is it. There is no `accounts.db`.

**Android deletes this directory** when the application is removed or
replaced (TECHNICAL-DECISIONS 3.1a); that is how the data was lost on
2026-09-12.

### 7.2 Recordings

`VoiceRepository.defaultAudioFileDir`:

- With all-files access granted (Android 11 and later): the shared folder
  `/storage/emulated/0/Recordings/Voice/`. It survives the application being
  removed, and holds Recordings and nothing else (TECHNICAL-DECISIONS 3.1a).
- Without it: the application's external files directory `.../Android/data/com.dotancohen.voiceandroid/files/audio/`, which Android deletes with the application.
- A folder the user chose is kept in the setting `audiofile_directory_path`.

A file is found by the name in `audio_files.disk_name` (FILE-6, FILE-15). A
file this application records is named from its start time and the last eight
characters of its id, for example `2026_09_21_14_30_59-abcdefgh.ogg`; an
imported file keeps its own name. The bucket object is named by the content
hash (FILE-18).

### 7.3 Settings

| Where | Synced? | Examples |
|---|---|---|
| `SharedPreferences` file `voice_settings`, written by `UiPreferences`, `RecorderPreferences`, `TranscriptionPreferences`, `PlaybackPreferences` | Never | Interface size, recording format, microphone, Whisper model, playback speed, date format. A unit test checks that no two classes use the same key |
| `files/config.json` (through the core) | Never | Peers, device key, listener idle stop, and `sync.max_sync_file_size_mb`, which is now only the listener's size limit for the body of its JSON routes (FILE-23) |
| Table `synced_settings` (through the core) | Yes | Preferred transcription languages, `tag_color.<name>` |
| Table `file_storage_config` | Yes | The bucket's settings, and the account's upload limit `max_upload_mb` (FILE-23) |

---

## 8. The database

The database is identical on the phone and the desktop: the same Rust code
creates it. Only its location differs (`files/notes.db`).

### 8.1 Basic facts

- **One SQLite file**, `notes.db`. SQLite is a database stored in one
  ordinary file, with no server process.
- It is opened by `Database::new` in `database.rs`, which sets **WAL** journal
  mode, sets `busy_timeout` to 10 seconds, and then calls `create_schema`.
- **One schema, with a number.** In an empty file, `create_schema` makes every
  table in one transaction (`create_tables`, `create_version_tables`,
  `create_sequence_triggers`, `create_identity`, `create_system_tags`) and
  writes `SCHEMA_VERSION` (1) into `PRAGMA user_version`. A file that has tables
  and another number is not opened: "This database was written by another
  version of Voice (schema N; this version reads schema 1) and is not opened.
  Start with an empty data directory." There are no migrations: nothing
  converts a database written by an earlier build, and a database written
  before the number existed carries `user_version` 0 and is refused the same
  way. A new table or column goes into `create_tables`, and a synced column
  also into its table's list in `create_sequence_triggers`.
- **Ids** are **UUID7** values stored as 16-byte **BLOB**s. Kotlin receives them
  as 32 lowercase hexadecimal characters with no hyphens. In the version
  tables, `entity_id` is stored as that text instead.
- **Timestamps** are `INTEGER` seconds since 1970-01-01 UTC (Unix time). Every
  user-visible timestamp `x` has two more columns: `x_offset` (seconds east of
  UTC where it happened) and `x_zone` (the IANA name, such as
  `Asia/Jerusalem`). Kotlin receives the three together as the generated
  `Stamp(at, offset, zone)`; `util/Stamps.kt` draws it at its own offset, so a
  Note made at 15:20 in Jerusalem shows 15:20 anywhere. The core cannot read
  Android's timezone, which is why the application reports it
  (`VoiceRepository.reportTimeZone`). `file_locations.changed_at` is the one
  exception: milliseconds.
- **Nothing is deleted by an ordinary delete.** Deleting sets `deleted_at` (a
  **soft delete**): the Note waits in the trash with its Recordings. Only a
  **purge**, asked for from the trash, removes rows. The core then returns the
  ids of the Recordings that went, and the application deletes their files
  (but see section 13.2).
- **Foreign keys are enforced** on the connections the core opens: the core's
  SQLite is the one bundled with `rusqlite`, compiled with
  `SQLITE_DEFAULT_FOREIGN_KEYS=1`. A row from a peer whose parent row is not
  there yet is refused and tried again later (`sync_failures`). A database
  written by a program that leaves foreign keys off (Python's `sqlite3` module,
  the `sqlite3` tool) can still hold rows without a parent; the Issues screen
  lists them.

### 8.2 How the user's data relates

```
                       tags ──┐ parent_id (a Tag inside another Tag)
                        ▲     │
                        │ ◄───┘
                   note_tags             (link: one row per Note and Tag)
                        │
notes ──────────────────┘
  │
  └── note_attachments ──(attachment_id, attachment_type = 'audio_file')──► audio_files
                                                                               │
                                                                     transcriptions
                                                                     file_locations
```

- A Note has any number of Tags, through `note_tags`.
- A Note has any number of Attachments. `note_attachments` is a
  **polymorphic association**: `attachment_type` says which table
  `attachment_id` points into. Today the only type in use is `audio_file`.
- A Recording (`audio_files` row) has any number of Transcriptions, and one
  `file_locations` row per place that is known to hold or not hold its file.
- `notes.primary_attachment_id` names the Attachment that stands for the Note,
  and `audio_files.primary_transcription_id` the Transcription that stands for
  the Recording (the star in the interface). Empty means "the oldest one".

### 8.3 Tables of the user's data

Every table in this section also has `seq`, `sync_received_at`, and the
`_offset`/`_zone` pair for each of its timestamps.

**`notes`** — one row per Note.

| Column | Meaning |
|---|---|
| `id` | UUID7 |
| `content` | The text. A copy of the head of the versioned field `note.content` |
| `created_at`, `modified_at`, `deleted_at` | When it was created, last changed, moved to the trash |
| `primary_attachment_id` | Section 8.2 |
| `di_cache_note_pane_display` | Cache, JSON: `tags`, `conflicts`, `attachments`, `cached_at` |
| `di_cache_note_list_pane_display` | Cache, JSON: `date`, `marked`, `content_preview`, `duration_seconds`, `tags`, `cached_at`. Arrives in Kotlin as `Note.listDisplayCache` |

**`tags`** — `id`, `name`, `parent_id` (NULL at the top level), the three
timestamps.

**`note_tags`** — `note_id`, `tag_id` (together the **primary key**), the three
timestamps. Removing a Tag from a Note sets `deleted_at`.

**`note_attachments`** — `id`, `note_id`, `attachment_id`, `attachment_type`,
`device_id`, the three timestamps.

**`audio_files`** — one row per Recording.

| Column | Meaning |
|---|---|
| `filename` | The name given when it was recorded or imported (the recorder gives `Recording 2026-09-13 14-30-59.ogg`) |
| `disk_name` | The file's name in the audio directory, the same on every device. **The only way to find the file** |
| `imported_at`, `file_created_at` | When it entered Voice; when the recording was made |
| `duration_seconds` | Length, when known |
| `summary` | Versioned text |
| `device_id` | The device that created the row |
| `origin_device_id`, `origin_kind` | The installation that made the Recording, and how: `recorded` by this application's recorder or `imported` from a file that already existed. Written by that installation, synced, set once and never changed by a later row (FILE-25) |
| `content_sha256` | SHA-256 of the file's bytes; also the bucket object's name |
| `size_bytes` | The file's size in bytes. Synced, written together with the content hash, never changed once known (FILE-23) |
| `storage_provider`, `storage_key`, `storage_uploaded_at` | Set after an upload to the bucket; NULL means not uploaded |
| `storage_encrypted` | 1 when the bucket object is encrypted |
| `waveform_levels` | The levels a waveform is drawn from, kept by the first device that decoded the file, so the phone need not decode what the desktop already decoded (FILE-20) |
| `primary_transcription_id` | Section 8.2 |

**`transcriptions`** — one row per Transcription.

| Column | Meaning |
|---|---|
| `audio_file_id` | The Recording |
| `content` | The text. `Pending...` while running; starts with `Error:` when it failed (`Transcription.isFinished` in `data/Note.kt`) |
| `content_segments` | JSON: pieces of text with start and end times |
| `service` | `local_whisper` for the phone |
| `service_arguments` | JSON: model, language ... Used to skip Recordings already transcribed with the chosen model |
| `service_response` | JSON, including `performance`: clock time, processor time, peak memory, the phone |
| `state` | The five flags, space-separated; `!` in front means "not". Default: `original !verified !verbatim !cleaned !polished`. The column keeps the name `state` because renaming a synced column is expensive (TECHNICAL-DECISIONS 1.4) |
| `device_id` | The device that made it |

**`file_locations`** — where each copy of a Recording is (FILE-22). It has a
`seq` column and `sync_received_at`, but no `_offset`/`_zone` pair.

| Column | Meaning |
|---|---|
| `audio_id` | The Recording |
| `place` | A device of the account (its id), or `cloud` for the bucket. `audio_id` and `place` together are the primary key |
| `present` | 1 when that place holds the file, 0 when it does not |
| `changed_at` | When this was stated, in **milliseconds** |
| `changed_by` | The device that stated it |

A device states its own copy when it hashes a file (`storeContentHash`, so a
recording or an import states this phone's copy at once), when it receives,
fetches or downloads a file, and when a sync, a handshake it answers as a
listener, a snapshot restore, the Issues screen or "Where are the copies?"
compares its audio folder with what it has stated. An upload states the
bucket's row; a download that finds no object, or an object whose hash is not
the Recording's, states that the bucket does not hold it. A sender or a fetcher
states that the peer holds the file. The newest statement about a
place wins, then the larger device id, then presence, so every device keeps the
same rows whatever order they arrive in.

**System Tags**, with the same ids on every device:

| Tag | Id | Purpose |
|---|---|---|
| `_system` | `a1b2c3d4-0000-5000-8000-000000000001` | Parent of the hidden Tags |
| `_system/_marked` | `...0002` | The star on a Note |
| `_system/_nonsynced` | `...0003` | Parent of Tags for items that are not synced |
| `_system/_nonsynced/_too-big` | `...0004` | Created with the others. No Kotlin code puts it on a Note; a Recording over the account's upload limit is listed under Issues instead |

No screen shows these Tags. Every list of Tags goes through
`TagTree.withoutSystemTags` (`data/TagTree.kt`), which leaves out `_system` and
every Tag under it however deep, and the four fixed ids even when a list lacks
their parents (a Note's own Tags). A Tag of the user's whose name starts with
`_` is shown like any other.

### 8.4 The version history (`versions.rs`)

**The idea.** Nothing the user wrote may be lost, not even when the phone and
the desktop changed the same thing while offline (TECHNICAL-DECISIONS 1.1). So
every editable value is kept like a small Git repository: a chain of
immutable **versions**, and a **head** that is the current value. The columns
in `notes`, `tags` and so on are copies of the heads.

| Table | Purpose |
|---|---|
| `field_versions` | Every version ever written, never changed and never deleted: `entity_type`, `entity_id`, `field`, `content`, `parent_id`, `merge_parent_id`, `conflict_kind`, `context`, `device_id`, `device_name`, `created_at`, `published` |
| `field_heads` | One row per field: `head_id` |
| `field_conflicts` | One row per merge that needs the user: base, side A, side B, merge version, both devices, `resolved_at` |
| `field_deferred` | Fields whose row could not be written yet; tried again after each batch |
| `synced_settings` | Copies of the heads of `setting.value` |
| `devices` | Copies of the heads of each device card |

**Versioned fields** (`FIELD_REGISTRY`) and their merge kinds:

| Entity | Fields | Merge kind |
|---|---|---|
| `note` | `content` / `primary_attachment` / `deleted` | Text / Scalar / Deleted |
| `transcription` | `content` / `state` / `deleted` | Text / Flags / Deleted |
| `audio_file` | `summary` / `primary_transcription` / `deleted` | Text / Scalar / Deleted |
| `tag` | `name`, `parent` / `deleted` | Scalar / Deleted |
| `note_tag` | `active` (entity id `<note id>:<tag id>`) | Membership |
| `note_attachment` | `active` | Membership |
| `setting` | `value` | Scalar |
| `device` | `name`, `certificate_fingerprint`, `addresses`, `listens`, `key_hash`, `application` / `revoked` | Scalar / Membership |

| Merge kind | When both devices changed it differently |
|---|---|
| Text | A **three-way merge**: edits to different lines combine; edits to the same lines keep both between `<<<<<<< VERSION A` and `>>>>>>> VERSION B`, and a conflict is recorded |
| Scalar | The later value is shown; a conflict is recorded |
| Flags | Merged flag by flag |
| Membership | The link stays attached; a conflict is recorded |
| Deleted | The item stays alive; a conflict is recorded |

**An example.** The phone and the desktop edit one Note while offline. After a
sync each holds two **leaves** for `note.content`. Each merges them and gets
the identical merge version, because merge ids are hashes of their inputs. The
Note shows both texts between markers and a conflict banner. Editing the text
or pressing "Accept merge" writes a version that descends from the merge, and
after the next sync the conflict is resolved on every device. The phone reads
history and conflicts through `getNoteHistory`, `getNoteConflicts` and
`acceptNoteConflicts` in `VoiceRepository`.

**Never change an editable value with plain SQL.** Every write goes through the
core's version functions (SYNC_SPECIFICATION DM-1).

Values written by the machine (`filename`, `duration_seconds`,
`service_response` ...) are not versioned; when a row arrives from a peer they
are merged column by column, and the newer `modified_at` wins a column (DM-4).
`file_locations` rows are not versioned either; they follow the order given in
section 8.3.

### 8.5 Tables for sync

| Table | Purpose |
|---|---|
| `sync_sequence` | One counter for the whole database |
| `seq` column | On `field_versions`, `notes`, `tags`, `note_tags`, `note_attachments`, `audio_files`, `transcriptions`, `file_storage_config`, `purges`, `file_locations`. **Triggers** set it when a row is inserted or a synced column really changed |
| `sync_meta` | `database_id` and `account_id` |
| `sync_peers` | One row per peer: id, name, URL, certificate fingerprint, cursors (`last_received_cursor`, `last_sent_seq`), `last_sync_at`, `last_operation`, the peer's database and account ids |
| `sync_failures` | Changes from a peer that could not be applied, tried again at the next batch |
| `purges` | Everything ever purged, kept for ever so no peer can bring it back |
| `file_storage_config` | One row: the bucket settings, including the account's upload limit `max_upload_mb` |
| `file_locations` | Where each copy of a Recording is (section 8.3) |

A sync sends every row and version whose `seq` is above what the peer already
received, for the entity types `note`, `tag`, `note_tag`, `note_attachment`,
`audio_file`, `transcription`, `file_storage_config`, `field_version`, `purge`
and `file_location` (PROTO-1).

### 8.6 Tables that never leave the phone

| Table | Purpose |
|---|---|
| `pairing_offers` | The hash of a pairing code while valid |
| `upload_parts` | Journal of an upload in parts |
| `pending_file_renames` | A Recording whose file must still be renamed |
| `purged_objects` | Bucket objects of purged Recordings, waiting for the purge tag at the next upload run. An object that a Recording which stays also uses is not listed |
| `file_holds` | A promise this phone gave a peer to keep its copy of a Recording while that peer removes its own, until `until_ms` (FILE-26) |
| `file_removals` | A removal of this phone's copy that is under way; while it is, a peer's request to keep a copy is refused (FILE-26) |

### 8.7 Reading the phone's database safely

1. Take the backup: `tools/voice-phone-backup`, as a command of its own
   (section 1). It needs a debug build (it uses `run-as`) and writes to
   `~/voice-phone-backups/` by default.
2. Open the copied `notes.db` read-only on the computer:
   `sqlite3 -readonly <copy>/notes.db`. Copy `notes.db-wal` with it if present.

```sql
-- The 20 newest Notes that are not in the trash
SELECT lower(hex(id)), datetime(created_at, 'unixepoch'), substr(content, 1, 60)
FROM notes WHERE deleted_at IS NULL ORDER BY created_at DESC LIMIT 20;

-- Recordings, with their names on disk, their sizes and whether they are in the bucket
SELECT lower(hex(id)), disk_name, size_bytes, duration_seconds, storage_key IS NOT NULL AS in_bucket
FROM audio_files WHERE deleted_at IS NULL ORDER BY imported_at DESC LIMIT 20;

-- Where each copy of each Recording is (FILE-22)
SELECT lower(hex(audio_id)), place, present, datetime(changed_at / 1000, 'unixepoch')
FROM file_locations ORDER BY audio_id, place;

-- Open conflicts
SELECT entity_type, entity_id, field, kind, device_a_name, device_b_name
FROM field_conflicts WHERE resolved_at IS NULL;
```

Never copy a database back onto the phone by hand, and never write to it with SQL.

---

## 9. How the parts work together

### 9.1 A new voice Note

1. `NotesScreen` calls `onNewRecording`. `VoiceApp` calls
   `VoiceRepository.createNote("")` and opens `note/<id>?record=true`.
2. `NoteDetailScreen` starts `VoiceRecorder`; `RecordingService` keeps the
   process alive and shows the elapsed time.
3. Saving calls `VoiceRecorder.save()`:
   - `importAudioFileIntoNote(note, filename, startedAt, duration)` creates
     the `audio_files` and `note_attachments` rows; the core decides `disk_name`;
   - `copyAudioFileToStorage(...)` copies the temporary file to
     `<audio directory>/<disk_name>`, never overwriting, then `storeContentHash`,
     which also records the size and states that this phone holds the file
     (FILE-22);
   - the temporary file is deleted.

### 9.2 Importing existing recordings

`ImportAudioViewModel` reads the folder, keeps the files whose extension is in
the core's list `audioFileFormats()` (FILE-21), calls `importAudioFile` for each
file (one new Note each) and `copyAudioFileToStorage`.

### 9.3 Transcribing on the phone

1. `OnDeviceTranscriber.enqueue(...)` checks the ten-minute limit and adds a
   job to `JobQueue`; `TranscriptionService` starts. Android refuses to start a
   foreground service for an application that is not on the screen, so a
   request from the background waits until the application is opened.
2. For the job: `createTranscription(audioFileId, "Pending... (...)", "local_whisper", arguments)`.
3. `AudioToWav` converts the Recording to 16 kHz mono WAV.
4. Whisper runs through `uniffi.voice_transcription` and
   `libvoice_transcription_android.so`.
5. The Transcription receives the text, the segments and `service_response`
   with the cost of the run.

### 9.4 Sync, and moving files

The words have exact meanings (TECHNICAL-DECISIONS 4.5), in code and in the
interface:

| Word | Meaning |
|---|---|
| **Sync** | Exchange database changes with a peer, both directions. No file moves |
| **Upload** / **Download** | Copy Recording files to / from the S3 bucket |
| **Send** / **Fetch** | Copy Recording files to / from another Voice installation |
| **Deliver** | Sync, then send |
| **Exchange** | Sync, then send and fetch |
| **Listen** | Accept connections from peers |
| **Host** | Serve an account that is not this device's own |
| **Pair** | Give a fresh device the account's id, a key of its own and one peer |

Every one runs only when the user presses a button.

**Path of an operation on the phone:** `SyncSettingsScreen` →
`SettingsViewModel` → `OperationService` (foreground service, notification
with Cancel) → `VoiceRepository.operate(operation, peerId, onProgress)` →
`VoiceClient.operate` in `android.rs` → `SyncClient` in `sync_client.rs`.
Progress comes back through the `OperationProgress` callback interface and is
published by `OperationState`.

**A sync, step by step** (SYNC_SPECIFICATION FLOW-1):

1. `POST /sync/handshake` to the peer: identities, protocol version `2.0`, account, key.
2. If the peer's `database_id` changed, both cursors restart from zero.
3. **Pull** `GET /sync/changes?cursor=N` page by page (about 4 MB at most),
   applying each page in one transaction and saving the cursor after it.
4. **Push** this phone's changes since `last_sent_seq` through `POST /sync/apply`.
5. Record the time and operation in `sync_peers`.

An interrupted sync continues from the last saved cursor. Applying a page twice
changes nothing (**idempotent**).

**The phone as a listener:** the switch on the sync screen starts
`SyncListenerService`, which calls `startListener(port)`; the Rust server in
`sync_server.rs` then serves peers over HTTPS with a self-signed certificate
that peers remember at first connection (**TOFU**).

**Where the phone can be reached** (LISTEN-4): `SettingsViewModel` reads
`VoiceRepository.listenAddresses(port)` (`VoiceClient.listen_addresses`), which
returns `detected`, `shown`, `urls` and `sentence`. The candidates are the
private IPv4 addresses of interfaces that can carry a local network (mobile
data, tunnels, VPNs and virtual networks are left out); the source address of
the phone's route is the one found, shown alone and tried first. Sync Settings
shows `AddressText.words(...)` after "Address", and "Show my code" puts every
URL of `urls` into the code; the reading device tries each in turn. When a peer
does not answer at its remembered address, the core tries each address on the
peer's device card, with the peer's pinned certificate, and remembers the one
that answers, before `OperationService` searches the network (`PeerDiscovery`).

**Files between installations** (FILE-12, FILE-13): `POST /sync/audio/missing`
finds what the receiver lacks; `GET` and `POST /sync/audio/:id/file` stream
bytes, resume, and verify the SHA-256. The receiver and the fetcher record the
new copy in `file_locations`.

**The bucket:** objects are named by content hash; files over 8 MiB are
uploaded in parts; objects may be encrypted with the account's recording key
(`crypto.rs`). An upload leaves a file larger than the account's upload limit
where it is and counts it as `too_large` (FILE-23). The phone downloads a
Recording from the bucket when the user asks for it.

**Network timeouts** (FILE-14, in the core, so the phone and the desktop
behave the same):

- Connecting: three seconds to this machine or a private address, ten seconds
  elsewhere.
- Reading, for a sync's requests and for a file: the request ends when a read
  moves nothing for thirty seconds. A file has no overall time limit, so a slow
  link that still moves bytes is never cut off.
- An upload (a send to a peer, a part or a small file to the bucket) has no
  read timeout. It ends when no byte of its body has moved for thirty seconds,
  or one minute after the last byte with no answer (`transfer::stall_of_upload`).
- A transfer (send, fetch, bucket upload, bucket download) is tried three
  times: the second try straight after the first, the third a minute after the
  second. After three files failed every try the operation stops and names the
  files it did not attempt. A new try asks the peer how many bytes it holds and
  continues from there. A refusal
  (4xx) is not tried again. The bytes that arrived stay in the part file on both
  sides.

### 9.5 Pairing

One device shows a QR code or a setup text `voice://pair?...` with a
single-use token valid for ten minutes. The phone reads it with the camera
(`QrReader`, CameraX and ZXing) or receives it as a tapped link (the
`voice://pair` intent filter in the manifest, passed on by `PairingRequests`).
In `QrReader` the camera is bound by a `DisposableEffect` keyed on the camera
provider and the count of presses of **Another camera**, so a press re-binds
through Compose; `CameraChoice.at(count)` picks the camera, and its own
`cameraSelector` is what is bound. The `PreviewView` runs in `COMPATIBLE` mode
(a TextureView), because a SurfaceView draws below its window and the reader
is a dialog window, where that picture is black. The reader sits on an opaque
`Surface`, so Sync Settings does not show through.
`VoiceRepository.pairWith(setupText)` gives the phone the account id, a device
key of its own and one peer.

### 9.6 Snapshots

A copy of `notes.db` goes into `files/snapshots/` before anything from a peer
is applied, before moving to another account and before a restore; five are
kept. Advanced settings list and restore them (`SnapshotsViewModel`). After a
restore, `VoiceClient.restore_snapshot` compares the audio folder with the
restored rows, which say where the copies were when the snapshot was taken
(FILE-22).

### 9.7 Where the copies are, removing this phone's copy, the upload limit, Issues

All four were added on 2026-09-14 and call the core functions of FILE-22,
FILE-23, FILE-25, FILE-26 and ISSUE-1 in `android.rs`, which use the phone's own audio folder
and device id.

**"Where are the copies?"** (the ⋮ menu of a file in the Note's player):
`AudioPlayerWidget` → `NoteDetailViewModel.showLocations` →
`VoiceRepository.checkFilesHere()` (compares the audio folder with what this
device has stated: a file that is there is stated present, a file that is gone
is stated absent) → `VoiceRepository.fileLocations(audioId)` →
`IssuesText.locationLines` → the dialog "Where the copies are". A place is
shown as "the bucket", "this device", the device's name, or the first 12
characters of its id. `VoiceRepository.madeHereButMissing(audioId)`
(`made_here_but_missing`) returns `recorded` or `imported` when this phone made
the Recording, no place is known to hold it, and its file is not in the audio
folder; the dialog then adds "Recorded on this device, but its file was not
found in the audio folder after the recording" or the same for an import
(FILE-25).

**"Remove from this phone"**: a confirmation dialog →
`NoteDetailViewModel.removeLocalCopy` → `VoiceRepository.removeLocalCopy` →
`VoiceClient.remove_local_copy` → `SyncClient::remove_local_copy` in
`sync_client.rs` (FILE-26). The file goes only when another place confirms at
that moment that it holds the file: the bucket, asked directly (the object
exists and does not carry the `voice-purged` tag), or a device stated to hold
it, which answers `POST /sync/audio/:audio_id/keep` with `holds`, `until_ms` and
`reason`, and promises to keep its own copy for ten minutes (`HOLD_MS`). Then
the file is deleted and this phone states it absent. The core refuses when the
file is not on this phone, while this phone has promised a peer to keep that
copy (`file_holds`), and when no place confirms; the refusal names what each
place answered. While a removal is under way (`file_removals`) the phone
refuses a peer's keep request, so two devices that count on each other never
both remove the file. The view model reloads the Note on success and shows
"Not removed: <reason>" on a refusal. The Recording row stays, and the file can
be fetched or downloaded again.

**The upload limit** (the "Upload limit" card on Sync Settings):
`SettingsViewModel.saveMaxUploadMb` → `VoiceRepository.setMaxUploadMb` → the
core writes `max_upload_mb` into the synced storage configuration, so every
device of the account uses the same limit. It is 100 MB until set. The core
refuses a value below 1 MB, and refuses to set it before a bucket is
configured. The per-device "largest file to sync" field is gone.

**Issues** (Settings → Issues): `IssuesScreen` → `IssuesViewModel.load` →
`VoiceRepository.deviceNames()`, `getDeviceId()` and `issues()` →
`IssuesText.sections`. The core compares the audio folder first, then lists,
from the database as it is: Recordings not in the bucket with the reason for
each (no bucket, over the upload limit, waiting for the named devices that
hold the file, or no copy known, with the line of "Where the copies are" added
when this phone recorded or imported the Recording and its file is gone); Transcriptions whose Recording row is not
there; Attachments whose Note or Recording row is not there; Recordings that no
Note holds; Tags whose names contain whitespace. Nothing is stored: an issue
that was dealt with is gone the next time the list is read. The lines are plain
text with no action.

---

## 10. Building and testing

### 10.1 The whole build: `build-app.sh`

The script stops at the first failure (`set -e`) and runs, in order:

1. `cargo build --release --features uniffi` in `submodules/voicecore`, for the computer.
2. `uniffi-bindgen generate --library $CARGO_TARGET/release/libvoicecore.so --language kotlin`,
   then copies `voicecore.kt` into `app/src/main/java/uniffi/voicecore/`.
3. `cargo ndk -t arm64-v8a -o <absolute path>/app/src/main/jniLibs build --release --features uniffi`,
   the native library for the phone.
4. `./gradlew assembleDebug` with `JAVA_HOME=/usr/lib/jvm/java-21-openjdk-amd64`.
5. `./gradlew testDebugUnitTest`.

It installs nothing; the install lines are commented out, and must stay behind
section 1's backup.

The APK is at `app/build/outputs/apk/debug/app-debug.apk`. It holds native
libraries for `arm64-v8a` only (`abiFilters`): the core, the Whisper library,
and FFmpeg's and JNA's libraries for that ABI. Whisper's library exists for
`arm64-v8a` only, so an APK for another ABI (`-PtargetAbi=...`) would have no
on-device transcription.

`app/build.gradle.kts` also defines the tasks `buildRust` and
`generateKotlinBindings`. No build runs them (`preBuild` does not depend on
them), and they read the library from `submodules/voicecore/target/...`, where
no Rust build writes any more (section 13). Use `build-app.sh` or the steps in
`CLAUDE.md` instead.

### 10.2 Pitfalls of the build

- **One build directory for the whole family** (`VoiceFamily/.cargo-target`,
  TECHNICAL-DECISIONS 7.4). The desktop's `maturin develop` rebuilds
  `.cargo-target/release/libvoicecore.so` **without** the `uniffi` feature.
  After that, `uniffi-bindgen` finds no interface, generates nothing and still
  exits with success. Generate the bindings before running maturin, or force
  the build with `touch src/lib.rs && cargo build --release --features uniffi`,
  and **check that the new function is in `voicecore.kt`** before continuing.
- **`cargo ndk -o` needs an absolute path.** A relative path is resolved from
  the Cargo workspace, and the library lands in the wrong place.
- **JDK 21, not Android Studio's bundled JDK 25**: Gradle fails at once with
  the bare message `25.0.3`.
- **Bindings and native library together, always.** Otherwise
  `UnsatisfiedLinkError` on the phone.
- **Whisper's native library** is built in VoiceTranscription; see its
  `README.md`, "Build Android Bindings". It exists for arm64 only.
- **A build that failed is reported as failed.** Read Gradle's
  `BUILD SUCCESSFUL` or `BUILD FAILED` line; an exit status hidden behind a pipe
  is not evidence.

### 10.3 Tests

```bash
JAVA_HOME=/usr/lib/jvm/java-21-openjdk-amd64 ./gradlew testDebugUnitTest
```

The report is `app/build/reports/tests/testDebugUnitTest/index.html`.

| Lane | Directory | Runs on | Use it for |
|---|---|---|---|
| JVM | `app/src/test/` (501 tests in 49 files) | The computer; **Robolectric** supplies Android's classes; the core is built for the computer | Logic; Compose screens (that pressing a control has the effect it appears to have); the core through the phone's own bindings, including storage against a local S3 server |
| Instrumented | `app/src/androidTest/` (6 tests in `TagTreeItemTest`) | A device, as the `.uitest` application | What needs the phone itself: the phone's native library, a microphone, the real Android framework on a device |

**The core in the JVM lane.** Every JVM test task first runs two Gradle tasks
defined in `app/build.gradle.kts`:

- **`hostCoreForTests`** runs `cargo build --release --features uniffi` in
  `submodules/voicecore` and copies `VoiceFamily/.cargo-target/release/libvoicecore.so`
  at once to `app/build/host-core/libvoicecore.so`. The copy is needed because
  the desktop's Python module has the same file name in the shared directory.
  The tests receive the copy's path in the system property
  `uniffi.component.voicecore.libraryOverride`, and JNA's desktop jar loads it.
- **`s3ServerForTests`** creates a Python environment in
  `app/build/s3-test-venv` and installs `moto[server]==5.2.3` into it, when
  `bin/moto_server` is not there yet. The first run needs the network. The
  tests receive the path in the system property `voice.test.motoServer`.

So the JVM lane needs `cargo` and `python3` on the computer. Neither task name
contains `connected` or `install`; neither touches a phone.

Two test classes call the core, by constructing `uniffi.voicecore.VoiceClient`:

- `data/CoreLocationsTest.kt`: where the copies are, this phone's copy stated
  when the file is hashed and not removed while no other place confirms that it
  holds the file, the upload limit, and Issues (FILE-22, FILE-23, FILE-26,
  ISSUE-1).
- `data/CoreStorageTest.kt`: upload and download through the phone's own client
  against moto's S3, directly and through `FaultyLink` (FILE-14, FILE-19,
  FILE-22): a refused connection, a part cut, a frozen download, a bucket that
  never answers; and `a copy the bucket holds is removed from this phone once
  the bucket is asked` (FILE-26).

The helpers, in `app/src/test/java/com/dotancohen/voiceandroid/network/`:

- `LocalS3.kt` starts moto's S3 server on this computer with authentication
  on: it creates a user and a key, every request must be signed with that key,
  and a wrong secret is refused. It gives each test a bucket of its own and the
  storage configuration JSON that reaches it. It is the same server as the
  desktop's `tests/local_s3.py`.
- `FaultyLink.kt` is a TCP proxy on this computer between the core's client and
  a server, which fails on command: `refuse`, `cutAfter`, `freezeAfter`,
  `stall`, `throttle`, and `passThrough` to work again. A fault applies to
  connections accepted after it is set. It is the same link as the desktop's
  `tests/faulty_network.py`.

Notes on the JVM lane:

- `isReturnDefaultValues = true`: an Android class that is only a stub answers
  with a default value instead of throwing. That is why the tests use the real
  `org.json` library; Android's stub would silently parse nothing.
- `app/src/test/resources/robolectric.properties` sets SDK 35, native graphics,
  and a plain `Application` instead of `VoiceApplication`, which would try to
  load a library built for the phone's processor.
- A Compose test finds controls through the semantics tree
  (`onNodeWithContentDescription` ...), so a control worth testing needs a
  `contentDescription`, which a screen reader needs anyway.
- `app/src/test/resources/transcription_flags_contract.json` must be
  byte-identical to `Voice/tests/fixtures/transcription_flags_contract.json`.

Instrumented tests are **not** run on the owner's phone (section 1). On an
emulator, it must run an `arm64-v8a` system image, or an image that translates
ARM, because the APK holds no other native libraries.

The Rust core has its own tests: `cd submodules/voicecore && cargo test`.

---

## 11. Rules to follow when changing the code

1. **The owner's phones are touched only as section 1 says.**
2. **A failing test is never made to pass by changing the test, the fixture or
   the data.** First decide, in writing, whether the code or the test's premise
   is wrong (TECHNICAL-DECISIONS 6.5).
3. **Nothing the user wrote is overwritten.** No "last write wins"; deletes are
   soft; disagreements become conflicts (1.1).
4. **Logic in view models and below, not in composables** (section 3).
5. **No floating action buttons**; actions go in the top bar (5.3).
6. **A field takes the keyboard only when it is the purpose of the screen** (5.4).
7. **Features under trial are behind `BuildConfig.DEV_FEATURES`**, true in
   debug and false in release (5.5).
8. **Tuned numbers go in `util/MagicNumbers.kt`** (3.7).
9. **Memory never grows with the user's data**: never a `List<Short>` of audio
   samples; stream or bound (3.1). An eight-hour Recording once killed the
   application.
10. **One decoder at a time** for waveforms (3.6).
11. **Right-to-left first**: test text contains Hebrew (6.3); a mixed symbol
    and digit label may need `LayoutDirection.Ltr` inside it.
12. **No ambiguous words** in names or text ("handle", "process", "manage",
    "fill", "update" as a vague verb); use the sync vocabulary of 9.4 exactly;
    entity nouns capitalised where the user reads them: Note, Tag, Recording,
    Transcription, Attachment (4.2, 4.4).
13. **A new user action is also added to `AdbCommandReceiver` and `tools/voice-adb`.**
14. **Core changes go to both applications**: the desktop binding too, and all
    three core checkouts at the same commit (7.2).
15. **A change the user can see goes into `USER_MANUAL.md` in the same change.**

---

## 12. Where to look first

| Question | Start at |
|---|---|
| Which screen is this? | Routes in `ui/VoiceApp.kt` |
| Where does this screen get its data? | Its view model, then the `VoiceRepository` method, then `android.rs` |
| What does the core function really read, write and return? | `submodules/voicecore/src/database.rs` or the module named in `android.rs` (`issues.rs`, `file_storage.rs`, `transfer.rs`, `sync_client.rs` ...) |
| Why did the recording not save? | `files/critical.log`, `files/voice.log`, `VoiceRecorder.save()` |
| Why did a sync fail? | The result's request id, the sync screen's connection check, `sync_failures` |
| Is this Recording anywhere else? | "Where are the copies?" in the Note, the table `file_locations`, Settings → Issues |
| Why is this Recording not in the bucket? | Settings → Issues, which gives the reason; `issues.rs` |
| What does this rule id mean? | `VoiceFamily/SYNC_SPECIFICATION.md` or `VoiceFamily/PLAN-ACCOUNTS-PAIRING-SYNC.md` |

---

## 13. Where older documents and files disagree with the code

Checked on 2026-09-14, at 03:00. This guide describes the code; the texts and
files below were not changed by it.

### 13.1 Documents, comments and files

`README.md`, `DEVELOPMENT.md` and `USER_MANUAL.md` were rewritten from the code
in commit `e86169a` (2026-09-14, 02:57). The disagreements this guide listed
on 2026-09-13 are gone from them: the four ABIs and JDK 17 in the build steps,
`connectedDebugAndroidTest` as a routine command, "a Voice server you run",
the Server URL fields, the weekday in the manual's examples. The Recorder file
names in `Voice/CLAUDE.md` (its Android notes are now in `VoiceAndroid/CLAUDE.md`)
and the file names in `SYNC_SPECIFICATION.md` FILE-6 were corrected, and
`app/src/main/jniLibs/` now holds `arm64-v8a` only. The three documents were
checked for those items and for the features of 2026-09-14, not read line by
line against every screen.

These disagreements remain:

| Where | What it says or contains | What is actually the case |
|---|---|---|
| `app/build.gradle.kts`, the comment above `abiFilters` | "The phones that use Voice are 64-bit ARM (the Galaxy S24 Ultra and the Galaxy A12)" | The A12's `ro.product.cpu.abilist` has not been checked (section 1; `VoiceAndroid/CLAUDE.md` says the same) |
| `app/build.gradle.kts`, tasks `buildRust` and `generateKotlinBindings` | `buildRust` copies the library from `submodules/voicecore/target/aarch64-linux-android/release/`, and copies nothing when the file is not there; `generateKotlinBindings` reads `target/aarch64-linux-android/release/libvoicecore.so` | Rust builds go to `VoiceFamily/.cargo-target/`, so neither task finds the library. No build runs them (`preBuild` does not depend on them). `build-app.sh` is the build |
| `app/src/androidTest/.../TagTreeItemTest.kt`, header comment | "To run: ./gradlew connectedAndroidTest --tests "*.TagTreeItemTest"" | A `connected` task installs an APK on every connected device, so it is covered by section 1 and needs the owner's request and a verified backup. The comment gives no warning |
| `app/src/main/AndroidManifest.xml`, the comment on `RecordingService` | The recording "is stopped from its notification or by returning to the app" | The recording notification has no action (`RecordingService.kt` adds none). Its tap opens a route that does not exist (13.2, item 1) |
| `tools/voice-phone-backup`, header comment | Offers `tools/voice-phone-backup && <the command that might destroy data>` | `VoiceFamily/CLAUDE.md`, `VoiceAndroid/CLAUDE.md` and `DEVELOPMENT.md` require the backup to run as a command of its own, unpiped, and its `Backup verified.` line to be read before the next command |
| `submodules/jniLibs/`, `submodules/kotlin-bindings/` (in git), `submodules/uniffi-generated/` (empty) | A `libvoicecore.so` and bindings from December 2025 and January 2026 | No build step reads them; Gradle reads only `app/src/main/jniLibs/` and `app/src/main/java/uniffi/` |

### 13.2 Suspected defects in the code

Found on 2026-09-14 by **reading** the code. None of them was run on a phone or
in a test. Confirm each one, with a test that fails, before changing the code.

1. **Tapping the recording notification.** `RecordingService.kt` line 82 opens
   the route `"recording"`, which is not in the navigation graph
   (`ui/VoiceApp.kt`). Compose's `navigate` throws `IllegalArgumentException`
   for an unknown route, so the tap probably crashes the application.
2. **Two notifications share one id.** `SyncListenerService` and
   `PlaybackService` both use notification id `4823`. While the phone listens
   and plays, one notification probably replaces the other.
3. **A purge probably deletes no file.** `VoiceRepository.purgeNote` deletes
   the files whose name, without its extension, equals the Recording's id. Files
   are named by `disk_name`, which is not the id (section 7.2), so the files
   probably stay in the audio folder.
4. **Files over the upload limit are not reported by Upload.** The core counts
   them (`too_large`), but the Kotlin `UploadResult` has no field for them, so
   an upload whose only file is too large reads "nothing to upload". The Issues
   screen lists the file.
5. **A refused Device ID is not shown.** `SettingsViewModel.saveSettings`
   calls `setDeviceId` and `setDeviceName` with `onSuccess` only; a refusal by
   the core is dropped without a message.
6. **A hidden pairing code stays valid.** "Hide my code" and the 60-second
   countdown do not withdraw the code: no view model calls
   `VoiceRepository.withdrawCode`. The token stays valid for its ten minutes.
7. **Joining an account is refused after any Note.** `pairing.rs` counts
   `SELECT COUNT(*) FROM notes`, which includes deleted and empty Notes, so
   pressing "Start on my own" once is enough to refuse joining another account
   later.
8. **The new actions have no ADB action.** "Where are the copies?", "Remove
   from this phone", the upload limit and Issues are not in
   `AdbCommandReceiver` or `tools/voice-adb` (rule 13 of section 11).
9. **A wrong weekday.** `AdvancedSettingsScreen.kt` line 92 says "Wednesday 3
   September 2026"; 3 September 2026 is a Thursday, and the presets in
   `TimeFormat.kt` say Thursday.
10. **Unreachable code.** `ui/screens/FilterScreen.kt` is not in the navigation
    graph.

---

## 14. Glossary

**ABI** — "Application binary interface": here, the processor family a native library is compiled for. Phones are almost all `arm64-v8a`; emulators are often `x86_64`. A phone lists the ABIs it runs in the system property `ro.product.cpu.abilist`.

**Activity** — An Android screen container. This application has one, `MainActivity`; the screens inside it are composables.

**ADB** — "Android Debug Bridge": the command-line tool that talks to a phone over USB or Wi-Fi.

**BLOB** — An SQLite column type that stores raw bytes. Ids are 16-byte BLOBs.

**Broadcast receiver** — An Android component that receives intents sent to it, here from ADB.

**Cache (display cache)** — A value calculated from other data and stored so it need not be calculated again. It can always be rebuilt and is never synced.

**Composable** — A Kotlin function annotated `@Composable` that describes part of the screen. Compose calls it again (**recomposition**) when the state it reads changes.

**Coroutine** — Kotlin's lightweight unit of concurrent work. `withContext(Dispatchers.IO)` moves work off the main thread so the screen stays responsive.

**Core library** — Code shared by several applications. Here: VoiceCore, in Rust.

**Denormalised copy** — The same value stored in a second place for faster reading. `notes.content` is a copy of the head in `field_versions`.

**Device card** — The synced record of one device of an account: name, addresses, certificate fingerprint, hash of its key, revoked or not.

**File location** — A row of `file_locations`: a statement that one place (a device or the bucket) holds, or does not hold, the file of one Recording, and when that was stated.

**Foreground service** — Android work that continues when the application is not on the screen, and must show a notification. Recording, playback, transcription, listening and sync operations each run in one.

**Head** — The version of a field that is its current value.

**Idempotent** — Running it twice has the same result as running it once.

**Intent** — An Android message asking a component to act; also how a `voice://pair` link opens the application.

**Jetpack Compose** — Android's toolkit for drawing screens from Kotlin functions.

**JNA** — "Java Native Access": a library that lets JVM code call functions in a native `.so` library. The UniFFI-generated Kotlin uses it.

**Keystore** — Android's store for encryption keys, often in hardware; a key in it cannot be copied out.

**Leaf** — A version that no other version was written on top of. Two leaves for one field means two devices changed it independently.

**moto** — A Python program that imitates Amazon S3 on the computer. The storage tests of both applications upload to it and download from it.

**MVVM** — Model, view, view model: screens (views) show state held by view models, which get data from a model layer (here the repository and the core).

**Peer** — Another Voice installation of the same account.

**Polymorphic association** — A link whose target table is named in a column (`attachment_type`).

**Primary key** — The column or columns that identify a row uniquely.

**Purge** — Removing an item for good, with its history, on every device. Only possible from the trash.

**Repository** — A class that is the single way the rest of the application reaches data. Here `VoiceRepository`.

**Robolectric** — A library that runs Android framework classes on an ordinary JVM, so screens can be tested without a phone.

**Schema version** — The number a database file carries in `PRAGMA user_version`. The core opens an empty file or a file of its own `SCHEMA_VERSION`, and refuses any other; there are no migrations.

**Singleton** — A class of which the process has exactly one instance.

**Soft delete** — Marking a row as deleted (`deleted_at`) instead of removing it.

**SQLite** — A database engine that stores a whole database in one file and runs inside the application.

**StateFlow** — A Kotlin stream that always holds a current value; a composable reading it with `collectAsState()` is redrawn when the value changes.

**Submodule** — A git repository placed inside another at a fixed commit.

**Three-way merge (diff3)** — Combining two edited versions of a text by comparing each with the version they both started from.

**TOFU** — "Trust on first use": a certificate is accepted the first time and remembered; a different one later is refused.

**Transaction** — A group of database writes that either all take effect or none takes effect.

**Trigger** — SQL that SQLite runs by itself when a row is inserted or changed. Here triggers set `seq`.

**UniFFI** — A Mozilla tool that generates Kotlin (and other languages') bindings for a Rust library from annotations such as `#[uniffi::export]`.

**UUID7** — A 128-bit id whose first part is its creation time, so ids sort by time; the last characters are random.

**Version** — One immutable value of a field, with a pointer to the version it replaced.

**View model** — A class that holds a screen's state and survives the screen being redrawn or rotated.

**WAL (write-ahead log)** — An SQLite journal mode: changes go to `notes.db-wal` first and into `notes.db` later.
