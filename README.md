<p align="center">
  <img src="docs/logo.png" width="96" alt="Wellness logo" />
</p>

<h1 align="center">Wellness</h1>

<p align="center">
  An open-source, offline-first health tracker for Android.<br/>
  Nutrition · Water · Steps & workouts · Blood pressure · Body composition · Health tips
</p>

<p align="center">
  No ads · No accounts · No analytics · Your data never leaves your phone
</p>

---

> Available in English and Russian. [Описание на русском ниже](#по-русски).

## Features

- **Nutrition** — 12 daily scales (calories, protein, fat, carbs, sugar, added sugar, sodium,
  fiber, saturated & trans fat, cholesterol, potassium). Each has a *minimum* (below it your
  health suffers), a *target* and a *maximum* (above it the goal is unreachable or intake is
  unsafe). Ranges are personal: computed from your profile, goal pace and today's activity.
- **Adaptive targets** (optional) — over- or under-eating during the last 7 days gently shifts
  the calorie target (±15 % max) to compensate.
- **Food database without AI, but fast** — favorites, recent foods, "repeat yesterday",
  a bundled offline database of common CIS dishes, [Open Food Facts](https://openfoodfacts.org)
  search (great coverage of Russian/CIS products and chains such as Dodo Pizza), USDA FoodData
  Central, an on-device cache that makes previously found foods available offline, barcode lookup,
  custom foods, and importable **food packs**.
- **Energy balance** — intake vs. expenditure (BMR × lifestyle + workouts + steps) with
  surplus/deficit converted into estimated fat mass.
- **Rule-based advice** — actionable tips when you are short on protein/fiber/potassium or over
  on salt/added sugar/saturated fat.
- **Water** — one big button, presets, history, charts, recommended min/max and a clearly marked
  dangerous range (hyponatremia).
- **Activity** — steps counted in the background by the phone's hardware step counter (no
  extra apps needed) or taken from **Health Connect** (Google Fit, Mi Fitness, Zepp, Samsung Health…), 20 built-in workout templates (MET-based),
  custom templates, manual entries.
- **Heart** — blood pressure and pulse log, ACC/AHA classification, charts, heart-rate zones.
- **Body** — weight trend with moving average, body fat (measured or US Navy estimate),
  BMI, waist-to-height ratio, healthy weight range.
- **Tips** — fresh air and ventilation, drinking water and filters, cancer risk factors and
  screening, sun safety, back health, sleep, blood pressure, nutrition.
- **Fully configurable** — language, enable/disable sections, theme & accent color, animations,
  goal pace slider (−1.5…+1.5 kg/week), custom nutrient limits, water presets and thresholds,
  hints on/off.
- **Sensible at any body weight** — protein and water targets use an adjusted reference weight,
  so they stay realistic at high body weight.
- **Export / import** — the full dataset as one JSON file.

## Screens & stack

- [Expo](https://expo.dev) SDK 57 · React Native 0.86 · TypeScript · Expo Router
- Charts are hand-written with `react-native-svg` (no charting dependency)
- State: a ~100-line store on `useSyncExternalStore`, persisted to AsyncStorage
- i18n: typed dictionaries (`src/i18n`), no runtime library
- Icons: [Lucide](https://lucide.dev); background steps: Health Connect

See [docs/architecture.md](docs/architecture.md) for a tour of the code.

## Getting started

Requirements: Node 20+, JDK 17–21, Android SDK (Android Studio).

```bash
npm install
npm run android        # builds a dev client and runs it on a device/emulator
```

Build a release APK locally:

```bash
npx expo prebuild --platform android
npm run build:apk      # → android/app/build/outputs/apk/release/app-release.apk
```

> The generated `android/` folder is not committed (Continuous Native Generation).
> Configure native behaviour in `app.json` instead.

Quality checks (also run in CI):

```bash
npm run check          # typecheck + lint + unit tests
```

## Privacy

All data is stored locally. The only network request is the optional Open Food Facts search,
which sends nothing but the search text or barcode. It can be turned off in settings.
Health Connect access is optional and read-only (steps), and stays on the device.

## Medical disclaimer

Wellness is not a medical device. Recommendations are general guidelines for healthy adults
(WHO, IOM/EFSA, ACC/AHA) and do not replace a doctor's advice.

## Contributing

Contributions are welcome — code, translations, food data and health tips.
Please read [CONTRIBUTING.md](CONTRIBUTING.md).

## License

[MIT](LICENSE). Food data from Open Food Facts is available under the
[ODbL](https://opendatacommons.org/licenses/odbl/1-0/); ANSES-CIQUAL 2020 data under the
[Etalab Open Licence 2.0](https://www.etalab.gouv.fr/licence-ouverte-open-licence/).

Building with push notifications requires your own `google-services.json` (see
[docs/notifications.md](docs/notifications.md)).

---

## По-русски

**Wellness** — бесплатный трекер здоровья с открытым кодом. Без рекламы, регистрации и слежки:
все данные хранятся только на телефоне.

- **Питание**: 12 шкал с личными диапазонами (минимум — цель — максимум), база продуктов СНГ,
  поиск в Open Food Facts (в том числе Додо Пицца), штрихкоды, свои продукты, избранное,
  «повторить вчера», графики, энергобаланс с пересчётом в жировую массу, советы.
- **Вода**: большая кнопка, история, графики, норма и опасный диапазон.
- **Активность**: шагомер или фоновый подсчёт шагов через Health Connect, шаблоны тренировок, свои нагрузки.
- **Сердце**: давление и пульс, классификация, графики, пульсовые зоны.
- **Тело**: вес, % жира, талия, ИМТ, подходящий вес.
- **Советы**: воздух, вода, профилактика рака, солнце, спина, сон.
- Всё настраивается: язык (русский/английский), разделы, тема, анимации, темп ползунком; экспорт и импорт данных.

Хотите помочь? Смотрите [CONTRIBUTING.md](CONTRIBUTING.md).
