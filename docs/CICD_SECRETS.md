# CI/CD Secrets — Azkura Auth Android

## Required GitHub Secrets

Go to **Settings → Secrets and variables → Actions → New repository secret**.

### Release Signing (4 secrets)

| Secret name | How to get the value |
|---|---|
| `AZKURA_AUTH_RELEASE_STORE_BASE64` | `base64 -w0 release.jks` (the keystore file, base64-encoded) |
| `AZKURA_AUTH_RELEASE_STORE_PASSWORD` | Keystore password |
| `AZKURA_AUTH_RELEASE_KEY_ALIAS` | Key alias inside the keystore |
| `AZKURA_AUTH_RELEASE_KEY_PASSWORD` | Key password for the alias |

> **All four must be present** or the release build is skipped entirely.
> Debug builds always run regardless.

### Base64-encode your keystore

```bash
# Linux / macOS
base64 -w0 release.jks

# Windows PowerShell
[Convert]::ToBase64String([IO.File]::ReadAllBytes("release.jks"))
```

### How the workflow works

1. **Push to main/develop or open PR** → debug APK is built
2. **Signing secrets present** → signed release AAB + APK are also built
3. **Push a version tag** (`v*`) → a GitHub Release is created with APK + AAB

### Verify SHA fingerprints

After the first release build, check the workflow log under
**"Decode release keystore"** — it prints SHA-1 and SHA-256.

Or run locally:
```bash
keytool -list -v -keystore release.jks -alias YOUR_ALIAS
```

## Environment Variables

The `app/build.gradle.kts` signing config reads these in order:
1. `keystore.properties` file (local dev)
2. Gradle `-P` properties
3. **Environment variables** (used by GitHub Actions)

| Env var | Purpose |
|---|---|
| `AZKURA_AUTH_RELEASE_STORE_FILE` | Keystore file path |
| `AZKURA_AUTH_RELEASE_STORE_PASSWORD` | Store password |
| `AZKURA_AUTH_RELEASE_KEY_ALIAS` | Key alias |
| `AZKURA_AUTH_RELEASE_KEY_PASSWORD` | Key password |

## What is NOT a secret

- `google-services.json` — **NOT needed** (app uses Identity Services, not Firebase)
- OAuth Client IDs — safe to ship in APK (public by design)
- Debug keystore — Android SDK provides one automatically
