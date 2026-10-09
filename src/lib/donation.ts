/**
 * Donation prompt schedule. The prompt appears once when the number of distinct
 * days the app was used reaches a milestone: 3, 7, 14, 30, 60, 90, then every 90 days.
 */
const FIXED = [3, 7, 14, 30, 60, 90];

export function isMilestone(days: number): boolean {
  return FIXED.includes(days) || (days > 90 && days % 90 === 0);
}

/** The milestone to show now, or null. Only the latest reached milestone counts. */
export function pendingMilestone(usageDays: number, shown: number[]): number | null {
  for (let d = usageDays; d >= 3; d--) {
    if (isMilestone(d)) return shown.includes(d) ? null : d;
  }
  return null;
}
