/**
 * Firebase Cloud Messaging implementation of `NotificationProvider`.
 * Imported once from app/_layout.tsx; registering the background handler at module scope is
 * required so messages received while the app is closed are handled.
 *
 * Builds without google-services.json still compile, but Firebase is then not initialised;
 * every call is wrapped so the app keeps working (see docs/notifications.md).
 */
import {
  getMessaging,
  getToken,
  onMessage,
  onTokenRefresh,
  setBackgroundMessageHandler,
} from '@react-native-firebase/messaging';
import { PermissionsAndroid, Platform } from 'react-native';

import type { NotificationProvider, PushMessage } from './notifications';

function messaging() {
  return getMessaging();
}

function toPush(m: { notification?: { title?: string; body?: string }; data?: Record<string, unknown> }): PushMessage {
  const data = m.data ? Object.fromEntries(Object.entries(m.data).map(([k, v]) => [k, String(v)])) : undefined;
  return { title: m.notification?.title, body: m.notification?.body, data };
}

try {
  // Data-only background messages: nothing to do yet; notification messages are shown by the OS.
  setBackgroundMessageHandler(messaging(), async () => {});
} catch (e) {
  console.warn('[fcm] background handler not set', e);
}

export const firebaseProvider: NotificationProvider = {
  name: 'fcm',
  async requestPermission() {
    // Android 13+ needs the runtime POST_NOTIFICATIONS permission; older versions grant it.
    if (Platform.OS === 'android' && Number(Platform.Version) >= 33) {
      const res = await PermissionsAndroid.request('android.permission.POST_NOTIFICATIONS');
      return res === PermissionsAndroid.RESULTS.GRANTED;
    }
    return true;
  },
  getToken: async () => {
    try {
      return await getToken(messaging());
    } catch (e) {
      console.warn('[fcm] getToken failed', e);
      return null;
    }
  },
  onTokenRefresh: (cb) => {
    try {
      return onTokenRefresh(messaging(), cb);
    } catch {
      return () => {};
    }
  },
  onMessage: (cb) => {
    try {
      return onMessage(messaging(), (m) => cb(toPush(m)));
    } catch {
      return () => {};
    }
  },
};
