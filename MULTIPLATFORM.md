# Multiplatform Setup

This repository now contains:

- `app`: the Android application module that still generates the APK.
- `shared`: a Kotlin Multiplatform library module used as the base for Android + iOS sharing.
- `iosApp`: an Xcode SwiftUI application that links the `shared` framework.

## Android build

From the project root:

```powershell
./gradlew.bat :app:assembleDebug
```

APK output:

- `app/build/outputs/apk/debug/`

## iOS build

iOS executables still require macOS + Xcode. This repository now includes the Xcode project and the Kotlin `shared` framework wiring, but the final app binary must be generated on a Mac.

Steps on macOS:

1. Install Xcode.
2. Open `iosApp/iosApp.xcodeproj`.
3. Set your Apple Team ID in `iosApp/Configuration/Config.xcconfig`.
4. Pick an iPhone simulator or device.
5. Build and run from Xcode.

The Xcode project already calls:

```sh
./gradlew :shared:embedAndSignAppleFrameworkForXcode
```

so the Kotlin `shared` framework is produced during the iOS build.

## Current iOS scope

The iOS app currently includes:

- SwiftUI event list UI
- live fetch from BuonaCaccia
- lightweight HTML parsing
- local cache
- search
- region filter
- link opening to event details

The Android app still remains the more complete build because Android background workers, notifications, and widgets are still Android-specific.
