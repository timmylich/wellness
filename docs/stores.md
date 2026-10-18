# Publishing in app stores

Everything a store asks for is already in the repository:

| What | Where |
| --- | --- |
| APK and AAB (signed) | GitHub → Releases (built by `.github/workflows/release.yml`) |
| Icon 512×512 | `fastlane/metadata/android/*/images/icon.png` |
| Feature graphic 1024×500 | `fastlane/metadata/android/*/images/featureGraphic.png` |
| Short / full description | `fastlane/metadata/android/*/short_description.txt`, `full_description.txt` |
| What's new | `fastlane/metadata/android/*/changelogs/<versionCode>.txt` |
| Privacy policy URL | `https://github.com/timmylich/wellness/blob/main/docs/PRIVACY.md` |
| Screenshots | add 4–8 PNGs to `fastlane/metadata/android/*/images/phoneScreenshots/` |

Typical form answers: category **Health & Fitness**; contains ads **no**; in-app purchases
**no**; target audience **18+** (health data); data collected **none** (everything stays on the
device); content rating questionnaire: no violence, no user-generated sharing → "Everyone / 3+".
"Medical device": no, informational only.

Always upload the **same signed file** from GitHub Releases, so all stores can update each
other's installs.

## Store by store

| Store | Account | Upload | Notes |
| --- | --- | --- | --- |
| F-Droid | none | merge request to fdroiddata (see `docs/releasing.md`) | F-Droid builds the app itself and signs with its own key |
| RuStore | rustore.ru/developer, Gosuslugi or individual, free | APK or AAB | review 1–3 days; RuStore SDK is **not** needed |
| Huawei AppGallery | developer.huawei.com, ID verification | APK or AAB | no HMS SDK needed; choose "Health & Fitness" |
| Samsung Galaxy Store | seller.samsungapps.com | APK | needs privacy policy URL |
| Xiaomi GetApps | dev.mi.com/xiaomihyperos | APK | individual developer accounts may require ID verification |
| NashStore | developer.nashstore.ru | APK | |
| MTS AppBazar | ask via their developer form | APK | |
| Aptoide | connect.aptoide.com | APK | |
| Uptodown | uptodown.com developer console | APK | |
| Amazon Appstore | developer.amazon.com | APK or AAB | answer "no" to Google Play services |
| APKPure | apkpure.com developer console | APK | |
| APKMirror | no self-upload for most apps; they mirror apps published elsewhere (e.g. GitHub) after verifying the signature | — | contact them once the app is on GitHub Releases |
