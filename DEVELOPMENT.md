# Building and hacking on Voice for Android

Everything needed to build the application from source, notes on selected
parts of it, and its tests. For what the application does, see the
[README](README.md); for how it is used, the [user manual](USER_MANUAL.md); for
where each part of the code is, the [maintainer's guide](MAINTAINER-GUIDE.md).

## Contents

- [The owner's phone](#the-owners-phone)
- [Technical decisions and related documents](#technical-decisions-and-related-documents)
- [Building](#building)
  - [Prerequisites](#prerequisites)
  - [Where the checkout must be](#where-the-checkout-must-be)
  - [Build steps](#build-steps)
  - [Pitfalls](#pitfalls)
- [Architecture](#architecture)
  - [Notes on a few pieces](#notes-on-a-few-pieces)
- [Tests](#tests)
  - [Running the JVM tests](#running-the-jvm-tests)
  - [What is covered](#what-is-covered)
  - [Testing a screen](#testing-a-screen)
  - [Instrumented tests](#instrumented-tests)
- [Project structure](#project-structure)


## The owner's phone

On 2026-09-12 the command `./gradlew connectedDebugAndroidTest` replaced the
application on the owner's phone. Android deletes an application's data when
the application is replaced, and a week of his notes and recordings was
destroyed. There was no backup.

This document gives no command that installs anything on a phone. Anything that
installs, replaces, uninstalls or clears the application on a phone — `adb
install`, `adb uninstall`, `pm clear`, any Gradle task whose name contains
`connected` or `install` — is run only when the owner asks for that exact action,
and only after `tools/voice-phone-backup` has been run as a command of its own
(never piped into another command) and has printed `Backup verified.`. The rules
are in [CLAUDE.md](CLAUDE.md) and in section 1 of the
[maintainer's guide](MAINTAINER-GUIDE.md).

## Technical decisions and related documents

Decisions that apply to more than one project in the Voice Family — data rules,
time handling, thresholds, naming, interface conventions, testing rules — are in
`../TECHNICAL-DECISIONS.md`. Read it before changing behaviour the other
projects share, and record new cross-project decisions there.

These documents are in the VoiceFamily directory, one level above this file:

- `../SYNC_SPECIFICATION.md`: the sync protocol and its rules (the ids such as
  FILE-22 used below).
- `../PLAN-ACCOUNTS-PAIRING-SYNC.md`: the plan for accounts, pairing and sync.
- `../BUGS-THE-TESTS-MISSED.md`: bugs that shipped in tested code, and the rules
  that would have caught them.
- `../test-plans/`: the manual test plans.

## Building

`build-app.sh` runs the build steps below in order and stops at the first
failure. It installs nothing: its two install lines are commented out.

### Prerequisites

1. **Android SDK** at the path named in `local.properties` (`sdk.dir=...`), with:
   - SDK Platform 35 (`compileSdk` and `targetSdk` are 35, `minSdk` is 29)
   - NDK 29.0.14206865, the version `build-app.sh` uses

2. **Rust toolchain** with the one Android target the application packs, and
   `cargo-ndk`:

```bash
rustup target add aarch64-linux-android
cargo install cargo-ndk
```

3. **JDK 21** for Gradle (`/usr/lib/jvm/java-21-openjdk-amd64`). Android
   Studio's bundled JDK is JDK 25, which this Gradle (8.11.1, with Android
   Gradle plugin 8.7.3) cannot run on: every task fails at once with the bare
   message `25.0.3`. The Kotlin and Java sources are compiled to Java 17.

4. **For the JVM tests only:** `python3` with the `venv` module, and network
   access on the first run (see [Running the JVM tests](#running-the-jvm-tests)).

### Where the checkout must be

The build depends on paths outside this repository:

- Every Rust crate in the family builds into one shared directory,
  `/home/dotancohen/Projects/VoiceFamily/.cargo-target`. Cargo is told so by
  `/home/dotancohen/Projects/VoiceFamily/.cargo/config.toml`
  (`[build] target-dir = ...`), and `../TECHNICAL-DECISIONS.md` 7.4 records the
  decision. The VoiceFamily directory is not a git repository, so on a new
  computer that file is written by hand.
- `build-app.sh` uses absolute paths under
  `/home/dotancohen/Projects/VoiceFamily/VoiceAndroid`.
- The Gradle task `hostCoreForTests` reads `../.cargo-target/release/libvoicecore.so`,
  relative to this directory.

So clone into the VoiceFamily directory, with the submodules:

```bash
cd /home/dotancohen/Projects/VoiceFamily
git clone --recursive https://github.com/dotancohen/voiceandroid.git VoiceAndroid
cd VoiceAndroid
echo "sdk.dir=/home/dotancohen/Android/Sdk" > local.properties
```

`app/src/main/jniLibs/` is listed in `.gitignore`, so a fresh clone has no
native libraries; steps 2 and 3 below put them there. The Kotlin bindings
(`app/src/main/java/uniffi/`) are in git.

### Build steps

The APK packs one ABI, `arm64-v8a` (`abiFilters` in `app/build.gradle.kts`).
The core, the Whisper library and FFmpeg's decoders are present for it alone;
`app/src/main/jniLibs/` holds no other ABI directory. Another ABI can be asked
for with `-PtargetAbi=<abi>`, and its libraries must then be built first.

1. **Regenerate the Kotlin bindings of the core** (needed when a function
   exposed through UniFFI was added or changed):

```bash
export CARGO_TARGET=/home/dotancohen/Projects/VoiceFamily/.cargo-target
cd /home/dotancohen/Projects/VoiceFamily/VoiceAndroid/submodules/voicecore
cargo build --release --features uniffi
"$CARGO_TARGET/release/uniffi-bindgen" generate --library "$CARGO_TARGET/release/libvoicecore.so" --language kotlin --out-dir /tmp/kotlin-bindings
cp /tmp/kotlin-bindings/uniffi/voicecore/voicecore.kt /home/dotancohen/Projects/VoiceFamily/VoiceAndroid/app/src/main/java/uniffi/voicecore/voicecore.kt
```

   Check that the new function is in the generated file before copying it (see
   [Pitfalls](#pitfalls)).

2. **Build the core for the phone.** The output path must be absolute:

```bash
cd /home/dotancohen/Projects/VoiceFamily/VoiceAndroid/submodules/voicecore
ANDROID_NDK_HOME=/home/dotancohen/Android/Sdk/ndk/29.0.14206865 cargo ndk -t arm64-v8a -o /home/dotancohen/Projects/VoiceFamily/VoiceAndroid/app/src/main/jniLibs build --release --features uniffi
```

   This writes `app/src/main/jniLibs/arm64-v8a/libvoicecore.so`.

3. **The Whisper library** (not part of `build-app.sh`). The application ships
   VoiceTranscription's `voice-transcription-android` crate (whisper.cpp) as
   `app/src/main/jniLibs/arm64-v8a/libvoice_transcription_android.so`, together
   with the NDK's `libc++_shared.so` in the same directory. It exists for
   arm64 only. `../VoiceTranscription/README.md`, section "Build Android
   Bindings", gives the commands that build it and regenerate
   `app/src/main/java/uniffi/voice_transcription/voice_transcription.kt`.

4. **Build the APK:**

```bash
cd /home/dotancohen/Projects/VoiceFamily/VoiceAndroid
JAVA_HOME=/usr/lib/jvm/java-21-openjdk-amd64 ./gradlew assembleDebug
```

   The APK is written to `app/build/outputs/apk/debug/app-debug.apk`.

5. **Run the JVM tests** (see [Tests](#tests)):

```bash
cd /home/dotancohen/Projects/VoiceFamily/VoiceAndroid
JAVA_HOME=/usr/lib/jvm/java-21-openjdk-amd64 ./gradlew testDebugUnitTest
```

The Gradle tasks `buildRust` and `generateKotlinBindings` in
`app/build.gradle.kts` are not part of the build (`preBuild` does not depend on
them) and read `submodules/voicecore/target/`, which the shared build directory
leaves empty. Use the commands above.

### Pitfalls

- **The shared build directory.** `release/libvoicecore.so` in
  `VoiceFamily/.cargo-target` is one path that several builds write. The
  desktop's `maturin develop` rebuilds the core **without** the `uniffi`
  feature and replaces that file; `uniffi-bindgen` then reads no interface from
  it, generates nothing, and still exits with status 0. Cargo does not notice,
  because its fingerprint says the build with the feature is current. Generate
  the Kotlin bindings before running maturin, or force the build first:

```bash
cd /home/dotancohen/Projects/VoiceFamily/VoiceAndroid/submodules/voicecore
touch src/lib.rs && cargo build --release --features uniffi
```

- **`cargo ndk -o` takes absolute paths only.** A relative path is resolved
  from the Cargo workspace root, not the current directory, and the library is
  written to the wrong place.
- **Bindings and native library together.** When the Kotlin bindings call a
  function the native library does not have, the application crashes on the
  phone with `UnsatisfiedLinkError: undefined symbol`. Rebuild both, and check
  that the library is in `app/src/main/jniLibs/arm64-v8a/`. The desktop build
  and tests can pass while the phone crashes.
- **Two cargo builds at once** wait for each other's lock on the shared build
  directory.
- **A failed build is a failed build.** Read Gradle's `BUILD SUCCESSFUL` or
  `BUILD FAILED` line; an exit status hidden behind a pipe is not evidence.

## Architecture

- **Kotlin + Jetpack Compose** for the interface, in the MVVM pattern: view
  models in `viewmodel/`, screens in `ui/screens/`, components in
  `ui/components/`.
- **The Rust core through UniFFI**: `data/VoiceRepository.kt` calls the core
  through the generated bindings in `app/src/main/java/uniffi/voicecore/voicecore.kt`,
  which load `libvoicecore.so` with JNA. The same core serves the desktop
  application.

The [maintainer's guide](MAINTAINER-GUIDE.md) describes every package.

### Notes on a few pieces

**Time formatting** is `util/TimeFormat.kt`, ported from SSIA's
specification; the two preference keys are `time_format` (a pattern, or the
literal `custom`) and `time_format_custom` (the free-form pattern), the same
names that specification uses, so the file can move between the two
applications unchanged. `SimpleDateFormat` throws on an unknown pattern letter,
so the `-N` token is rewritten as a quoted private-use character (`U+E000`)
before the pattern reaches it, and that character is replaced afterwards by the
day count, wrapped in directional isolates (`U+2066`, `U+2069`) so the minus
stays left of the digits in a right-to-left interface.

**Transcription** runs in a foreground service that outlives the activity
(`stopWithTask="false"`), because Android freezes an ordinary background
process within seconds and a twenty-minute recording is minutes of solid CPU.
Android refuses to start a foreground service for an application that is not on
screen. A transcription asked for while the application is in the background
stays in the queue, the request answers "Transcription will start when the app
is open", and the job starts when the application is next opened.

**Icons.** The application depends on `androidx.compose.material:material-icons-core`
only. The few icons of the extended set that it draws are copied as source into
`app/src/main/java/androidx/compose/material/icons/` (under `filled/`,
`outlined/` and `automirrored/filled/`), because the extended set is thousands
of classes the application never draws. An extended icon that is not copied
there does not compile; copy its file there.

**Tuned numbers** — the ones chosen by looking at a screen rather than
derived from anything, such as how much larger the large interface is and how
much smaller the time of day is drawn than the date — are gathered in
`util/MagicNumbers.kt` as `object Magic`, and referred to from wherever they
are used. A number fixed by something outside the application (the 44 bytes
of a WAV header, Whisper's 16 kHz) is not one of these and stays beside the
code that depends on it.

**The five transcription flags** are `data/TranscriptionFlags.kt`. They are
stored in the transcription's `state` field — the name in the database and in
the sync protocol, kept as it is because every synced device knows it — and
everything above the database calls them flags. The wording is printed in
both user manuals and in the desktop application, and the two applications
must agree on the words exactly: `app/src/test/resources/transcription_flags_contract.json`
holds the agreed cases and is read by `TranscriptionFlagsContractTest` here and
by `tests/sync/test_transcription_flags_contract.py` in `../Voice`, whose copy,
`tests/fixtures/transcription_flags_contract.json`, must be byte for byte the
same. The desktop also sends each of those fields through a real sync between
two installations (`tests/sync/test_transcription_flags_sync.py`).

**One player.** `AudioPlayerManager.shared(context)` is the single player of
the application, made on first use and kept for the life of the process:
playback started in the notes list continues when the Note is opened and when
its Tags screen is visited. `adoptAudioFiles` gives the running player a
screen's list of files without interrupting it when one of them is the file
already playing.

**Tag colours** are `data/TagColours.kt`: the default is the first six
characters of the MD5 of the Tag's *name*, so every device agrees without
storing anything; a chosen colour is stored in the synced settings under the
key that `TagColours.settingKey` returns, `tag_color.<name>`.

**Sharing** is `util/NoteSharing.kt` (what a Note offers, what is sent) plus
the `FileProvider` declared in the manifest as `${applicationId}.shared`
(`res/xml/shared_files.xml`). A recording is given to the receiving application
as a `content://` URI granted for one action; Android refuses a `file://` path.

**Features under trial** are behind `BuildConfig.DEV_FEATURES`, which is true
in the `debug` build type (and in `uitest`, which copies `debug`) and false in
`release`, so a release build never shows them. At present: holding a Note in
the list and dragging to select several (`detectDragGesturesAfterLongPress`,
since a plain drag is the list's own scroll), transcribing every Recording as
it is saved (Settings → Recorder), and editing a Transcription's text from its ⓘ
dialogue. Put a new one behind the same constant rather than behind a setting
that a release build would still carry.

**Automation from a computer (debug build only).**
`app/src/debug/java/com/dotancohen/voiceandroid/automation/AdbCommandReceiver.kt`
accepts user actions as broadcast intents, and `tools/voice-adb` sends them to
`com.dotancohen.voiceandroid`, the application that holds the owner's notes.
The manual test plans in `../test-plans/` use it.

**Unit tests and Android's stub classes.** `testOptions.unitTests.isReturnDefaultValues`
is on, so an Android class that is only a stub in a JVM test answers with a
default instead of throwing. That is what lets the tests exercise error paths
that log, but it also means Android's `org.json` would silently parse nothing —
so the tests depend on the real `org.json:json`, which takes precedence over the
stub.

**Settings** are all stored in one `SharedPreferences` file, `voice_settings`,
written by `UiPreferences`, `RecorderPreferences`, `TranscriptionPreferences`
and `PlaybackPreferences`, and by `VoiceRepository` and `SettingsViewModel` for
the audio folder (`audiofile_directory_path`, `pending_audiofile_path`).
`SettingsFileTest` writes a value different from the default into each of the
four preference classes over one shared file and checks that each reads back
its own.

## Tests

| Lane | Directory | Runs on | Tests |
|---|---|---|---|
| JVM | `app/src/test/` | This computer | 501 `@Test` in 49 files |
| Instrumented | `app/src/androidTest/` | A device or emulator, as the separate application `com.dotancohen.voiceandroid.uitest` | 6 `@Test` in one file, `TagTreeItemTest.kt` |

The Rust core has its own tests: `cd submodules/voicecore && cargo test`.

### Running the JVM tests

No device or emulator is needed, and no task below contains `connected` or
`install`:

```bash
cd /home/dotancohen/Projects/VoiceFamily/VoiceAndroid
JAVA_HOME=/usr/lib/jvm/java-21-openjdk-amd64 ./gradlew testDebugUnitTest
```

One class, or one package:

```bash
JAVA_HOME=/usr/lib/jvm/java-21-openjdk-amd64 ./gradlew testDebugUnitTest --tests 'com.dotancohen.voiceandroid.data.CoreStorageTest'
JAVA_HOME=/usr/lib/jvm/java-21-openjdk-amd64 ./gradlew testDebugUnitTest --tests 'com.dotancohen.voiceandroid.util.*'
```

The suite does more than compile Kotlin. Every unit test task first runs two
tasks defined in `app/build.gradle.kts`:

- **`hostCoreForTests`** runs `cargo build --release --features uniffi` in
  `submodules/voicecore` — the core built for this computer, with the phone's
  bindings — and copies `../.cargo-target/release/libvoicecore.so` to
  `app/build/host-core/libvoicecore.so`. The tests receive that path as the
  system property `uniffi.component.voicecore.libraryOverride`, and JNA's
  desktop jar (`testImplementation("net.java.dev.jna:jna:5.14.0")`) loads it.
  So `cargo` must be on `PATH`, and a change to the core is compiled before the
  tests run.
- **`s3ServerForTests`** creates `app/build/s3-test-venv` with `python3 -m venv`
  and installs `moto[server]==5.2.3` into it, when `bin/moto_server` is not
  there yet; the first run therefore needs the network. The tests receive the
  server's path as the system property `voice.test.motoServer`, and
  `CoreStorageTest` starts that S3 server on this computer.

If `cargo` or `python3` is missing, the task that needs it fails, and no test
runs. Robolectric also downloads its Android image for SDK 35 on the first run,
about 190 MB, into `~/.m2/repository/org/robolectric`.

The report is written to
`app/build/reports/tests/testDebugUnitTest/index.html`, and the raw results
to `app/build/test-results/testDebugUnitTest/`.

### What is covered

Paths are under `app/src/test/java/com/dotancohen/voiceandroid/`.

| Area | What is checked | Files |
|---|---|---|
| The core on this computer | This phone's copy of a recording stated when the file is hashed, and not removed while no other place confirms that it holds the file; a file deleted by hand; the account's upload limit and the reasons Issues gives (FILE-22, FILE-23, FILE-26, ISSUE-1); uploads and downloads against a moto S3 server, uploads in parts, a copy the bucket holds removed from this phone once the bucket is asked, and a bucket that is unreachable, cut off, frozen or silent behind the fault proxy (FILE-14, FILE-19, FILE-22, FILE-26) | `data/CoreLocationsTest`, `data/CoreStorageTest`, helpers `network/LocalS3.kt`, `network/FaultyLink.kt` |
| Settings | Defaults, ranges, mark sizes, interface size and toolbar switch, and the four preference classes sharing `voice_settings` | `util/UiSettingsTest`, `util/UiPreferencesTest`, `util/SettingsFileTest` |
| Date and time | The twelve preset formats and the custom choice, the chosen format, the `-N` days-ago token, a broken pattern falling back, splitting date from time, which clock a stamp is drawn on, lengths and positions as clock readings | `util/TimeFormat*Test`, `util/StampsTest`, `util/DurationsTest` |
| Tags | Paths, depths, descendants, collapsing, sorting, and a parent chain that loops — which two phones syncing can produce; the calculated and chosen colour | `data/TagTreeTest`, `viewmodel/TagPathsTest`, `viewmodel/FilterTagTreeTest`, `data/TagColoursTest` |
| Search | The `is:marked` term the star adds to and removes from the query | `viewmodel/FilterTagTreeTest` (`MarkedFilterTest`) |
| Notes list and Note | The lines a row shows, the notes either side of the one open, whether leaving an empty Note deletes it, the main recording and the order of recordings | `viewmodel/NoteListTest`, `viewmodel/NoteDetailDecisionsTest` |
| Recording | The WAV header field by field, recording format, behaviour during a telephone call, the New and + buttons, microphones, the recorder clock and which Note shows the recorder | `audio/WavHeaderTest`, `audio/RecorderSettingsTest`, `audio/CallHandlingTest`, `ui/components/AudioRecorderWidgetTest` |
| Playback and waveforms | Playback speed and its slider, waveform bars, the levels kept with a recording (FILE-20), the waveform cache, which recordings are drawn only when asked | `audio/PlaybackSpeedTest`, `audio/WaveformTest`, `audio/WaveformCacheTest`, `audio/LargeRecordingTest` |
| Audio conversion | Conversion to 16 kHz mono 16-bit, the resampler, clipping, and which files need no conversion | `transcription/AudioConversionTest`, `transcription/AudioToWavTest` |
| Transcription | The language and model catalogues, part-downloaded model files, model, language and beam settings, the ten-minute limit, the queue's order and "transcribe next", the line each queued job shows, cost, rate and wait estimates, which placeholder rows are removed after a transcription, the performance numbers, finished versus pending | `transcription/*Test`, `data/TranscriptionPerformanceTest`, `data/TranscriptionStateTest` |
| Transcription flags | The five flags, their wording and the `state` word list, and the contract file shared with the desktop | `data/TranscriptionFlagsTest`, `data/TranscriptionFlagWordsTest`, `data/TranscriptionFlagsContractTest` |
| Missing data and Issues | Counting and calculating missing data; the wording of Issues and of where the copies are, including a recording this phone recorded or imported whose file is not in the audio folder (ISSUE-1, FILE-22, FILE-25), the same as the desktop's | `data/MissingDataTest`, `util/IssuesTextTest` |
| This phone's address | The words after **Address** on Sync Settings: the address found, alone; the candidates, with the sentence that only one of them is correct; no address found (LISTEN-4), the same as the desktop's | `util/AddressTextTest` |
| Cloud storage state | A recording counts as in the bucket only with both provider and key | `data/AudioFileCloudTest` |
| Pairing and secrets | A setup text read back from a camera frame, the order cameras are tried in, no camera (UI-12); a secret wrapped and unwrapped (AUTH-9) | `ui/components/QrReaderTest`, `util/SecretWrapTest` |
| Sharing | What a Note offers to share and what is sent | `util/NoteSharingTest` |
| Critical log | Entries, rotation at a megabyte, and a log that cannot be written | `util/CriticalLogTest` |
| Screens | That a control does what it looks like it does: the chevron on a Note's row opens that row's section, wherever the row is drawn (see *Testing a screen* below) | `ui/NoteCardChevronTest` |

Tests that involve text are written with Hebrew, since that is what the
application is used for.

### Testing a screen

The screens are written in Jetpack Compose, and a Compose screen can be tested
without a phone: **Robolectric** supplies Android's own framework classes to
the JVM, and **`androidx.compose.ui:ui-test-junit4`** hosts a composable,
presses it and reads what it drew. These tests are in `app/src/test/` with the
other unit tests and run in the same command.

```kotlin
@RunWith(RobolectricTestRunner::class)
class NoteCardChevronTest {
    @get:Rule val compose = createComposeRule()

    @Test
    fun `a card left to itself opens when its chevron is pressed`() {
        compose.setContent { NoteCard(noteWithAudio = row, getAudioFilePath = { null }) }

        compose.onNodeWithText("פגישה-בוקר.opus").assertDoesNotExist()
        compose.onNodeWithContentDescription("Open this Note").performClick()
        compose.onNodeWithText("פגישה-בוקר.opus").assertIsDisplayed()
    }
}
```

A test finds things the way a screen reader does, through the **semantics tree**:
`onNodeWithText`, `onNodeWithContentDescription`, `onNodeWithTag`. So a control
worth testing needs a `contentDescription` — which it needed anyway, for anyone
using the phone by voice. `performClick`, `performTouchInput { swipeUp() }` and
the rest act on it; `assertIsDisplayed`, `assertDoesNotExist`,
`assertTextEquals` read the result. After a press, the test framework
recomposes before the assertions run, so they see the new state.

`app/src/test/resources/robolectric.properties` sets three things for every
such test, with the reasoning in the file: which Android version they run as
(35, the one the application is built against), that Compose draws for real
(`graphicsMode=NATIVE`), and that the application under test is a plain
`Application` rather than `VoiceApplication` — whose `onCreate` loads
`libvoicecore.so` for the phone's processor, which cannot be loaded here. A test
that needs the whole application can override that with
`@Config(application = VoiceApplication::class)`.

`NoteCardChevronTest`, with 8 tests, is at present the only Robolectric test.

### Instrumented tests

`app/src/androidTest/` holds the instrumented tests. There is one class,
`TagTreeItemTest.kt`, with 6 tests of the `TagTreeItem` composable: the Expand
and Collapse arrows call `onToggleExpand` and not `onClick`, the tag name calls
`onClick` only, the arrow follows `isExpanded`, a leaf row has no arrow, and a
nested row still draws its name. It uses no database and no recording.

**They install as a separate application.** `app/build.gradle.kts` defines the
build type `uitest` (`initWith(debug)`, `applicationIdSuffix = ".uitest"`,
`versionNameSuffix = "-uitest"`) and sets `testBuildType = "uitest"`. An
instrumented run therefore builds and installs `com.dotancohen.voiceandroid.uitest`
and its test APK, beside `com.dotancohen.voiceandroid`, with data of its own.

**The task name.** The Android Gradle plugin names the instrumented test task of
a build type `connected<BuildType>AndroidTest`, and creates it only for the
`testBuildType`. The task is therefore **`connectedUitestAndroidTest`**, and
`connectedAndroidTest` runs it. This name is derived from the plugin's naming
rule; the task has not been run. Its name contains `connected`: it installs on
every connected device.

**Where to run them.** An emulator is the intended target. The APK packs only
`arm64-v8a` and `VoiceApplication.onCreate` calls `System.loadLibrary("voicecore")`,
so the emulator's system image must run `arm64-v8a` code.

**Never on the owner's phone** unless he asks for that exact action, and only
after `tools/voice-phone-backup` has been run alone, unpiped, and has printed
`Backup verified.` The separate application id does not protect everything: the
default audio folder, `/storage/emulated/0/Recordings/Voice`, is chosen by
`VoiceRepository.defaultAudioFileDir` whenever all-files access is granted,
whatever the application id.

**Compiling them does not need a device.** `testDebugUnitTest` and
`assembleDebug` do not compile `app/src/androidTest/`, so a broken instrumented
test goes unnoticed: `TagTreeItemTest` once stopped compiling when timestamps
became `Stamp`, and nothing reported it for months.

Which lane a test belongs in:

| Put it in `src/test` (JVM) | Put it in `src/androidTest` (device) |
|---|---|
| Logic, a control that does nothing, a gesture reaching the wrong handler, a row drawing the wrong thing | Something that needs the Android framework on a device: a real recording, the microphone, the camera |
| The core through its bindings (the host-built `libvoicecore.so`, as in `CoreLocationsTest` and `CoreStorageTest`) | |

A JVM test is worth more than an instrumented one of the same thing, because it
runs on every change without a device.

## Project structure

```
VoiceAndroid/
├── app/
│   ├── build.gradle.kts                  SDK levels, build types (debug, release, uitest), dependencies, hostCoreForTests, s3ServerForTests
│   └── src/
│       ├── main/
│       │   ├── AndroidManifest.xml
│       │   ├── java/com/dotancohen/voiceandroid/
│       │   │   ├── audio/                player, recorder, microphones, playback preferences
│       │   │   ├── data/                 repository, data classes, sync services
│       │   │   ├── transcription/        Whisper on the phone: models, WAV conversion, queue, service
│       │   │   ├── ui/                   Compose: screens/, components/, theme/, VoiceApp.kt
│       │   │   ├── util/                 time formats, preferences, logs, sharing, Issues and address wording
│       │   │   ├── viewmodel/            view models
│       │   │   ├── MainActivity.kt
│       │   │   └── VoiceApplication.kt
│       │   ├── java/androidx/compose/material/icons/   extended icons copied as source (see Icons)
│       │   ├── java/uniffi/voicecore/voicecore.kt      GENERATED bindings to the core
│       │   ├── java/uniffi/voice_transcription/        GENERATED bindings to Whisper
│       │   ├── jniLibs/arm64-v8a/        libvoicecore.so, libvoice_transcription_android.so, libc++_shared.so (not in git)
│       │   └── res/                      resources, xml/shared_files.xml
│       ├── debug/                        debug build only: automation/AdbCommandReceiver.kt and its manifest
│       ├── test/
│       │   ├── java/com/dotancohen/voiceandroid/
│       │   │   ├── audio/, data/, transcription/, ui/, util/, viewmodel/   JVM tests
│       │   │   ├── network/              LocalS3.kt (moto), FaultyLink.kt (fault proxy)
│       │   │   └── testing/              FakePreferences.kt
│       │   └── resources/                robolectric.properties, transcription_flags_contract.json
│       └── androidTest/                  instrumented tests (TagTreeItemTest.kt), installed as .uitest
├── submodules/
│   ├── voicecore/                        the Rust core (git submodule)
│   ├── voicetranscription/               Whisper bindings (git submodule)
│   └── jniLibs/, kotlin-bindings/, uniffi-generated/   older copies of a library and bindings; no build step reads them
├── tools/
│   ├── voice-adb                         drives the debug application from a computer
│   └── voice-phone-backup                copies the phone's Voice data to this computer and verifies the copy
├── gradle/libs.versions.toml             library versions
├── build-app.sh                          bindings, core for the phone, APK, JVM tests
├── settings.gradle.kts, build.gradle.kts, gradle.properties, gradlew, local.properties
└── README.md, USER_MANUAL.md, DEVELOPMENT.md, MAINTAINER-GUIDE.md, CLAUDE.md, PLAN-ANDROID.md
```

`README.md` says what the application is, `USER_MANUAL.md` how it is used,
this file how it is built and tested, `MAINTAINER-GUIDE.md` where things are in
the code, and `CLAUDE.md` the rules for Claude Code in this project.
