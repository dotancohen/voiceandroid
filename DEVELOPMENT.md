# Building and hacking on Voice for Android

Everything needed to build the application from source, and a map of what is
where. For what the application does, see the [README](README.md); for how it
is used, the [user manual](USER_MANUAL.md).

## Contents

- [Building](#building)
- [Architecture](#architecture)
  - [Notes on a few pieces](#notes-on-a-few-pieces)
- [Tests](#tests)
- [Project structure](#project-structure)


## Technical decisions

Decisions that apply to more than one project in the Voice Family — data rules,
time handling, thresholds, naming, interface conventions, testing rules — are in
`../TECHNICAL-DECISIONS.md`. Read it before changing behaviour the other
projects share, and record new cross-project decisions there.

## Building

### Prerequisites

1. **Android SDK** with:
  - SDK Platform 35
  - Build Tools 34
  - NDK 29.x

2. **Rust toolchain** with Android targets:
```bash
rustup target add aarch64-linux-android armv7-linux-androideabi x86_64-linux-android i686-linux-android
cargo install cargo-ndk
```

3. **JDK 17+** (Android Studio includes one)

### Build steps

1. Clone with submodules:
```bash
git clone --recursive https://github.com/dotancohen/VoiceAndroid.git
cd VoiceAndroid
```

2. Set up local.properties:
```bash
echo "sdk.dir=/path/to/Android/Sdk" > local.properties
```

3. Build Rust native libraries (if not already built):
```bash
export ANDROID_NDK_HOME=/path/to/ndk
cd submodules/voicecore

# Build for all architectures
for target in aarch64-linux-android armv7-linux-androideabi x86_64-linux-android i686-linux-android; do
    cargo ndk -t $target --platform 29 build --release --no-default-features --features uniffi
done

# Copy libraries
cd ../..
mkdir -p app/src/main/jniLibs/{arm64-v8a,armeabi-v7a,x86_64,x86}
cp "$CARGO_TARGET/aarch64-linux-android/release/libvoicecore.so" app/src/main/jniLibs/arm64-v8a/
cp "$CARGO_TARGET/armv7-linux-androideabi/release/libvoicecore.so" app/src/main/jniLibs/armeabi-v7a/
cp "$CARGO_TARGET/x86_64-linux-android/release/libvoicecore.so" app/src/main/jniLibs/x86_64/
cp "$CARGO_TARGET/i686-linux-android/release/libvoicecore.so" app/src/main/jniLibs/x86/
```

4. Generate Kotlin bindings (if not already generated):
```bash
cd submodules/voicecore
cargo build --release --features uniffi
"$CARGO_TARGET/release/uniffi-bindgen" generate \
    --library "$CARGO_TARGET/release/libvoicecore.so" \
    --language kotlin \
    --out-dir ../../app/src/main/java
```

5. Build the APK:
```bash
JAVA_HOME=/usr/lib/jvm/java-21-openjdk-amd64 ./gradlew assembleDebug
```

Java 21. Android Studio's bundled JBR is Java 25, which this Gradle cannot
compile the build scripts with: it fails with the bare message `25.0.3`.

The APK will be at `app/build/outputs/apk/debug/app-debug.apk`

## Architecture

- **Kotlin + Jetpack Compose**: Modern Android UI toolkit
- **Rust Core (via UniFFI)**: Cross-platform business logic shared with desktop
- **MVVM**: Clean architecture with ViewModels and Repository pattern

### Notes on a few pieces

**Time formatting** is `util/TimeFormat.kt`, ported from SSIA's
specification; the two preference keys are `time_format` (a pattern, or the
literal `custom`) and `time_format_custom` (the free-form pattern), the same
names that specification uses, so the file can move between the two
applications unchanged. The `-N` token is handled by rewriting it as a quoted
private-use character (`U+E000`) before `SimpleDateFormat` sees it — that
class throws on an unknown pattern letter — and substituting the day count
afterwards, wrapped in directional isolates (`U+2066`, `U+2069`) so the minus
stays left of the digits in a right-to-left interface.

**Transcription** runs in a foreground service that outlives the activity
(`stopWithTask="false"`), because Android freezes an ordinary background
process within seconds and a twenty-minute recording is minutes of solid CPU.
Queueing needs the app to be on screen, since Android refuses to start a
foreground service for an app that is not; a request made from the background
waits in the queue until the app is next opened.

The Whisper native library is VoiceTranscription's
`voice-transcription-android` crate (whisper.cpp), shipped for arm64 only in
`app/src/main/jniLibs/arm64-v8a/` together with `libc++_shared.so`; see
VoiceTranscription's README, "Build Android Bindings", to rebuild it and
regenerate `app/src/main/java/uniffi/voice_transcription/voice_transcription.kt`.

**Tuned numbers** — the ones chosen by looking at a screen rather than
derived from anything, such as how much larger the large interface is and how
much smaller the time of day is drawn than the date — are gathered in
`util/MagicNumbers.kt` as `object Magic`, and referred to from wherever they
are used. A number fixed by something outside the application (the 44 bytes
of a WAV header, Whisper's 16 kHz) is not one of these and stays beside the
code that depends on it.

**The five transcription flags** are `data/TranscriptionFlags.kt`. They live
in the transcription's `state` field — the name in the database and in the
sync protocol, kept as it is because every synced device knows it — and
everything above the database calls them flags. The wording is printed in
both user manuals and in the desktop application, and the two applications
must agree on the words exactly: `app/src/test/resources/transcription_flags_contract.json`
holds the agreed cases and is read by
`TranscriptionFlagsContractTest` here and by
`tests/sync/test_transcription_flags_contract.py` in VOICE, whose copy of the
file must be byte for byte the same. VOICE also sends each of those fields
through a real sync between two installations
(`tests/sync/test_transcription_flags_sync.py`).

**One player.** `AudioPlayerManager.shared(context)` is the single player of
the application, made on first use and never released: playback started in
the notes list survives opening the Note and visiting its Tags screen.
`adoptAudioFiles` hands a screen's list of files to the running player
without interrupting it when one of them is the file already playing.

**Tag colours** are `data/TagColours.kt`: the default is the first six
characters of the MD5 of the Tag's *name*, so every device agrees without
storing anything; a chosen colour lives in the synced settings under
`tag_color.<name>`.

**Sharing** is `util/NoteSharing.kt` (what a Note offers, what is sent) plus
the `FileProvider` declared in the manifest as `${applicationId}.shared`
(`res/xml/shared_files.xml`). A recording is handed over as a `content://`
URI granted for one action; a `file://` path is refused by Android.

**Features under trial** are behind `BuildConfig.DEV_FEATURES`, which is true
in the debug build and false in a release, so the code is dropped from a
release build entirely. At present: holding a Note in the list and dragging to
select several (`detectDragGesturesAfterLongPress`, since a plain drag is the
list's own scroll), transcribing every Recording as it is saved
(Settings → Recorder), and editing a Transcription's text from its ⓘ
dialogue. Gate a new one the same way rather than hiding it behind a setting
that a release build would still carry.

**Unit tests and Android's stub classes.** `testOptions.unitTests.isReturnDefaultValues`
is on, so an Android class that is only a stub in a JVM test answers with a
default instead of throwing. That is what lets the tests exercise error paths
that log, but it also means `org.json` would silently parse nothing — so the
tests depend on the real `org.json:json` , which takes precedence over the
stub.

**Settings** all live in one `SharedPreferences` file, `voice_settings`,
written by `UiPreferences`, `RecorderPreferences`, `TranscriptionPreferences`
and `PlaybackPreferences`. A unit test checks that no two of them claim the
same key.

## Tests

The application's logic is covered by JVM unit tests — no device or emulator
is needed, and the whole suite runs in a few seconds:

```bash
JAVA_HOME=/usr/lib/jvm/java-21-openjdk-amd64 ./gradlew testDebugUnitTest
```

The report is written to
`app/build/reports/tests/testDebugUnitTest/index.html`, and the raw results
to `app/build/test-results/testDebugUnitTest/`.

What is covered:

| Area | What is checked |
|------|-----------------|
| Settings | Defaults, clamping, unreadable values, and that the five classes sharing `voice_settings` never claim the same key |
| Date and time | The stamp's own timezone, the twelve preset formats against what they render, the custom pattern, the `-N` days-ago token, a broken pattern falling back |
| Tag hierarchy | Paths, depths, descendants, collapsing, and a parent chain that loops — which two phones syncing can produce |
| Search | The `tag:` and `is:marked` terms the buttons add to and remove from the query |
| Notes list | The lines a row shows, the notes either side of the one open, and whether leaving an empty note deletes it |
| Recordings | The WAV header byte by byte, the waveform bars, playback speed and its slider, what happens when a call arrives |
| Transcription | The language catalogue, the model catalogue and part-downloaded model files, the queue's own description of each job, the state tags of a transcription |
| Audio conversion | The resampler at every rate the phone produces, clipping, and which files need no conversion |
| Critical log | Entries, rotation at a megabyte, and a log that cannot be written |
| Screens | That a control does what it looks like it does: the chevron on a note's row opens that row's section, wherever the row is drawn (see *Testing a screen* below) |

Tests for anything touching text are written with Hebrew, since that is what
the application is used for.

### Testing a screen

The screens are written in Jetpack Compose, and a Compose screen can be put on
trial without a phone: **Robolectric** supplies Android's own framework classes
to the JVM, and **`androidx.compose.ui:ui-test-junit4`** hosts a composable,
presses it and reads what it drew. These tests live with the other unit tests in
`app/src/test/` and run in the same command, so nothing has to be remembered.

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
`assertTextEquals` read the result. Recomposition is driven for you: after a
press, the assertions see the new state.

`app/src/test/resources/robolectric.properties` settles three things for every
such test, with the reasoning in the file: which Android version they run as
(35, the one the application is built against), that Compose draws for real
(`graphicsMode=NATIVE`), and that the application under test is a plain
`Application` rather than `VoiceApplication` — which would load
`libvoicecore.so`, built for the phone's processor and unloadable here. A test
that really needs the whole application can override that with
`@Config(application = VoiceApplication::class)`.

**What this lane costs.** Robolectric downloads one Android image, about
190 MB, into `~/.m2/repository/org/robolectric`, once. The eight Compose tests
add about eleven seconds to a suite that otherwise takes three.

**The other lane: on the phone.** `app/src/androidTest/` holds instrumented
tests, which run on a connected device with the real framework, the real
database and the real native library:

```bash
adb devices    # the phone must be connected
JAVA_HOME=/usr/lib/jvm/java-21-openjdk-amd64 ./gradlew connectedDebugAndroidTest
```

The same Compose test API is used in both, so a test can be moved between them.
Which lane a test belongs in:

| Put it in `src/test` (JVM) | Put it in `src/androidTest` (phone) |
|---|---|
| A control that does nothing, a gesture reaching the wrong handler, a row drawing the wrong thing | Anything needing `libvoicecore.so`, the real database, a real recording, or the microphone |
| Anything that should run on every change | Anything that should run before a release |

A JVM test is worth more than an instrumented one of the same thing, because it
runs whether or not a phone is plugged in — an instrumented test that nobody can
run rots: `TagTreeItemTest` had not compiled since timestamps became `Stamp`, and
nothing said so for months.

## Project structure

```
VoiceAndroid/
├── app/
│   └── src/main/
│       ├── java/com/dotancohen/voiceandroid/
│       │   ├── audio/         # Player, recorder, microphone and playback preferences
│       │   ├── data/          # Repository and data models
│       │   ├── transcription/ # Whisper on the phone: models, WAV conversion, job runner, service
│       │   ├── ui/            # Compose UI screens
│       │   ├── viewmodel/     # ViewModels
│       │   └── MainActivity.kt
│       ├── jniLibs/           # Native Rust libraries
│       └── res/               # Android resources
│   └── src/test/              # JVM unit tests (see Tests, above)
├── submodules/
│   └── voicecore/             # Rust core library
└── gradle/
```

`README.md` beside this file says what the application is; `USER_MANUAL.md`
documents it in use.
