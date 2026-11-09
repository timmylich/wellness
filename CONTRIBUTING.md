# Contributing to Wellness

Thanks for helping! Every kind of contribution is welcome: bug reports, code, food data,
health tips, translations and design.

## Ground rules

- **Privacy first.** No analytics, trackers, ads or mandatory accounts. New network calls must
  be optional and documented in the About screen and README.
- **Keep it light.** Prefer the Android platform and small hand-written code over dependencies.
  No Google Play services or Firebase (the app must stay F-Droid compatible). Discuss new
  dependencies in an issue first.
- **Cite health claims.** Every formula, limit or tip must reference a mainstream source
  (WHO, national health services, peer-reviewed literature) in a code comment or `source` field.
- **Language.** Code, comments, commits and docs — English. UI strings never go into code
  directly: add them to `app/src/main/assets/i18n_ru.json` **and** `i18n_en.json` (a unit test
  checks that every key used in the code exists in both). New languages are welcome.

## Development setup

Requirements: JDK 17–21 and the Android SDK (Android Studio works out of the box).

```bash
./gradlew installDebug          # build and install on a device or emulator
./gradlew testDebugUnitTest     # unit tests — must pass before a PR
./gradlew lintDebug             # Android lint
```

## Project layout

```
app/src/main/java/io/github/timmylich/wellness/
  data/      persisted model (AppData), store, actions, bundled data loading
  lib/       pure logic: health math, dates, food search, backup, system services
  i18n/      dictionary access and the few strings with logic
  ui/        shared UI: primitives, charts, sheets, pickers, forms, navigation
  screens/   tabs, overview sections, settings, food search, onboarding
  steps/     step counter foreground service
app/src/main/assets/   UI strings, built-in foods, CIQUAL, tips, shelf life, workouts
app/src/test/          unit tests
docs/                  architecture notes and file formats
```

More detail in [docs/architecture.md](docs/architecture.md).

## Adding things

- **A food to the bundled database** — add an entry to `assets/foods.json` (values per 100 g;
  `ru` and `en` names). Mention the source in the PR.
- **A food pack** (e.g. a restaurant menu) — see [docs/food-packs.md](docs/food-packs.md).
  Only publish data you are allowed to share (official nutrition tables, your own measurements).
- **A health tip** — add it to both `assets/tips_ru.json` and `assets/tips_en.json` with the
  same `id` and a `source`.
- **A workout template** — `assets/workouts.json`, with the MET value from the Compendium of
  Physical Activities, and its English name under `en`.
- **A string** — add the key to both dictionaries. Interpolation uses `{0}` (as is),
  `{0:n1}` (locale number, up to 1 decimal) and `{0:f1}` (fixed decimals); call
  `L.t("key", value)`. Strings with logic live as methods in `i18n/Dict.kt`.
- **A new persisted field** — add it with a default to `AppData` in `data/Model.kt`. Missing
  fields take their defaults when older backups are imported; bump `SCHEMA_VERSION` and extend
  `normalized()` if the shape changes incompatibly. Backups must stay readable both ways.

## Commits and pull requests

- Small, focused PRs are easier to review.
- Use clear commit messages in the imperative mood: `Add potassium advice rule`.
- Update `CHANGELOG.md` for user-visible changes.

## Code of conduct

Be kind and respectful. Harassment of any kind is not tolerated. Maintainers may remove
comments or contributions that violate this principle.
