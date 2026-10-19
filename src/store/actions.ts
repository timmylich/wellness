/** Typed mutations. Screens call these instead of touching `setState` directly. */
import { todayKey } from '../lib/date';
import { setState, uid } from './store';
import type {
  PantryItem,
  HealthRecord,
  AppData,
  BodyEntry,
  Food,
  FoodEntry,
  HeartEntry,
  FluidKind,
  MealType,
  Profile,
  Settings,
  WaterEntry,
  WorkoutEntry,
  WorkoutTemplate,
} from './types';

type ListKey = 'foodLog' | 'water' | 'workouts' | 'heart' | 'body' | 'pantry' | 'health';

function removeFrom(key: ListKey, id: string) {
  setState((s) => ({ ...s, [key]: (s[key] as { id: string }[]).filter((e) => e.id !== id) }));
}

function updateIn<T extends { id: string }>(list: T[], id: string, patch: Partial<T>): T[] {
  return list.map((e) => (e.id === id ? { ...e, ...patch } : e));
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


export function updateMeta(patch: Partial<AppData['meta']>) {
  setState((s) => ({ ...s, meta: { ...s.meta, ...patch } }));
}

export function resetDonationPrompts() {
  setState((s) => ({ ...s, meta: { ...s.meta, donationShown: [] } }));
}

// ---- nutrition ---------------------------------------------------------------

export function logFood(food: Food, grams: number, meal: MealType, date = todayKey(), pieces?: number) {
  const entry: FoodEntry = { id: uid(), date, time: Date.now(), meal, food, grams, pieces: pieces && pieces > 1 ? pieces : undefined };
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

export function updateWater(id: string, patch: Partial<Omit<WaterEntry, 'id'>>) {
  setState((s) => ({ ...s, water: updateIn<WaterEntry>(s.water, id, patch) }));
}

// ---- activity ------------------------------------------------------------------

export function addWorkout(w: Omit<WorkoutEntry, 'id' | 'time'>) {
  setState((s) => ({ ...s, workouts: [...s.workouts, { ...w, id: uid(), time: Date.now() }] }));
}

export const removeWorkout = (id: string) => removeFrom('workouts', id);

export function updateWorkout(id: string, patch: Partial<Omit<WorkoutEntry, 'id'>>) {
  setState((s) => ({ ...s, workouts: updateIn<WorkoutEntry>(s.workouts, id, patch) }));
}

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

export function updateHeart(id: string, patch: Partial<Omit<HeartEntry, 'id'>>) {
  setState((s) => ({ ...s, heart: updateIn<HeartEntry>(s.heart, id, patch) }));
}

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

/**
 * Edit a body entry. Fields set to undefined are cleared. The profile weight and height follow
 * the latest entry that has them, so editing (or back-dating) the last weigh-in keeps the
 * calculations in step.
 */
export function updateBody(id: string, patch: Partial<Omit<BodyEntry, 'id'>>) {
  setState((s) => {
    const body = updateIn<BodyEntry>(s.body, id, patch);
    const latest = (k: 'weightKg' | 'heightCm') =>
      body.filter((e) => e[k] != null).reduce<BodyEntry | null>((a, e) => (!a || e.time > a.time ? e : a), null)?.[k];
    const weightKg = latest('weightKg');
    const heightCm = latest('heightCm');
    return {
      ...s,
      body,
      profile: { ...s.profile, ...(weightKg ? { weightKg } : null), ...(heightCm ? { heightCm } : null) },
    };
  });
}

// ---- pantry ------------------------------------------------------------------------

export function savePantryItem(item: PantryItem) {
  setState((s) => ({
    ...s,
    pantry: s.pantry.some((p) => p.id === item.id) ? s.pantry.map((p) => (p.id === item.id ? item : p)) : [...s.pantry, item],
  }));
}

export function setPantryLeft(id: string, left: number) {
  setState((s) => ({ ...s, pantry: s.pantry.map((p) => (p.id === id ? { ...p, left } : p)) }));
}

export const removePantryItem = (id: string) => removeFrom('pantry', id);

// ---- health records ---------------------------------------------------------------

export function saveHealthRecord(r: HealthRecord) {
  setState((s) => ({
    ...s,
    health: s.health.some((x) => x.id === r.id) ? s.health.map((x) => (x.id === r.id ? r : x)) : [...s.health, r],
  }));
}

export const removeHealthRecord = (id: string) => removeFrom('health', id);

// ---- data ------------------------------------------------------------------------

export function wipeSection(key: keyof Pick<AppData, ListKey | 'steps'>) {
  setState((s) => ({ ...s, [key]: key === 'steps' ? {} : [] }));
}
