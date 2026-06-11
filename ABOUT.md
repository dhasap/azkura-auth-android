# About Azkura Auth Android

Azkura Auth Android is a native authenticator application for Android, focused on privacy, speed, and a polished day-to-day OTP experience.

The project started as a mobile companion to the Azkura Auth ecosystem and is designed around a local-first security model: OTP generation should work offline, user secrets should remain on-device, and backup/export operations should be deliberate and transparent.

## Product Goals

- **Private by default** — OTP secrets are stored locally and protected by the app vault.
- **Fast daily use** — opening the app, finding an account, copying a code, and locking again should feel instant.
- **Modern Android UX** — Jetpack Compose, Material 3, smooth transitions, and subtle micro-interactions.
- **Reliable imports** — QR and `otpauth://` parsing should prioritize issuer data correctly and avoid noisy guesses from email domains.
- **Portable backups** — backup/restore flows should support encrypted data and remain compatible with the broader Azkura Auth vault format where possible.
- **Professional release hygiene** — generated APKs, signing keys, credentials, and local machine files do not belong in git.

## Notable Capabilities

- TOTP account management with QR scanner and deep-link support.
- Real-time sort order preferences: Custom, Alphabetical, Most Used, and Recently Added.
- Long-press custom reordering with visual and haptic feedback.
- Animated statistics for accounts, usage, backups, and top services.
- Universal service logos shared across Home and Statistics.
- Local bundled brand icons with dynamic logo/fallback behavior.
- Encrypted backup/restore through local files or Google Drive integration.

## Design Principles

1. **Security before convenience** — avoid leaking secrets into logs, docs, screenshots, or version control.
2. **Observable state** — settings should update the UI immediately without forcing navigation reloads.
3. **Small reusable components** — shared behaviors like service logos should live in one place.
4. **Graceful fallback** — missing service logos, bad QR labels, and unavailable cloud APIs should not break core OTP use.
5. **Release artifacts outside git** — GitHub Releases are the correct place for APKs; git is for source code and documentation.
