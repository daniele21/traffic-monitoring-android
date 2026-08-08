# Local Android development and Play internal testing

This document defines the local debug and release-signing workflow for the Android validation app.

The workflow deliberately separates three artifacts:

1. **Debug APK** — installed locally on an emulator/device as `com.daniele21.trafficmonitoring.debug`.
2. **Unsigned release AAB** — produced by CI only as a convenience artifact; never upload it directly to Play.
3. **Signed release AAB** — signed locally with the private Play upload key and suitable for Google Play Console internal testing.

The debug application ID is intentionally different from the release application ID (`com.daniele21.trafficmonitoring`), so a local debug build can coexist with a Play/internal-testing build on the same device.

## Prerequisites

Use JDK 17 and an Android SDK containing API 35, build-tools and platform-tools.

A typical macOS SDK is at:

```text
~/Library/Android/sdk
```

If the SDK is elsewhere, either export `ANDROID_HOME` or create an ignored `local.properties` file:

```properties
sdk.dir=/absolute/path/to/Android/sdk
```

**A global Gradle installation is not required.** The repository commits the standard Gradle Wrapper:

```text
gradlew
gradlew.bat
gradle/wrapper/gradle-wrapper.jar
gradle/wrapper/gradle-wrapper.properties
```

The wrapper is pinned to Gradle 8.9 and the distribution SHA-256 is pinned in `gradle-wrapper.properties`. On the first invocation, Gradle downloads the verified distribution into the normal Gradle user cache (typically `~/.gradle/wrapper/dists`). Subsequent invocations reuse it.

Verify the local build toolchain with:

```bash
./gradlew --version
```

If the executable bit was lost after copying the repository, restore it once:

```bash
chmod +x gradlew
```

## 1. Debug locally on an emulator

Create an Android Virtual Device once, either from Android Studio Device Manager or with the Android command-line tools.

List configured AVDs:

```bash
bash scripts/run-emulator-debug.sh --list-avds
```

If an emulator is already running:

```bash
bash scripts/run-emulator-debug.sh
```

If no emulator is running, start a specific AVD and launch the app:

```bash
bash scripts/run-emulator-debug.sh --avd Pixel_8_API_35
```

To stream only the app process logs after launch:

```bash
bash scripts/run-emulator-debug.sh --avd Pixel_8_API_35 --logs
```

To validate a clean first-run database/state:

```bash
bash scripts/run-emulator-debug.sh --avd Pixel_8_API_35 --clear-data
```

The runner:

- resolves the Android SDK and `adb`;
- verifies that the committed Gradle Wrapper is present;
- optionally starts the requested AVD;
- waits for Android boot completion;
- runs `./gradlew :app:installDebug`;
- launches `com.daniele21.trafficmonitoring.debug`;
- optionally streams logcat for only that process.

Useful low-level checks:

```bash
adb devices
adb shell pidof -s com.daniele21.trafficmonitoring.debug
adb logcat --pid="$(adb shell pidof -s com.daniele21.trafficmonitoring.debug)"
```

An emulator is useful for UI, Room persistence, lifecycle and export debugging. Network-attribution feasibility still requires the scripted real-device tests because emulator network transitions do not reproduce OEM/background behavior faithfully.

## 2. Create the private Play upload key once

The release signing helper follows one rule: **the private upload keystore and its password never enter GitHub**.

Create a PKCS12 upload keystore outside the repository:

```bash
bash scripts/build-play-release.sh create-key
```

Default location:

```text
~/.keystore/traffic-monitoring-upload.p12
```

Default alias:

```text
traffic-monitoring-upload
```

`keytool` asks for the password interactively. Store both the keystore file and password in a safe backup. Losing the upload key can complicate future Play uploads even when Play App Signing is enabled.

If an upload key already exists, do not create another one. Point the script to it instead:

```bash
export TRAFFIC_MONITORING_ANDROID_UPLOAD_STORE_FILE="/absolute/path/upload-key.p12"
export TRAFFIC_MONITORING_ANDROID_UPLOAD_KEY_ALIAS="your-existing-alias"
```

## 3. Store the password in macOS Keychain

Run:

```bash
bash scripts/build-play-release.sh setup
```

The password is stored as a generic macOS Keychain item and is not written to a repository file or shell command history.

Check non-secret configuration:

```bash
bash scripts/build-play-release.sh status
```

Inspect the upload certificate/fingerprints when Play asks for them:

```bash
bash scripts/build-play-release.sh certificate
```

## 4. Build the signed AAB locally

For the first Play upload, build the current `versionCode` and `versionName` from `app/version.properties`:

```bash
bash scripts/build-play-release.sh build
```

The release helper uses the committed `./gradlew`, so no global Gradle installation is needed for AAB builds either.

The script:

1. retrieves the password from macOS Keychain;
2. exports signing variables only for the build process lifetime;
3. runs `./gradlew :app:bundleRelease`;
4. verifies the resulting AAB signature with `jarsigner`;
5. copies the final AAB to `dist/`;
6. writes a SHA-256 checksum next to it.

Example output:

```text
dist/traffic-monitoring-0.1.0-m1a-vc1.aab
```

Upload that `.aab` in Google Play Console under the chosen Internal testing release.

For every later Play upload, `versionCode` must be greater than the previous uploaded build. The helper can increment it before building:

```bash
bash scripts/build-play-release.sh build-next
```

`app/version.properties` is source-controlled on purpose. After a successful upload, commit the updated `versionCode` so the repository remains aligned with Play.

Change `versionName` manually when the human-readable release version changes.

## 5. Alternative: build in CI, sign only on your Mac

GitHub Actions also produces:

```text
traffic-monitoring-android-release-unsigned
```

This artifact contains `app-release.aab`, intentionally unsigned. CI never receives the private upload key.

After downloading and extracting it, sign it locally:

```bash
bash scripts/build-play-release.sh sign-ci-aab \
  /path/to/app-release.aab \
  dist/traffic-monitoring-ci-signed.aab
```

Only the resulting signed AAB should be uploaded to Play.

This path is useful when local Android compilation is inconvenient but signing must remain under the developer's control.

## Release signing guardrails

`app/build.gradle.kts` refuses a release package when signing is only partially configured. It also refuses an unsigned local `bundleRelease`/`assembleRelease` unless the explicit CI-only escape hatch is set:

```text
TRAFFIC_MONITORING_ALLOW_UNSIGNED_RELEASE=true
```

Do not use that environment variable for a Play upload build.

The release build reads these variables:

```text
TRAFFIC_MONITORING_ANDROID_UPLOAD_STORE_FILE
TRAFFIC_MONITORING_ANDROID_UPLOAD_STORE_PASSWORD
TRAFFIC_MONITORING_ANDROID_UPLOAD_KEY_ALIAS
TRAFFIC_MONITORING_ANDROID_UPLOAD_KEY_PASSWORD
```

The helper script provides them from the local keystore and macOS Keychain. Do not store them in `.env`, `gradle.properties`, GitHub Actions secrets, or committed files for this workflow.

## Google Play signing model

When Play App Signing is enabled, the local key used here is the **upload key**. It authenticates the AAB you upload. Google Play then signs distributed APKs with the app-signing key managed by Play.

Therefore:

- keep the upload key private and backed up;
- use the same upload identity for subsequent releases unless Play explicitly rotates/resets it;
- never commit the keystore;
- never upload an unsigned CI AAB directly.
