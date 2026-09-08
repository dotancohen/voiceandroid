#!/bin/bash

echo "Regenerate Kotlin bindings (only if Rust UniFFI interface changed)"
cd /home/dotancohen/Projects/VoiceAndroid
cd submodules/voicecore
cargo build --release --features uniffi
./target/release/uniffi-bindgen generate --library target/release/libvoicecore.so --language kotlin --out-dir /tmp/kotlin-bindings
cp /tmp/kotlin-bindings/uniffi/voicecore/voicecore.kt /home/dotancohen/Projects/VoiceAndroid/app/src/main/java/uniffi/voicecore/voicecore.kt

echo "Build native library for Android (use absolute path!)"
ANDROID_NDK_HOME=/home/dotancohen/Android/Sdk/ndk/29.0.14206865 cargo ndk -t arm64-v8a -o /home/dotancohen/Projects/VoiceAndroid/app/src/main/jniLibs build --release --features uniffi

echo "Return to VoiceAndroid and build APK"
cd /home/dotancohen/Projects/VoiceAndroid
JAVA_HOME=/snap/android-studio/current/jbr ./gradlew assembleDebug

echo "Install on device (optional)"
#adb shell am force-stop com.dotancohen.voiceandroid # Kill the app first to avoid corruption
#adb install app/build/outputs/apk/debug/app-debug.apk

