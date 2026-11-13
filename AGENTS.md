This is a native Android app: Kotlin + Jetpack Compose, one activity, no Google Play services.
Prioritize mobile performance, small APK size and F-Droid compatibility.

## Commands

```bash
./gradlew testDebugUnitTest   # unit tests
./gradlew lintDebug           # Android lint
./gradlew installDebug        # build and install on a device
./gradlew assembleRelease     # unsigned release APK (scripts/install-signed.ps1 builds, signs, installs)
```

Run tests and lint before declaring any task done.

## Layout

Code: `app/src/main/java/io/github/timmylich/wellness/` — `data/` (model, store, actions),
`lib/` (logic), `i18n/`, `ui/` (shared composables), `screens/`, `steps/`.
Bundled data and UI strings: `app/src/main/assets/`. See `docs/architecture.md`.

## Conventions

- Code comments, commits and docs in English. UI strings only via the dictionaries
  (`assets/i18n_ru.json` + `i18n_en.json`, same keys); in composables `L["key"]`, `L.t("key", …)`.
- Health formulas and limits live in `lib/Calc.kt` and must cite a source; add a unit test in
  `app/src/test`.
- All persisted state is `AppData` (`data/Model.kt`); mutate only through `data/Actions.kt`.
  The JSON format must stay compatible with older backups (including 0.5.x React Native ones).
- No analytics, ads or trackers, no Google Play services/Firebase. Network access is limited to
  the optional food search (Open Food Facts, USDA), the optional GitHub Releases update check and
  the optional shared pantry (Supabase, end-to-end encrypted; `lib/PantryShare.kt`).
- Prefer platform APIs and existing helpers over new dependencies.
