# Shipping SideQuest to the Play Store

A practical, repeatable guide to building a signed release of SideQuest and
publishing it to Google Play. Helper scripts live in `scripts/`.

> TL;DR
> 1. `./scripts/release-keystore.ps1` — once, to create your signing key.
> 2. `./scripts/release-build.ps1` — build a signed `.aab` for Play.
> 3. Upload the `.aab` in the Play Console.

---

## 1. Prerequisites

| Need | Notes |
| --- | --- |
| JDK 17 + `keytool` | Ships with Android Studio (`...\jbr\bin`). |
| Android SDK | Already set up (you've been building debug APKs). |
| Google Play Developer account | One-time **$25** registration at <https://play.google.com/console>. |
| App signing decision | Recommended: enroll in **Play App Signing** (Google holds the app signing key; you keep an *upload* key). The key this guide creates is your upload key. |

---

## 2. One-time: create your signing (upload) key

Run from the repo root:

```powershell
./scripts/release-keystore.ps1
```

It will:
- generate `sidequest-release.jks` (RSA-2048, ~27-year validity) at the repo root, and
- write `keystore.properties` (store path, passwords, alias).

Both files are **gitignored** — never commit them.

> ⚠️ Back up `sidequest-release.jks` and the passwords in a password manager.
> They are not recoverable. With Play App Signing you can reset a lost *upload*
> key via support, but it's far easier to just keep the backup.

How Gradle uses it: `app/build.gradle.kts` loads `keystore.properties` if present
and signs the `release` build type with it. If the file is absent (e.g. a fresh
clone or CI without secrets), release builds stay unsigned and **debug builds are
unaffected** — nothing breaks.

---

## 3. Before each release: set version + backend URL

### Version
Play requires a **strictly increasing `versionCode`** for every upload.
`versionName` is the human label (e.g. `1.1`). Bump them with the build script:

```powershell
./scripts/release-build.ps1 -VersionName '1.1' -VersionCode 2
```

…or edit `app/build.gradle.kts` (`defaultConfig { versionCode / versionName }`) by hand.

### Backend URL
The `release` build type's `API_BASE_URL` in `app/build.gradle.kts` is currently a
placeholder (`https://api.sidequest.invalid/`). Backend-dependent features (sync,
backend transcription/LLM) won't work until you set a real HTTPS endpoint. The
fully offline features (board, buckets, games, on-device transcription + heuristic
extraction) work regardless.

---

## 4. Build the release artifact

**App Bundle (.aab) — required for the Play Store:**
```powershell
./scripts/release-build.ps1
# -> app/build/outputs/bundle/release/app-release.aab
```

**APK — for direct sideloading / sharing (not for Play upload):**
```powershell
./scripts/release-build.ps1 -Apk
# -> app/build/outputs/apk/release/app-release.apk
```

Add `-Clean` for a from-scratch build. The script verifies signing is configured
and prints the output path + size.

Verify the signature if you like:
```powershell
# apksigner is in the SDK build-tools folder
apksigner verify --print-certs app\build\outputs\apk\release\app-release.apk
```

---

## 5. First-time Play Console setup

1. **Create the app** in the Play Console (app name, default language, app/game,
   free/paid).
2. **App content / Data safety** — declare what data the app collects. SideQuest
   uses: microphone (voice journal), audio recordings stored on-device, optional
   profile name/photo stored on-device. Be accurate about whether anything is sent
   to your backend.
3. **Privacy policy URL** — required because the app requests the microphone.
4. **Store listing** — short + full description, app icon (512×512), feature
   graphic (1024×500), and at least 2 phone screenshots.
5. **Content rating** questionnaire.
6. **Target audience** and ads declaration (SideQuest has no ads).
7. **Play App Signing** — accept enrollment when prompted (recommended).

---

## 6. Upload + roll out

1. Start with a **testing track** (Internal testing is fastest — available to your
   tester list within minutes, no review wait).
2. Create a release on that track and upload `app-release.aab`.
3. Add release notes, save, and roll out to testers.
4. Install via the opt-in link on a real device and verify:
   - mic permission prompt + recording,
   - on-device transcription (needs an on-device speech model installed),
   - board / buckets / games / stats.
5. When happy, promote the release to **Production** (first production submission
   goes through Google review — can take a few hours to a few days).

---

## 7. Updating later

```powershell
./scripts/release-build.ps1 -VersionName '1.2' -VersionCode 3 -Clean
```
Upload the new `.aab` to a track, add release notes, roll out. Always use the same
keystore — that's why the backup in step 2 matters.

---

## 8. Pre-publish checklist

- [ ] `versionCode` is higher than the last published build.
- [ ] `release` `API_BASE_URL` points at the real backend (or backend features are intentionally disabled).
- [ ] Signed `.aab` builds cleanly (`./scripts/release-build.ps1`).
- [ ] App launches and the mic flow works on a physical device.
- [ ] Data safety form + privacy policy reflect microphone/audio usage.
- [ ] Keystore + passwords are backed up off-machine.
- [ ] `keystore.properties` and `*.jks` are **not** committed (they're gitignored).

---

## Files this adds to the repo

| Path | Purpose |
| --- | --- |
| `scripts/release-keystore.ps1` | One-time: create the signing key + `keystore.properties`. |
| `scripts/release-build.ps1` | Build a signed `.aab` (or `-Apk`), with optional version bump. |
| `keystore.properties` *(gitignored)* | Local signing secrets read by Gradle. |
| `sidequest-release.jks` *(gitignored)* | Your signing/upload key. |
