/**
 * Export / import of all user data as a single JSON file, plus importing
 * "food packs" — shareable JSON lists of foods (e.g. a restaurant chain menu).
 * Food pack format is documented in docs/food-packs.md.
 * Errors are thrown with i18n keys from `settings` (shareUnavailable, notBackup, noFoods).
 */
import { File, Paths } from 'expo-file-system';
import * as Sharing from 'expo-sharing';

import { getState, normalize, replaceState, setState } from '../store/store';
import type { AppData, Food } from '../store/types';
import { todayKey } from './date';

export async function exportData(): Promise<void> {
  const file = new File(Paths.cache, `wellness-backup-${todayKey()}.json`);
  if (file.exists) file.delete();
  file.create();
  file.write(JSON.stringify(getState(), null, 2));
  if (!(await Sharing.isAvailableAsync())) throw new Error('shareUnavailable');
  await Sharing.shareAsync(file.uri, { mimeType: 'application/json', dialogTitle: 'Wellness backup' });
}

/** Opens the system picker and returns parsed JSON, or null if the user cancelled. */
async function pickJson(): Promise<unknown | null> {
  // Some file managers report JSON as text/plain or octet-stream, so accept anything.
  const picked = await File.pickFileAsync({ mimeTypes: '*/*' });
  if (picked.canceled) return null;
  return JSON.parse(await picked.result.text());
}

function isAppData(x: unknown): x is Partial<AppData> {
  return !!x && typeof x === 'object' && 'profile' in x && 'settings' in x;
}

/** Replace all data with a backup. Returns false if the user cancelled. */
export async function importData(): Promise<boolean> {
  const json = await pickJson();
  if (json == null) return false;
  if (!isAppData(json)) throw new Error('notBackup');
  replaceState(normalize(json));
  return true;
}

/** Merge a food pack into the local database. Returns number of foods added/updated. */
export async function importFoodPack(): Promise<number | null> {
  const json = (await pickJson()) as { name?: string; foods?: Partial<Food>[] } | null;
  if (json == null) return null;
  if (!Array.isArray(json.foods)) throw new Error('noFoods');
  const packName = json.name ?? 'pack';
  const foods: Food[] = json.foods
    .filter((f) => f.name && f.per100g)
    .map((f, i) => ({
      ...f,
      id: `pack-${packName}-${f.id ?? i}`,
      name: f.name!,
      per100g: f.per100g!,
      source: 'pack',
    }));
  setState((s) => {
    const ids = new Set(foods.map((f) => f.id));
    return { ...s, foods: [...s.foods.filter((f) => !ids.has(f.id)), ...foods] };
  });
  return foods.length;
}
