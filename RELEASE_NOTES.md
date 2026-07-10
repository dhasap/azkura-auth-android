# Azkura Auth Android v2.13.0

Version: `2.13.0`
Version code: `2130`
Package: `id.azkura.auth`

## Overview

This release is a full audit-and-fix pass across QR gallery import, Google
Sign-In UX, Google Drive auto-backup/auto-restore, the Recents (App
Switcher) thumbnail, and the CI build pipeline itself. Every fix targets
the root cause, not just the visible symptom — see `CHANGELOG.md` for the
full technical write-up.

## What's New

- **Gallery QR import, fixed at the root.** Replaced the permission-gated
  `ACTION_PICK` fallback with the official Android Photo Picker
  (`PickVisualMedia`, no runtime permission) as the primary path, with
  `GetContent()` as an automatic fallback. `READ_MEDIA_IMAGES` /
  `READ_EXTERNAL_STORAGE` are no longer declared in the manifest. Bitmap
  decoding now runs off the main thread, and EXIF rotation is honored.
- **Google Sign-In UX.** Failures are now classified
  (`GoogleSignInException`: cancelled / no network / token error / session
  expired / Play services problem / timeout) with clear Indonesian
  messages, a 25s timeout so the flow can never hang, and an animated
  Connect/Backup button state.
- **Auto Backup after login.** Signing in with Google now automatically
  backs up to Drive in the background, with retry + exponential backoff
  on transient failures and structured logging.
- **Auto Restore after login.** After login, the app checks Drive for an
  existing backup and restores it automatically via the existing additive
  merge — it only adds missing accounts/folders and never deletes or
  overwrites local data, so this can never cause data loss.
- **Recents (App Switcher) preview.** The app previously showed a solid
  black thumbnail in Recents because `FLAG_SECURE` blocks the OS from
  capturing any image of the window. Added a branded, blurred
  `PrivacyOverlay` that is shown before `FLAG_SECURE` is briefly cleared in
  `onPause()` (so the Recents snapshot captures only the safe overlay) and
  restored before the overlay is hidden again in `onResume()`. Security
  level (screenshot/screen-recording protection) is unchanged.
- **CI pipeline.** Fixed a fake-passing "Verify APK signature" step
  (`apksigner: command not found` was being silently swallowed) so it now
  genuinely verifies the signature and fails loudly if it can't. Bumped
  `actions/checkout`, `actions/setup-java`, `actions/upload-artifact`,
  `actions/download-artifact`, and `gradle/actions/setup-gradle` to clear
  all "Node.js 20 is deprecated" warnings. The build now completes with
  zero warnings and zero errors.

## Build & Signing

This release is built and signed entirely by GitHub Actions
(`.github/workflows/build-release.yml`). The signed APK and AAB are
attached to this GitHub Release as assets.

To verify the signature locally:

```bash
apksigner verify --verbose app-release.apk
```

Verified metadata:

- Package: `id.azkura.auth`
- Version name: `2.13.0`
- Version code: `2130`
- APK signature schemes: v2 and v3 (verified in CI)

## Install

Download `azkura-auth-v2.13.0-release.apk` below and install it on a
device with "Install unknown apps" allowed for your file manager/browser,
or install via `adb install azkura-auth-v2.13.0-release.apk`.
