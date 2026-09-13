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
            // The phones that use Voice are 64-bit ARM (the Galaxy S24 Ultra
            // and the Galaxy A12), so only arm64-v8a is packed: the core, the
            // transcription library and FFmpeg's decoders are built for it
            // alone. Another architecture is asked for with -PtargetAbi=...,
            // and its libraries must then be built for it first.
            val targetAbi = project.findProperty("targetAbi") as String?
            abiFilters += listOf(targetAbi ?: "arm64-v8a")
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
            // The core itself, built for this computer, for the tests that
            // call it through its bindings (see hostCoreForTests below)
            all {
                it.systemProperty(
                    "uniffi.component.voicecore.libraryOverride",
                    layout.buildDirectory.file("host-core/libvoicecore.so").get().asFile.absolutePath
                )
                it.dependsOn("hostCoreForTests")
                // An S3 server on this computer for the storage tests (moto, Apache 2.0)
                it.systemProperty(
                    "voice.test.motoServer",
                    layout.buildDirectory.file("s3-test-venv/bin/moto_server").get().asFile.absolutePath
                )
                it.dependsOn("s3ServerForTests")
            }
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
    // The core icon set only: the few extended icons the app uses are copied
    // into app/src/main/java/androidx/compose/material/icons, because the
    // extended set is thousands of classes the application never draws
    implementation("androidx.compose.material:material-icons-core")
    implementation(libs.androidx.navigation.compose)
    implementation(libs.androidx.datastore.preferences)

    // Media3/ExoPlayer for audio playback
    implementation(libs.androidx.media3.exoplayer)
    implementation(libs.androidx.media3.ui)
    implementation(libs.androidx.media3.common)
    implementation(libs.jellyfin.media3.ffmpeg.decoder)

    // JNA for UniFFI bindings
    implementation("net.java.dev.jna:jna:5.14.0@aar")

    // DocumentFile for accessing files via SAF
    implementation("androidx.documentfile:documentfile:1.0.1")
    // The code shown and read at pairing (Stage 9)
    implementation(libs.zxing.core)
    implementation(libs.androidx.camera.core)
    implementation(libs.androidx.camera.camera2)
    implementation(libs.androidx.camera.lifecycle)
    implementation(libs.androidx.camera.view)

    debugImplementation(libs.androidx.ui.tooling)
    debugImplementation("androidx.compose.ui:ui-test-manifest")

    // Test dependencies
    testImplementation(libs.junit)
    // A real org.json for the JVM tests. Android's own is a stub that, with
    // isReturnDefaultValues on, answers 0 and null instead of parsing, which
    // would let a test read a fixture file and quietly see nothing in it.
    testImplementation("org.json:json:20250107")
    // JNA's desktop jar, whose native part loads the core on this computer in JVM tests
    testImplementation("net.java.dev.jna:jna:5.14.0")

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
        // The one architecture packed into the application (see abiFilters)
        val targets = mapOf(
            "aarch64-linux-android" to "arm64-v8a"
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

// The core built for this computer, with the phone's bindings, for the JVM
// tests that call it. Cargo's output directory is shared by the whole family
// (.cargo/config.toml), and the desktop's Python module is also named
// libvoicecore.so there, so the library is copied to this build's own
// directory the moment it is built.
tasks.register("hostCoreForTests") {
    group = "rust"
    description = "Build the core for this computer with the phone's bindings, for the JVM tests"
    val core = File(rootDir, "submodules/voicecore")
    val target = File(rootDir, "../.cargo-target/release/libvoicecore.so")
    val copied = layout.buildDirectory.file("host-core/libvoicecore.so")
    doLast {
        exec {
            workingDir = core
            commandLine("cargo", "build", "--release", "--features", "uniffi")
        }
        copy {
            from(target)
            into(copied.get().asFile.parentFile)
        }
    }
}

// moto's S3 server, installed once into this build's own Python environment,
// for the storage tests: the same server the desktop's tests use
// (Voice/tests/local_s3.py). The first build needs the network to install it.
tasks.register("s3ServerForTests") {
    group = "verification"
    description = "Install moto's S3 server into the build directory for the storage tests"
    val venv = layout.buildDirectory.dir("s3-test-venv")
    doLast {
        val dir = venv.get().asFile
        if (!File(dir, "bin/moto_server").exists()) {
            exec { commandLine("python3", "-m", "venv", dir.absolutePath) }
            exec { commandLine(File(dir, "bin/pip").absolutePath, "install", "--quiet", "moto[server]==5.2.3") }
        }
    }
}
