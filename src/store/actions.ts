/** Typed mutations. Screens call these instead of touching `setState` directly. */
import { todayKey } from '../lib/date';
import { setState, uid } from './store';
import type {
  AppData,
  BodyEntry,
  Food,
  FoodEntry,
  HeartEntry,
  FluidKind,
  MealType,
  Profile,
  Settings,
  WorkoutEntry,
  WorkoutTemplate,
} from './types';

type ListKey = 'foodLog' | 'water' | 'workouts' | 'heart' | 'body';

function removeFrom(key: ListKey, id: string) {
  setState((s) => ({ ...s, [key]: (s[key] as { id: string }[]).filter((e) => e.id !== id) }));
}

// ---- profile & settings ----------------------------------------------------

export function updateProfile(patch: Partial<Profile>) {
  setState((s) => ({ ...s, profile: { ...s.profile, ...patch } }));
}

/** Re-run the first-launch wizard (data is kept). */
export function restartOnboarding() {
  setState((s) => ({ ...s, meta: { ...s.meta, onboarded: false } }));
}

export function updateSettings(patch: Partial<Settings>) {
  setState((s) => ({ ...s, settings: { ...s.settings, ...patch } }));
}

export function completeOnboarding() {
  setState((s) => ({ ...s, meta: { ...s.meta, onboarded: true } }));
}

export function markDonationShown(milestone: number) {
  setState((s) => ({
    ...s,
    meta: { ...s.meta, donationShown: [...s.meta.donationShown, milestone] },
  }));
}

export function setStepCounter(stepCounter: AppData['meta']['stepCounter']) {
  setState((s) => ({ ...s, meta: { ...s.meta, stepCounter } }));
}

export function setPushToken(pushToken: string) {
  setState((s) => (s.meta.pushToken === pushToken ? s : { ...s, meta: { ...s.meta, pushToken } }));
}

export function resetDonationPrompts() {
  setState((s) => ({ ...s, meta: { ...s.meta, donationShown: [] } }));
}

// ---- nutrition ---------------------------------------------------------------

export function logFood(food: Food, grams: number, meal: MealType, date = todayKey()) {
  const entry: FoodEntry = { id: uid(), date, time: Date.now(), meal, food, grams };
  setState((s) => ({ ...s, foodLog: [...s.foodLog, entry] }));
}

export function updateFoodEntry(id: string, patch: Partial<FoodEntry>) {
  setState((s) => ({
    ...s,
    foodLog: s.foodLog.map((e) => (e.id === id ? { ...e, ...patch } : e)),
  }));
}

export const removeFoodEntry = (id: string) => removeFrom('foodLog', id);

/** Copy all entries of one day (optionally one meal) to another day. */
export function copyDay(from: string, to: string, meal?: MealType) {
  setState((s) => {
    const copies = s.foodLog
      .filter((e) => e.date === from && (!meal || e.meal === meal))
      .map((e) => ({ ...e, id: uid(), date: to, time: Date.now() }));
    return copies.length ? { ...s, foodLog: [...s.foodLog, ...copies] } : s;
  });
}

/** Insert or replace a food in the user's local database. */
export function saveFood(food: Food) {
  setState((s) => {
    const exists = s.foods.some((f) => f.id === food.id);
    return {
      ...s,
      foods: exists ? s.foods.map((f) => (f.id === food.id ? food : f)) : [...s.foods, food],
    };
  });
}

export function deleteFood(id: string) {
  setState((s) => ({
    ...s,
    foods: s.foods.filter((f) => f.id !== id),
    favorites: s.favorites.filter((f) => f !== id),
  }));
}

export function toggleFavorite(id: string) {
  setState((s) => ({
    ...s,
    favorites: s.favorites.includes(id)
      ? s.favorites.filter((f) => f !== id)
      : [...s.favorites, id],
  }));
}

// ---- water -------------------------------------------------------------------

export function addWater(ml: number, date = todayKey(), kind: FluidKind = 'water') {
  setState((s) => ({ ...s, water: [...s.water, { id: uid(), date, time: Date.now(), ml, kind }] }));
}

export const removeWater = (id: string) => removeFrom('water', id);

// ---- activity ------------------------------------------------------------------

export function addWorkout(w: Omit<WorkoutEntry, 'id' | 'time'>) {
  setState((s) => ({ ...s, workouts: [...s.workouts, { ...w, id: uid(), time: Date.now() }] }));
}

export const removeWorkout = (id: string) => removeFrom('workouts', id);

export function saveWorkoutTemplate(t: WorkoutTemplate) {
  setState((s) => {
    const exists = s.workoutTemplates.some((x) => x.id === t.id);
    return {
      ...s,
      workoutTemplates: exists
        ? s.workoutTemplates.map((x) => (x.id === t.id ? t : x))
        : [...s.workoutTemplates, t],
    };
  });
}

export function deleteWorkoutTemplate(id: string) {
  setState((s) => ({ ...s, workoutTemplates: s.workoutTemplates.filter((t) => t.id !== id) }));
}

export function setSteps(date: string, steps: number) {
  setState((s) => ({ ...s, steps: { ...s.steps, [date]: Math.max(0, Math.round(steps)) } }));
}

/** Overwrite several days at once (Health Connect sync). Skips the write if nothing changed. */
export function setStepsBulk(byDay: Record<string, number>) {
  setState((s) => {
    const changed = Object.entries(byDay).some(([d, v]) => s.steps[d] !== v);
    return changed ? { ...s, steps: { ...s.steps, ...byDay } } : s;
  });
}

export function addSteps(date: string, delta: number) {
  setState((s) => ({ ...s, steps: { ...s.steps, [date]: (s.steps[date] ?? 0) + delta } }));
}

// ---- heart & body --------------------------------------------------------------

export function addHeart(e: Omit<HeartEntry, 'id' | 'time' | 'date'>) {
  const now = Date.now();
  setState((s) => ({ ...s, heart: [...s.heart, { ...e, id: uid(), time: now, date: todayKey() }] }));
}

export const removeHeart = (id: string) => removeFrom('heart', id);

/** Log body measurements; a new weight also updates the profile weight used in calculations. */
export function addBody(e: Omit<BodyEntry, 'id' | 'time' | 'date'>) {
  setState((s) => ({
    ...s,
    body: [...s.body, { ...e, id: uid(), time: Date.now(), date: todayKey() }],
    profile: {
      ...s.profile,
      ...(e.weightKg ? { weightKg: e.weightKg } : null),
      ...(e.heightCm ? { heightCm: e.heightCm } : null),
    },
  }));
}

export const removeBody = (id: string) => removeFrom('body', id);

// ---- data ------------------------------------------------------------------------

export function wipeSection(key: keyof Pick<AppData, ListKey | 'steps'>) {
  setState((s) => ({ ...s, [key]: key === 'steps' ? {} : [] }));
}
