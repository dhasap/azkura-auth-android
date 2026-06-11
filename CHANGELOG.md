# Changelog

All notable changes to Azkura Auth Android are documented here.

The format is inspired by [Keep a Changelog](https://keepachangelog.com/en/1.1.0/), and versioning follows the app's Android `versionName` / `versionCode` metadata.

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
