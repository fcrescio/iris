# Camera Access App

A sample Android application demonstrating integration with Meta Wearables Device Access Toolkit. This app showcases streaming video from Meta AI glasses, capturing photos, and managing connection states.

## Features

- Connect to Meta AI glasses
- Stream camera feed from the device
- Capture photos from glasses
- Share captured photos

## Prerequisites

- Android Studio Arctic Fox (2021.3.1) or newer
- JDK 11 or newer
- Android SDK 31+ (Android 12.0+)
- Meta Wearables Device Access Toolkit (included as a dependency)
- A Meta AI glasses device for testing (optional for development)

## Building the app

### Using Android Studio

1. Clone this repository
1. Open the project in Android Studio
1. Add your personal access token (classic) to the `local.properties` file (see [SDK for Android setup](https://wearables.developer.meta.com/docs/getting-started-toolkit/#sdk-for-android-setup))
1. Click **File** > **Sync Project with Gradle Files**
1. Click **Run** > **Run...** > **app**

## Running the app

1. Turn 'Developer Mode' on in the Meta AI app.
1. Launch the app.
1. Press the "Connect" button to complete app registration.
1. Once connected, the camera stream from the device will be displayed
1. Use the on-screen controls to:
   - Capture photos
   - View and save captured photos
   - Disconnect from the device

## Troubleshooting

For issues related to the Meta Wearables Device Access Toolkit, please refer to the [developer documentation](https://wearables.developer.meta.com/docs/develop/) or visit our [discussions forum](https://github.com/facebook/meta-wearables-dat-android/discussions)

## License

This source code is licensed under the license found in the LICENSE file in the root directory of this source tree.

## CI: APK build and Firebase App Distribution

This repository includes a GitHub Actions workflow at `.github/workflows/firebase-app-distribution.yml` that:

1. Builds the release APK (`:app:assembleRelease`)
2. Uploads the generated APK to Firebase App Distribution

It runs automatically on pushes to `main` and can also be started manually from **Actions** > **Build and distribute APK**.

### Required GitHub Actions secrets

Configure these repository secrets in **GitHub** > **Settings** > **Secrets and variables** > **Actions**:

1. `MWDAT_GITHUB_TOKEN`
   - A GitHub personal access token that can read `facebook/meta-wearables-dat-android` packages.
   - The workflow exports this token as `GITHUB_TOKEN` so Gradle can resolve the Meta Wearables dependency.
2. `FIREBASE_APP_ID`
   - The Firebase Android app ID (format similar to `1:1234567890:android:abcdef123456`).
   - Find it in Firebase console under **Project settings** > **Your apps**.
3. `FIREBASE_SERVICE_ACCOUNT_JSON`
   - The full JSON content of a Firebase service account key.
   - Create in **Google Cloud Console** for your Firebase project and grant access suitable for Firebase App Distribution uploads.
4. `FIREBASE_TESTER_GROUPS` (optional but recommended)
   - Comma-separated Firebase App Distribution tester groups (for example: `qa-team,android-devs`).

### Manual run options

When manually triggering the workflow (`workflow_dispatch`), you can provide `release_notes`.
If omitted, release notes default to the branch and commit SHA.
