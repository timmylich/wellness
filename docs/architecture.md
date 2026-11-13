# Architecture

Wellness is a single-user, offline-first native Android app (Kotlin, Jetpack Compose). There is
no backend. Code lives in `app/src/main/java/io/github/timmylich/wellness/`.

## Data flow

```
screens / ui ──read──▶ Store.data (Compose state) ◀──update── Actions
                                │ debounced 300 ms, background thread
                                ▼
                     files/data.json (atomic write)
```

- **`data/Model.kt`** — the whole persisted shape, `AppData` (kotlinx.serialization). The backup
  file *is* this object, in the same JSON format as the React Native versions (0.5.x), so
  backups move between versions in both directions. `normalized()` fills and migrates old data.
- **`data/Store.kt`** — one immutable object in a Compose state; composables that read it
  recompose on change. Loaded off the main thread at start. On the first start after updating
  from 0.5.x it takes over the old AsyncStorage data (SQLite `RKStorage`).
- **`data/Actions.kt`** — every mutation. Screens never build new state themselves.
- **`data/Assets.kt`** — bundled read-only data from `assets/`, parsed on first use.

## Health math (`lib/Calc.kt`)

Pure functions with cited sources, covered by unit tests in `app/src/test`.

- Expenditure = BMR (Mifflin–St Jeor) × lifestyle factor + logged workouts + steps above 3000.
  The lifestyle factor intentionally excludes exercise to avoid double counting.
- Calorie target = expenditure + goal delta (pace × 7700 / 7) + adaptive correction, never below
  max(1200, the energy covering the macro minimums).
- Every nutrient has a `Range(min, target, max, lowerIsBetter)`. Users can override min/max.
- Adaptive correction looks at the previous 7 days with food entries and spreads half the
  imbalance over the next week, capped at ±15 %.
- Weight-scaled norms use `referenceWeight()` (adjusted body weight) above BMI 25.

## Food search (`lib/FoodApi.kt`)

1. Local: user foods, built-in foods, cached online foods and CIQUAL; a forgiving word matcher
   (stems, small typos, any word order, ё = е).
2. Online (optional): Open Food Facts and, for Latin queries, USDA, in parallel, debounced.
   Results are cached on the device (`FoodCache`, separate from AppData, not in backups).
3. Barcode: OFF product API.

## Navigation and UI

- `ui/Nav.kt` — a tiny stack: pushed screens slide in over the tabs, which stay composed.
- `screens/App.kt` — bottom tabs (Tips · Pantry · Overview · Health · More) in a pager.
- `screens/Overview.kt` — sections in a pager with a floating section bar and the "+" menu.
  `ui/PagerJump.kt` makes taps on distant tabs switch at once and slide in, without flashing
  the tabs in between, while the bar highlight glides.
- `ui/Kit.kt` — themed primitives (Card, T, Button, Field, Hint, `Modifier.tap`, …).
- `ui/Charts.kt` — BarChart, LineChart, RangeBar, Ring on Canvas; tap or hold to see values.
- `ui/Overlays.kt` — dialogs, bottom sheets, wheel picker, slider, date pickers.
- `ui/Theme.kt` — light/dark/AMOLED palettes + user accent.
- Animations: `MainActivity` runs Compose with its own duration scale — the system "animator
  duration scale" can slow animations down or turn them off, but never speeds them up.

## i18n

`assets/i18n_ru.json` and `i18n_en.json` share the same keys. `L["section.key"]` returns a string,
`L.t("key", args…)` fills a template, `L.list(…)` returns arrays. Pure logic returns keys, never
UI text. A unit test checks that every key the code uses exists in both languages.

## Platform

- Steps, `device` source (default): `steps/StepService.kt` is a low-priority foreground service
  holding a batched registration of the hardware step counter (many phones count only while
  someone is registered). Steps are bucketed per day in SharedPreferences and drained into
  AppData when the app is open; a boot receiver restarts the service.
- Steps, `healthConnect` source (`lib/System.kt`): daily totals are read on resume and every
  5 minutes; while Health Connect is missing or not connected the device counter is used.
- Notifications (`lib/System.kt`, `Reminders`): local only, AlarmManager, no Firebase. See
  [notifications.md](notifications.md).
- Backups (`lib/Backup.kt`): Storage Access Framework or the share sheet; photos of health records
  travel as base64. A reminder offers a backup every 30 days (`BackupPrompt`).
- Pantry tab (`screens/Pantry.kt`): three swipeable pages — products with expiry, opened state
  (`ShelfBase.opened`), typical package sizes and counts; the shopping list (bought items are
  offered back on the first page); statistics, history (`AppData.pantryLog`) and family members.
- Shared pantry (`lib/PantryShare.kt`, server `supabase/pantry-share.sql`): a 16-character code;
  the pantry and shopping lists are AES-GCM encrypted on the phone and stored in Supabase under a hash of the code.
  Sync is a 3-way merge with optimistic versions; the server rate-limits and deletes lists idle
  for 7 days. The code is kept in SharedPreferences, outside AppData and backups. Configure with
  `supabaseUrl` / `supabaseKey` Gradle properties; without them the feature is hidden.
