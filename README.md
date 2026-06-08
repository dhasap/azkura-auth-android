# Azkura Auth Android

Native Android port of [Azkura Auth](https://github.com/dhasap/azkura-auth) — a minimal, privacy-focused TOTP authenticator.

## Features

- **TOTP Generation** — RFC 6238 compliant with SHA1/SHA256/SHA512 support
- **QR Code Scanner** — CameraX + ML Kit barcode scanning
- **Encrypted Vault** — AES-256-GCM with PBKDF2 key derivation (cross-compatible with browser extension)
- **PIN Lock** — 6-digit PIN protection
- **Biometric Unlock** — Fingerprint/Face authentication
- **Google Drive Backup** — Encrypted backup & restore via Google Drive
- **Dark Theme** — Sleek dark UI ported from the browser extension
- **Deep Links** — Handle `otpauth://` URIs from other apps

## Tech Stack

- Kotlin + Jetpack Compose + Material 3
- Hilt (Dependency Injection)
- Room (Local Database)
- DataStore (Preferences)
- CameraX + ML Kit (QR Scanning)
- Google Credential Manager (Sign-In)
- Retrofit + kotlinx.serialization (Google Drive API)

## Build

```bash
./gradlew assembleDebug
```

APK output: `app/build/outputs/apk/debug/app-debug.apk`

## Requirements

- Android SDK 35 (compileSdk)
- Android 8.0+ (minSdk 26)
- Java 17

## Vault Compatibility

The encrypted vault format is identical to the browser extension. Backups created in the extension can be restored in the Android app and vice versa.

## License

MIT
