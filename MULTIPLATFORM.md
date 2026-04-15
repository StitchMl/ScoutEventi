# Multiplatform Setup

This repository now contains:

- `app`: the Android application module that still generates the APK.
- `shared`: a Kotlin Multiplatform library module used as the base for Android + iOS sharing.
- `iosApp`: an Xcode SwiftUI application for iPhone/iPad distribution.

## Android build

From the project root:

```powershell
./gradlew.bat :app:assembleDebug
```

APK output:

- `app/build/outputs/apk/debug/`

## iOS build

iOS executables still require macOS + Xcode. This repository now includes the Xcode project, but the final app binary must be generated on a Mac.

Steps on macOS:

1. Install Xcode.
2. Open `iosApp/iosApp.xcodeproj`.
3. Set your Apple Team ID in `iosApp/Configuration/Config.xcconfig`.
4. Pick an iPhone simulator or device.
5. Build and run from Xcode.

## Signed IPA from Windows via GitHub Actions

You can now generate a real signed `.ipa` without building locally on macOS by using the workflow:

- `Build Signed iOS IPA`

The workflow runs on `macos-15`, signs the app, archives it, exports the `.ipa`, and uploads it as a GitHub Actions artifact.

### Required GitHub secrets

Add these repository secrets before running the workflow:

- `IOS_CERTIFICATE_P12_BASE64`: base64 of the signing certificate `.p12`
- `IOS_PROVISIONING_PROFILE_BASE64`: base64 of the `.mobileprovision`

Optional overrides:

- `IOS_CERTIFICATE_PASSWORD`: only if the `.p12` was exported with a password
- `IOS_TEAM_ID`: must match the provisioning profile Team ID
- `IOS_BUNDLE_ID`: defaults to `it.buonacaccia.scouteventi.ios`
- `IOS_APP_NAME`: defaults to `ScoutEventi`

### Export the signing files from Windows

PowerShell examples:

```powershell
[Convert]::ToBase64String([IO.File]::ReadAllBytes("C:\signing\ScoutEventiDistribution.p12"))
[Convert]::ToBase64String([IO.File]::ReadAllBytes("C:\signing\ScoutEventi.mobileprovision"))
```

Paste the resulting strings into the GitHub secrets above.

### One-command upload + build from Windows

Instead of opening GitHub manually, you can now use:

```powershell
.\scripts\ios\start_signed_ipa.ps1 `
  -CertificatePath "C:\signing\ScoutEventiDistribution.p12" `
  -ProvisioningProfilePath "C:\signing\ScoutEventi.mobileprovision" `
  -GitHubToken $env:GITHUB_TOKEN `
  -ExportMethod ad-hoc
```

What the script does:

- base64-encodes the `.p12` and `.mobileprovision`
- uploads the required GitHub Actions secrets through the GitHub API
- dispatches the `Build Signed iOS IPA` workflow
- waits for the run to finish
- downloads the artifact zip locally

Files:

- [scripts/ios/start_signed_ipa.ps1](/C:/Users/matte/AndroidStudioProjects/ScoutEventi/scripts/ios/start_signed_ipa.ps1)
- [scripts/ios/github_signed_ipa.mjs](/C:/Users/matte/AndroidStudioProjects/ScoutEventi/scripts/ios/github_signed_ipa.mjs)
- [scripts/ios/package.json](/C:/Users/matte/AndroidStudioProjects/ScoutEventi/scripts/ios/package.json)

Requirements:

- Node.js + npm
- a GitHub token with permission to manage Actions secrets and workflow runs on the repository
- the Apple signing certificate and provisioning profile files

Notes:

- `TeamId` is inferred automatically from the provisioning profile unless you explicitly override it
- `CertificatePassword` is optional and is needed only if your `.p12` was exported with a password

## Unlisted App distribution

If you want a direct App Store link that installs on Apple devices but does not appear in search results, use Apple's Unlisted App flow.

Repository support:

- dedicated workflow: [ios-unlisted-release.yml](/C:/Users/matte/AndroidStudioProjects/ScoutEventi/.github/workflows/ios-unlisted-release.yml)
- Windows launcher: [start_unlisted_release.ps1](/C:/Users/matte/AndroidStudioProjects/ScoutEventi/scripts/ios/start_unlisted_release.ps1)
- operational checklist: [ios-unlisted-release.md](/C:/Users/matte/AndroidStudioProjects/ScoutEventi/docs/ios-unlisted-release.md)

This flow builds an `app-store` IPA, uploads it to App Store Connect, and leaves you only the Apple-side submission steps:

1. complete App Store metadata
2. submit to App Review
3. request unlisted distribution from Apple

Once Apple approves the unlisted request, you can share the generated App Store link with iPhone and iPad users.

### Which export method to use

When you manually start the workflow, choose one of these:

- `ad-hoc`: generates an installable IPA for physical iPhones/iPads already registered in the provisioning profile
- `development`: generates a development-signed IPA for registered development devices
- `app-store`: generates the IPA meant to App Store Connect / TestFlight delivery

### Important Apple limitation

If you need a standalone installation file for iPhone users, `ad-hoc` works only for devices whose UDIDs are present in the provisioning profile. If you need distribution to any iPhone user without registering each device, Apple requires TestFlight or the App Store.

### Output

After a successful run, download the artifact from GitHub Actions:

- `ios-ipa-ad-hoc`
- `ios-ipa-development`
- `ios-ipa-app-store`

Each artifact contains:

- the signed `.ipa`
- `archive.log`
- `export.log`
- the generated `export-options.plist`

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
