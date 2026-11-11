# Building and Testing WatchaWatch

WatchaWatch provides two independent build pathways:
1. **Standard Android Studio / Gradle Build** (standard development environments).
2. **Autonomous Headless CLI Toolchain** (designed for low-memory VMs, CI/CD runners, and resource-constrained environments).

---

## 1. Prerequisites

- **Java Development Kit (JDK)**: OpenJDK 17 or 21.
- **Android SDK Platform**: API Level 34 (Android 14) and Android 36 compile tools.
- **Android Build Tools**: 34.0.0+ (`aapt2`, `d8`, `apksigner`, `zipalign`).
- **Kotlin Compiler**: `kotlinc` 2.3.0 or bundled Gradle Kotlin plugin.
- **Python**: Python 3.9+ (for automated verification and build orchestration scripts).

---

## 2. Standard Gradle Build

To build using standard Gradle:

```bash
# Debug APK
./gradlew assembleDebug

# Release Bundle (AAB for Google Play)
./gradlew bundleRelease

# Run Local Unit Tests
./gradlew testReleaseUnitTest
```

Release signing configuration can be supplied via environment variables or `gradle.properties`:
```properties
WATCHAWATCH_UPLOAD_STORE_FILE=/path/to/keystore.keystore
WATCHAWATCH_UPLOAD_STORE_PASSWORD=keystore_password
WATCHAWATCH_UPLOAD_KEY_ALIAS=key_alias
WATCHAWATCH_UPLOAD_KEY_PASSWORD=key_password
```

---

## 3. Autonomous Headless CLI Toolchain

For resource-constrained build environments without sufficient RAM for full Gradle daemon execution:

### Step 1: Compile Kotlin Modules
```bash
python3 scripts/build_modules.py
```
This incrementally compiles all application modules (`Models.kt`, `SessionTimeline.kt`, `CsvTransfer.kt`, `LocalInsights.kt`, `FloatingTimerService.kt`, `MainActivity.kt`) into optimized JVM bytecode with precise classpath resolution.

### Step 2: Assemble, Align & Sign Release APK
```bash
python3 scripts/package_apk.py
```
This pipeline:
1. Compiles vector and bitmap resources using `aapt2 compile`.
2. Links resources with `--min-sdk-version 26` and `--target-sdk-version 34` via `aapt2 link` using stable resource IDs.
3. Desugars and generates Dalvik bytecode (`classes.dex`) via `d8`.
4. Packages and aligns APK structure via `zip` and `zipalign`.
5. Cryptographically signs the APK with `apksigner` using the production release keystore with v2 and v3 signature schemes.
6. Asserts Google Play Protect and Android manifest compliance via automated badging inspection.

---

## 4. Automated Verification Suite

To verify security posture, service lifecycle, Compose state synchronization, and run all unit tests:

```bash
python3 tests/verify_watchawatch.py
```

### Verification Checks Performed:
1. **Manifest & Security**: Confirms zero `INTERNET` permission, verifies `POST_NOTIFICATIONS`, `WAKE_LOCK`, and `FOREGROUND_SERVICE_SPECIAL_USE`.
2. **Service & Notification Lifecycle**: Asserts `START_STICKY`, public lockscreen notification channel, dynamic broadcast receivers, and screen-off WakeLock management.
3. **State Synchronization**: Asserts `OnSharedPreferenceChangeListener` bridging UI and background services.
4. **JUnit Core Execution**: Runs `SessionTimelineTest`, `LocalInsightsTest`, and `CsvTransferTest` directly in JVM runtime.
5. **Compiler Verification**: Executes clean-pass Kotlin compilation for critical service components.
