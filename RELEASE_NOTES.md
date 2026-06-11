# Azkura Auth Android v2.9.1

Version: `2.9.1`
Version code: `291`
Package: `id.azkura.auth`

## Overview

This release focuses on polishing the authenticator experience, improving service identity detection, and preparing the repository for professional open-source distribution.

## What's New

- **Smarter Sort Order**
  - Persistent modes: Custom, Alphabetical, Most Used, Recently Added.
  - Settings bottom sheet with immediate subtitle updates.
  - Custom drag-and-drop ordering with long-press activation and haptic feedback.

- **Animated Statistics**
  - Smooth count-up values.
  - Animated progress and chart fills.
  - Staggered screen entrance effects.
  - Top-service cards using the shared logo system.

- **Universal Service Logos**
  - Shared logo component across Home and Statistics.
  - Bundled offline logos for popular services.
  - Dynamic logo/favicons when local assets are unavailable.
  - Clean initials fallback.

- **Safer URI and Logo Identity Parsing**
  - `otpauth://...?issuer=` is treated as the primary identity source.
  - Label prefix before `:` is used only when issuer is missing.
  - Email domains are ignored to prevent false-positive service logos.

- **Repository Hygiene**
  - Added MIT License.
  - Added professional About, Privacy, Security, Contributing, Changelog, and Release Notes documents.
  - Strengthened `.gitignore` to keep secrets, keystores, local config, and generated APK/AAB artifacts out of git.

## Verification

The release APK was verified locally with:

```bash
./gradlew :app:compileDebugKotlin :app:testDebugUnitTest :app:assembleRelease
apksigner verify --verbose artifacts/azkura-auth-v2.9.1-uri-logo-hierarchy-release-signed.apk
aapt dump badging artifacts/azkura-auth-v2.9.1-uri-logo-hierarchy-release-signed.apk
```

Verified metadata:

- Package: `id.azkura.auth`
- Version name: `2.9.1`
- Version code: `291`
- APK signature schemes: v2 and v3 valid

## Install

Download the APK asset from this GitHub Release and install it on Android 8.0+.

If a file manager displays an older icon for the APK, clear the file manager cache or rename the APK. Android launchers and file managers can cache icons independently.
