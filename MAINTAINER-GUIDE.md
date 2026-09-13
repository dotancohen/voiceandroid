# Voice for Android — a maintainer's guide

A map of the Android application `VoiceAndroid/`: what each part of the code is
for, how the parts call each other, and what is in the database. It is written
for a developer who is new to the project. A term in **bold** at its first use
is defined in the [glossary](#14-glossary) at the end.

Written on 2026-09-13 from the code itself, on branch `accounts-pairing-sync`
(VoiceAndroid commit `9f1acd8` with uncommitted changes, core checkout
`987f769`). Where an older document says something different from the code,
the code was taken as the truth, and the disagreements are listed in
[section 13](#13-where-older-documents-and-files-disagree-with-the-code).

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

Therefore:

- **Never** run `adb install`, `adb uninstall`, `pm clear`, or any Gradle task
  whose name contains `install` or `connected`, on the owner's phone, unless
  the owner asked for that exact action and a backup exists.
- **Make the backup first** with `tools/voice-phone-backup`, and check what it
  contains. It copies the database, the settings and the Recordings, and exits
  with an error when the copy does not verify.
- Instrumented tests install as a separate application,
  `com.dotancohen.voiceandroid.uitest` (`testBuildType = "uitest"` in
  `app/build.gradle.kts`), so they cannot reach the real application's data.
  Do not remove this.

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
`Voice/submodules/voicecore` and the standalone `VoiceCore/`. They are git
**submodules** and must point at the same commit (`TECHNICAL-DECISIONS.md` 7.2).

### Where the rules are written

| Document | What it holds |
|---|---|
| `VoiceFamily/TECHNICAL-DECISIONS.md` | Decisions shared by all projects, numbered (for example "3.1a") |
| `Voice/SYNC_SPECIFICATION.md` | Numbered rules of the data model and the sync protocol. An id such as `FILE-15`, `AUTH-9` or `PURGE-5` in a comment refers to a rule here |
| `Voice/PLAN-ACCOUNTS-PAIRING-SYNC.md` | The plan for accounts, pairing and file transfer, in 16 stages. "Stage 9" in a comment refers to a stage here |
| `Voice/CLAUDE.md` | The most detailed notes on the Android build and on several Android features |
| `VoiceAndroid/DEVELOPMENT.md` | Building, notes on selected parts, the two lanes of tests |
| `VoiceAndroid/USER_MANUAL.md` | How the application is used, screen by screen |

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
   `libvoicecore.so`, a native library for the phone's processor (**ABI**
   `arm64-v8a`). It is placed in `app/src/main/jniLibs/arm64-v8a/`, and Gradle
   packs it into the APK.
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

### Keep screens thin

The owner intends to replace Jetpack Compose with another toolkit. Logic
belongs in view models, `data/`, `audio/`, `transcription/` and `util/`; a
screen should only draw state and forward the user's actions.

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
│   ├── build.gradle.kts            the application's build: SDK levels, build types, dependencies
│   └── src/
│       ├── main/
│       │   ├── AndroidManifest.xml permissions, the activity, five services, the file provider
│       │   ├── java/com/dotancohen/voiceandroid/   the application (section 5)
│       │   ├── java/uniffi/voicecore/voicecore.kt  GENERATED binding to the core (8,340 lines)
│       │   ├── java/uniffi/voice_transcription/    GENERATED binding to Whisper
│       │   ├── jniLibs/arm64-v8a/  libvoicecore.so, libvoice_transcription_android.so, libc++_shared.so
│       │   └── res/                strings, theme, launcher icons, xml/shared_files.xml
│       ├── debug/                  debug build only: AdbCommandReceiver and its manifest
│       ├── test/                   JVM unit tests, including Compose screens with Robolectric
│       └── androidTest/            instrumented tests that run on a device (TagTreeItemTest)
├── submodules/
│   ├── voicecore/                  the Rust core (git submodule)
│   └── voicetranscription/         Whisper bindings (git submodule)
├── tools/
│   ├── voice-adb                   drives the debug application from a computer
│   └── voice-phone-backup          copies and verifies the phone's data
├── gradle/libs.versions.toml       library versions
├── build-app.sh                    the full build: bindings, native library, APK, unit tests
├── settings.gradle.kts, build.gradle.kts, gradle.properties, gradlew
├── README.md, DEVELOPMENT.md, USER_MANUAL.md
└── build/, .gradle/, .kotlin/      build output
```

**Versions** (`gradle/libs.versions.toml`, `app/build.gradle.kts`): Android
Gradle Plugin 8.7.3, Kotlin 2.0.21, Compose BOM 2024.12.01, Media3 1.5.1,
CameraX 1.4.1, ZXing 3.5.3, Robolectric 4.14.1, JNA 5.14.0. `minSdk = 29`
(Android 10), `compileSdk` and `targetSdk` 35. Java bytecode target 17; Gradle
must run on JDK 21.

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
| `note/{noteId}?record={record}` | `NoteDetailScreen.kt` | One Note: text, Recordings with player and recorder, Transcriptions, Tags, conflicts, previous and next Note |
| `tags/{noteId}` | `TagManagementScreen.kt` | The Tags of one Note |
| `tag_hierarchy` | `TagHierarchyScreen.kt` | Create, rename, move, delete Tags |
| `settings` | `SettingsScreen.kt` | The settings menu |
| `sync_settings` | `SyncSettingsScreen.kt` | Peers, the operation buttons, pairing codes, listening, the bucket |
| `import_audio` | `ImportAudioScreen.kt` | Importing a folder of existing recordings |
| `recorder_settings`, `microphone_settings`, `playback_settings`, `transcription_settings`, `advanced_settings` | `...SettingsScreen.kt` | One settings page each |
| `transcription_queue` | `TranscriptionQueueScreen.kt` | What is being transcribed and what waits |
| `trash` | `TrashScreen.kt` | Deleted Notes: recover or purge |
| `missing_data` | `MissingDataScreen.kt` | Survey and calculation of facts that were never recorded |

`ui/components/` holds pieces used by several screens: `AudioPlayerWidget`
and `CompactAudioPlayer` (players with waveform), `AudioRecorderWidget`,
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
| `NoteDetailViewModel.kt` | One Note, saving, neighbours, transcriptions |
| `SharedFilterViewModel.kt` | The current search and filter, shared by the list and the Note screen (scoped to the activity) |
| `FilterViewModel.kt` | Tag filter tree |
| `TagManagementViewModel.kt`, `TagHierarchyViewModel.kt` | Tags |
| `SettingsViewModel.kt` | Settings and sync screen |
| `ImportAudioViewModel.kt` | Import |
| `RecorderSettingsViewModel.kt`, `TranscriptionSettingsViewModel.kt` | Settings pages |
| `TranscriptionQueueViewModel.kt`, `TrashViewModel.kt`, `MissingDataViewModel.kt`, `SnapshotsViewModel.kt` | Their screens |

### 5.4 `data/` — the repository, data classes, sync services

| File | Purpose |
|---|---|
| `VoiceRepository.kt` | The only class that calls `VoiceClient`. A process-wide **singleton** (`VoiceRepository.getInstance(context)`). Chooses the audio directory, creates the client, reports the timezone. About 150 methods, grouped by Notes, Tags, Recordings, Transcriptions, conflicts and history, peers and operations, pairing, snapshots, bucket and encryption |
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
| `RecorderPreferences.kt` | Format (Opus in Ogg by default, AAC, WAV), microphone, behaviour during a telephone call, the New button's default |
| `MicLevelMeter.kt` | Level stream for the microphone test |
| `AudioPlayerManager.kt` | The single ExoPlayer of the application (`AudioPlayerManager.shared(context)`), never released, so playback survives moving between screens |
| `PlaybackService.kt` | Foreground service (type `mediaPlayback`) with Pause and Stop in the notification |
| `PlaybackPreferences.kt` | One speed for every player, 0.5× to 3× |
| `WaveformExtractor.kt` | Decodes a file into waveform levels, one decoder at a time (TECHNICAL-DECISIONS 3.6), and stores them with the Recording |
| `WaveformCache.kt`, `LargeRecording.kt` | Local waveform cache; the "large recording" test (60 minutes or 100 MiB) |

### 5.6 `transcription/` — Whisper on the phone

| File | Purpose |
|---|---|
| `OnDeviceTranscriber.kt` | The queue and the job: creates a `Pending...` Transcription, converts the audio, runs Whisper, stores text, segments and the cost of the run. Service name `local_whisper` |
| `TranscriptionService.kt` | Foreground service that works the queue (type `mediaProcessing`, or `dataSync` on API 34) |
| `JobQueue.kt` | The in-process queue: one job at a time; "do this one next" never interrupts the running job |
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

### 5.8 `app/src/debug/.../automation/AdbCommandReceiver.kt`

A **broadcast receiver** that exists only in the debug build. It turns every
user action into an **intent** that a computer can send over ADB, and replies
with a line starting `OK` or `ERROR`. `tools/voice-adb` sends them:

```bash
tools/voice-adb ping
tools/voice-adb create-note "פתק מהטלפון"
tools/voice-adb list-notes
tools/voice-adb help
```

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
   - `Database::new(files/notes.db)` opens the database and runs every
     migration (section 8.1);
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

A file is found by the name in `audio_files.disk_name`. A file this
application records is named from its start time and the last eight
characters of its id, for example `2026_09_21_14_30_59-abcdefgh.ogg`; an
imported file keeps its own name.

### 7.3 Settings

| Where | Synced? | Examples |
|---|---|---|
| `SharedPreferences` file `voice_settings`, written by `UiPreferences`, `RecorderPreferences`, `TranscriptionPreferences`, `PlaybackPreferences` | Never | Interface size, recording format, microphone, Whisper model, playback speed, date format. A unit test checks that no two classes use the same key |
| `files/config.json` (through the core) | Never | Peers, device key, listener idle stop, largest file to sync |
| Table `synced_settings` (through the core) | Yes | Preferred transcription languages, `tag_color.<name>` |
| Table `file_storage_config` | Yes | The bucket's settings |

---

## 8. The database

The database is identical on the phone and the desktop: the same Rust code
creates it. Only its location differs (`files/notes.db`).

### 8.1 Basic facts

- **One SQLite file**, `notes.db`. SQLite is a database stored in one
  ordinary file, with no server process.
- It is opened by `Database::new` in `database.rs`, which sets **WAL** journal
  mode, sets `busy_timeout` to 10 seconds, and then runs every **migration** in
  a fixed order:

```
init_database                        the original tables, indexes and system Tags
migrate_add_sync_received_at
migrate_timestamps_to_unix           text dates became Unix seconds
migrate_add_storage_columns
migrate_add_file_storage_config_table
migrate_drop_legacy_conflict_tables  the six old conflicts_* tables
create_version_tables                field_versions, field_heads ... (versions.rs)
migrate_create_root_versions
migrate_add_sync_sequence            seq columns, triggers, most newer tables and columns
migrate_add_timezone_columns
```

- **There is no schema version number.** Each migration checks whether its
  table or column already exists and does nothing when it does. A new build
  therefore opens an old database and adds what is missing. To add a column,
  add a "check, then `ALTER TABLE`" step to a migration function.
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
  (`VoiceRepository.reportTimeZone`).
- **Nothing is deleted by an ordinary delete.** Deleting sets `deleted_at` (a
  **soft delete**): the Note waits in the trash with its Recordings. Only a
  **purge**, asked for from the trash, removes rows. The core then returns the
  ids of the Recordings that went, and the application deletes their files.
- **Foreign keys** are declared but not enforced; the code does not rely on them.

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
```

- A Note has any number of Tags, through `note_tags`.
- A Note has any number of Attachments. `note_attachments` is a
  **polymorphic association**: `attachment_type` says which table
  `attachment_id` points into. Today the only type in use is `audio_file`.
- A Recording (`audio_files` row) has any number of Transcriptions.
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
| `content_sha256` | SHA-256 of the file's bytes; also the bucket object's name |
| `storage_provider`, `storage_key`, `storage_uploaded_at` | Set after an upload to the bucket; NULL means not uploaded |
| `storage_encrypted` | 1 when the bucket object is encrypted |
| `waveform_levels` | The levels a waveform is drawn from, kept by the first device that decoded the file, so the phone need not decode what the desktop already decoded |
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

**System Tags**, with the same ids on every device:

| Tag | Id | Purpose |
|---|---|---|
| `_system` | `a1b2c3d4-0000-5000-8000-000000000001` | Parent of the hidden Tags |
| `_system/_marked` | `...0002` | The star on a Note |
| `_system/_nonsynced` | `...0003` | Parent of Tags for items that are not synced |
| `_system/_nonsynced/_too-big` | `...0004` | Recordings larger than the largest file to sync |

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

### 8.5 Tables for sync

| Table | Purpose |
|---|---|
| `sync_sequence` | One counter for the whole database |
| `seq` column | On `field_versions`, `notes`, `tags`, `note_tags`, `note_attachments`, `audio_files`, `transcriptions`, `file_storage_config`, `purges`. **Triggers** set it when a row is inserted or a synced column really changed |
| `sync_meta` | `database_id` and `account_id` |
| `sync_peers` | One row per peer: id, name, URL, certificate fingerprint, cursors (`last_received_cursor`, `last_sent_seq`), `last_sync_at`, `last_operation`, the peer's database and account ids |
| `sync_failures` | Changes from a peer that could not be applied, tried again at the next batch |
| `purges` | Everything ever purged, kept for ever so no peer can bring it back |
| `file_storage_config` | One row: the bucket settings |

A sync sends every row and version whose `seq` is above what the peer already
received, for the entity types `note`, `tag`, `note_tag`, `note_attachment`,
`audio_file`, `transcription`, `file_storage_config`, `field_version` and
`purge`.

### 8.6 Tables that never leave the phone

| Table | Purpose |
|---|---|
| `pairing_offers` | The hash of a pairing code while valid |
| `audio_file_copies` | Which peer is known to hold which Recording (the line "3 notes and 2 recordings are not duplicated off this device") |
| `upload_parts` | Journal of an upload in parts |
| `pending_file_renames` | A Recording whose file must still be renamed |
| `purged_objects` | Bucket objects deleted by a purge |

### 8.7 Reading the phone's database safely

1. Take the backup: `tools/voice-phone-backup`. It needs a debug build
   (it uses `run-as`) and writes to `~/voice-phone-backups/` by default.
2. Open the copied `notes.db` read-only on the computer:
   `sqlite3 -readonly <copy>/notes.db`. Copy `notes.db-wal` with it if present.

```sql
-- The 20 newest Notes that are not in the trash
SELECT lower(hex(id)), datetime(created_at, 'unixepoch'), substr(content, 1, 60)
FROM notes WHERE deleted_at IS NULL ORDER BY created_at DESC LIMIT 20;

-- Recordings, with their names on disk and whether they are in the bucket
SELECT lower(hex(id)), disk_name, duration_seconds, storage_key IS NOT NULL AS in_bucket
FROM audio_files WHERE deleted_at IS NULL ORDER BY imported_at DESC LIMIT 20;

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
     `<audio directory>/<disk_name>`, never overwriting, then `storeContentHash`;
   - the temporary file is deleted.

### 9.2 Importing existing recordings

`ImportAudioViewModel` reads the folder, calls `importAudioFile` for each file
(one new Note each) and `copyAudioFileToStorage`.

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

**Files between installations:** `POST /sync/audio/missing` finds what the
receiver lacks; `GET` and `POST /sync/audio/:id/file` stream bytes, resume, and
verify the SHA-256.

**The bucket:** objects are named by content hash; files over 8 MiB are
uploaded in parts; objects may be encrypted with the account's recording key
(`crypto.rs`). The phone downloads a Recording from the bucket when the user
asks for it.

### 9.5 Pairing

One device shows a QR code or a setup text `voice://pair?...` with a
single-use token valid for ten minutes. The phone reads it with the camera
(`QrReader`, CameraX and ZXing) or receives it as a tapped link (the
`voice://pair` intent filter in the manifest, passed on by `PairingRequests`).
`VoiceRepository.pairWith(setupText)` gives the phone the account id, a device
key of its own and one peer.

### 9.6 Snapshots

A copy of `notes.db` goes into `files/snapshots/` before anything from a peer
is applied, before moving to another account and before a restore; five are
kept. Advanced settings list and restore them (`SnapshotsViewModel`).

---

## 10. Building and testing

### 10.1 The whole build: `build-app.sh`

The script stops at the first failure (`set -e`) and does, in order:

1. `cargo build --release --features uniffi` in `submodules/voicecore`, for the computer.
2. `uniffi-bindgen generate --library $CARGO_TARGET/release/libvoicecore.so --language kotlin`,
   then copies `voicecore.kt` into `app/src/main/java/uniffi/voicecore/`.
3. `cargo ndk -t arm64-v8a -o <absolute path>/app/src/main/jniLibs build --release --features uniffi`,
   the native library for the phone.
4. `./gradlew assembleDebug` with `JAVA_HOME=/usr/lib/jvm/java-21-openjdk-amd64`.
5. `./gradlew testDebugUnitTest`.

It does not install anything; the install lines are commented out, and must
stay behind section 1's backup.

The APK is at `app/build/outputs/apk/debug/app-debug.apk`.

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

### 10.3 Tests

```bash
JAVA_HOME=/usr/lib/jvm/java-21-openjdk-amd64 ./gradlew testDebugUnitTest
```

The report is `app/build/reports/tests/testDebugUnitTest/index.html`.

| Lane | Directory | Runs on | Use it for |
|---|---|---|---|
| JVM | `app/src/test/` | The computer; **Robolectric** supplies Android's classes | Logic, and Compose screens: that a control does what it looks like it does |
| Instrumented | `app/src/androidTest/` | A device, as the `.uitest` application | Anything that needs `libvoicecore.so`, the real database, a microphone |

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

Instrumented tests are **not** run on the owner's phone (section 1).

The Rust core has its own tests: `cd submodules/voicecore && cargo test`.

---

## 11. Rules to follow when changing the code

1. **The owner's phone is touched only as section 1 says.**
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
14. **Core changes go to both applications**: the desktop binding too, and both
    core checkouts at the same commit (7.2).

---

## 12. Where to look first

| Question | Start at |
|---|---|
| Which screen is this? | Routes in `ui/VoiceApp.kt` |
| Where does this screen get its data? | Its view model, then the `VoiceRepository` method, then `android.rs` |
| What does the core function really do? | `submodules/voicecore/src/database.rs` or the module named in `android.rs` |
| Why did the recording not save? | `files/critical.log`, `files/voice.log`, `VoiceRecorder.save()` |
| Why did a sync fail? | The result's request id, the sync screen's connection check, `sync_failures` |
| What does this rule id mean? | `Voice/SYNC_SPECIFICATION.md` or `Voice/PLAN-ACCOUNTS-PAIRING-SYNC.md` |

---

## 13. Where older documents and files disagree with the code

Checked on 2026-09-13. This guide describes the code; these texts and files
were not changed.

| Where | What it says or contains | What is actually the case |
|---|---|---|
| Working tree, at the time of writing | — | Ten files have uncommitted changes (including `voicecore.kt` and `VoiceRepository.kt`), and `submodules/voicecore` is checked out at `987f769` while the last commit records `4d11292`. Work was in progress; run `git status` before building on it |
| `app/src/main/jniLibs/armeabi-v7a/`, `x86/`, `x86_64/` | A `libvoicecore.so` each, dated 2026-09-09 | `build-app.sh` builds `arm64-v8a` only (dated 2026-09-13, with the bindings). The other three were built before the current bindings and very likely lack functions the bindings call, yet `abiFilters` still packs all four ABIs into the APK. An emulator on x86_64 would load an old library |
| `DEVELOPMENT.md`, "Build steps" | Builds and copies four ABIs | The current build uses one ABI (above); Whisper exists for arm64 only |
| `DEVELOPMENT.md`, "Prerequisites" | JDK 17 or newer | Gradle must run on JDK 21 (the same file says so further down) |
| `app/build.gradle.kts`, tasks `buildRust` and `generateKotlinBindings` | Read the library from `submodules/voicecore/target/...` | Rust builds go to `VoiceFamily/.cargo-target/`, so these tasks would not find the library. Neither is part of the normal build (`preBuild` does not depend on them) |
| `submodules/jniLibs/`, `submodules/kotlin-bindings/`, `submodules/uniffi-generated/` | Libraries and bindings from December 2025 and January 2026 | No build script refers to them; Gradle reads only `app/src/main/jniLibs` |
| `Voice/CLAUDE.md`, "Recorder" | `viewmodel/RecordingViewModel.kt` and `ui/screens/RecordingScreen.kt` | Neither file exists. Recording happens inside the Note screen (`Screen.NoteDetail` with `record=true`) |
| `VoiceAndroid/README.md` | Syncing "talks to a Voice server you run" | The phone can also listen for peers itself and pair by code (Stages 6 and 9) |
| `Voice/SYNC_SPECIFICATION.md` FILE-6, and `Voice/CLAUDE.md` | A file and its bucket object are named `{audio_id}.{ext}` | The file is named by `disk_name` (TECHNICAL-DECISIONS 3.1a); the bucket object by the content hash (3.1b) |

---

## 14. Glossary

**ABI** — "Application binary interface": here, the processor family a native library is compiled for. Phones are almost all `arm64-v8a`; emulators are often `x86_64`.

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

**Foreground service** — Android work that continues when the application is not on the screen, and must show a notification. Recording, playback, transcription, listening and sync operations each run in one.

**Head** — The version of a field that is its current value.

**Idempotent** — Doing it twice has the same result as doing it once.

**Intent** — An Android message asking a component to do something; also how a `voice://pair` link opens the application.

**Jetpack Compose** — Android's toolkit for drawing screens from Kotlin functions.

**JNA** — "Java Native Access": a library that lets JVM code call functions in a native `.so` library. The UniFFI-generated Kotlin uses it.

**Keystore** — Android's store for encryption keys, often in hardware; a key in it cannot be copied out.

**Leaf** — A version that no other version was written on top of. Two leaves for one field means two devices changed it independently.

**Migration** — Code that changes an existing database's structure so it matches what the current code expects.

**MVVM** — Model, view, view model: screens (views) show state held by view models, which get data from a model layer (here the repository and the core).

**Peer** — Another Voice installation of the same account.

**Polymorphic association** — A link whose target table is named in a column (`attachment_type`).

**Primary key** — The column or columns that identify a row uniquely.

**Purge** — Removing an item for good, with its history, on every device. Only possible from the trash.

**Repository** — A class that is the single way the rest of the application reaches data. Here `VoiceRepository`.

**Robolectric** — A library that runs Android framework classes on an ordinary JVM, so screens can be tested without a phone.

**Singleton** — A class of which the process has exactly one instance.

**Soft delete** — Marking a row as deleted (`deleted_at`) instead of removing it.

**SQLite** — A database engine that stores a whole database in one file and runs inside the application.

**StateFlow** — A Kotlin stream that always holds a current value; a composable reading it with `collectAsState()` is redrawn when the value changes.

**Submodule** — A git repository placed inside another at a fixed commit.

**Three-way merge (diff3)** — Combining two edited versions of a text by comparing each with the version they both started from.

**TOFU** — "Trust on first use": a certificate is accepted the first time and remembered; a different one later is refused.

**Transaction** — A group of database writes that either all take effect or none do.

**Trigger** — SQL that SQLite runs by itself when a row is inserted or changed. Here triggers set `seq`.

**UniFFI** — A Mozilla tool that generates Kotlin (and other languages') bindings for a Rust library from annotations such as `#[uniffi::export]`.

**UUID7** — A 128-bit id whose first part is its creation time, so ids sort by time; the last characters are random.

**Version** — One immutable value of a field, with a pointer to the version it replaced.

**View model** — A class that holds a screen's state and survives the screen being redrawn or rotated.

**WAL (write-ahead log)** — An SQLite journal mode: changes go to `notes.db-wal` first and into `notes.db` later.
