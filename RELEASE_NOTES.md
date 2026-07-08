# Azkura Auth Android v2.10.0

Version: `2.10.0`
Version code: `2100`
Package: `id.azkura.auth`

## Overview

This release adds **QR import from the phone gallery**, makes the account-add /
QR-scan / gallery-import flows crash-safe on unsupported devices, and closes the
remaining open security-hardening issues. It also fixes a build-breaking bug that
prevented the previous source from compiling.

## What's New

- **Import QR from Gallery**
  - New gallery action on the scanner screen decodes an `otpauth://` QR from any
    saved image.
  - Uses the Android Photo Picker, so it needs **no storage/media permission** on
    any supported API level.
  - Works even when the device has no camera or the camera permission is denied.

- **Crash-Safe Enrollment**
  - Unreadable/corrupted images, non-`otpauth` QR codes, and invalid Base32
    secrets now show a clear message instead of crashing.
  - Devices without a camera fall back gracefully to gallery import.
  - A single malformed secret can no longer freeze the Home screen's code refresh.

- **Security Hardening**
  - Release builds are now minified/obfuscated with R8 + `proguard-rules.pro` (#12).
  - TLS certificate pinning for Google API traffic via `network_security_config.xml` (#13).
  - Removed the dead, unencrypted local-export path — all exports are encrypted (#14).
  - Usage statistics file is now encrypted at rest (#15).
  - Cleartext HTTP traffic is blocked app-wide, including on Android 8.0–8.1 (#16).
  - `security-crypto` upgraded from alpha to the stable `1.1.0`.

- **Build Fix**
  - Removing a PIN now re-verifies the current PIN in a confirmation dialog
    (restores compilation and closes a privilege-escalation gap).

## Build & Signing

This release is built and signed by GitHub Actions (`.github/workflows/release.yml`).
The signed APK is attached to this GitHub Release as an asset.

To build locally instead:

```bash
./gradlew :app:compileDebugKotlin :app:assembleRelease
apksigner verify --verbose app/build/outputs/apk/release/app-release.apk
```

Verified metadata:

- Package: `id.azkura.auth`
- Version name: `2.10.0`
- Version code: `2100`
- APK signature schemes: v1, v2, and v3

## Install

Download the APK asset from this GitHub Release and install it on Android 8.0+.

If a file manager displays an older icon for the APK, clear the file manager cache
or rename the APK. Android launchers and file managers can cache icons independently.
