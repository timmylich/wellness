/**
 * Health Connect integration (Android): reads daily step totals written by any app
 * (Google Fit, Mi Fitness, Zepp, Samsung Health, the OS itself on Android 14+).
 * This gives background step counting without running our own service.
 *
 * Read-only: Wellness never writes to Health Connect.
 */
import { Linking, Platform } from 'react-native';
import {
  aggregateRecord,
  getGrantedPermissions,
  getSdkStatus,
  initialize,
  openHealthConnectSettings,
  requestPermission,
  SdkAvailabilityStatus,
} from 'react-native-health-connect';

import { setStepsBulk } from '../store/actions';
import { addDays, dateKey, parseKey, todayKey } from './date';

export type HcStatus = 'unavailable' | 'update' | 'noPermission' | 'connected';

const PLAY_STORE = 'market://details?id=com.google.android.apps.healthdata';
const PLAY_STORE_WEB = 'https://play.google.com/store/apps/details?id=com.google.android.apps.healthdata';

let initialized = false;

async function ensureInit(): Promise<boolean> {
  if (initialized) return true;
  initialized = await initialize();
  return initialized;
}

export async function hcStatus(): Promise<HcStatus> {
  if (Platform.OS !== 'android') return 'unavailable';
  try {
    const sdk = await getSdkStatus();
    if (sdk === SdkAvailabilityStatus.SDK_UNAVAILABLE) return 'unavailable';
    if (sdk === SdkAvailabilityStatus.SDK_UNAVAILABLE_PROVIDER_UPDATE_REQUIRED) return 'update';
    if (!(await ensureInit())) return 'unavailable';
    const granted = await getGrantedPermissions();
    return granted.some((p) => p.recordType === 'Steps' && p.accessType === 'read') ? 'connected' : 'noPermission';
  } catch (e) {
    console.warn('Health Connect status failed', e);
    return 'unavailable';
  }
}

/** Ask for read access to steps. Returns true if granted. */
export async function hcConnect(): Promise<boolean> {
  if (!(await ensureInit())) return false;
  const granted = await requestPermission([{ accessType: 'read', recordType: 'Steps' }]);
  return granted.some((p) => p.recordType === 'Steps' && p.accessType === 'read');
}

export function hcInstall() {
  Linking.openURL(PLAY_STORE).catch(() => Linking.openURL(PLAY_STORE_WEB));
}

export function hcOpenSettings() {
  try {
    openHealthConnectSettings();
  } catch {
    hcInstall();
  }
}

/**
 * Pull daily step totals for the last `days` days (today included) and store them.
 * Health Connect de-duplicates overlapping sources itself in aggregate queries.
 */
export async function hcSyncSteps(days = 14): Promise<boolean> {
  if ((await hcStatus()) !== 'connected') return false;
  const today = todayKey();
  const result: Record<string, number> = {};
  for (let i = days - 1; i >= 0; i--) {
    const day = addDays(today, -i);
    const start = parseKey(day);
    const end = parseKey(addDays(day, 1));
    try {
      const agg = await aggregateRecord({
        recordType: 'Steps',
        timeRangeFilter: { operator: 'between', startTime: start.toISOString(), endTime: end.toISOString() },
      });
      result[dateKey(start)] = Math.round(agg.COUNT_TOTAL ?? 0);
    } catch (e) {
      console.warn('Health Connect step read failed for', day, e);
    }
  }
  setStepsBulk(result);
  return true;
}
