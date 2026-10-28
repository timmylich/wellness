/**
 * Export / import of all user data as a single JSON file (health record photos included as
 * base64 under `photoFiles`, keyed by file name), plus importing
 * "food packs" — shareable JSON lists of foods (e.g. a restaurant chain menu).
 * Food pack format is documented in docs/food-packs.md.
 * Errors are thrown with i18n keys from `settings` (shareUnavailable, notBackup, noFoods).
 */
import { Directory, File, Paths } from 'expo-file-system';
import * as Sharing from 'expo-sharing';

import { getState, normalize, replaceState, setState } from '../store/store';
import type { AppData, Food } from '../store/types';
import { todayKey } from './date';
import { photoDir, photoName, relinkPhotos } from './photos';

/** Backup file: the app data plus the photos it refers to. */
type Backup = AppData & { photoFiles?: Record<string, string> };

/** Base64 of every existing photo the health records refer to. */
async function collectPhotos(s: AppData): Promise<Record<string, string>> {
  const out: Record<string, string> = {};
  for (const uri of s.health.flatMap((r) => r.photos ?? [])) {
    try {
      const f = new File(uri);
      if (f.exists) out[photoName(uri)] = await f.base64();
    } catch {}
  }
  return out;
}

/**
 * Write the backup's photos into the photo folder, point the records at them and delete
 * photos no longer referenced by anything. Photos missing from an old backup are kept if a
 * file with the same name is still on the phone.
 */
function restorePhotos(data: AppData, files: Record<string, string>): AppData {
  const dir = photoDir();
  for (const [name, b64] of Object.entries(files)) {
    // Names are generated ids; refuse anything that could leave the folder.
    if (!/^[\w.-]+$/.test(name)) continue;
    try {
      const f = new File(dir, name);
      if (!f.exists) f.create();
      f.write(b64, { encoding: 'base64' });
    } catch {}
  }
  const health = relinkPhotos(data.health, (name) => {
    const f = new File(dir, name);
    return f.exists ? f.uri : null;
  });
  const used = new Set(health.flatMap((r) => r.photos.map(photoName)));
  for (const item of dir.list()) {
    if (item instanceof File && !used.has(item.name)) {
      try {
        item.delete();
      } catch {}
    }
  }
  return { ...data, health };
}

/**
 * Save a backup file into a folder the user picks (Storage Access Framework). Returns the file
 * name, or null if the user cancelled. Falls back to the share sheet if folder picking fails.
 */
export async function exportData(): Promise<string | null> {
  const name = `wellness-backup-${todayKey()}.json`;
  const state = getState();
  const backup: Backup = { ...state, photoFiles: await collectPhotos(state) };
  const json = JSON.stringify(backup);
  let dir: Directory | null = null;
  try {
    dir = await Directory.pickDirectoryAsync();
  } catch (e) {
    // Cancelling the picker rejects; anything else falls through to sharing.
    if (String(e).toLowerCase().includes('cancel')) return null;
  }
  if (dir) {
    const file = dir.createFile(name, 'application/json');
    file.write(json);
    return name;
  }
  const file = new File(Paths.cache, name);
  if (file.exists) file.delete();
  file.create();
  file.write(json);
  if (!(await Sharing.isAvailableAsync())) throw new Error('shareUnavailable');
  await Sharing.shareAsync(file.uri, { mimeType: 'application/json', dialogTitle: 'Wellness backup' });
  return name;
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
  const { photoFiles, ...data } = json as Partial<Backup>;
  replaceState(restorePhotos(normalize(data), photoFiles ?? {}));
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
