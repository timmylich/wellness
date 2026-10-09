# Architecture

Wellness is a single-user, offline-first Expo app. There is no backend.

## Data flow

```
screens (src/app) ──read──▶ useStore(selector) ◀──notify── store (src/store/store.ts)
        │                                                   ▲
        └──call──▶ actions (src/store/actions.ts) ──setState┘
                                                            │ debounced 300 ms
                                                            ▼
                                                     AsyncStorage
```

- **`store/types.ts`** — the whole persisted shape, `AppData`. The backup file *is* this object.
- **`store/store.ts`** — one immutable object, `useSyncExternalStore` subscriptions, debounced
  persistence, `normalize()` for forward-compatible imports, usage-day tracking.
- **`store/actions.ts`** — every mutation. Screens never build new state themselves.

## Health math (`lib/calc.ts`)

Pure functions with cited sources, covered by unit tests in `lib/__tests__`.

- Expenditure = BMR (Mifflin–St Jeor) × lifestyle factor + logged workouts + steps above 3000.
  The lifestyle factor intentionally excludes exercise to avoid double counting.
- Calorie target = expenditure + goal delta (pace × 7700 / 7) + adaptive correction,
  never below max(1200/1500, 0.9 × BMR).
- Every nutrient has a `Range { min, target, max, lowerIsBetter? }`. Users can override min/max.
- Adaptive correction looks at the previous 7 days with food entries and spreads half the
  imbalance over the next week, capped at ±15 %.

## Food search (`lib/foodApi.ts`)

1. Local: user foods + bundled `data/foods.ts`, word-wise substring match (ё = е).
2. Online (optional): Open Food Facts search API, debounced, abortable. Foods picked from
   OFF are saved locally so they work offline later.
3. Barcode: OFF product API.

## Navigation

- `app/(tabs)` — bottom bar: Tips · Overview (default) · More.
- `app/(tabs)/index.tsx` — Overview: a rounded tab bar of enabled sections and a pinned "+"
  button. Sections are plain components in `src/sections/` (Summary, Nutrition, Water,
  Activity, Heart, Body).
- `app/settings/[section].tsx` — settings sub-pages listed in More (`data/settingsPages.ts`).

## i18n

`src/i18n/ru.ts` defines the dictionary shape (`Dict`); `en.ts` must match it (enforced by
TypeScript and a unit test). Values can be functions for interpolation. Use
`const L = useT()` in components. Pure logic (`lib/calc.ts`, `lib/advice.ts`) returns keys,
never UI text. Long-form content (tips) lives in per-language data files.

## UI

- `components/ui.tsx` — themed primitives (Card, T, Button, Field, Hint, Tap, …). `Tap` gives
  every tappable element spring feedback.
- `components/Icon.tsx` — Lucide icons behind app-level names, optional glow.
- `lib/motion.ts` — animation toggle (settings + OS reduce-motion), `animateLayout()`.
- `components/charts.tsx` — BarChart, LineChart, RangeBar, Ring on `react-native-svg`.
- `lib/theme.ts` — light/dark palettes + user accent.

## Platform notes

- Steps, `device` source (default): `modules/wellness-native` (local Expo module, Kotlin) reads
  Android's hardware TYPE_STEP_COUNTER — a cumulative "steps since boot" value maintained by the
  sensor hub even while the app is closed. `lib/deviceSteps.ts` turns consecutive readings into
  daily increments (reboot-aware). Readings happen every 5 s in the foreground and from a
  WorkManager task (`lib/backgroundSteps.ts`, expo-background-task, ≥15 min). OEM battery
  savers can delay the task; `lib/battery.ts` opens autostart/battery settings.
- Steps, `healthConnect` source (`lib/healthConnect.ts`): daily totals are read on launch, on
  resume and every 5 minutes. On Android 13 and older the Health Connect app must be installed.
- The same native module exposes the system animator duration scale; `lib/motion.ts` scales all
  JS animation durations by it.
- Online food results are cached on the device (`lib/foodCache.ts`, separate from AppData).
- Weight-scaled norms use `referenceWeight()` (adjusted body weight) above BMI 25.
