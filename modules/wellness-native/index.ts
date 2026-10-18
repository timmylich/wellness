/**
 * JS bindings for the local `WellnessNative` Android module (see android/…/WellnessNativeModule.kt).
 * On other platforms (or in tests) every function degrades to a safe default.
 */
import { requireNativeView, requireOptionalNativeModule } from 'expo';
import { createElement, type ComponentType } from 'react';
import { Platform, View, type ViewProps } from 'react-native';

type EventSubscription = { remove(): void };

interface WellnessNativeModule {
  hasStepCounter(): boolean;
  elapsedRealtime(): number;
  animatorDurationScale(): number;
  readStepCounter(timeoutMs: number): Promise<number | null>;
  startWatching(): boolean;
  stopWatching(): void;
  startStepService(title: string, text: string): boolean;
  stopStepService(): void;
  drainSteps(): Record<string, number>;
  openStepNotificationSettings(): void;
  notify(id: number, title: string, body: string, url: string | null): void;
  scheduleNotification(id: number, atMs: number, title: string, body: string): void;
  cancelNotification(id: number): void;
  addListener(event: 'onStepCounter', cb: (e: { steps: number }) => void): EventSubscription;
}

const native = requireOptionalNativeModule<WellnessNativeModule>('WellnessNative');

export function hasStepCounter(): boolean {
  return native?.hasStepCounter() ?? false;
}

/** Milliseconds since device boot. */
export function elapsedRealtime(): number {
  return native?.elapsedRealtime() ?? 0;
}

/** Android developer option "Animator duration scale" (1 = normal, 0.5 = faster, 0 = off). */
export function animatorDurationScale(): number {
  try {
    return native?.animatorDurationScale() ?? 1;
  } catch {
    return 1;
  }
}

/** Cumulative steps since boot from the hardware step counter, or null if unavailable. */
export async function readStepCounter(timeoutMs = 8000): Promise<number | null> {
  if (!native) return null;
  return native.readStepCounter(timeoutMs);
}

/**
 * Subscribe to live step-counter values (cumulative since boot). Returns an unsubscribe
 * function, or null if the sensor is unavailable.
 */
export function watchStepCounter(cb: (steps: number) => void): (() => void) | null {
  if (!native) return null;
  const sub = native.addListener('onStepCounter', (e) => cb(e.steps));
  if (!native.startWatching()) {
    sub.remove();
    return null;
  }
  return () => {
    sub.remove();
    native.stopWatching();
  };
}

/** Foreground service that keeps the step counter registered while the app is closed. */
export function startStepService(title: string, text: string): boolean {
  try {
    return native?.startStepService(title, text) ?? false;
  } catch {
    return false;
  }
}

export function stopStepService(): void {
  try {
    native?.stopStepService();
  } catch {}
}

/** Opens the step notification's channel settings so the user can hide it. */
export function openStepNotificationSettings(): void {
  try {
    native?.openStepNotificationSettings();
  } catch {}
}

/** Steps per day ("YYYY-MM-DD") recorded by the service since the previous call. */
export function drainSteps(): Record<string, number> {
  try {
    return native?.drainSteps() ?? {};
  } catch {
    return {};
  }
}

/** Show a local notification now; `url` opens a link when tapped. */
export function notify(id: number, title: string, body: string, url?: string): void {
  try {
    native?.notify(id, title, body, url ?? null);
  } catch (e) {
    console.warn('[notify] failed', e);
  }
}

/** Schedule a local notification (inexact; may be delayed by battery optimisations). */
export function scheduleNotification(id: number, atMs: number, title: string, body: string): void {
  try {
    native?.scheduleNotification(id, atMs, title, body);
  } catch (e) {
    console.warn('[notify] schedule failed', e);
  }
}

export function cancelNotification(id: number): void {
  try {
    native?.cancelNotification(id);
  } catch {}
}

type NestedScrollHostProps = ViewProps & { mode: 'horizontal' | 'hold' };

const NativeNestedScrollHost: ComponentType<NestedScrollHostProps> | null =
  native && Platform.OS === 'android' ? requireNativeView<NestedScrollHostProps>('WellnessNative', 'NestedScrollHost') : null;

/**
 * Keeps a gesture inside its children instead of letting the section pager or a vertical
 * ScrollView steal it (see NestedScrollHost.kt). `horizontal`: sideways drags stay inside;
 * `hold`: the gesture is claimed after a short press-and-hold. Plain View elsewhere.
 */
export function NestedScrollHost(props: NestedScrollHostProps) {
  if (!NativeNestedScrollHost) return createElement(View, props);
  return createElement(NativeNestedScrollHost, props);
}
