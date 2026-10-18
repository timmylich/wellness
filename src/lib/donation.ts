/**
 * Donation prompt schedule. The prompt appears once when the number of distinct
 * days the app was used reaches a milestone: 3, 7, 14, 30, then every 30 days.
 */
import type { AppData } from '../store/types';
import { expenditureFor, KCAL_PER_KG_FAT, sumEntries, weightTrend } from './calc';
import { todayKey } from './date';

const FIXED = [3, 7, 14];
const DAY_MS = 86_400_000;

export function isMilestone(days: number): boolean {
  return FIXED.includes(days) || (days >= 30 && days % 30 === 0);
}

/**
 * Weight the user has lost while actively using the app, or null when it should not be shown.
 * Active: food logged on at least 70 % of the days since the first entry (7+ days).
 * Uses the weigh-in trend when available, otherwise the logged calorie deficit (7700 kcal/kg).
 */
export function weightLost(s: AppData): number | null {
  const days = [...new Set(s.foodLog.map((e) => e.date))].sort();
  if (!days.length) return null;
  const span = Math.round((Date.parse(todayKey()) - Date.parse(days[0])) / DAY_MS) + 1;
  if (span < 7 || days.length / span < 0.7) return null;
  let balance = 0;
  for (const d of days) balance += sumEntries(s.foodLog.filter((e) => e.date === d)).kcal - expenditureFor(s, d);
  if (balance >= 0) return null;
  const since = Date.parse(days[0]);
  const weights = s.body
    .filter((e) => e.weightKg && e.time >= since - DAY_MS)
    .sort((a, b) => a.time - b.time)
    .map((e) => ({ time: e.time, weightKg: e.weightKg! }));
  const trend = weightTrend(weights);
  const kg = trend.length >= 2 ? weights[0].weightKg - trend[trend.length - 1] : -balance / KCAL_PER_KG_FAT;
  return kg >= 0.5 ? kg : null;
}

/** The milestone to show now, or null. Only the latest reached milestone counts. */
export function pendingMilestone(usageDays: number, shown: number[]): number | null {
  for (let d = usageDays; d >= 3; d--) {
    if (isMilestone(d)) return shown.includes(d) ? null : d;
  }
  return null;
}

// Manual preview of the prompt (Settings → Support → "Show the window"), for testing.
let preview = false;
const listeners = new Set<() => void>();
const emit = () => listeners.forEach((l) => l());

export function setDonationPreview(on: boolean) {
  preview = on;
  emit();
}

export function subscribeDonationPreview(l: () => void) {
  listeners.add(l);
  return () => {
    listeners.delete(l);
  };
}

export function getDonationPreview() {
  return preview;
}
