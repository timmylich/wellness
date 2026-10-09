/**
 * Health math. Every formula here cites its source so contributors can verify it.
 * None of this is medical advice — ranges are population guidelines for healthy adults.
 */
import type { AppData, FoodEntry, NutrientKey, Nutrients, Profile, Sex } from '../store/types';
import { addDays, lastDays } from './date';

/** Energy stored in 1 kg of body fat tissue (commonly used ~7700 kcal estimate). */
export const KCAL_PER_KG_FAT = 7700;

export function age(p: Profile): number {
  return new Date().getFullYear() - p.birthYear;
}

/** Basal metabolic rate, Mifflin–St Jeor (1990). kcal/day. */
export function bmr(p: Profile): number {
  if (p.bmrOverride) return p.bmrOverride;
  const base = 10 * p.weightKg + 6.25 * p.heightCm - 5 * age(p);
  return Math.round(base + (p.sex === 'male' ? 5 : -161));
}

/** Everyday expenditure without logged workouts and extra steps. */
export function baseExpenditure(p: Profile): number {
  return Math.round(bmr(p) * p.lifestyleFactor);
}

/** Absolute minimum intake commonly advised without medical supervision. */
export function calorieFloor(sex: Sex): number {
  return sex === 'male' ? 1500 : 1200;
}

/** Slider limits for the weekly weight change, kg/week. */
export const PACE_MIN = -1.5;
export const PACE_MAX = 1.5;

/** Signed daily calorie delta for the chosen weekly change (negative = deficit). */
export function goalDelta(p: Profile): number {
  return Math.round((p.weeklyChangeKg * KCAL_PER_KG_FAT) / 7);
}

export type PaceWarning = 'lossFast' | 'lossVeryFast' | 'gainFast';

/**
 * Safety assessment of the weekly change. Loss above ~1 % of body weight per week raises
 * the risk of muscle loss and gallstones; gain above 0.5 kg/week is mostly fat.
 */
export function paceWarning(p: Profile): PaceWarning | null {
  const pct = (-p.weeklyChangeKg / p.weightKg) * 100;
  if (pct > 1.25) return 'lossVeryFast';
  if (pct > 1) return 'lossFast';
  if (p.weeklyChangeKg > 0.5) return 'gainFast';
  return null;
}

/** Weeks until the target weight at the chosen pace, or null if the pace points elsewhere. */
export function weeksToTarget(p: Profile): number | null {
  const diff = p.targetWeightKg - p.weightKg;
  if (Math.abs(diff) < 0.3 || p.weeklyChangeKg === 0 || Math.sign(diff) !== Math.sign(p.weeklyChangeKg)) return null;
  return Math.ceil(diff / p.weeklyChangeKg);
}

/**
 * Body weight used for weight-scaled norms (protein, water, fat floor).
 * At high body weight, g/kg norms based on total weight become unrealistically high, because
 * adipose tissue needs little protein or water. Following the clinical "adjusted body weight"
 * idea (ASPEN/ESPEN), we start from the goal weight (or the BMI-25 weight) and add 25 % of
 * the excess.
 */
export function referenceWeight(p: Profile): number {
  const h = p.heightCm / 100;
  const healthyMax = 24.9 * h * h;
  if (p.weightKg <= healthyMax) return p.weightKg;
  const goal = p.targetWeightKg < p.weightKg ? p.targetWeightKg : healthyMax;
  const base = Math.min(p.weightKg, Math.max(healthyMax, goal));
  return base + 0.25 * (p.weightKg - base);
}

/** Steps below this are assumed already covered by the lifestyle factor. */
export const BASELINE_STEPS = 3000;

/** Rough walking cost: ~0.5 kcal per kg per km, ~1300 steps per km. */
export function stepsKcal(steps: number, weightKg: number): number {
  const extra = Math.max(0, steps - BASELINE_STEPS);
  return Math.round((extra / 1300) * 0.5 * weightKg);
}

/** Calories burned for a MET activity (Compendium of Physical Activities). */
export function metKcal(met: number, weightKg: number, minutes: number): number {
  // Subtract 1 MET: resting burn is already counted by BMR.
  return Math.round(Math.max(0, met - 1) * weightKg * (minutes / 60));
}

// ---- nutrient sums -------------------------------------------------------------

export const NUTRIENT_KEYS: NutrientKey[] = [
  'kcal', 'protein', 'fat', 'carbs', 'sugar', 'addedSugar',
  'sodium', 'fiber', 'satFat', 'transFat', 'cholesterol', 'potassium',
];

