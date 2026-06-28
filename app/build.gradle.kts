import java.io.FileInputStream
import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.ksp)
    alias(libs.plugins.hilt)
}

// Release signing is configured from a gitignored `keystore.properties` at the
// repo root (see docs/SHIPPING.md + scripts/release-keystore.ps1). When that
// file is absent (local dev, CI without secrets) the release build stays
// unsigned and debug builds are unaffected — nothing here fails.
val keystorePropsFile = rootProject.file("keystore.properties")
val keystoreProps = Properties().apply {
    if (keystorePropsFile.exists()) {
        FileInputStream(keystorePropsFile).use { load(it) }
    }
}

android {
    namespace = "com.sidequest"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.sidequest"
        minSdk = 26
        targetSdk = 35
        versionCode = 1
        versionName = "1.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    signingConfigs {
        if (keystorePropsFile.exists()) {
            create("release") {
                storeFile = file(keystoreProps.getProperty("storeFile"))
                storePassword = keystoreProps.getProperty("storePassword")
                keyAlias = keystoreProps.getProperty("keyAlias")
                keyPassword = keystoreProps.getProperty("keyPassword")
            }
        }
    }

    buildTypes {
        debug {
            // Local backend reached from the Android emulator. 10.0.2.2 is the
            // emulator's alias for the host machine's loopback (the Go server
            // listens on 127.0.0.1:8080 on the host). On a physical device,
            // replace with the host's LAN IP.
            buildConfigField(
                "String",
                "API_BASE_URL",
                "\"http://10.0.2.2:8080/\"",
            )
        }
        release {
            if (keystorePropsFile.exists()) {
                signingConfig = signingConfigs.getByName("release")
            }
            isMinifyEnabled = false
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro",
            )
            // Real deployed backend endpoint (replace before shipping).
            buildConfigField(
                "String",
                "API_BASE_URL",
                "\"https://api.sidequest.invalid/\"",
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
        buildConfig = true
    }

    testOptions {
        unitTests {
            isReturnDefaultValues = true
            // Robolectric needs access to merged Android resources/manifest to
            // stand up an Application context for the in-memory Room database.
            isIncludeAndroidResources = true
            all {
                // Kotest runs on the JUnit 5 platform for local unit tests; the
                // JUnit Vintage engine (added in test deps) runs the JUnit4
                // Robolectric runner on that same platform.
                it.useJUnitPlatform()
            }
        }
    }

    packaging {
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
        }
    }
}

dependencies {
    // Pure domain logic.
    implementation(project(":domain"))

    // AndroidX core + lifecycle.
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.activity.compose)

    // Compose (BOM-managed versions).
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.graphics)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.compose.material.icons.extended)
    implementation(libs.androidx.navigation.compose)
    implementation(libs.coil.compose)
    debugImplementation(libs.androidx.compose.ui.tooling)
    debugImplementation(libs.androidx.compose.ui.test.manifest)

    // Coroutines.
    implementation(libs.kotlinx.coroutines.android)

    // Hilt.
    implementation(libs.hilt.android)
    ksp(libs.hilt.compiler)
    implementation(libs.androidx.hilt.navigation.compose)
    implementation(libs.androidx.hilt.work)
    ksp(libs.androidx.hilt.compiler)

    // Room.
    implementation(libs.androidx.room.runtime)
    implementation(libs.androidx.room.ktx)
    ksp(libs.androidx.room.compiler)

    // WorkManager.
    implementation(libs.androidx.work.runtime.ktx)

    // DataStore Preferences (reminder settings persistence).
    implementation(libs.androidx.datastore.preferences)

    // Encrypted token storage (Jetpack Security): auth tokens are persisted in
    // EncryptedSharedPreferences (Req 13.3).
    implementation(libs.androidx.security.crypto)

    // Networking + serialization.
    implementation(libs.retrofit)
    implementation(libs.retrofit.kotlinx.serialization)
    implementation(libs.okhttp)
    implementation(libs.okhttp.logging.interceptor)
    implementation(libs.kotlinx.serialization.json)

    // Unit testing (Kotest + property testing).
    testImplementation(libs.kotest.runner.junit5)
    testImplementation(libs.kotest.assertions.core)
    testImplementation(libs.kotest.property)
    testImplementation(libs.mockk)
    testImplementation(libs.turbine)
    testImplementation(libs.kotlinx.coroutines.test)

    // MockWebServer stands up a fake Transcription Proxy for the JVM
    // integration test (pairs with the existing OkHttp dependency).
    testImplementation(libs.okhttp.mockwebserver)

    // Robolectric + AndroidX test runtime so Room runs as a JVM unit test
    // (no device/emulator). JUnit Vintage runs the JUnit4-based Robolectric
    // runner on the JUnit Platform that Kotest configures.
    testImplementation(libs.robolectric)
    testImplementation(libs.androidx.test.core)
    testImplementation(libs.androidx.test.ext.junit)
    testImplementation(libs.androidx.room.testing)
    testImplementation(libs.junit4)
    testRuntimeOnly(libs.junit.vintage.engine)

    // Instrumentation testing.
    androidTestImplementation(libs.androidx.test.ext.junit)
    androidTestImplementation(libs.androidx.test.core)
    androidTestImplementation(libs.androidx.test.runner)
    androidTestImplementation(libs.androidx.test.rules)
    androidTestImplementation(platform(libs.androidx.compose.bom))
    androidTestImplementation(libs.androidx.compose.ui.test.junit4)
}
