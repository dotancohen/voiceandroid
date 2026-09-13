# Claude Code instructions for Voice for Android

Read `../CLAUDE.md` first: the rules there (tests, naming, **the owner's data**,
violation logging, data loss, the three checkouts of the core, documentation)
apply here too. `MAINTAINER-GUIDE.md` in this directory is a map of this
project's code; `DEVELOPMENT.md` says how it is built and tested.

## The owner's phone

The phone connected to this computer is the owner's own, with his notes and
recordings on it. Before anything that installs, replaces, uninstalls or clears
the application — `adb install`, `adb uninstall`, `pm clear`, any Gradle task
whose name contains `connected` or `install` — the owner must have asked for
that exact action in that message, and `tools/voice-phone-backup` must have run
alone, unpiped, and printed `Backup verified.` Touch nothing on the phone that
is not Voice. Instrumented tests install as `com.dotancohen.voiceandroid.uitest`
(`../TECHNICAL-DECISIONS.md` 7.6).

## Architecture

The application uses **Jetpack Compose** with the MVVM pattern:

- view models in `viewmodel/`
- composable screens in `ui/screens/`, components in `ui/components/`
- the repository `data/VoiceRepository.kt`, which calls the core through the
  UniFFI bindings in `app/src/main/java/uniffi/voicecore/voicecore.kt`

The interface is to be replaced by another toolkit
(`../PLAN-ACCOUNTS-PAIRING-SYNC.md`), so screens stay thin: logic goes into view
models and plain classes with no toolkit import.

**Times and timezones:** timestamps are Unix seconds; beside each user-visible
one the core stores `<stamp>_offset` (seconds east of UTC where it happened) and
`<stamp>_zone` (IANA name). A reader renders the instant at that offset, so a
note written at 15:20 in Jerusalem still reads 15:20 in New York; rows with no
offset fall back to the reader's zone. The phone reports its zone
(`VoiceRepository.reportTimeZone`), because the core cannot see Android's
settings. Locale and the 12- or 24-hour preference are never stored:
`util/Stamps.kt` formats with the phone's own. Ordering and merging use the
instant alone. See `../SYNC_SPECIFICATION.md` 3.4.

