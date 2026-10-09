/**
 * Core domain types. Everything the app persists is described here, so the
 * export/import format is defined by `AppData` (versioned via `schemaVersion`).
 */

export type Sex = 'male' | 'female';

/** ISO calendar date in local time, e.g. "2026-10-06". */
export type DateKey = string;

export type SectionId = 'nutrition' | 'water' | 'activity' | 'heart' | 'body' | 'tips';

export type ThemeMode = 'system' | 'light' | 'dark';

export type LanguageSetting = 'system' | 'ru' | 'en';

/**
 * Where daily step counts come from:
 * - `device`: the phone's hardware step counter, read in the foreground and by a periodic
 *   background task (works while the app is closed);
 * - `healthConnect`: daily totals from Health Connect.
 */
export type StepsSource = 'device' | 'healthConnect';

/** Plain water counts towards the daily target; other drinks only towards the danger limit. */
export type FluidKind = 'water' | 'other';

export interface Profile {
  name: string;
  sex: Sex;
  birthYear: number;
  heightCm: number;
  weightKg: number;
  /** Goal weight; informational (ETA) and used to scale weight-based norms for high body weight. */
  targetWeightKg: number;
  /** Desired weight change in kg per week: negative = lose, 0 = maintain, positive = gain. Range −1.5…+1.5. */
  weeklyChangeKg: number;
  /**
   * Multiplier for everyday life activity WITHOUT logged workouts
   * (1.2 desk job … 1.725 physical work). Workouts and steps are added on top.
   */
  lifestyleFactor: number;
  /** Optional manual override of basal metabolic rate, kcal/day. */
  bmrOverride?: number;
}

/** Nutrient values per 100 g (or per serving for quick entries). Units noted per field. */
export interface Nutrients {
  kcal: number;
  protein: number; // g
  fat: number; // g
  carbs: number; // g
  sugar: number; // g
  addedSugar: number; // g
  sodium: number; // mg
  fiber: number; // g
  satFat: number; // g
  transFat: number; // g
  cholesterol: number; // mg
  potassium: number; // mg
}

export type NutrientKey = keyof Nutrients;

export interface Food {
  id: string;
  name: string;
  brand?: string;
  /** Extra searchable names (e.g. the name in another language). */
  aliases?: string[];
  /** Where the record came from: bundled, user-made, Open Food Facts, imported pack. */
  source: 'builtin' | 'custom' | 'off' | 'usda' | 'ciqual' | 'pack';
  per100g: Partial<Nutrients>;
  /** Typical serving size in grams, used as default amount. */
  servingG?: number;
  barcode?: string;
}

export type MealType = 'breakfast' | 'lunch' | 'dinner' | 'snack';

export interface FoodEntry {
  id: string;
  date: DateKey;
  time: number; // epoch ms
  meal: MealType;
  /** Snapshot of the food so history stays correct if the food is edited later. */
  food: Food;
  grams: number;
}

export interface WaterEntry {
  id: string;
  date: DateKey;
  time: number;
  ml: number;
  /** Missing in data from v0.2 and earlier, meaning 'water'. */
  kind?: FluidKind;
}

export interface WorkoutTemplate {
  id: string;
  name: string;
  /** Metabolic equivalent of task; kcal = MET × kg × hours. */
  met: number;
  defaultMinutes: number;
  description?: string;
  builtin?: boolean;
}

export interface WorkoutEntry {
  id: string;
  date: DateKey;
  time: number;
  name: string;
  minutes: number;
  /** Calories burned; either computed from MET or entered by the user. */
  kcal: number;
  note?: string;
}

export interface HeartEntry {
  id: string;
  date: DateKey;
  time: number;
  systolic?: number;
  diastolic?: number;
  pulse?: number;
  note?: string;
}

export interface BodyEntry {
  id: string;
  date: DateKey;
  time: number;
  weightKg?: number;
  bodyFatPct?: number;
  waistCm?: number;
  neckCm?: number;
  hipCm?: number;
  heightCm?: number;
}

export interface Settings {
  language: LanguageSetting;
  theme: ThemeMode;
  /** UI motion (press feedback, transitions). Also disabled when the OS asks to reduce motion. */
  animations: boolean;
  stepsSource: StepsSource;
  accent: string;
  sections: Record<SectionId, boolean>;
  /** Shift daily calorie target to compensate the last 7 days' surplus/deficit. */
  adaptiveTargets: boolean;
  /** Allow network requests to Open Food Facts. Off = fully offline app. */
  onlineFoodSearch: boolean;
  /** Quick-add buttons on the water screen, ml. */
  waterPresets: number[];
  /** Volume added by the big water button (last chosen by the user), ml. */
  waterAmountMl: number;
  /** Daily water amount considered dangerous, ml (risk of hyponatremia). */
  waterDangerMl: number;
  stepGoal: number;
  /** Show the hint cards (“?” explanations) across the app. */
  showHints: boolean;
  donationPrompts: boolean;
  /** Whether the step-counting explainer card was dismissed. */
  stepsInfoDismissed: boolean;
  /** Optional personal USDA FoodData Central API key (falls back to the rate-limited DEMO_KEY). */
  usdaApiKey: string;
  /** Local/push notification preferences (push delivery is not wired up yet, see docs/notifications.md). */
  notifications: NotificationSettings;
  /** Per-nutrient manual overrides of the daily target range. */
  nutrientOverrides: Partial<Record<NutrientKey, { min?: number; max?: number }>>;
}

export interface NotificationSettings {
  enabled: boolean;
  waterReminders: boolean;
  weighInReminder: boolean;
}

/** Last reading of the hardware step counter, used to turn cumulative counts into daily steps. */
export interface StepCounterState {
  /** Cumulative steps since boot at the last reading. */
  value: number;
  /** Estimated boot time (epoch ms) at the last reading; changes after a reboot. */
  bootAt: number;
  /** Epoch ms of the last reading. */
  at: number;
}

export interface Meta {
  onboarded: boolean;
  /** Distinct local days on which the app was opened. */
  usageDays: DateKey[];
  /** Usage-day milestones for which the donation prompt was already shown. */
  donationShown: number[];
  stepCounter?: StepCounterState;
  /** Push token registered with the messaging provider, if any. */
  pushToken?: string;
  createdAt: number;
}

export interface AppData {
  schemaVersion: number;
  profile: Profile;
  settings: Settings;
  meta: Meta;
  foods: Food[]; // user's local database (custom + saved online items)
  favorites: string[]; // food ids
  foodLog: FoodEntry[];
  water: WaterEntry[];
  workouts: WorkoutEntry[];
  workoutTemplates: WorkoutTemplate[];
  /** Step counts per day. */
  steps: Record<DateKey, number>;
  heart: HeartEntry[];
  body: BodyEntry[];
}
