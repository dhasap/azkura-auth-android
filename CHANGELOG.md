# Changelog

All notable changes to Azkura Auth Android are documented here.

The format is inspired by [Keep a Changelog](https://keepachangelog.com/en/1.1.0/), and versioning follows the app's Android `versionName` / `versionCode` metadata.

## [2.12.7] - 2026-07-09

### Fixed

- **Gallery QR: downsample large images.** Screenshots (1080x2392+) now
  get downsampled to max 1024px on the longest side before ML Kit processes
  them, preventing OOM and scan failures on large gallery images.
- Gallery QR: switch from `InputImage.fromFilePath()` to
  `contentResolver.openInputStream()` → `BitmapFactory.decodeStream()` →
  `InputImage.fromBitmap()` which works reliably on Xiaomi/MIUI devices.
- Gallery QR: configure ML Kit to scan QR_CODE, AZTEC, and DATA_MATRIX
  formats specifically for higher detection success rate.
- Gallery QR: detailed error messages now include `e.message` for easier
  debugging.

## [2.12.6] - 2026-07-09

### Fixed

- Gallery QR: replace invalid Kotlin `return@try` labels with
  `return@processGalleryUri`.

## [2.12.5] - 2026-07-09

### Added

- **Xiaomi/MIUI gallery import fix.** Added `READ_MEDIA_IMAGES` and
  `READ_EXTERNAL_STORAGE` (maxSdkVersion=32) permissions to manifest.
- Scanner now requests storage permission before opening gallery — MIUI
  requires explicit permission for gallery access instead of relying on
  the Photo Picker.
- Three-strategy fallback: permission request → ACTION_PICK → GetContent.
- Centralized `processGalleryUri()` to deduplicate image processing logic
  across all launcher types.

### Changed

- Gallery import now uses permission-based approach instead of
  `PickVisualMedia` which silently fails on Xiaomi/MIUI.

## [2.12.4] - 2026-07-09

### Fixed

- **Gallery QR race condition.** Camera scanner now pauses
  (`isGalleryActive` flag) while the gallery picker is open, preventing
  the camera from stealing the scan via race condition.
- Gallery import errors auto-dismiss after 8 seconds instead of 4 seconds
  so users have time to read them.

## [2.12.2] - 2026-07-08

### Added

- **Auto-backup to Google Drive** after adding a new account. Uses a
  10-second debounce (multiple adds within 10s trigger one backup) and
  a 5-minute rate limit. Silent operation — logs only, no UI blocking.
- `lastAutoBackupAt` persistence in `PreferencesManager` for rate limiting
  across app restarts.

### Security

- Auto-backup respects user's encryption setting and skips silently if
  Google is not connected, token is expired, or encryption is enabled
  without a backup password.

## [2.12.1] - 2026-07-08

### Fixed

- Google login success animation — dialog now uses `AnimatedVisibility`
  with fade-in + slide-in, shows checkmark icon and "Berhasil!" title
  for success messages.
- Token purge on app startup — expired Google OAuth tokens are
  automatically cleaned up during cold start to prevent silent failures.
- Gallery import: add `isPhotoPickerAvailable()` check with
  `ACTION_GET_CONTENT` fallback for devices without Android Photo Picker.

## [2.11.0] - 2026-07-08

### Added

- **GitHub Actions CI/CD pipeline.** Unified workflow for debug APK,
  signed release AAB/APK, and GitHub Release publishing on version tags.
  Secrets validation ensures build fails if signing credentials are missing.
- **Modular cloud sync provider architecture.** New `CloudSyncProvider`
  interface enables adding new cloud backup providers (OneDrive, Dropbox,
  etc.) through configuration only — no changes to UI or ViewModel code.
  Providers registered via Hilt `@Binds @IntoSet` multibinding.
- `GoogleDriveSyncProvider` — wraps existing Google Auth + Drive services
  behind the new interface.
- `CloudSyncProviderRegistry` — query providers by ID, discover available
  providers, no hardcoding.
- `AuthConfigManager` — non-sensitive provider configuration backup
  (never backs up tokens, keys, or secrets).
- Provider-agnostic methods in `SettingsViewModel` (`onConnectProvider`,
  `onBackupToProvider`, `onRestoreFromProvider`) alongside existing
  Google-specific methods for backward compatibility.
- `docs/CICD_SECRETS.md` — full documentation of all required GitHub
  Secrets for the CI/CD pipeline.

### Changed

- Removed old `release.yml` workflow, replaced by unified `build-release.yml`.
- Release signing reads from `keystore.properties` → Gradle properties →
  environment variables (GitHub Actions).

### Security

- No credentials stored in repository. Keystore file is base64-encoded
  in GitHub Secrets only. OAuth Client IDs are public (safe in APK).

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
