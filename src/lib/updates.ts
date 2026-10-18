/**
 * Optional update check against GitHub Releases (no account, no server of our own).
 * Runs at most once a day when enabled in Settings → Notifications, and notifies once per new
 * version. Builds distributed by F-Droid set EXPO_PUBLIC_UPDATE_CHECK=0: F-Droid updates the
 * app itself and does not allow a self-update channel.
 */
import Constants from 'expo-constants';

import { REPO_URL } from '../data/donate';
import { updateMeta } from '../store/actions';
import { getState } from '../store/store';
import { showNotification } from './notifications';

const API = 'https://api.github.com/repos/timmylich/wellness/releases/latest';
const DAY_MS = 86_400_000;

export const UPDATE_CHECK_BUILT_IN = process.env.EXPO_PUBLIC_UPDATE_CHECK !== '0';

export function currentVersion(): string {
  return Constants.expoConfig?.version ?? '0.0.0';
}

/** Compare dotted versions ("v1.2.10" > "1.2.9"). */
export function isNewer(latest: string, current: string): boolean {
  const p = (v: string) => v.replace(/^v/i, '').split(/[.-]/).map((x) => parseInt(x, 10) || 0);
  const a = p(latest);
  const b = p(current);
  for (let i = 0; i < Math.max(a.length, b.length); i++) {
    if ((a[i] ?? 0) !== (b[i] ?? 0)) return (a[i] ?? 0) > (b[i] ?? 0);
  }
  return false;
}

export interface Release {
  version: string;
  url: string;
}

/** Latest published release, or null on network errors. */
export async function fetchLatest(): Promise<Release | null> {
  try {
    const res = await fetch(API, { headers: { Accept: 'application/vnd.github+json' } });
    if (!res.ok) return null;
    const j = (await res.json()) as { tag_name?: string; html_url?: string; draft?: boolean; prerelease?: boolean };
    if (!j.tag_name || j.draft || j.prerelease) return null;
    return { version: j.tag_name.replace(/^v/i, ''), url: j.html_url ?? `${REPO_URL}/releases/latest` };
  } catch {
    return null;
  }
}

/** Daily background-friendly check; `texts` builds the localized notification. */
export async function maybeCheckForUpdate(texts: (v: string) => { title: string; body: string }): Promise<void> {
  const { settings, meta } = getState();
  const n = settings.notifications;
  if (!UPDATE_CHECK_BUILT_IN || !n.enabled || !n.updates) return;
  if (meta.updateCheckedAt && Date.now() - meta.updateCheckedAt < DAY_MS) return;
  const latest = await fetchLatest();
  updateMeta({ updateCheckedAt: Date.now() });
  if (!latest || !isNewer(latest.version, currentVersion()) || meta.updateNotified === latest.version) return;
  const t = texts(latest.version);
  showNotification(`update-${latest.version}`, t.title, t.body, latest.url);
  updateMeta({ updateNotified: latest.version });
}