export const EMPTY_NUTRIENTS: Nutrients = Object.fromEntries(
  NUTRIENT_KEYS.map((k) => [k, 0]),
) as unknown as Nutrients;

export function entryNutrients(e: Pick<FoodEntry, 'food' | 'grams'>): Nutrients {
  const out = { ...EMPTY_NUTRIENTS };
  const f = e.grams / 100;
  for (const k of NUTRIENT_KEYS) out[k] = (e.food.per100g[k] ?? 0) * f;
  return out;
}

export function sumEntries(entries: FoodEntry[]): Nutrients {
  const out = { ...EMPTY_NUTRIENTS };
  for (const e of entries) {
    const n = entryNutrients(e);
    for (const k of NUTRIENT_KEYS) out[k] += n[k];
  }
  return out;
}

// ---- daily targets -----------------------------------------------------------

/**
 * A recommended range. `min` = intake below which health suffers,
 * `target` = the sweet spot, `max` = above it the goal is unreachable or it is unsafe.
 * `lowerIsBetter` marks limits (sodium, trans fat…) where 0 is perfectly fine.
 */
export interface Range {
  min: number;
  target: number;
  max: number;
  lowerIsBetter?: boolean;
}

export type Targets = Record<NutrientKey, Range>;

export interface TargetContext {
  /** Extra calories burned today by workouts and steps. */
  activityKcal: number;
  /** Adaptive correction in kcal (from recent surplus/deficit). */
  adaptiveKcal: number;
}

/**
 * Energy needed just to cover the protein, fat and carbohydrate minimums
 * (0.8 g/kg protein, 0.5 g/kg fat, 130 g carbs), using the reference weight.
 */
export function macroMinimumKcal(p: Profile): number {
  const w = referenceWeight(p);
  return Math.round(0.8 * w * 4 + 0.5 * w * 9 + 130 * 4);
}

/**
 * Daily calorie range. The target sits in the middle; the band is ±10 % (at least 150 kcal).
 * The minimum never drops below what the macro minimums require or the absolute floor
 * (1200/1500 kcal), so eating at the bottom of the range is still nutritionally adequate.
 */
export function dailyCalorieTarget(p: Profile, ctx: TargetContext): Range {
  const tdee = baseExpenditure(p) + ctx.activityKcal;
  const floor = Math.max(calorieFloor(p.sex), macroMinimumKcal(p));
  const target = Math.round(Math.max(floor, tdee + goalDelta(p) + ctx.adaptiveKcal));
  const margin = Math.max(150, Math.round(target * 0.1));
  return { min: Math.max(floor, target - margin), target, max: target + margin };
}

/**
 * Macro and micronutrient ranges.
 * Sources: WHO sugar & sodium guidelines (2012/2015), US DRI (IOM 2005),
 * EFSA dietary reference values, AHA saturated fat advice.
 */