**Recorder:** `audio/VoiceRecorder.kt` is the single recorder and lives outside
any screen, because leaving the application, going home or locking the phone
must not stop a recording; `audio/RecordingService.kt` is the foreground service
(type `microphone`) that keeps the process alive and shows the elapsed time in a
notification. `ui/components/AudioRecorderWidget.kt`, shown in
`ui/screens/NoteDetailScreen.kt`, only collects `VoiceRecorder`'s flows and
presses its buttons, so the screen can come and go. During a telephone call
Android gives the microphone to the telephone, so Settings → Recorder chooses
between keeping the silence and pausing until the call ends
(`RecorderPreferences.duringCall`, watched through `AudioManager.mode` in the
recorder's ticker). `RecorderPreferences.kt` also holds the selected microphone
and its friendly names, the default action of the New button, the recording
format, and whether the recording screen starts recording as it opens.
`audio/MicLevelMeter.kt` is the level stream for the microphone test. Saving a
recording imports the file the recorder closed, so a recording is a note with an
attachment exactly like an imported file, written to the audio directory
(`Recordings/Voice` once all-files access is granted, FILE-16) under its
`disk_name` (FILE-15). Formats: Opus 128 kb/s 48 kHz in Ogg (`.ogg`, the
default), Opus 32 kb/s for speech (`.ogg`), AAC 96 kb/s (`.m4a`) through
MediaRecorder, and 16 kHz mono 16-bit WAV (`.wav`) through
`audio/WavRecorder.kt` (AudioRecord, Whisper's native input). The notes list's
`+` button taps the default action and long-presses for the menu.

**Playback speed:** `audio/PlaybackPreferences.kt` (one speed for every player,
0.5× to 3×), `ui/components/PlaybackSpeedControl.kt` (a Canvas slider with ½, 1
and 2 marks that snap, under the waveform in both `AudioPlayerWidget` and the
list's `CompactAudioPlayer`). `AudioPlayerManager.setPlaybackSpeed` sets
ExoPlayer's `PlaybackParameters(speed, pitch = 1)` on the running player, which
stretches time without restarting. Formats the phone cannot decode play through
FFmpeg's decoders (FILE-21).

**The notes list** shows one line of the note and one line of the first
transcription of any recording on it, and skips whichever is missing rather than
leaving an empty row. The New button turns into a red record icon when a tap
would start a recording. In the list each recording is a small `🔊n` button at
the left of the row (`AttachmentChip` in `NotesScreen.kt`) that unfolds the
compact player; it carries a small transcribe icon when that recording already
has a transcription. The chip's contents are wrapped in
`CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Ltr)`:
in a right-to-left interface the bidirectional algorithm otherwise renders `🔊1`
as `1🔊` (the symbol is a neutral, the digit a European number). Video
attachments get their own symbol next to `AUDIO_SYMBOL`.

**Selecting several notes:** long-press a note in the list
(`NotesViewModel.selectedNoteIds`); while a selection exists the star becomes a
tick box, a tap toggles selection instead of opening the note, Back leaves the
selection, and a bar at the bottom offers Tag (`ui/components/MultiTagDialog.kt`,
a three-state box per tag: filled when every selected note has it, half when
only some, and tapping adds to those missing or removes from all), Delete (soft
delete of each note), Transcribe (queues every recording of the selected notes)
and Merge (`NotesViewModel.mergeSelected`; the oldest survives, and `merge_notes`
in the core moves tags and attachments and deletes the emptied notes).
Transcriptions always run one at a time; `OnDeviceTranscriber` holds the queue
and `TranscriptionService` shows "N waiting" in its notification. A bulk
transcription skips any recording that already has a finished transcription from
the chosen model (`NoteWithAudioFiles.transcribedModels`, filled from each row's
`service_arguments`); asking the same model again is deliberate and therefore
only possible in a note, where it asks for confirmation first.
`Transcription.isFinished` in `data/Note.kt` is what "already transcribed"
means: not `Pending...` and not `Error:`.

**Leaving a note:** the list keeps its scroll position (`rememberLazyListState`
hoisted in `NotesScreen`; item keys are note ids) and points out the row of the
note just left with two dots that travel from the middle of the row to its edges
(`ui/components/Spotlight.kt`; bright warm dots in the light theme, barely
lighter than the row in the dark one). How long that takes is Settings →
Advanced → Spotlight duration (`util/UiPreferences.kt`, 0 to 1 second, 0 turns
it off), a device setting that is not synced.

**On-device transcription (`transcription/`):** whisper.cpp through
VoiceTranscription's `voice-transcription-android` crate
(`jniLibs/arm64-v8a/libvoice_transcription_android.so` and `libc++_shared.so`,
Kotlin in `java/uniffi/voice_transcription/`; arm64 only). `WhisperModels.kt` is
the model catalogue (ggml files from Hugging Face, stored in
`files/whisper-models/`, downloaded with resume), `TranscriptionPreferences.kt`
the chosen model, language and beam size, `AudioToWav.kt` decodes any recording
with MediaCodec and resamples (windowed sinc) to 16 kHz mono WAV,
`OnDeviceTranscriber.kt` the queue and the job itself (creates a "Pending..."
transcription row, then stores content, segments JSON and service response
exactly like the desktop's `transcription_service.py`, service name
`local_whisper`), `TranscriptionService.kt` the foreground service that runs the
queue (type `mediaProcessing` on API 35 and later, `dataSync` on 34). The phone
transcribes at most ten minutes of a recording
(`Magic.TRANSCRIBE_MAX_SECONDS_ON_PHONE`, `../TECHNICAL-DECISIONS.md` 3.4).
Interface: Settings → Transcription (`TranscriptionSettingsScreen`), and a
transcribe icon at the left of every file in the note's player
(`AudioFileListItem`), which opens `ui/components/TranscribeDialog.kt`. The note
screen shows only the transcriptions of the file the player is on
(`onCurrentFileChanged`); `TranscriptionCard` carries two buttons under the text:
copy, and `TranscriptionDetailsDialog`, which holds when, service, model and
language, how the transcription ran (elapsed, CPU, cores busy, peak memory, model
size, the phone — written by `OnDeviceTranscriber`'s `WorkWatch` into
`service_response.performance`), the main-transcription mark, and the five flags
of `data/TranscriptionFlags.kt` (the same five, in the same words, as the
desktop's `src/core/transcription_flags.py`). Features under trial are behind
`BuildConfig.DEV_FEATURES` (debug builds only). Rebuilding the whisper library:
`../VoiceTranscription/README.md`, "Build Android Bindings".

**ADB automation (debug builds):**
`app/src/debug/.../automation/AdbCommandReceiver.kt` exposes user actions as
broadcast intents; `tools/voice-adb` wraps it. When adding a user-facing action
to the application, add the matching action to the receiver, its intent filter
in `app/src/debug/AndroidManifest.xml`, and a line in `voice-adb`, so the manual
test plans in `../test-plans/` can script it.

**Interface rules:**

- **Never use floating action buttons.** They hover over content and hide what
  is under them (the last item in a list, an open menu). Action buttons go in
  the top app bar's `actions` slot (`../TECHNICAL-DECISIONS.md` 5.3).

## Building

`build-app.sh` runs the steps below in order and stops at the first failure.
They are written out here so a single step can be repeated.

The APK packs only `arm64-v8a` (`abiFilters` in `app/build.gradle.kts`): the
owner's Samsung S24 Ultra is arm64, and the whisper library exists for arm64
only. His second phone, a Samsung A12 (SM-A12F), is assumed to run a 64-bit
system and has not been checked; `adb shell getprop ro.product.cpu.abilist`
must list `arm64-v8a` before the APK can install there. If it does not, the
build needs `armeabi-v7a` added (the core builds for it; transcription would
not exist there).

1. **Regenerate the Kotlin bindings** (when a UniFFI-exposed function was added
   or changed):

```bash
export CARGO_TARGET=/home/dotancohen/Projects/VoiceFamily/.cargo-target
cd /home/dotancohen/Projects/VoiceFamily/VoiceAndroid/submodules/voicecore
cargo build --release --features uniffi
"$CARGO_TARGET/release/uniffi-bindgen" generate --library "$CARGO_TARGET/release/libvoicecore.so" --language kotlin --out-dir /tmp/kotlin-bindings
cp /tmp/kotlin-bindings/uniffi/voicecore/voicecore.kt /home/dotancohen/Projects/VoiceFamily/VoiceAndroid/app/src/main/java/uniffi/voicecore/voicecore.kt
```

2. **Build the native library**, always with an absolute output path:

```bash
cd /home/dotancohen/Projects/VoiceFamily/VoiceAndroid/submodules/voicecore
ANDROID_NDK_HOME=/home/dotancohen/Android/Sdk/ndk/29.0.14206865 cargo ndk -t arm64-v8a -o /home/dotancohen/Projects/VoiceFamily/VoiceAndroid/app/src/main/jniLibs build --release --features uniffi
```

3. **Build the APK and run the JVM tests**:

```bash
cd /home/dotancohen/Projects/VoiceFamily/VoiceAndroid
JAVA_HOME=/usr/lib/jvm/java-21-openjdk-amd64 ./gradlew assembleDebug
JAVA_HOME=/usr/lib/jvm/java-21-openjdk-amd64 ./gradlew testDebugUnitTest
```

Use the system JDK 21, not Android Studio's bundled JBR. As of 2026-09-11 that
JBR is JDK 25, and this Gradle cannot parse its version string: every task fails
at once with `java.lang.IllegalArgumentException: 25.0.3`. Gradle needs about
2 GB of heap for the unit tests; do not run it at the same time as a heavy
desktop test run.

**WARNING: the shared build directory.** Every Rust crate in the family builds
into `VoiceFamily/.cargo-target` (`../TECHNICAL-DECISIONS.md` 7.4), and
`release/libvoicecore.so` is one path several builds write to. A
`maturin develop` for the desktop rebuilds the core **without** the `uniffi`
feature and replaces that file, after which `uniffi-bindgen` reads no interface
from it and silently generates nothing (it still exits 0). Cargo does not
notice, because its fingerprint says the featured build is current. So generate
the Kotlin bindings *before* running maturin, or force the build first:

```bash
cd /home/dotancohen/Projects/VoiceFamily/VoiceAndroid/submodules/voicecore
touch src/lib.rs && cargo build --release --features uniffi
```

Always check that a new function is in the generated file before copying it.

**WARNING: `cargo ndk -o` takes absolute paths only.** A relative path is
resolved from the Cargo workspace root, not the current directory, and the
library lands in the wrong place.

**A build that failed is reported as failed.** Read Gradle's `BUILD SUCCESSFUL`
or `BUILD FAILED` line; an exit status hidden behind a pipe is not evidence.

### Installing on a phone

Only when the owner asked for it in that message, and only after the backup
(above):

```bash
cd /home/dotancohen/Projects/VoiceFamily/VoiceAndroid
tools/voice-phone-backup
# read "Backup verified." and the listing, then, as a separate command:
adb install -r app/build/outputs/apk/debug/app-debug.apk
```

**Common crash: `UnsatisfiedLinkError: undefined symbol`.** The Kotlin bindings
call functions the native library does not have: the library was not rebuilt
after new Rust functions, it was copied to the wrong path, or the bindings were
regenerated and the library was not. Rebuild both, together, and check the
library is in `app/src/main/jniLibs/arm64-v8a/`. The desktop build and tests can
pass while the phone crashes, so this is easy to miss.

## Documentation

`README.md` says what the application is, `USER_MANUAL.md` how it is used,
screen by screen, `DEVELOPMENT.md` how it is built and tested, and
`MAINTAINER-GUIDE.md` where things are in the code. A change the user can see
goes into `USER_MANUAL.md` in the same change.
