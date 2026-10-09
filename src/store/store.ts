/**
 * Minimal global store: one immutable state object, persisted to AsyncStorage.
 *
 * Deliberately dependency-free — `useSyncExternalStore` gives us selector-based
 * subscriptions, and a debounced writer keeps disk I/O off the hot path.
 */
import AsyncStorage from '@react-native-async-storage/async-storage';
import { useSyncExternalStore } from 'react';

import { BUILTIN_WORKOUTS } from '../data/workouts';
import { todayKey } from '../lib/date';
import type { AppData, Settings } from './types';

const STORAGE_KEY = 'wellness/data';
export const SCHEMA_VERSION = 1;

export const DEFAULT_SETTINGS: Settings = {
  language: 'system',
  theme: 'system',
  animations: true,
  stepsSource: 'device',
  accent: '#2E9E6A',
  sections: { nutrition: true, water: true, activity: true, heart: true, body: true, tips: true },
  adaptiveTargets: false,
  onlineFoodSearch: true,
  waterPresets: [150, 250, 330, 500],
  waterAmountMl: 250,
  waterDangerMl: 6000,
  stepGoal: 8000,
  showHints: true,
  donationPrompts: true,
  stepsInfoDismissed: false,
  usdaApiKey: '',
  notifications: { enabled: false, waterReminders: false, weighInReminder: false },
  nutrientOverrides: {},
};

export function createDefaultData(): AppData {
  return {
    schemaVersion: SCHEMA_VERSION,
    profile: {
      name: '',
      sex: 'male',
      birthYear: new Date().getFullYear() - 30,
      heightCm: 175,
      weightKg: 75,
      targetWeightKg: 75,
      weeklyChangeKg: 0,
      lifestyleFactor: 1.3,
    },
    settings: DEFAULT_SETTINGS,
    meta: { onboarded: false, usageDays: [], donationShown: [], createdAt: Date.now() },
    foods: [],
    favorites: [],
    foodLog: [],
    water: [],
    workouts: [],
    workoutTemplates: BUILTIN_WORKOUTS,
    steps: {},
    heart: [],
    body: [],
  };
}

let state: AppData = createDefaultData();
let hydrated = false;
const listeners = new Set<() => void>();
let saveTimer: ReturnType<typeof setTimeout> | null = null;

function emit() {
  listeners.forEach((l) => l());
}

async function persist() {
  try {
    await AsyncStorage.setItem(STORAGE_KEY, JSON.stringify(state));
  } catch (e) {
    console.warn('Failed to persist data', e);
  }
}

/** Write pending changes immediately (background tasks may be killed before the debounce fires). */
export async function flushSave(): Promise<void> {
  if (saveTimer) {
    clearTimeout(saveTimer);
    saveTimer = null;
  }
  await persist();
}

function scheduleSave() {
  if (saveTimer) clearTimeout(saveTimer);
  saveTimer = setTimeout(() => {
    saveTimer = null;
    persist();
  }, 300);
}

/**
 * Fill in any fields missing from older/partial data (e.g. an import made by an
 * earlier app version) so the rest of the app can rely on a complete shape.
 */
export function normalize(raw: Partial<AppData>): AppData {
  const def = createDefaultData();
  return {
    ...def,
    ...raw,
    schemaVersion: SCHEMA_VERSION,
    profile: migrateProfile({ ...def.profile, ...raw.profile }),
    settings: {
      ...def.settings,
      ...raw.settings,
      sections: { ...def.settings.sections, ...raw.settings?.sections },
      notifications: { ...def.settings.notifications, ...raw.settings?.notifications },
      // v0.2 called the live pedometer source 'sensor'; the device counter replaces it.
      stepsSource: raw.settings?.stepsSource === 'healthConnect' ? 'healthConnect' : 'device',
    },
    meta: { ...def.meta, ...raw.meta },
    workoutTemplates: raw.workoutTemplates?.length ? raw.workoutTemplates : def.workoutTemplates,
  };
}

/**
 * v0.1 stored an unsigned `paceKgPerWeek` whose direction came from the target weight.
 * Convert it to the signed `weeklyChangeKg`.
 */
function migrateProfile(p: AppData['profile'] & { paceKgPerWeek?: number }): AppData['profile'] {
  const { paceKgPerWeek, ...rest } = p;
  if (paceKgPerWeek == null) return rest;
  const diff = rest.targetWeightKg - rest.weightKg;
  return { ...rest, weeklyChangeKg: Math.abs(diff) < 0.3 ? 0 : Math.sign(diff) * paceKgPerWeek };
}

export function getState(): AppData {
  return state;
}

/** Apply an immutable update. `fn` must return a new object (or the same one for no-op). */
export function setState(fn: (s: AppData) => AppData) {
  const next = fn(state);
  if (next === state) return;
  state = next;
  emit();
  scheduleSave();
}

export function replaceState(data: AppData) {
  setState(() => data);
}

export async function hydrate(): Promise<void> {
  if (hydrated) return;
  try {
    const raw = await AsyncStorage.getItem(STORAGE_KEY);
    if (raw) state = normalize(JSON.parse(raw));
  } catch (e) {
    console.warn('Failed to load data, starting fresh', e);
  }
  hydrated = true;
  recordUsageDay();
  emit();
}

function recordUsageDay() {
  const today = todayKey();
  if (!state.meta.usageDays.includes(today)) {
    setState((s) => ({ ...s, meta: { ...s.meta, usageDays: [...s.meta.usageDays, today] } }));
  }
}

function subscribe(l: () => void) {
  listeners.add(l);
  return () => listeners.delete(l);
}

/** Subscribe a component to a slice of state. Selectors should return stable references. */
export function useStore<T>(selector: (s: AppData) => T): T {
  return useSyncExternalStore(subscribe, () => selector(state));
}

export function useHydrated(): boolean {
  return useSyncExternalStore(subscribe, () => hydrated);
}

/** Short random id; collision-safe enough for local single-user data. */
export function uid(): string {
  return Date.now().toString(36) + Math.random().toString(36).slice(2, 8);
}
