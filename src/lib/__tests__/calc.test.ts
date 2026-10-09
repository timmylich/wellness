import { describe, expect, it } from '@jest/globals';

import {
  bmi,
  bmr,
  bpCategory,
  computeTargets,
  dailyCalorieTarget,
  goalDelta,
  macroMinimumKcal,
  metKcal,
  navyBodyFat,
  paceWarning,
  referenceWeight,
  weeksToTarget,
  rangeStatus,
  stepsKcal,
  sumEntries,
  waterRange,
} from '../calc';
import { isMilestone, pendingMilestone } from '../donation';
import { fromOff } from '../foodApi';
import { en } from '../../i18n/en';
import { ru } from '../../i18n/ru';
import { normalize } from '../../store/store';
import type { FoodEntry, Profile } from '../../store/types';

const year = new Date().getFullYear();
const man: Profile = {
  name: '', sex: 'male', birthYear: year - 30, heightCm: 180, weightKg: 80,
  targetWeightKg: 75, weeklyChangeKg: -0.5, lifestyleFactor: 1.2,
};

describe('energy', () => {
  it('computes Mifflin–St Jeor BMR', () => {
    // 10*80 + 6.25*180 - 5*30 + 5 = 1780
    expect(bmr(man)).toBe(1780);
    expect(bmr({ ...man, sex: 'female' })).toBe(1614);
    expect(bmr({ ...man, bmrOverride: 1500 })).toBe(1500);
  });

  it('derives signed goal delta from the weekly change', () => {
    expect(goalDelta(man)).toBe(-550);
    expect(goalDelta({ ...man, weeklyChangeKg: 0.5 })).toBe(550);
    expect(goalDelta({ ...man, weeklyChangeKg: 0 })).toBe(0);
  });

  it('warns about unsafe paces', () => {
    expect(paceWarning(man)).toBeNull();
    expect(paceWarning({ ...man, weeklyChangeKg: -0.9 })).toBe('lossFast');
    expect(paceWarning({ ...man, weeklyChangeKg: -1.5 })).toBe('lossVeryFast');
    expect(paceWarning({ ...man, weeklyChangeKg: 0.75 })).toBe('gainFast');
    expect(weeksToTarget(man)).toBe(10);
    expect(weeksToTarget({ ...man, weeklyChangeKg: 0.5 })).toBeNull();
  });

  it('scales weight-based norms down for high body weight', () => {
    // 180 cm: BMI-25 weight ≈ 80.7 kg. Normal weight → unchanged.
    expect(referenceWeight(man)).toBe(80);
    const heavy = { ...man, weightKg: 130, targetWeightKg: 90 };
    // 90 + 25 % of the 40 kg excess = 100 kg.
    expect(referenceWeight(heavy)).toBeCloseTo(100, 5);
    const tHeavy = computeTargets(heavy, { activityKcal: 0, adaptiveKcal: 0 });
    expect(tHeavy.protein.target).toBe(160);
    expect(waterRange(heavy, 0, 6000).target).toBe(3300);
  });

  it('never targets below the safety floor', () => {
    const r = dailyCalorieTarget({ ...man, weeklyChangeKg: -1.5 }, { activityKcal: 0, adaptiveKcal: 0 });
    expect(r.target).toBeGreaterThanOrEqual(r.min);
    expect(r.min).toBeGreaterThanOrEqual(1500);
  });

  it('keeps the calorie target in the middle of the range', () => {
    const heavy = { ...man, weightKg: 130, heightCm: 180, targetWeightKg: 90, weeklyChangeKg: -1, lifestyleFactor: 1.3 };
    const r = dailyCalorieTarget(heavy, { activityKcal: 0, adaptiveKcal: 0 });
    expect(r.target - r.min).toBe(r.max - r.target);
    expect(r.min).toBeLessThan(r.target);
    expect(r.min).toBeGreaterThanOrEqual(macroMinimumKcal(heavy));
  });

  it('adds activity to the calorie target', () => {
    // Active lifestyle keeps the base target above the safety floor, so the delta is exact.
    const p = { ...man, lifestyleFactor: 1.45 };
    const base = dailyCalorieTarget(p, { activityKcal: 0, adaptiveKcal: 0 }).target;
    const active = dailyCalorieTarget(p, { activityKcal: 300, adaptiveKcal: 0 }).target;
    expect(active - base).toBe(300);
  });

  it('counts only steps above baseline and subtracts resting MET', () => {
    expect(stepsKcal(2000, 80)).toBe(0);
    expect(stepsKcal(3000 + 1300, 80)).toBe(40);
    expect(metKcal(1, 80, 60)).toBe(0);
    expect(metKcal(8, 80, 30)).toBe(280);
  });
});

