# RollCall

RollCall is an Android app for university and college class attendance. It helps teachers manage a student roster, take attendance, and keep a history of attendance sessions.

## Download

Download the Android APK from the [v0.0.1 release](https://github.com/MdKhalidHassan/RollCall/releases/download/v0.0.1/RollCall.apk). To install, download the APK to your Android device and open it. Your device may ask you to allow installation from that source.

## Features

- Maintain a student roster and edit student details.
- Mark students present or absent, with search and filtering.
- Organize attendance by course, department, and batch.
- Sort students by ascending roll number.
- Copy clean attendance output for teachers.
- Save attendance sessions and review or reuse them later.
- Sign in with Google and sync data using Firebase.
- Responsive interface built with Jetpack Compose.

## Requirements

- Android Studio with Android SDK 36 support.
- Android 7.0 (API 24) or newer.
- A Firebase project configured for the app.

## Setup

1. Clone this repository and open it in Android Studio.
2. Configure Firebase for the Android app. Add the Firebase configuration file (`google-services.json`) and enable the authentication and Cloud Firestore services used by the app.
3. Make sure your Firestore database and deployed security rules match the database configured by the project. The repository includes `firestore.rules` and `firebase.json`.
4. Sync Gradle, then build and run the `app` configuration on an emulator or device.

Do not commit private credentials, signing keys, or other secrets to the repository.

## Tech stack

- Kotlin
- Jetpack Compose and Material 3
- Firebase Authentication
- Cloud Firestore
- Kotlin Coroutines

## License

This project is licensed under the MIT License. See [LICENSE](LICENSE) for details.
