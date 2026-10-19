# Releasing

Releases are built by GitHub Actions (`.github/workflows/release.yml`) and published as
GitHub Releases with a signed APK and its SHA-256 checksum. The app's optional update check
(Settings → Notifications) reads the latest GitHub Release.

## One-time setup: signing key

Every update must be signed with the **same key**, or Android refuses to install it over the
previous version. Create the key once and keep a backup somewhere safe (password manager,
encrypted drive). Losing it means users have to uninstall and reinstall.

```bash
keytool -genkeypair -v -keystore wellness-release.jks -alias wellness \
  -keyalg RSA -keysize 4096 -validity 10000
```

Add four repository secrets (GitHub → Settings → Secrets and variables → Actions):

| Secret | Value |
| --- | --- |
| `ANDROID_KEYSTORE_BASE64` | `base64 -w0 wellness-release.jks` (PowerShell: `[Convert]::ToBase64String([IO.File]::ReadAllBytes("wellness-release.jks"))`) |
| `ANDROID_KEYSTORE_PASSWORD` | keystore password |
| `ANDROID_KEY_ALIAS` | `wellness` |
| `ANDROID_KEY_PASSWORD` | key password |

Never commit the `.jks` file (it is git-ignored).

> APKs built locally with `npm run build:apk` are signed with the debug key. They cannot be
> updated by a CI-signed APK (and vice versa): uninstall once when switching.

## Making a release

1. Bump `version` and `android.versionCode` in `app.json`, and `version` in `package.json`.
2. Move the `[Unreleased]` notes in `CHANGELOG.md` under a new `## [x.y.z] — YYYY-MM-DD` heading.
3. Add `fastlane/metadata/android/en-US/changelogs/<versionCode>.txt` (and `ru-RU`), used by F-Droid.
4. Commit, then tag and push:

   ```bash
   git tag v0.5.1
   git push origin main v0.5.1
   ```

The workflow checks that the tag matches `app.json`, runs `npm run check`, builds, signs and
publishes the release. Running the workflow manually ("Run workflow") creates a draft.

## F-Droid

The app has no proprietary dependencies (no Google Play services, no Firebase) and the store
listing lives in `fastlane/metadata/android/` in the format F-Droid reads.

To submit, open a merge request to [fdroiddata](https://gitlab.com/fdroid/fdroiddata) adding
`metadata/io.github.timmylich.wellness.yml`. A starting point:

```yaml
Categories:
  - Sports & Health
License: AGPL-3.0-only
SourceCode: https://github.com/timmylich/wellness
IssueTracker: https://github.com/timmylich/wellness/issues
Changelog: https://github.com/timmylich/wellness/blob/main/CHANGELOG.md
Donate: https://web.tribute.tg/d/RVs

AutoName: Wellness
RepoType: git
Repo: https://github.com/timmylich/wellness.git

Builds:
  - versionName: 0.5.1
    versionCode: 8
    commit: v0.5.1
    sudo:
      - curl -Lo node.tar.xz https://nodejs.org/dist/v22.11.0/node-v22.11.0-linux-x64.tar.xz
      - tar xJf node.tar.xz --strip-components=1 -C /usr/local
    init: npm ci
    prebuild: EXPO_PUBLIC_UPDATE_CHECK=0 npx expo prebuild --platform android --no-install
    subdir: android/app
    gradle:
      - yes

AutoUpdateMode: Version
UpdateCheckMode: Tags
```

`EXPO_PUBLIC_UPDATE_CHECK=0` removes the GitHub update check from F-Droid builds: F-Droid
delivers updates itself. F-Droid reviewers may ask for adjustments (for example, scanning
`node_modules` for prebuilt binaries); follow their comments on the merge request.
