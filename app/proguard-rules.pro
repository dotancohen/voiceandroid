# Add project specific ProGuard rules here.
# You can control the set of applied configuration files using the
# proguardFiles setting in build.gradle.kts.
#
# For more details, see
#   http://developer.android.com/guide/developing/tools/proguard.html

# Keep UniFFI generated bindings
-keep class uniffi.** { *; }
-keep class com.dotancohen.voiceandroid.rust.** { *; }

# Keep native method names
-keepclasseswithmembernames class * {
    native <methods>;
}
