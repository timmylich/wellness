# Changelog

All notable changes to this project are documented here.
The format follows [Keep a Changelog](https://keepachangelog.com/en/1.1.0/), and the project
uses [Semantic Versioning](https://semver.org/).

## [Unreleased]

## [0.4.0] — 2026-10-07

### Added
- Firebase Cloud Messaging wired in (`src/lib/firebaseMessaging.ts`); opt-in via Settings →
  Notifications. `google-services.json` is git-ignored.
- Nutrient details sheet: gauge, explanation, recommendations and today's top contributing foods.
- ~2300 generic European foods from ANSES-CIQUAL (Etalab Open Licence), offline.
- Open Food Facts names in Ukrainian, Belarusian, Kazakh, Georgian, Armenian, German, French,
  Polish are now used for display and search.
- Live step-counter subscription while the app is open; step-counter debug logging.

### Changed
- Calorie range: target in the middle (±10 %), minimum covers the protein/fat/carb minimums.
- Overview section bar: transparent, fixed height; labels expand smoothly with the swipe.
- Bottom tabs animate when switching.
- Water chart axis stops at the recommended maximum (grows to value + 1 L when exceeded).
- Icon glow is a soft radial halo (no more square clipping).

### Fixed
- Nutrition stat cards could not be swiped inside the overview pager.
- Tapping the water card on the summary now opens the Water section.
- Steps were not recorded on devices whose step counter reports only on change, and when
  Health Connect was selected but not installed (the device counter is now the fallback).

## [0.3.0] — 2026-10-07

### Added
- Background step counting without Health Connect: the phone's hardware step counter is read in
  the foreground and by a periodic background task (local native module `modules/wellness-native`
  + expo-background-task). A first-run explainer covers limitations and OEM battery settings.
- "Other drink" water entries: count towards the danger limit, not the plain-water target;
  water log with drink types.
- Swipe between overview sections; the tab highlight follows the swipe.
- "+" button fans out quick actions around itself instead of a scrolling sheet.
- ± buttons with a custom step when editing a food amount.
- ~160 more built-in foods (popular products, ready meals, fast food, drinks, desserts).
- USDA FoodData Central as a second online database (optional personal API key).
- On-device cache for online food searches and barcodes; cached foods are searchable offline.
- "Initial setup" entry in More to re-run onboarding.
- Notification groundwork for Firebase Cloud Messaging (`src/lib/notifications.ts`,
  docs/notifications.md) and notification preferences.
- Proper branded splash screen.

### Changed
- Animations follow Android's "Animator duration scale" developer option; faster bottom sheets;
  expandable hints and tips animate their height.
- Nutrition stat cards reduced from 7 to 4; the card height adapts smoothly while swiping.
- Range bars: track and fill now share the same rounded shape.
- Section tab bar on the overview has no opaque background.
- Tips: tag row is sticky with a transparent background; fixed the empty gap under the search.

### Fixed
- Input fields stretched to 120 px tall in vertical layouts (water card, tips search).

## [0.2.0] — 2026-10-07

### Added
- English UI language alongside Russian (follows the system language by default); typed i18n
  dictionaries in `src/i18n` make adding languages straightforward.
- Background step counting via Health Connect (Settings → Steps & Health Connect).
- Animations throughout the app (press feedback, expanding cards, section transitions, animated
  charts) with a toggle in settings; they also respect the system "reduce motion" setting.
- Universal pinned "+" button on the overview with a category picker.
- Quick water button with the user's chosen amount on the summary.
- Tips: what IARC carcinogen groups mean; benzene and exhaust fumes.

### Changed
- New navigation: bottom bar Tips · Overview · More; sections are tabs in a rounded bar at the top
  of the overview.
- Weight change pace is a slider from −1.5 to +1.5 kg/week (lose / maintain / gain) with warnings.
- Protein, fat floor and water targets use an adjusted reference weight at high body weight
  instead of total weight.
- Nutrition redesigned: swipeable stat cards (max half the screen), then an add button and a flat
  log — no more breakfast/lunch/dinner split.
- Food search shows local and online results in one list.
- Tip tags scroll to their category instead of filtering.
- "More" is a plain list of settings pages and info.
- Emoji replaced with minimal line icons in the accent color.
- Default branch renamed to `main`.

## [0.1.0] — 2026-10-06

### Added
- Onboarding wizard: profile, goal weight and pace, lifestyle activity, section selection.
- Nutrition: 12 nutrient scales with personal min/target/max, meal log, food search
  (bundled CIS database, Open Food Facts, barcode), custom foods, favorites, recent foods,
  "repeat yesterday", quick calorie entry, trends, energy balance, rule-based advice,
  optional adaptive targets.
- Water tracker with big add button, presets, history, chart and danger zone.
- Activity: pedometer, 20 workout templates, custom templates and entries, steps chart.
- Heart: blood pressure and pulse log, classification, charts, heart-rate zones.
- Body: weight trend, body fat (measured or Navy estimate), BMI, waist-to-height, healthy weight.
- Health tips library with search.
- Settings for everything; section toggles; theme and accent color.
- JSON backup export/import and food pack import.
- Donation reminder on usage milestones (placeholder, can be disabled).
