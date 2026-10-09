/** Derived-data hooks shared by several screens. */
import { useMemo } from 'react';

import { getState, useStore } from '../store/store';
import { activityKcalFor, expenditureFor, sumEntries, targetsFor, waterRange } from './calc';

/** Everything the nutrition/dashboard screens need for one day. */
export function useDaySummary(date: string) {
  // Subscribe to every slice the computation reads so it re-runs on change.
  const foodLog = useStore((s) => s.foodLog);
  const workouts = useStore((s) => s.workouts);
  const steps = useStore((s) => s.steps);
  const profile = useStore((s) => s.profile);
  const settings = useStore((s) => s.settings);

  return useMemo(() => {
    const data = getState();
    const entries = foodLog.filter((e) => e.date === date);
    const totals = sumEntries(entries);
    const targets = targetsFor(data, date);
    const expenditure = expenditureFor(data, date);
    const activityKcal = activityKcalFor(data, date);
    return { entries, totals, targets, expenditure, activityKcal, balance: totals.kcal - expenditure };
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [date, foodLog, workouts, steps, profile, settings]);
}

export function useWaterDay(date: string) {
  const water = useStore((s) => s.water);
  const workouts = useStore((s) => s.workouts);
  const profile = useStore((s) => s.profile);
  const dangerMl = useStore((s) => s.settings.waterDangerMl);
  return useMemo(() => {
    const entries = water.filter((w) => w.date === date).sort((a, b) => b.time - a.time);
    // Only plain water counts towards the target; every drink counts towards the danger limit.
    const total = entries.reduce((a, w) => a + ((w.kind ?? 'water') === 'water' ? w.ml : 0), 0);
    const other = entries.reduce((a, w) => a + (w.kind === 'other' ? w.ml : 0), 0);
    const fluid = total + other;
    const minutes = workouts.filter((w) => w.date === date).reduce((a, w) => a + w.minutes, 0);
    return { entries, total, other, fluid, range: waterRange(profile, minutes, dangerMl) };
  }, [date, water, workouts, profile, dangerMl]);
}
