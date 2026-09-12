plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
}

android {
    namespace = "com.dotancohen.voiceandroid"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.dotancohen.voiceandroid"
        minSdk = 29
        targetSdk = 35
        versionCode = 1
        versionName = "1.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"

        ndk {
            // Architectures to build for (override with -PtargetAbi=arm64-v8a)
            val targetAbi = project.findProperty("targetAbi") as String?
            if (targetAbi != null) {
                abiFilters += listOf(targetAbi)
            } else {
                abiFilters += listOf("arm64-v8a", "armeabi-v7a", "x86_64", "x86")
            }
        }
    }

    buildTypes {
        debug {
            // Features still being tried out. They are in the debug build
            // only, so an unfinished idea cannot reach a release, and the
            // code that reads this constant is dropped from a release build
            // entirely.
            buildConfigField("boolean", "DEV_FEATURES", "true")
        }
        release {
            isMinifyEnabled = false
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
            buildConfigField("boolean", "DEV_FEATURES", "false")
        }

        // Instrumented tests run as a different application, so that no test
        // run can ever touch the notes and recordings on a real phone.
        //
        // On 2026-09-12 `connectedDebugAndroidTest` replaced the debug app on the
        // owner's phone; Android deletes an app's private and external data when
        // the app is replaced, and a week of his work was destroyed. With a
        // separate application id the worst an instrumented run can do is
        // destroy its own empty sandbox.
        create("uitest") {
            initWith(getByName("debug"))
            applicationIdSuffix = ".uitest"
            versionNameSuffix = "-uitest"
            isDebuggable = true
            matchingFallbacks += listOf("debug")
        }
    }

    // `connectedAndroidTest` builds and installs this build type, not debug.
    testBuildType = "uitest"

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    kotlinOptions {
        jvmTarget = "17"
    }

    buildFeatures {
        compose = true
        buildConfig = true
    }

    testOptions {
        unitTests {
            // Android's own classes are empty stubs in a JVM test, and calling
            // one throws by default. The code under test logs through
            // android.util.Log on its error paths, and those paths are exactly
            // what the tests exercise, so the stubs return a default instead.
            //
            // A test that wants the real framework asks for it with
            // @RunWith(RobolectricTestRunner::class); that is how the Compose
            // screens are tested without a phone. The two live together: a
            // Robolectric test gets real Android classes, every other test gets
            // the stubs.
            isReturnDefaultValues = true
            // Robolectric reads the application's resources and manifest, so
            // they have to be packaged for the JVM tests as well.
            isIncludeAndroidResources = true
        }
    }

    // Include native libraries from the jniLibs directory
    sourceSets {
        getByName("main") {
            jniLibs.srcDirs("src/main/jniLibs")
        }
    }
}

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.activity.compose)
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.ui)
    implementation(libs.androidx.ui.graphics)
    implementation(libs.androidx.ui.tooling.preview)
    implementation(libs.androidx.material3)
    implementation("androidx.compose.material:material-icons-extended")
    implementation(libs.androidx.navigation.compose)
    implementation(libs.androidx.datastore.preferences)

    // Media3/ExoPlayer for audio playback
    implementation(libs.androidx.media3.exoplayer)
    implementation(libs.androidx.media3.ui)
    implementation(libs.androidx.media3.common)

    // JNA for UniFFI bindings
    implementation("net.java.dev.jna:jna:5.14.0@aar")

    // DocumentFile for accessing files via SAF
    implementation("androidx.documentfile:documentfile:1.0.1")

    debugImplementation(libs.androidx.ui.tooling)
    debugImplementation("androidx.compose.ui:ui-test-manifest")

    // Test dependencies
    testImplementation(libs.junit)
    // A real org.json for the JVM tests. Android's own is a stub that, with
    // isReturnDefaultValues on, answers 0 and null instead of parsing, which
    // would let a test read a fixture file and quietly see nothing in it.
    testImplementation("org.json:json:20250107")

    // Compose screens, tested on the JVM: Robolectric supplies the Android
    // framework, ui-test-junit4 hosts a composable and drives it (see
    // DEVELOPMENT.md "Testing a screen"). ui-test-manifest provides the empty
    // activity the test host needs, and is already a debug dependency below.
    testImplementation(libs.robolectric)
    testImplementation(libs.androidx.test.core)
    testImplementation(platform(libs.androidx.compose.bom))
    testImplementation(libs.androidx.ui.test.junit4)
    testImplementation(libs.androidx.ui.test.manifest)
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.espresso.core)
    androidTestImplementation(platform(libs.androidx.compose.bom))
    androidTestImplementation(libs.androidx.ui.test.junit4)
}

// Task to build Rust library for all Android targets
tasks.register("buildRust") {
    group = "rust"
    description = "Build Rust library for Android"

    doLast {
        val targets = mapOf(
            "aarch64-linux-android" to "arm64-v8a",
            "armv7-linux-androideabi" to "armeabi-v7a",
            "x86_64-linux-android" to "x86_64",
            "i686-linux-android" to "x86"
        )

        val ndkHome = System.getenv("ANDROID_NDK_HOME")
            ?: "${System.getenv("ANDROID_HOME")}/ndk/${android.ndkVersion}"

        val projectDir = project.rootDir
        val rustDir = File(projectDir, "submodules/voicecore")
        val jniLibsDir = File(projectDir, "app/src/main/jniLibs")

        targets.forEach { (rustTarget, abiFolder) ->
            println("Building for $rustTarget...")

            val abiDir = File(jniLibsDir, abiFolder)
            abiDir.mkdirs()

            exec {
                workingDir = rustDir
                environment("ANDROID_NDK_HOME", ndkHome)
                commandLine(
                    "cargo", "ndk",
                    "-t", rustTarget,
                    "--platform", "29",
                    "build", "--release",
                    "--features", "uniffi"
                )
            }

            // Copy the library
            val libPath = File(rustDir, "target/$rustTarget/release/libvoicecore.so")
            if (libPath.exists()) {
                copy {
                    from(libPath)
                    into(abiDir)
                }
            }
        }
    }
}

// Task to generate Kotlin bindings from Rust
tasks.register("generateKotlinBindings") {
    group = "rust"
    description = "Generate Kotlin bindings from UniFFI"

    dependsOn("buildRust")

    doLast {
        val projectDir = project.rootDir
        val rustDir = File(projectDir, "submodules/voicecore")
        val bindingsDir = File(projectDir, "app/src/main/java")

        bindingsDir.mkdirs()

        exec {
            workingDir = rustDir
            commandLine(
                "cargo", "run", "--release",
                "--features", "uniffi",
                "--bin", "uniffi-bindgen",
                "generate",
                "--library", "target/aarch64-linux-android/release/libvoicecore.so",
                "--language", "kotlin",
                "--out-dir", bindingsDir.absolutePath
            )
        }
    }
}

// Make preBuild depend on Rust compilation
tasks.named("preBuild") {
    // Uncomment when ready to build Rust
    // dependsOn("buildRust")
}
