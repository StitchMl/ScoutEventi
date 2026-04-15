# iOS Unlisted Release

This project can now produce an App Store build for Apple's unlisted distribution flow.

## Outcome

If you complete the Apple-side steps below, the final user-facing result is an App Store link that installs the app on iPhone and iPad but does not appear in App Store search, charts, recommendations, or categories.

Apple requires this sequence:

1. Create an App Store Connect app record.
2. Upload an App Store build.
3. Complete metadata and screenshots.
4. Submit the app to App Review and state that it is intended for unlisted distribution.
5. Submit the unlisted distribution request to Apple.
6. Share the generated App Store link with your users.

## Official Apple references

- Unlisted app distribution: https://developer.apple.com/support/unlisted-app-distribution/
- Set distribution methods: https://developer.apple.com/help/app-store-connect/manage-your-apps-availability/set-distribution-methods
- Add a new app record: https://developer.apple.com/help/app-store-connect/create-an-app-record/add-a-new-app
- Upload builds: https://developer.apple.com/help/app-store-connect/manage-builds/upload-builds/
- App Store Connect API keys: https://developer.apple.com/help/app-store-connect/get-started/app-store-connect-api
- Transporter authentication with API keys: https://help.apple.com/itc/transporteruserguide/en.lproj/static.html

## Repository automation

Files:

- [ios-unlisted-release.yml](/C:/Users/matte/AndroidStudioProjects/ScoutEventi/.github/workflows/ios-unlisted-release.yml)
- [start_unlisted_release.ps1](/C:/Users/matte/AndroidStudioProjects/ScoutEventi/scripts/ios/start_unlisted_release.ps1)
- [github_signed_ipa.mjs](/C:/Users/matte/AndroidStudioProjects/ScoutEventi/scripts/ios/github_signed_ipa.mjs)
- [ios-unlisted-review-notes.md](/C:/Users/matte/AndroidStudioProjects/ScoutEventi/docs/ios-unlisted-review-notes.md)

The workflow does two things:

- builds a signed `app-store` IPA on `macos-15`
- uploads the build to App Store Connect through an App Store Connect API key

## Required Apple assets

- Apple Distribution certificate exported as `.p12`
- App Store provisioning profile `.mobileprovision`
- App Store Connect API key `.p8`
- App Store Connect API Key ID
- App Store Connect Issuer ID

## Windows command

```powershell
$env:GITHUB_TOKEN="YOUR_GITHUB_TOKEN"

.\scripts\ios\start_unlisted_release.ps1 `
  -CertificatePath "C:\signing\ScoutEventiDistribution.p12" `
  -ProvisioningProfilePath "C:\signing\ScoutEventi.mobileprovision" `
  -AppStoreConnectKeyPath "C:\signing\AuthKey_ABCDEFG123.p8" `
  -AppStoreConnectKeyId "ABCDEFG123" `
  -AppStoreConnectIssuerId "00000000-0000-0000-0000-000000000000"
```

If your `.p12` was exported with a password, add:

```powershell
-CertificatePassword "YOUR_P12_PASSWORD"
```

## App Store Connect steps

1. In App Store Connect, create a new iOS app record whose bundle ID matches the iOS target bundle ID.
2. Make sure the app uses Public distribution. Apple says unlisted apps begin as Public and are later changed to Unlisted after approval.
3. Run the Windows command above to upload the build.
4. Wait until App Store Connect finishes processing the build.
5. Complete the required metadata:
   support URL, privacy policy URL, age rating, screenshots, description, keywords, copyright, and app review contact data.
6. Attach the processed build to the version you want to submit.
7. Paste the text from [ios-unlisted-review-notes.md](/C:/Users/matte/AndroidStudioProjects/ScoutEventi/docs/ios-unlisted-review-notes.md) into Review Notes and adapt it if needed.
8. Submit the app to App Review.
9. After the app is submitted to review or already approved, request unlisted distribution here:
   https://developer.apple.com/contact/request/unlisted-app/

## What users receive

After Apple approves the unlisted request, App Store Connect marks the app as Unlisted App and the app becomes installable through a direct App Store link.

That is the link you send to Apple users.
