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
            // Architectures to build for
            abiFilters += listOf("arm64-v8a", "armeabi-v7a", "x86_64", "x86")
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    kotlinOptions {
        jvmTarget = "17"
    }

    buildFeatures {
        compose = true
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
    implementation(libs.androidx.navigation.compose)
    implementation(libs.androidx.datastore.preferences)

    // JNA for UniFFI bindings
    implementation("net.java.dev.jna:jna:5.14.0@aar")

    debugImplementation(libs.androidx.ui.tooling)
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
        val rustDir = File(projectDir, "submodules/voice-core")
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
                    "--no-default-features", "--features", "uniffi"
                )
            }

            // Copy the library
            val libPath = File(rustDir, "target/$rustTarget/release/libvoice_core.so")
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
        val rustDir = File(projectDir, "submodules/voice-core")
        val bindingsDir = File(projectDir, "app/src/main/java/com/dotancohen/voiceandroid/rust")

        bindingsDir.mkdirs()

        exec {
            workingDir = rustDir
            commandLine(
                "cargo", "run", "--release",
                "--no-default-features", "--features", "uniffi",
                "--bin", "uniffi-bindgen",
                "generate",
                "--library", "target/aarch64-linux-android/release/libvoice_core.so",
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
