plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
}

android {
    namespace = "com.tuxungram.android"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.tuxungram.android"
        minSdk = 29
        targetSdk = 35
        versionCode = 8
        versionName = "0.8.0"

        ndk {
            abiFilters += "arm64-v8a"
        }
        externalNativeBuild {
            cmake {
                cppFlags += "-std=c++17"
            }
        }

        // Telegram API credentials, injected at build time. CI Secrets
        // (TUXUNGRAM_API_ID / TUXUNGRAM_API_HASH) win when present.
        // The fallback is Telegram Desktop's publicly known pair, used
        // TEMPORARILY for development until the project's own api
        // application is approved; swapping means filling the Secrets,
        // nothing else in the app depends on these values.
        val apiId = System.getenv("TUXUNGRAM_API_ID") ?: "2040"
        val apiHash = System.getenv("TUXUNGRAM_API_HASH") ?: "b18441a1ff607e10a989891a5462e627"
        buildConfigField("int", "API_ID", apiId)
        buildConfigField("String", "API_HASH", "\"$apiHash\"")
    }

    // Release signing is injected by CI from repository secrets (the
    // keystore is restored to a temp path and passed via environment).
    // Without those variables the release build stays unsigned and local
    // debug builds are unaffected.
    signingConfigs {
        // Pinned TEST signing: without this, every CI runner generates
        // a fresh debug keystore and test builds can never be installed
        // over each other. This key is a throwaway committed on purpose
        // (standard debug alias/password); it is NOT the release key.
        create("testPin") {
            storeFile = rootProject.file("tools/test-signing.keystore")
            storePassword = "android"
            keyAlias = "androiddebugkey"
            keyPassword = "android"
        }
        if (System.getenv("TUXUNGRAM_KEYSTORE_PATH") != null) {
            create("release") {
                storeFile = file(System.getenv("TUXUNGRAM_KEYSTORE_PATH"))
                storePassword = System.getenv("TUXUNGRAM_STORE_PASSWORD")
                keyAlias = System.getenv("TUXUNGRAM_KEY_ALIAS")
                keyPassword = System.getenv("TUXUNGRAM_KEY_PASSWORD")
            }
        }
    }

    buildTypes {
        debug {
            signingConfig = signingConfigs.getByName("testPin")
        }
        release {
            isMinifyEnabled = false
            if (System.getenv("TUXUNGRAM_KEYSTORE_PATH") != null) {
                signingConfig = signingConfigs.getByName("release")
            }
        }
    }
    packaging {
        jniLibs {
            // libtdjson.so must be extracted to nativeLibraryDir.
            useLegacyPackaging = true
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
        buildConfig = true
    }
    externalNativeBuild {
        cmake {
            path = file("src/main/cpp/CMakeLists.txt")
            version = "3.22.1"
        }
    }
}

dependencies {
    val composeBom = platform("androidx.compose:compose-bom:2024.09.00")
    implementation(composeBom)
    implementation("androidx.core:core-ktx:1.13.1")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.8.1")
    implementation("androidx.activity:activity-compose:1.9.2")
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-graphics")
    implementation("androidx.compose.material3:material3")
    // Core Material icons only (the extended set is huge); enough for
    // settings / back / send / attach style icon buttons.
    implementation("androidx.compose.material:material-icons-core")
    // Fingerprint unlock for the app lock (v0.6.0, E2);
    // BiometricPrompt needs the host activity to be a
    // FragmentActivity, which MainActivity now is.
    implementation("androidx.biometric:biometric:1.1.0")
    debugImplementation("androidx.compose.ui:ui-tooling")
    implementation("androidx.compose.ui:ui-tooling-preview")
    testImplementation("junit:junit:4.13.2")
}
