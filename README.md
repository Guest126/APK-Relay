# APK Relay

[日本語](README.ja.md)

## Overview

Receives APK files opened from apps without install permission and relays them to the standard Android package installer using its own granted permission, enabling one-stop installation. This app has no launcher icon or main screen.

Android 8.0 or later. Standalone APKs only.

## Usage

- Open or share an APK and choose **APK Relay**.
- On first use, allow installation from this source, then return to continue.
- Install using Android’s system package installer.

## Build

Build with JDK 17 and Android SDK 35:

```sh
./gradlew assembleRelease
```

Sign the generated APK before installing or distributing it.

## Notes

**Testing with the ChatGPT app**

| Result | Link example |
| --- | --- |
| ✅ Opens APK Relay | `sandbox:/…/app.apk` |
| ❌ Request blocked | `https://chatgpt.com/api/library/files/…/download` |
| ❌ Opens a browser and requires login | `https://chatgpt.com/library?file_id=…&account_id=…` |

## License

Original code and icon: [Unlicense](LICENSES/Unlicense.txt).

AndroidX Core, its dependencies, and the Gradle Wrapper: [Apache 2.0](LICENSES/Apache-2.0.txt).