export function computeTargets(
  p: Profile,
  ctx: TargetContext,
  overrides: AppData['settings']['nutrientOverrides'] = {},
): Targets {
  const cal = dailyCalorieTarget(p, ctx);
  const kcal = cal.target;
  const w = referenceWeight(p);
  const losing = goalDelta(p) < 0;

  // Protein: 0.8 g/kg is the RDA floor; higher intake preserves muscle in a deficit.
  const protein: Range = {
    min: Math.round(0.8 * w),
    target: Math.round((losing ? 1.6 : 1.4) * w),
    max: Math.round(2.5 * w),
  };
  // Fat: 20–35 % of energy (IOM AMDR), never below ~0.5 g/kg for hormones.
  const fat: Range = {
    min: Math.round(Math.max(0.5 * w, (kcal * 0.2) / 9)),
    target: Math.round((kcal * 0.28) / 9),
    max: Math.round((kcal * 0.35) / 9),
  };
  // Carbs: RDA 130 g (brain glucose), rest of energy, cap at 65 %.
  const carbTarget = Math.max(130, (kcal - protein.target * 4 - fat.target * 9) / 4);
  const carbs: Range = {
    min: 130,
    target: Math.round(carbTarget),
    max: Math.round(Math.max(carbTarget + 20, (kcal * 0.65) / 4)),
  };

  const ranges: Targets = {
    kcal: cal,
    protein,
    fat,
    carbs,
    // Total sugars (incl. fruit/milk): soft limit ~20 % of energy.
    sugar: { min: 0, target: Math.round((kcal * 0.1) / 4), max: Math.round((kcal * 0.2) / 4), lowerIsBetter: true },
    // WHO: free sugars < 10 % of energy, ideally < 5 %.
    addedSugar: { min: 0, target: Math.round((kcal * 0.05) / 4), max: Math.round((kcal * 0.1) / 4), lowerIsBetter: true },
    // WHO: < 2000 mg sodium (5 g salt). ~500 mg is the physiological minimum.
    sodium: { min: 500, target: 1500, max: 2000 },
    // 14 g per 1000 kcal (IOM); >70 g often causes GI discomfort.
    fiber: { min: 20, target: Math.round(Math.max(25, (kcal / 1000) * 14)), max: 70 },
    // < 10 % of energy (WHO/AHA).
    satFat: { min: 0, target: Math.round((kcal * 0.07) / 9), max: Math.round((kcal * 0.1) / 9), lowerIsBetter: true },
    // As low as possible; WHO: < 1 % of energy.
    transFat: { min: 0, target: 0, max: Math.round(((kcal * 0.01) / 9) * 10) / 10, lowerIsBetter: true },
    // Classic 300 mg/day guidance.
    cholesterol: { min: 0, target: 200, max: 300, lowerIsBetter: true },
    // WHO: ≥ 3510 mg; EFSA AI 3500 mg. No UL for food sources in healthy kidneys.
    potassium: { min: 2600, target: 3500, max: 6000 },
  };

  for (const [k, o] of Object.entries(overrides) as [NutrientKey, { min?: number; max?: number }][]) {
    if (!o) continue;
    ranges[k] = { ...ranges[k], ...(o.min != null && { min: o.min }), ...(o.max != null && { max: o.max }) };
  }
  return ranges;
}

export type RangeStatus = 'low' | 'ok' | 'good' | 'high';

export function rangeStatus(value: number, r: Range): RangeStatus {
  if (value > r.max) return 'high';
  if (r.lowerIsBetter) return value <= r.target ? 'good' : 'ok';
  if (value < r.min) return 'low';
  return Math.abs(value - r.target) <= r.target * 0.1 ? 'good' : 'ok';
}

// ---- per-day aggregates --------------------------------------------------------

export function activityKcalFor(data: AppData, date: string): number {
  const workouts = data.workouts.filter((w) => w.date === date).reduce((a, w) => a + w.kcal, 0);
  return workouts + stepsKcal(data.steps[date] ?? 0, data.profile.weightKg);
}

export function intakeKcalFor(data: AppData, date: string): number {
  return Math.round(sumEntries(data.foodLog.filter((e) => e.date === date)).kcal);
}

export function expenditureFor(data: AppData, date: string): number {
  return baseExpenditure(data.profile) + activityKcalFor(data, date);
}

/**
 * Adaptive correction: if the user over/under-ate vs target in the previous 7 days,
 * spread half of that imbalance over the next 7 days, capped at ±15 % of base target.
 * Days without food entries are ignored (most likely just not logged).
 */
export function adaptiveCorrection(data: AppData, date: string): number {
  if (!data.settings.adaptiveTargets) return 0;
  const p = data.profile;
  let imbalance = 0;
  for (const d of lastDays(7, addDays(date, -1))) {
    const entries = data.foodLog.filter((e) => e.date === d);
    if (!entries.length) continue;
    const t = dailyCalorieTarget(p, { activityKcal: activityKcalFor(data, d), adaptiveKcal: 0 }).target;
    imbalance += sumEntries(entries).kcal - t;
  }
  const cap = dailyCalorieTarget(p, { activityKcal: 0, adaptiveKcal: 0 }).target * 0.15;
  return Math.round(Math.max(-cap, Math.min(cap, -imbalance / 2 / 7)));
}

export function targetsFor(data: AppData, date: string): Targets {
  return computeTargets(
    data.profile,
    { activityKcal: activityKcalFor(data, date), adaptiveKcal: adaptiveCorrection(data, date) },
    data.settings.nutrientOverrides,
  );
}

// ---- water ---------------------------------------------------------------------

export interface WaterRange {
  min: number;
  target: number;
  max: number;
  danger: number;
}

/**
 * Total drinking-water guidance: ~30–35 ml/kg (EFSA AI 2.0 L women / 2.5 L men total
 * water, ~80 % from drinks) plus ~500 ml per hour of exercise.
 * Danger: kidneys excrete ~0.8–1 L/hour; very large daily volumes risk hyponatremia.
 */
