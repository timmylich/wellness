/**
 * Photos attached to health records. They are copied into the app's private document folder
 * (Paths.document/health), so they survive the user deleting the original from the gallery,
 * and travel inside backups as base64 (see backup.ts).
 */
import { Directory, File, Paths } from 'expo-file-system';

import { uid } from '../store/store';
import type { HealthRecord } from '../store/types';

export function photoDir(): Directory {
  const d = new Directory(Paths.document, 'health');
  if (!d.exists) d.create({ intermediates: true, idempotent: true });
  return d;
}

/** File name of a photo URI (the part after the last slash, without a query). */
export function photoName(uri: string): string {
  return uri.split('?')[0].split('/').pop() ?? '';
}

/** Copy picked images into the app folder; returns their new URIs. */
export async function keepPhotos(uris: string[]): Promise<string[]> {
  const dir = photoDir();
  const out: string[] = [];
  for (const uri of uris) {
    const ext = uri.split('?')[0].split('.').pop()?.toLowerCase() || 'jpg';
    const dest = new File(dir, `${uid()}.${ext.length <= 4 ? ext : 'jpg'}`);
    await new File(uri).copy(dest);
    out.push(dest.uri);
  }
  return out;
}

export function deletePhotos(uris: string[]) {
  for (const u of uris) {
    try {
      const f = new File(u);
      if (f.exists) f.delete();
    } catch {}
  }
}

/**
 * Point every record's photos at `uriOf(name)` (the photo folder on this phone), dropping the
 * ones `uriOf` cannot find. Backups made on another phone or before a reinstall carry old paths.
 */
export function relinkPhotos(records: HealthRecord[], uriOf: (name: string) => string | null): HealthRecord[] {
  return records.map((r) => ({
    ...r,
    photos: (r.photos ?? []).map((p) => uriOf(photoName(p))).filter((p): p is string => !!p),
  }));
}
