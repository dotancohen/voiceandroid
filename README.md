# VoiceAndroid

Android client for the [Voice](https://github.com/dotancohen/voice) note-taking application. Syncs with a Voice server to retrieve and display notes.

## Features

- View notes synced from a Voice server
- Configure sync server settings
- Manual sync with the server
- Record voice notes as Opus (128 kb/s, 48 kHz, `.ogg`), AAC (`.m4a`) or 16 kHz WAV (Settings → Recorder)
- Play recordings at 0.5× to 3× with a slider and one-tap presets under the waveform (pitch unchanged)
- Transcribe recordings on the phone with Whisper, no network needed (Settings → Transcription)
- Material Design 3 / Material You theming
- Follows system light/dark theme

## Requirements

- Android 10 (API 29) or higher
- Voice sync server running and accessible

## Permissions

- MANAGE_EXTERNAL_STORAGE - Grants full filesystem access, needed to store audio files in a user-accessible location for integrating with additional tools.

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

### Build Steps

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
cp submodules/voicecore/target/aarch64-linux-android/release/libvoicecore.so app/src/main/jniLibs/arm64-v8a/
cp submodules/voicecore/target/armv7-linux-androideabi/release/libvoicecore.so app/src/main/jniLibs/armeabi-v7a/
cp submodules/voicecore/target/x86_64-linux-android/release/libvoicecore.so app/src/main/jniLibs/x86_64/
cp submodules/voicecore/target/i686-linux-android/release/libvoicecore.so app/src/main/jniLibs/x86/
```

4. Generate Kotlin bindings (if not already generated):
```bash
cd submodules/voicecore
cargo build --release --features uniffi
./target/release/uniffi-bindgen generate \
    --library target/release/libvoicecore.so \
    --language kotlin \
    --out-dir ../../app/src/main/java
```

5. Build the APK:
```bash
./gradlew assembleDebug
```

The APK will be at `app/build/outputs/apk/debug/app-debug.apk`

## Configuration

In the app's Settings screen, configure:

- **Server URL**: The URL of your Voice sync server (e.g., `https://myserver.com:8384`)
- **Server Peer ID**: The 32-character hex device ID of the server
- **Device Name**: A friendly name for this device
- **Device ID**: Auto-generated or paste an existing ID to link with another installation

## On-device transcription

Settings → Transcription lists the Whisper models the app can download (ggml files from
Hugging Face, kept in the app's private storage and deleted with the app):

| Model | Size | Notes |
|-------|------|-------|
| Whisper large-v3, 5-bit | 1.1 GB | Recommended. Hebrew, English, Arabic, Russian and 95 more |
| ivrit.ai large-v3-turbo | 1.6 GB | Fine-tuned on Hebrew; the most accurate for Hebrew; Hebrew only |
| ivrit.ai large-v3 | 3.1 GB | Full-size Hebrew fine-tune; needs about 4 GB of free memory |
| Whisper large-v3, full | 3.1 GB | Needs about 4 GB of free memory |
| Whisper large-v3-turbo, 5-bit | 0.6 GB | Several times faster, a little less accurate |
| Whisper medium, 5-bit | 0.5 GB | Small and fast, weaker on Hebrew |

Pick a language (Hebrew by default) and *Beam search* (accurate) or *Greedy* (fast), then open a
note and tap **Transcribe on device** under its player. The work runs in a foreground service with
a progress notification, so it continues with the screen off. The result is a normal transcription
record (service `local_whisper`) and syncs to every other device.

The native library is VoiceTranscription's `voice-transcription-android` crate (whisper.cpp),
shipped for arm64 only in `app/src/main/jniLibs/arm64-v8a/` together with `libc++_shared.so`;
see VoiceTranscription's README, "Build Android Bindings", to rebuild it and regenerate
`app/src/main/java/uniffi/voice_transcription/voice_transcription.kt`.

## Architecture

- **Kotlin + Jetpack Compose**: Modern Android UI toolkit
- **Rust Core (via UniFFI)**: Cross-platform business logic shared with desktop
- **MVVM**: Clean architecture with ViewModels and Repository pattern

## Project Structure

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
├── submodules/
│   └── voicecore/             # Rust core library
└── gradle/
```

## License

GPL version 3.0 or above

