# Azkura Auth Android

[![Platform](https://img.shields.io/badge/platform-Android-3DDC84?logo=android&logoColor=white)](https://developer.android.com/)
[![Kotlin](https://img.shields.io/badge/Kotlin-2.1-7F52FF?logo=kotlin&logoColor=white)](https://kotlinlang.org/)
[![Compose](https://img.shields.io/badge/UI-Jetpack%20Compose-4285F4?logo=jetpackcompose&logoColor=white)](https://developer.android.com/compose)
[![License: MIT](https://img.shields.io/badge/License-MIT-yellow.svg)](LICENSE)

Azkura Auth Android is a native, privacy-focused TOTP authenticator built with Kotlin, Jetpack Compose, Room, DataStore, and Hilt. It is designed for fast daily OTP access while keeping vault data encrypted locally and making backup/export flows explicit.

> Native Android companion to [Azkura Auth](https://github.com/dhasap/azkura-auth).

## Highlights

- **RFC 6238 TOTP generation** with SHA-1, SHA-256, and SHA-512 support.
- **QR code scanning** with CameraX and ML Kit for quick account enrollment.
- **`otpauth://` deep-link import** with strict issuer handling to avoid false-positive service detection.
- **Encrypted local vault** using Android security primitives and app-managed vault protection.
- **PIN and biometric unlock** for everyday app access.
- **Google Drive backup and restore** with encrypted backup support.
- **Smart sort order** with persistent, real-time modes: Custom, Alphabetical, Most Used, and Recently Added.
- **Drag-and-drop custom ordering** with long-press activation, haptic feedback, and batch persistence.
- **Animated statistics** with usage insights, counters, progress fills, and top-service visualizations.
- **Reusable service logo system** with bundled popular brand assets, dynamic favicon/logo fallback, and elegant initials.
- **Modern dark UI** built fully in Jetpack Compose and Material 3.

## Screens and Flows

| Area | What it does |
| --- | --- |
| Home | Displays OTP accounts, live codes, copy actions, sorting, and custom reordering. |
| Add Account | Imports from QR scanner, otpauth URI, or manual entry. |
| Settings | Manages security, backups, sort order, and app preferences. |
| Statistics | Shows account and copy usage patterns with animated micro-interactions. |
| Backup/Restore | Exports and restores encrypted vault data locally or through Google Drive. |

## Privacy and Security Model

Azkura Auth is built around local-first storage:

- OTP secrets are stored on-device in the app vault.
- Signing files, keystores, local SDK configuration, and APK/AAB artifacts are intentionally ignored by git.
- Backup data can be encrypted before upload.
- The app never needs a central Azkura server to generate OTP codes.
- Repository documentation and examples must never include real OTP secrets, tokens, or private keys.

See [SECURITY.md](SECURITY.md) and [PRIVACY.md](PRIVACY.md) for responsible disclosure and data-handling notes.

## Tech Stack

- **Language:** Kotlin
- **UI:** Jetpack Compose, Material 3
- **Architecture:** MVVM, Kotlin Flow, Hilt dependency injection
- **Persistence:** Room, Jetpack DataStore
- **Camera/QR:** CameraX, ML Kit Barcode Scanning
- **Auth/Backup:** Google Credential Manager, Google Drive API integration
- **Crypto:** Android security APIs, AES-GCM style vault protection, PBKDF2-derived backup encryption
- **Build:** Gradle Kotlin DSL, Android Gradle Plugin

## Requirements

- Android Studio Ladybug or newer recommended
- JDK 17
- Android SDK / compileSdk 35
- Android 8.0+ device or emulator (minSdk 26)

## Build Locally

```bash
./gradlew :app:assembleDebug
```

Debug APK output:

```text
app/build/outputs/apk/debug/app-debug.apk
```

Run unit tests:

```bash
./gradlew :app:testDebugUnitTest
```

## Release Build

Release signing is intentionally externalized. Do **not** commit keystores or signing properties.

Create a local `keystore.properties` file or provide equivalent Gradle/environment properties:

```properties
AZKURA_AUTH_RELEASE_STORE_FILE=/absolute/path/to/release.jks
AZKURA_AUTH_RELEASE_STORE_PASSWORD=replace-with-local-secret
AZKURA_AUTH_RELEASE_KEY_ALIAS=replace-with-local-alias
AZKURA_AUTH_RELEASE_KEY_PASSWORD=replace-with-local-secret
```

Then build:

```bash
./gradlew :app:assembleRelease
```

Signed APK output:

```text
app/build/outputs/apk/release/app-release.apk
```

Publish binaries through GitHub Releases, not as committed repository files.

## Project Structure

```text
app/src/main/java/id/azkura/auth/
├── data/          # Room entities, repositories, preferences, backup services
├── di/            # Hilt modules
├── ui/            # Compose screens, navigation, reusable components, theme
└── util/          # TOTP, URI parsing, biometric, clipboard, backup helpers
```

## Documentation

- [ABOUT.md](ABOUT.md) — project background, goals, and design principles.
- [CHANGELOG.md](CHANGELOG.md) — release history and notable changes.
- [CONTRIBUTING.md](CONTRIBUTING.md) — development workflow and contribution rules.
- [SECURITY.md](SECURITY.md) — vulnerability reporting and secret-handling policy.
- [PRIVACY.md](PRIVACY.md) — data collection and storage summary.
- [RELEASE_NOTES.md](RELEASE_NOTES.md) — current release notes for GitHub Releases.

## License

This project is licensed under the [MIT License](LICENSE).
