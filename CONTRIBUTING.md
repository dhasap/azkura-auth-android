# Contributing

Thanks for helping improve Azkura Auth Android. Contributions should preserve the app's privacy-first and security-sensitive nature.

## Development Setup

Requirements:

- JDK 17
- Android SDK with compileSdk 35
- Android Studio Ladybug or newer recommended

Common commands:

```bash
./gradlew :app:assembleDebug
./gradlew :app:testDebugUnitTest
./gradlew :app:compileDebugKotlin
```

## Workflow

1. Create a focused branch.
2. Keep changes small and reviewable.
3. Run relevant Gradle tasks before opening a pull request.
4. Update documentation and changelog entries when behavior changes.
5. Do not include generated APK/AAB files in commits.

## Commit Style

Prefer Conventional Commits:

```text
feat: add animated statistics screen
fix: prevent email domain from influencing service logo lookup
docs: add privacy and release documentation
chore: ignore generated release artifacts
```

## Security Requirements

Do not commit:

- OTP secrets or real `otpauth://` URIs.
- Keystores, signing configs, passwords, tokens, or private keys.
- `local.properties`, `.env`, `google-services.json`, or service account JSON.
- Generated APK/AAB artifacts.

Use dummy values in tests and documentation. Redact sensitive material as `[REDACTED]`.

## UI Guidelines

- Follow existing Compose and Material 3 patterns.
- Keep state observable and reactive with Flow/DataStore where appropriate.
- Prefer reusable components over duplicated screen-specific logic.
- Preserve accessibility-friendly hitboxes and readable contrast.

## Pull Request Checklist

- [ ] Code builds locally.
- [ ] Tests or manual verification notes are included.
- [ ] No secrets or release binaries are committed.
- [ ] Documentation is updated for user-visible changes.
- [ ] Screenshots or short notes are included for UI changes when helpful.