export function waterRange(p: Profile, workoutMinutes: number, dangerMl: number): WaterRange {
  const extra = (workoutMinutes / 60) * 500;
  const w = referenceWeight(p);
  const target = Math.round((w * 33 + extra) / 50) * 50;
  return {
    min: Math.round((w * 25 + extra) / 50) * 50,
    target,
    max: Math.round((w * 45 + extra) / 50) * 50,
    danger: Math.max(dangerMl, target + 1500),
  };
}

// ---- body composition -------------------------------------------------------------

export function bmi(weightKg: number, heightCm: number): number {
  const h = heightCm / 100;
  return weightKg / (h * h);
}

export type Tone = 'ok' | 'low' | 'high' | 'danger';

export type BmiKey = 'severeUnder' | 'under' | 'normal' | 'over' | 'obese';

export function bmiCategory(v: number): { key: BmiKey; tone: Tone } {
  if (v < 16) return { key: 'severeUnder', tone: 'danger' };
  if (v < 18.5) return { key: 'under', tone: 'low' };
  if (v < 25) return { key: 'normal', tone: 'ok' };
  if (v < 30) return { key: 'over', tone: 'high' };
  return { key: 'obese', tone: 'danger' };
}

/** Healthy weight band from BMI 18.5–24.9 and Devine (1974) ideal body weight. */
export function healthyWeight(heightCm: number, sex: Sex) {
  const h = heightCm / 100;
  const inchesOver5ft = Math.max(0, heightCm / 2.54 - 60);
  const devine = (sex === 'male' ? 50 : 45.5) + 2.3 * inchesOver5ft;
  return { min: 18.5 * h * h, max: 24.9 * h * h, devine };
}

/** US Navy circumference method body-fat estimate (Hodgdon & Beckett, 1984). */
export function navyBodyFat(
  sex: Sex,
  heightCm: number,
  waistCm: number,
  neckCm: number,
  hipCm?: number,
): number | null {
  const log = Math.log10;
  if (sex === 'male') {
    if (waistCm <= neckCm) return null;
    return 495 / (1.0324 - 0.19077 * log(waistCm - neckCm) + 0.15456 * log(heightCm)) - 450;
  }
  if (!hipCm || waistCm + hipCm <= neckCm) return null;
  return 495 / (1.29579 - 0.35004 * log(waistCm + hipCm - neckCm) + 0.221 * log(heightCm)) - 450;
}

/** Healthy body-fat bands for adults (ACE). */
export type BodyFatKey = 'essential' | 'athletic' | 'fit' | 'average' | 'high';

export function bodyFatCategory(sex: Sex, pct: number): BodyFatKey {
  const b = sex === 'male' ? [6, 14, 18, 25] : [14, 21, 25, 32];
  if (pct < b[0]) return 'essential';
  if (pct < b[1]) return 'athletic';
  if (pct < b[2]) return 'fit';
  if (pct < b[3]) return 'average';
  return 'high';
}

// ---- heart -------------------------------------------------------------------------

export type BpKey = 'crisis' | 'stage2' | 'stage1' | 'elevated' | 'low' | 'normal';

/** Blood pressure classification per ACC/AHA 2017, plus hypotension below 90/60. */
export function bpCategory(sys: number, dia: number): { key: BpKey; tone: Tone } {
  if (sys > 180 || dia > 120) return { key: 'crisis', tone: 'danger' };
  if (sys >= 140 || dia >= 90) return { key: 'stage2', tone: 'danger' };
  if (sys >= 130 || dia >= 80) return { key: 'stage1', tone: 'high' };
  if (sys >= 120) return { key: 'elevated', tone: 'high' };
  if (sys < 90 || dia < 60) return { key: 'low', tone: 'low' };
  return { key: 'normal', tone: 'ok' };
}

export type PulseKey = 'veryLow' | 'low' | 'normal' | 'elevated' | 'tachy';

export function pulseCategory(bpm: number): { key: PulseKey; tone: Tone } {
  if (bpm < 40) return { key: 'veryLow', tone: 'danger' };
  if (bpm < 60) return { key: 'low', tone: 'low' };
  if (bpm <= 100) return { key: 'normal', tone: 'ok' };
  if (bpm <= 120) return { key: 'elevated', tone: 'high' };
  return { key: 'tachy', tone: 'danger' };
}

/** Tanaka (2001) estimate of maximum heart rate. */
export function maxHeartRate(p: Profile): number {
  return Math.round(208 - 0.7 * age(p));
}
