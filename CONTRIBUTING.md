# Contributing to Wellness

Thanks for helping! Every kind of contribution is welcome: bug reports, code, food data,
health tips, translations and design.

## Ground rules

- **Privacy first.** No analytics, trackers, ads or mandatory accounts. New network calls must
  be optional and documented in the About screen and README.
- **Keep it light.** Prefer Expo modules and small hand-written code over heavy dependencies.
  Discuss new dependencies in an issue first.
- **Cite health claims.** Every formula, limit or tip must reference a mainstream source
  (WHO, national health services, peer-reviewed literature) in a code comment or `source` field.
- **Language.** Code, comments, commits and docs — English. UI strings never go into
  components directly: add them to `src/i18n/ru.ts` **and** `src/i18n/en.ts` (TypeScript and a
  unit test check that both have the same keys). New languages are welcome — copy `en.ts`.

## Development setup

```bash
npm install
npm run android   # dev build on a device or emulator
npm run check     # typecheck + lint + tests — must pass before a PR
```

Use `npx expo install <pkg>` (not `npm install <pkg>`) so versions match the Expo SDK.

## Project layout

```
src/
  app/          routes (Expo Router: every file is a route)
  sections/     overview sections (Summary, Nutrition, Water, Activity, Heart, Body)
  i18n/         typed UI dictionaries (ru, en)
  components/   shared UI: primitives, charts, sheets
  data/         static data: bundled foods, tips, workout templates, nutrient info
  lib/          pure logic: health math, dates, food search, backup, advice
  store/        types, persisted store and actions
docs/           architecture notes and file formats
```

More detail in [docs/architecture.md](docs/architecture.md).

## Adding things

- **A food to the bundled database** — append a row to `src/data/foods.ts`. Values per 100 g;
  mention the source in the PR.
- **A food pack** (e.g. a restaurant menu) — see [docs/food-packs.md](docs/food-packs.md).
  Only publish data you are allowed to share (official nutrition tables, your own measurements).
- **A health tip** — add it to both `src/data/tips.ru.ts` and `src/data/tips.en.ts` with the same `id` and a `source`.
- **A workout template** — `src/data/workouts.ts`, with the MET value from the Compendium of
  Physical Activities.
- **A new persisted field** — add it to `AppData` in `src/store/types.ts` and a default in
  `createDefaultData()`. `normalize()` fills missing fields when older backups are imported;
  bump `SCHEMA_VERSION` and add a migration if the shape changes incompatibly.

## Commits and pull requests

- Small, focused PRs are easier to review.
- Use clear commit messages in the imperative mood: `Add potassium advice rule`.
- Update `CHANGELOG.md` for user-visible changes.

## Code of conduct

Be kind and respectful. Harassment of any kind is not tolerated. Maintainers may remove
comments or contributions that violate this principle.
