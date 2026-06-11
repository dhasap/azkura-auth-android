# Security Policy

Azkura Auth Android handles sensitive authenticator data. Please treat all OTP secrets, backup passwords, signing credentials, access tokens, and keystores as confidential.

## Supported Versions

Security fixes are prioritized for the latest published release.

| Version | Supported |
| --- | --- |
| 2.9.x | Yes |
| Older releases | Best effort |

## Reporting a Vulnerability

Please do **not** open a public GitHub issue for sensitive vulnerabilities.

Recommended report content:

- A concise description of the issue.
- Steps to reproduce using dummy data only.
- Impact and affected versions, if known.
- Logs or screenshots with all secrets redacted.

If a private security advisory channel is available on GitHub, use it. Otherwise contact the repository owner through GitHub and request a private disclosure channel.

## Secret Handling Rules

- Never commit real `otpauth://` URIs containing production secrets.
- Never commit keystores, `.jks`, `.keystore`, `.p12`, `.pem`, `.key`, or signing property files.
- Never commit `local.properties`, `.env`, Google service account JSON, or access tokens.
- Redact secrets as `[REDACTED]` in issues, pull requests, documentation, and logs.
- Release APKs should be uploaded to GitHub Releases, not stored in git history.

## Build and Release Integrity

Release builds should be verified before publication:

```bash
./gradlew :app:compileDebugKotlin :app:testDebugUnitTest :app:assembleRelease
apksigner verify --verbose app/build/outputs/apk/release/app-release.apk
```

Use the `apksigner` binary from the installed Android SDK build-tools directory if it is not on `PATH`.
