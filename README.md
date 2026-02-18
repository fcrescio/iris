# Iris

Iris is a compact Android control surface for wearable camera workflows. It focuses on a direct loop: register a device, verify readiness, stream camera output, and capture photos with configurable intervals.

## What Iris provides

- **Registration and connection flow** with clear app states (home, ready-to-stream, active stream)
- **Live stream + capture UI** built with Jetpack Compose
- **Capture cadence controls** from an in-app settings page
- **MockDeviceKit debug tooling** for local QA without physical hardware (debug builds)
- **Instrumentation coverage** for launch, paired-device, and capture scenarios

## Tech stack

- Kotlin + Coroutines + StateFlow
- Jetpack Compose (Material 3)
- Meta Wearables Android toolkit dependencies
- Android instrumentation tests with Compose test APIs

## Local setup

### Prerequisites

- Android Studio (recent stable)
- JDK 11+
- Android SDK API 31+
- Token access required for Wearables dependency resolution

### Credentials

1. Create `local.properties` in repository root if missing.
2. Add the token configuration expected by the dependency setup.
3. Follow the official setup instructions for the Wearables SDK:
   - https://wearables.developer.meta.com/docs/getting-started-toolkit/#sdk-for-android-setup

## Run flow

1. Open the project in Android Studio.
2. Sync Gradle.
3. Run `app` on a phone/device target.
4. In Iris:
   - Grant Android runtime permissions.
   - Complete registration with your wearable account/device.
   - Start streaming once an active device is available.
   - Capture photos and adjust interval settings from the settings page.

## Test flow

- Instrumentation tests are under:
  - `app/src/androidTest/java/li/crescio/penates/iris`
- Typical validation includes app launch, mock pairing behavior, and stream capture interactions.

## Support links

- Wearables docs: https://wearables.developer.meta.com/docs/develop/
- Toolkit discussions: https://github.com/facebook/meta-wearables-dat-android/discussions
- Iris issues: use this repository issue tracker for product bugs and feature requests

## CI/CD

The GitHub Actions workflow `.github/workflows/firebase-app-distribution.yml` can:

1. Build release APK (`:app:assembleRelease`)
2. Publish to Firebase App Distribution

### Required secrets

- `MWDAT_GITHUB_TOKEN`
- `FIREBASE_APP_ID`
- `FIREBASE_SERVICE_ACCOUNT_JSON`
- `FIREBASE_TESTER_GROUPS` *(optional)*

## License

Distributed under the terms in `LICENSE`.
