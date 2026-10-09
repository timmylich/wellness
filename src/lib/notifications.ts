/**
 * Notification layer, prepared for Firebase Cloud Messaging (FCM).
 *
 * The app talks only to `NotificationProvider`. The Firebase Cloud Messaging implementation
 * lives in lib/firebaseMessaging.ts and is installed in app/_layout.tsx; forks without
 * Firebase can keep `noopProvider` (see docs/notifications.md).
 *
 * Privacy: push stays opt-in (Settings → Notifications) and no health data is ever sent to
 * the push server — only the device token, if the user enables notifications.
 */
import { setPushToken } from '../store/actions';
import { getState } from '../store/store';

export interface NotificationProvider {
  /** Human-readable id for logs. */
  name: string;
  /** Ask the OS for notification permission (Android 13+ POST_NOTIFICATIONS). */
  requestPermission(): Promise<boolean>;
  /** Device token for server-side pushes, or null if not supported. */
  getToken(): Promise<string | null>;
  /** Subscribe to token rotation. Returns an unsubscribe function. */
  onTokenRefresh(cb: (token: string) => void): () => void;
  /** Handle a message received while the app is in the foreground. */
  onMessage(cb: (msg: PushMessage) => void): () => void;
}

export interface PushMessage {
  title?: string;
  body?: string;
  /** Free-form payload, e.g. { type: 'waterReminder' }. */
  data?: Record<string, string>;
}

/** Default provider: does nothing. Keeps the app fully functional without Firebase. */
export const noopProvider: NotificationProvider = {
  name: 'noop',
  requestPermission: async () => false,
  getToken: async () => null,
  onTokenRefresh: () => () => {},
  onMessage: () => () => {},
};

let provider: NotificationProvider = noopProvider;

export function setNotificationProvider(p: NotificationProvider) {
  provider = p;
}

export function notificationsAvailable(): boolean {
  return provider !== noopProvider;
}

/**
 * Called on app start: if the user enabled notifications, (re)register and keep the token
 * up to date. Returns a cleanup function.
 */
export function initNotifications(onMessage?: (m: PushMessage) => void): () => void {
  if (!getState().settings.notifications.enabled || provider === noopProvider) return () => {};
  provider
    .requestPermission()
    .then((ok) => (ok ? provider.getToken() : null))
    .then((token) => token && setPushToken(token))
    .catch((e) => console.warn(`[notifications] ${provider.name} init failed`, e));
  const unsubToken = provider.onTokenRefresh(setPushToken);
  const unsubMsg = provider.onMessage((m) => onMessage?.(m));
  return () => {
    unsubToken();
    unsubMsg();
  };
}
