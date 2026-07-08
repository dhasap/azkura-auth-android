# Changelog

All notable changes to Azkura Auth Android are documented here.

The format is inspired by [Keep a Changelog](https://keepachangelog.com/en/1.1.0/), and versioning follows the app's Android `versionName` / `versionCode` metadata.

## [2.10.0] - 2026-07-08

### Added

- **Import QR code from gallery.** The scanner screen now has a gallery action
  that decodes an `otpauth://` QR from any saved image using the Android Photo
  Picker (no storage/media permission required on any supported API level).
- **No-camera device support.** Devices without a camera (or with the camera
  permission denied) now get a clear message and can still add accounts via the
  gallery import path instead of being stuck.
- R8 code shrinking/obfuscation for release builds with a curated
  `proguard-rules.pro` (keeps serialization models, Room, Hilt, Retrofit, ML
  Kit, and Google Sign-In working while obfuscating internals).
- `network_security_config.xml` that forbids cleartext (HTTP) traffic app-wide
  — including on Android 8.0–8.1 where it is not the platform default — and pins
  Google API TLS certificates (`googleapis.com`) at the CA level with a
  fail-open expiration date.
- GitHub Actions workflow that assembles and signs the release APK and attaches
  it to a GitHub Release.

### Fixed

- **Build-breaking bug:** PIN removal called `onRemovePin()` without the required
  `currentPin` argument, so the project failed to compile. Removing a PIN now
  shows a confirmation dialog that re-verifies the current PIN first (closes the
  privilege-escalation gap and restores compilation).
- **Crash hardening:** a single malformed/corrupted TOTP secret could throw
  inside the once-per-second refresh loop and freeze every code on the Home
  screen. Code generation is now guarded per account and the refresh loop is
  crash-safe.
- Scanning, gallery import, and adding an account no longer crash on unsupported
  devices, unreadable images, non-`otpauth` QR codes, or invalid Base32 secrets
  — each case surfaces a friendly message instead.

### Changed

- Add Account now validates the secret is real Base32 before saving, so an
  unusable account can never reach the vault or the TOTP generator.
- Removed the dead, unencrypted local backup export path (`exportToUri`); all
  local exports go through the encrypted `.vault` flow.
- Usage statistics (`app_stats.json`) are now stored encrypted with the
  Keystore-backed encryptor instead of plaintext JSON (with transparent
  migration of any existing plaintext file).
- Upgraded `androidx.security:security-crypto` from `1.1.0-alpha06` to the
  stable `1.1.0` and migrated off the deprecated `MasterKeys` API.

### Security

- Resolves the remaining open hardening issues: release obfuscation (#12), TLS
  certificate pinning (#13), plaintext local export (#14), unprotected usage
  metadata (#15), and missing network security config (#16).

## [2.9.1] - 2026-06-11

### Added

- Professional repository documentation: README, About, Privacy, Security, Contributing, and Release Notes.
- MIT license file.
- Stronger `.gitignore` rules for signing files, local configuration, secrets, and generated release artifacts.

### Changed

- Tightened `otpauth://` parsing so the query `issuer` is the primary identity source.
- Improved service logo lookup hierarchy: issuer, label issuer, sanitized keyword, then fallback initials.
- Prevented email domains from being used as service-logo identity hints.

### Verified

- Debug Kotlin compilation passed.
- Unit test task passed.
- Release APK assembled successfully.
- Release APK signing verified with Android SDK `apksigner`.

## [2.9.0] - 2026-06-11

### Added

- Animated Statistics screen micro-interactions including counters, chart/progress fills, and staggered entrances.
- Reusable service logo component shared by Home and Statistics.
- Bundled offline brand assets for popular services and crypto/Web3 accounts.

### Changed

- Home account cards now use the universal service logo pipeline.
- Statistics Top Services now uses the same logo rendering behavior as Home.

## [2.8.2] - 2026-06-11

### Added

- Persistent sort order preference with Custom, Alphabetical, Most Used, and Recently Added modes.
- Sort Order bottom sheet in Settings with dynamic selected-state indicator.
- Long-press drag-and-drop custom ordering on Home.
- Visual and haptic feedback while reordering accounts.
- Updated launcher icon resources.

### Changed

- Home list sorting now reacts to DataStore preference changes.
- Manual account reordering is active only in Custom sort mode.
- Custom order persistence happens after drop using a batch database update.
