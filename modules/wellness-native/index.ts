/**
 * JS bindings for the local `WellnessNative` Android module (see android/…/WellnessNativeModule.kt).
 * On other platforms (or in tests) every function degrades to a safe default.
 */
import { requireOptionalNativeModule } from 'expo';

type EventSubscription = { remove(): void };

interface WellnessNativeModule {
  hasStepCounter(): boolean;
  elapsedRealtime(): number;
  animatorDurationScale(): number;
  readStepCounter(timeoutMs: number): Promise<number | null>;
  startWatching(): boolean;
  stopWatching(): void;
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
