/**
 * Local notifications (no push server, no Firebase/Play services): pantry expiry reminders and
 * app-update notices. Delivered by the local native module (modules/wellness-native Reminders.kt).
 * Everything is opt-in via Settings → Notifications.
 */
import { PermissionsAndroid, Platform } from 'react-native';

import { cancelNotification, notify, scheduleNotification } from '../../modules/wellness-native';

/** Ask for Android 13+ POST_NOTIFICATIONS; older versions grant it at install. */
export async function requestNotificationPermission(): Promise<boolean> {
  if (Platform.OS !== 'android') return false;
  if (Number(Platform.Version) < 33) return true;
  const res = await PermissionsAndroid.request('android.permission.POST_NOTIFICATIONS');
  return res === PermissionsAndroid.RESULTS.GRANTED;
}

/** Stable positive 31-bit id from a string key (notification ids must be ints). */
export function notificationId(key: string): number {
  let h = 0;
  for (let i = 0; i < key.length; i++) h = (Math.imul(31, h) + key.charCodeAt(i)) | 0;
  return Math.abs(h) || 1;
}

export function showNotification(key: string, title: string, body: string, url?: string) {
  notify(notificationId(key), title, body, url);
}

export function scheduleAt(key: string, at: number, title: string, body: string) {
  if (at <= Date.now()) return;
  scheduleNotification(notificationId(key), at, title, body);
}

export function cancelScheduled(key: string) {
  cancelNotification(notificationId(key));
}
