# Privacy Policy

Azkura Auth Android is designed as a local-first authenticator. This document summarizes the data behavior of the app and repository.

## Data Stored by the App

The app may store the following data on the user's device:

- TOTP account names and issuers.
- TOTP shared secrets required to generate one-time codes.
- Folder/order metadata and app preferences.
- Usage statistics such as copy counts and backup timestamps.
- Optional backup configuration selected by the user.

## Network Usage

Core OTP generation works offline. Network access may be used for:

- Optional Google sign-in and Google Drive backup/restore flows.
- Optional dynamic service logo/favicons when a bundled local logo is not available.

## Backups

Backup and restore are user-triggered operations. If backup encryption is enabled, backup data is encrypted before upload/export using the configured backup password.

Users are responsible for storing backup passwords safely. Lost backup passwords may make encrypted backups unrecoverable.

## What Is Not Collected by This Repository

This open-source project does not operate a central analytics or OTP-generation service. Repository maintainers do not receive user vaults, OTP secrets, or generated OTP codes through normal app use.

## Version Control Hygiene

The repository `.gitignore` excludes local SDK files, signing keys, keystores, credentials, generated APK/AAB files, and environment files. Do not commit real OTP secrets, tokens, passwords, or private keys.

## Contact

For vulnerability reports or sensitive privacy issues, follow the process in [SECURITY.md](SECURITY.md).
