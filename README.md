# Action Tracker

Android-first action tracker app. Multi-module Gradle project:

- `:app` — Android application (Kotlin + Jetpack Compose / Material 3, Hilt, Room, WorkManager, Retrofit/OkHttp + kotlinx.serialization).
- `:domain` — pure-Kotlin (JVM) module holding portable client logic, validated with Kotest property tests.

The Go 1.26 backend is a separate, later milestone and is not part of this repository yet.

## Requirements

- JDK 17+
- Android SDK (set `sdk.dir` in `local.properties` or the `ANDROID_HOME` environment variable). Android Studio installs this automatically.

## Build & test

```bash
# Pure domain logic (no Android SDK required)
./gradlew :domain:test

# Android app (requires the Android SDK)
./gradlew :app:assembleDebug
./gradlew :app:testDebugUnitTest
```

If `:app` tasks fail with "SDK location not found", create a `local.properties`
file at the project root containing:

```properties
sdk.dir=/absolute/path/to/Android/Sdk
```
