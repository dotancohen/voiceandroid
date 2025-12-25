# VoiceAndroid

Android client for the [Voice](https://github.com/dotancohen/voice) note-taking application. Syncs with a Voice server to retrieve and display notes.

## Features

- View notes synced from a Voice server
- Configure sync server settings
- Manual sync with the server
- Material Design 3 / Material You theming
- Follows system light/dark theme

## Requirements

- Android 10 (API 29) or higher
- Voice sync server running and accessible

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
│       │   ├── data/          # Repository and data models
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

