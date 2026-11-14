# APK Relay

[日本語](README.ja.md)

## Overview

Pass an APK to the Android installer without an app screen or launcher icon.

Android 8.0 or later. Standalone APKs only.

## Usage

- Open or share an APK and choose **APK Relay**.
- On first use, allow installation from this source, then return to continue.
- Confirm installation in Android.

## Build

Build with JDK 17 and Android SDK 35:

```sh
./gradlew assembleRelease
```

Sign the generated APK before installing or distributing it.

## Sharing APKs in ChatGPT

| Result | Link example |
| --- | --- |
| ✅ Opens APK Relay | `sandbox:/…/app.apk` |
| ❌ Request blocked | `https://chatgpt.com/api/library/files/…/download` |
| ❌ Opens a browser and requires login | `https://chatgpt.com/library?file_id=…&account_id=…` |

## License

Original code and icon: [Unlicense](LICENSES/Unlicense.txt).

AndroidX Core, its dependencies, and the Gradle Wrapper: [Apache 2.0](LICENSES/Apache-2.0.txt).
