# Changelog

All notable changes to this project are documented here.
The format follows [Keep a Changelog](https://keepachangelog.com/en/1.1.0/), and the project
uses [Semantic Versioning](https://semver.org/).

## [Unreleased]

## [0.5.2] — 2026-10-29

First public release. Wellness is a free, open-source health tracker for Android that keeps
all your data on the phone: no ads, no accounts, no analytics, no Google services.

**Why Wellness**
- **Private by design** — offline-first, nothing leaves the phone except optional food search
  and update check (both can be turned off). JSON export/import.
- **Evidence-based** — every norm and limit cites WHO, EFSA/IOM, ACC/AHA or USDA and is
  covered by tests.
- **Fast food logging without AI** — offline database (CIS dishes + ~2300 European foods),
  Open Food Facts and USDA search, barcodes, counting by pieces, recents and favourites.
- **Everything in one place** — 12 nutrient scales, energy balance, water, steps without Google
  Fit, workouts, blood pressure, weight trend and body fat, pantry with expiry reminders, tips.
- **Light** — no Google Play services, works on Android 8+, English and Russian.

**Install**: download `wellness-0.5.2.apk` below and open it on your phone. Verify with the
`.sha256` file if you like. Not a medical device.


### Added
- Swiping past the first or last Overview section opens the neighbouring bottom tab.
- Backups now include health record photos, so they survive a reinstall or a new phone.

### Changed
- Editing a weight, measurement or blood pressure entry asks only for the date. Water and
  workout entries keep the time wheels.

### Fixed
- Tab bar highlights no longer jerk back during a switch.
- A crash in the bottom tabs when the set of tabs changed.

## [0.5.1] — 2026-10-19

### Added
- Tap a weight, measurement, blood pressure, water or workout entry to edit all its values,
  date and time.
- Health tab: doctor visits, lab tests and other records with photos (stored privately on the
  phone).
- Pantry moved to the bottom bar.

### Changed
- Food amount is entered as pieces × grams per piece (1 piece by default).
- Section bar is translucent with a blur; with many sections it shows icons only.
- Food trend charts always end today; switching days animates the date first.
- Nutrition card height springs to the current card.
- Bottom sheets: drag the grabber down to close; content stays while the sheet animates out;
  focused fields scroll above the keyboard (also on full screens).
- Pantry: 1 % slider steps without vibration; a finished item stays until you leave the
  screen; picking a type fills an empty name; far dates show the year.
- Donation reminders can no longer be turned off; the payment hint sits inside the button.

- Section bar: a rounded pill only under the tabs (no full-width blur); "+" button without
  glow; more than four quick actions are laid out in a grid; no "Add pantry item".
- Food cards expand to full height; the "how to read" hint no longer stutters.
- Pieces are stored with a food entry; a new custom food opens its amount sheet right away.
- A finished pantry item shows a 5-second countdown before it disappears.
- Removed the greeting and the background-permission hints for steps.

- AMOLED option for the dark theme (pure black backgrounds).
- Faster startup: inline requires; the CIQUAL table loads on first search. Nutrition carousel
  dots run on the native thread.
- App-styled switches; lighter light theme; softer icon glows on light backgrounds.
- Section bar: solid rounded background only under the tabs (no blur).
- Food search needs every word, in any order, and tolerates endings and small typos; online
  results are filtered the same way. Added cooked spaghetti, rice, couscous, quinoa and more.
- Pantry amounts in g, ml or pieces; finished items slide out.
- Backup export saves into a folder you pick.
- Step notification: tap it (or Settings → Steps) to hide its channel; counting continues.
- Release builds are minified (R8, resource shrinking) and drop console logging; releases
  also include a signed AAB. Store listing assets and a privacy policy (docs/PRIVACY.md,
  docs/stores.md).

- Swipe between bottom tabs; custom bottom bar whose highlight slides with the swipe.
- Blood pressure, pulse and weight are entered with wheel pickers, prefilled with the last
  values.
- Charts restyled after Google Fit: smooth lines, dashed target and drop line, haloed marker,
  pill-shaped value bubble above the point.
- Nutrition cards change with a lighter swipe; page dots with a sliding indicator.
- Onboarding offers theme, AMOLED and adaptive targets.
- Food packs are explained in plain words, with alternatives.

### Fixed
- Section bar: the background resizes smoothly with the swipe; its edges and the highlight
  are drawn cleanly (no rectangular overhangs).
- Tapping a distant section or bottom tab switches straight to it, without showing (and
  loading) the tabs in between: the highlight glides directly to the target and the new page
  slides in like a neighbour. The section highlight no longer snaps back to the old section.
- Food cards: a swipe past the first or last card turns the section page; swipes in between
  (also ones starting on a chart) stay in the cards.
- Chart value bubble is dark in the dark theme.
- Remaining sections are pre-rendered in the background once the app is idle, so switching
  to them never stalls.
- Notification icon was a blank square.
- Bottom sheets paused for a few frames after a swipe-down release.
- Food amount sheet closed without animation (backdrop tap, released drag).
- Chart value bubble was clipped at the top.
- Crash when adding food ("addViewAt … already has a parent"): LayoutAnimation is no longer
  used (it is unstable with the new architecture on Android).
- Sliders jumped to the minimum when the thumb itself was touched.
- Lag after adding food before returning to the Food screen.

## [0.5.0] — 2026-10-10

### Added
- Step counting through a small foreground service that keeps the hardware step counter
  registered (as Google Fit does via Play services). On many phones the counter only counts
  while someone listens, so the periodic background read used before saw no steps.
- Pantry section (optional): products at home with purchase and expiry dates, a freshness bar,
  how much is left, nutrition tags (FSA / EU 1924/2006 criteria) and a reminder the day before
  expiry. Base shelf lives from USDA FoodKeeper.
- Counting food by pieces (±1 piece, ±10 g per piece); portion buttons 50/100/200/300/450 g.
- Edit the food data of a log entry; create a product from a base product.
- "Salt not listed" tag for foods without sodium data.
- Body fat estimate from BMI and age (Deurenberg 1991) when there are no measurements.
- App-styled dialogs instead of the system Alert.
- Support window: GitHub star, financial support screen (Tribute, EVM, Solana, TRON, TON
  addresses) and Telegram; shows the weight lost while actively using the app. Milestones:
  3, 7, 14, 30 days, then every 30 days.
- Optional update check against GitHub Releases with a local notification.
- Release workflow (signed APK + checksum on tag), F-Droid metadata, docs/releasing.md.

### Changed
- License: GNU AGPL v3 (was MIT).
- Firebase removed; notifications are local (no Google Play services).
- "More" is grouped into Data, Settings and App, with uniform icon tiles.
- Nutrition is called "Food" in the section bar; "Other drink" is now "Drink".
- Fat and carb targets always sit above their minimums.
- Food search: buttons on top, instant local results, online search after a pause, shorter
  recent list, no "Repeat yesterday".
- Switching days slides the date and morphs charts from the previous day's values.
- Activity templates keep even gaps; "+" actions sit closer to the button.

### Fixed
- Section bar highlight looked patchy where its pieces overlapped; the bar is centred for any
  active tab.
- Expandable hints no longer shrink when tapped.

## [0.4.2] — 2026-10-09

### Added
- Support window: Tribute link and crypto addresses (EVM networks, Solana, TRON), tap to copy.
  Channels are configured in `src/data/donate.ts`; empty entries show "soon". Settings → Support
  can open the window for testing and lists the same options.
- Weight form shows recent weigh-ins with the weighted average line, including the value being
  typed.
- Native `NestedScrollHost` view (local module) that keeps carousel swipes and chart scrubbing
  away from the section pager without racing the JS thread.

### Changed
- Weight trend is a time-weighted exponential average (7-day half-life) instead of a 7-reading mean.
- Water section: separate buttons for water and other drinks instead of a switch.
- Section bar animates on the native thread (transforms only): no lag or jitter at the end.
- Pace slider commits to storage only on release (dragging no longer re-renders the app).
- Quick-add labels wrap to the spacing between buttons instead of overlapping.

## [0.4.1] — 2026-10-09

### Added
- Press and hold a chart to highlight a day and show its value.
- Separate quick button for other drinks next to the water button on the overview.
- Bottom sheets close with a swipe down.
- Switching days in Nutrition replays chart and journal animations.

### Changed
- Section pager uses the native `react-native-pager-view`: smoother swipes, instant tab taps,
  nested carousels no longer move the whole page. The section bar is centred.
- Calorie range: the minimum is the energy that covers the macro minimums (never below
  1200 kcal); when losing weight the target sits in the middle, so averaging the middle gives the
  chosen pace.
- Limit-only nutrients (sugar, added sugar, cholesterol, saturated and trans fat) show a
  limit instead of a target.

### Fixed
- Device steps: use the wake-up step counter; on some Xiaomi/Qualcomm phones the non-wake-up
  variant only re-delivers a stale value, so no steps were counted.
- Possible crash when adding food (sheet and screen were torn down in the same frame).

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
