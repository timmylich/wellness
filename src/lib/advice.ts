/**
 * Rule-based nutrition advice — the "smart" part without AI. Each rule inspects the
 * day's totals against targets and produces a short actionable tip.
 */
import type { NutrientKey, Nutrients } from '../store/types';
import { type Targets } from './calc';

export type AdviceKey =
  | 'kcalOver' | 'kcalUnder' | 'proteinUnder' | 'proteinOver' | 'addedSugarOver' | 'sodiumOver'
  | 'satFatOver' | 'transFatOver' | 'fiberUnder' | 'potassiumUnder' | 'cholesterolOver' | 'fatUnder'
  | 'allGoodLate' | 'allGoodSoFar';

/** Texts live in the i18n dictionaries under `advice`. */
export interface Advice {
  tone: 'ok' | 'low' | 'high' | 'danger';
  key: AdviceKey;
}

/**
 * @param dayProgress fraction of the waking day elapsed (0..1); "too little" warnings
 *   are only shown late in the day, otherwise they'd fire every morning.
 */
export function nutritionAdvice(n: Nutrients, t: Targets, dayProgress: number): Advice[] {
  const out: Advice[] = [];
  const late = dayProgress > 0.75;
  const over = (k: NutrientKey) => n[k] > t[k].max;
  const under = (k: NutrientKey) => late && n[k] < t[k].min;

  if (over('kcal')) out.push({ tone: 'high', key: 'kcalOver' });
  if (under('kcal')) out.push({ tone: 'low', key: 'kcalUnder' });
  if (under('protein')) out.push({ tone: 'low', key: 'proteinUnder' });
  if (over('protein')) out.push({ tone: 'high', key: 'proteinOver' });
  if (over('addedSugar')) out.push({ tone: 'danger', key: 'addedSugarOver' });
  if (over('sodium')) out.push({ tone: 'danger', key: 'sodiumOver' });
  if (over('satFat')) out.push({ tone: 'high', key: 'satFatOver' });
  if (n.transFat > t.transFat.max) out.push({ tone: 'danger', key: 'transFatOver' });
  if (under('fiber')) out.push({ tone: 'low', key: 'fiberUnder' });
  if (under('potassium')) out.push({ tone: 'low', key: 'potassiumUnder' });
  if (over('cholesterol')) out.push({ tone: 'high', key: 'cholesterolOver' });
  if (under('fat')) out.push({ tone: 'low', key: 'fatUnder' });

  if (!out.length && n.kcal > 0) {
    out.push({ tone: 'ok', key: late ? 'allGoodLate' : 'allGoodSoFar' });
  }
  return out;
}

/** 0 at 7:00, 1 at 23:00. */
export function wakingDayProgress(now = new Date()): number {
  const h = now.getHours() + now.getMinutes() / 60;
  return Math.max(0, Math.min(1, (h - 7) / 16));
}