describe('targets', () => {
  const t = computeTargets(man, { activityKcal: 0, adaptiveKcal: 0 });

  it('keeps every range ordered', () => {
    for (const r of Object.values(t)) {
      expect(r.min).toBeLessThanOrEqual(r.target);
      expect(r.target).toBeLessThanOrEqual(r.max);
    }
  });

  it('follows WHO limits', () => {
    expect(t.sodium.max).toBe(2000);
    expect(t.addedSugar.max).toBeCloseTo((t.kcal.target * 0.1) / 4, 0);
    expect(t.carbs.min).toBe(130);
  });

  it('applies user overrides', () => {
    const o = computeTargets(man, { activityKcal: 0, adaptiveKcal: 0 }, { sodium: { max: 1500 } });
    expect(o.sodium.max).toBe(1500);
    expect(o.sodium.min).toBe(t.sodium.min);
  });

  it('classifies values against ranges', () => {
    const r = { min: 10, target: 20, max: 30 };
    expect(rangeStatus(5, r)).toBe('low');
    expect(rangeStatus(20, r)).toBe('good');
    expect(rangeStatus(31, r)).toBe('high');
    expect(rangeStatus(0, { ...r, min: 0, lowerIsBetter: true })).toBe('good');
  });
});

describe('nutrients', () => {
  it('scales per-100g values by grams', () => {
    const e = { grams: 150, food: { id: 'x', name: 'x', source: 'custom', per100g: { kcal: 100, protein: 10 } } } as FoodEntry;
    const n = sumEntries([e, e]);
    expect(n.kcal).toBe(300);
    expect(n.protein).toBe(30);
    expect(n.fat).toBe(0);
  });

  it('maps Open Food Facts products, converting g → mg', () => {
    const f = fromOff({ code: '1', product_name: 'Test', brands: ['Додо'], nutriments: { 'energy-kcal_100g': 250, salt_100g: 1 } });
    expect(f?.per100g.kcal).toBe(250);
    expect(f?.per100g.sodium).toBe(400);
    expect(f?.brand).toBe('Додо');
    expect(fromOff({ product_name: '' })).toBeNull();
  });
});

describe('body & heart', () => {
  it('computes BMI', () => {
    expect(bmi(80, 180)).toBeCloseTo(24.69, 2);
  });

  it('estimates body fat with the Navy method', () => {
    const bf = navyBodyFat('male', 180, 85, 38)!;
    expect(bf).toBeGreaterThan(10);
    expect(bf).toBeLessThan(25);
    expect(navyBodyFat('male', 180, 30, 38)).toBeNull();
  });

  it('classifies blood pressure (ACC/AHA 2017)', () => {
    expect(bpCategory(115, 75).tone).toBe('ok');
    expect(bpCategory(125, 75).key).toBe('elevated');
    expect(bpCategory(135, 85).key).toBe('stage1');
    expect(bpCategory(150, 95).key).toBe('stage2');
    expect(bpCategory(190, 100).key).toBe('crisis');
    expect(bpCategory(85, 55).key).toBe('low');
  });

  it('keeps the water danger threshold above the normal max', () => {
    const w = waterRange(man, 60, 3000);
    expect(w.min).toBeLessThan(w.target);
    expect(w.target).toBeLessThan(w.max);
    expect(w.danger).toBeGreaterThan(w.target);
  });
});

describe('donation milestones', () => {
  it('fires on the documented schedule', () => {
    expect([3, 7, 14, 30, 60, 90, 180, 270].every(isMilestone)).toBe(true);
    expect(isMilestone(4)).toBe(false);
    expect(isMilestone(120)).toBe(false);
  });

  it('shows only the latest unseen milestone', () => {
    expect(pendingMilestone(2, [])).toBeNull();
    expect(pendingMilestone(8, [])).toBe(7);
    expect(pendingMilestone(8, [7])).toBeNull();
  });
});

describe('data migration', () => {
  it('converts the v0.1 unsigned pace into a signed weekly change', () => {
    const old = { profile: { ...man, weeklyChangeKg: undefined, paceKgPerWeek: 0.75, targetWeightKg: 70 } } as never;
    expect(normalize(old).profile.weeklyChangeKg).toBe(-0.75);
    expect('paceKgPerWeek' in normalize(old).profile).toBe(false);
  });

  it('fills settings added in later versions', () => {
    const d = normalize({ settings: { theme: 'dark' } } as never);
    expect(d.settings.theme).toBe('dark');
    expect(d.settings.language).toBe('system');
    expect(d.settings.stepsSource).toBe('device');
    expect(normalize({ settings: { stepsSource: 'sensor' } } as never).settings.stepsSource).toBe('device');
  });
});

describe('i18n', () => {
  it('ships the same keys in every language', () => {
    const shape = (o: unknown): unknown =>
      Array.isArray(o) ? o.length : o && typeof o === 'object' ? Object.fromEntries(Object.entries(o).map(([k, v]) => [k, shape(v)])) : typeof o;
    expect(shape(en)).toEqual(shape(ru));
  });
});
