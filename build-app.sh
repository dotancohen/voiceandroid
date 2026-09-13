#!/bin/bash
# Stop at the first failure: without this the script ends with exit 0
# after a failed compile, because the last echo succeeds.
set -e

# The Voice Family builds every Rust crate into one shared directory; see
# VoiceFamily/.cargo/config.toml and TECHNICAL-DECISIONS.md §7.4.
export CARGO_TARGET=/home/dotancohen/Projects/VoiceFamily/.cargo-target


echo "Regenerate Kotlin bindings (only if Rust UniFFI interface changed)"
cd /home/dotancohen/Projects/VoiceFamily/VoiceAndroid
cd submodules/voicecore
cargo build --release --features uniffi
"$CARGO_TARGET/release/uniffi-bindgen" generate --library "$CARGO_TARGET/release/libvoicecore.so" --language kotlin --out-dir /tmp/kotlin-bindings
cp /tmp/kotlin-bindings/uniffi/voicecore/voicecore.kt /home/dotancohen/Projects/VoiceFamily/VoiceAndroid/app/src/main/java/uniffi/voicecore/voicecore.kt

echo "Build native library for Android (use absolute path!)"
ANDROID_NDK_HOME=/home/dotancohen/Android/Sdk/ndk/29.0.14206865 cargo ndk -t arm64-v8a -o /home/dotancohen/Projects/VoiceFamily/VoiceAndroid/app/src/main/jniLibs build --release --features uniffi

echo "Return to VoiceAndroid and build APK"
cd /home/dotancohen/Projects/VoiceFamily/VoiceAndroid
# Java 21. Android Studio's bundled JBR was updated to Java 25, which this
# Gradle cannot compile the build scripts with: it fails with the bare
# message "25.0.3". Use the system JDK 21 instead.
JAVA_HOME=/usr/lib/jvm/java-21-openjdk-amd64 ./gradlew assembleDebug

echo "Run the JVM unit tests"
JAVA_HOME=/usr/lib/jvm/java-21-openjdk-amd64 ./gradlew testDebugUnitTest

echo "Install on device (optional)"
#adb shell am force-stop com.dotancohen.voiceandroid # Kill the app first to avoid corruption
#adb install app/build/outputs/apk/debug/app-debug.apk

