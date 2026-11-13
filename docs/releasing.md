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

> `./gradlew assembleRelease` builds an unsigned APK. `scripts/install-signed.ps1` builds it, signs it with
> the release key and installs it over ADB. A debug-signed build cannot update a release-signed
> one (and vice versa): uninstall once when switching.

## Testing a release build on your phone

```powershell
./scripts/install-signed.ps1            # build, sign with key.jks, install over ADB
./scripts/install-signed.ps1 -NoBuild   # sign and install the APK already built
```

The script sets `JAVA_HOME` (JDK 17+), `ANDROID_HOME` and `GRADLE_USER_HOME` for this machine
and asks for the keystore password. The result updates the installed app in place.

## Making a GitHub release

1. Bump `versionName` and `versionCode` in `app/build.gradle.kts` (versionCode always grows).
2. In `CHANGELOG.md`, move the `[Unreleased]` notes under `## [x.y.z] — YYYY-MM-DD`. This
   section becomes the release description on GitHub, so write it for users.
3. Add `fastlane/metadata/android/en-US/changelogs/<versionCode>.txt` and `ru-RU/…` (up to
   500 characters, plain text). F-Droid shows them as "What's new".
4. Check locally: `./gradlew testDebugUnitTest lintDebug`, then install with
   `./scripts/install-signed.ps1` and try the app.
5. Commit, merge into `main`, tag and push:

   ```bash
   git add -A
   git commit -m "Release 0.6.0"
   git checkout main
   git merge rework            # if the work was on a branch
   git tag v0.6.0
   git push origin main v0.6.0
   ```

6. GitHub → Actions → **Release**: wait for the green check (~5 min). The workflow checks that
   the tag matches `versionName`, runs the tests, builds, signs with the repository secrets and
   publishes `wellness-x.y.z.apk`, its `.sha256` and the AAB.
7. Open the release page and check the notes and the APK. Installed apps with the update check
   on will offer the update on their next check.

If the workflow fails, fix it, then move the tag: `git tag -f v0.6.0 && git push -f origin v0.6.0`
(only while nobody has downloaded the release). Running the workflow manually ("Run workflow")
creates a draft release for a dry run.

## IzzyOnDroid

Not available: IzzyOnDroid declined the app in 2026-11 under its policy on LLM-assisted
development (the APK and metadata scans passed). Use F-Droid instead.

## F-Droid

F-Droid builds the app from source on its own servers and signs it with **its own key**, so the
F-Droid build cannot update a GitHub install (and vice versa); users pick one
source. The app has no proprietary dependencies (no Google Play services, no Firebase), and the
store listing lives in `fastlane/metadata/android/` in the format F-Droid reads.

One-time submission:

1. Create a GitLab account, fork <https://gitlab.com/fdroid/fdroiddata>.
2. Add `metadata/io.github.timmylich.wellness.yml`:

   ```yaml
   Categories:
     - Sports & Health
   License: AGPL-3.0-only
   AuthorName: timmylich
   SourceCode: https://github.com/timmylich/wellness
   IssueTracker: https://github.com/timmylich/wellness/issues
   Changelog: https://github.com/timmylich/wellness/blob/main/CHANGELOG.md
   Donate: https://web.tribute.tg/d/RVs

   AutoName: Wellness
   RepoType: git
   Repo: https://github.com/timmylich/wellness.git

   Builds:
     - versionName: 0.6.0
       versionCode: 10
       commit: v0.6.0
       subdir: app
       gradle:
         - yes
       gradleprops:
         - updateCheck=false

   AutoUpdateMode: Version
   UpdateCheckMode: Tags
   CurrentVersion: 0.6.0
   CurrentVersionCode: 10
   ```

   `updateCheck=false` removes the GitHub update check from F-Droid builds: F-Droid delivers
   updates itself.
3. Optionally test the recipe locally with `fdroid build -v -l io.github.timmylich.wellness`
   (fdroidserver), or rely on the CI pipeline of the merge request.
4. Open a merge request to fdroiddata with the template "App inclusion". Reviewers usually
   answer within days to weeks; follow their comments in the merge request.

After inclusion, F-Droid's bot checks the tags (`UpdateCheckMode: Tags`) and builds every new
`vX.Y.Z` tag on its own, usually within a few days. Per release you only need the steps of
"Making a GitHub release": the versionCode bump and the `changelogs/<versionCode>.txt` files.
