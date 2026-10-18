/**
 * Step counting from the phone's hardware step counter (Android TYPE_STEP_COUNTER).
 *
 * A small foreground service (modules/wellness-native StepService.kt) keeps the sensor
 * registered while the app is closed and buckets steps per local day. Many phones (e.g.
 * Xiaomi) only count while a client holds a registration, so periodic one-shot reads miss
 * every step. JS drains the buckets on resume, on sensor events and from the background task.
 */
import { Pedometer } from 'expo-sensors';

import { drainSteps, hasStepCounter, startStepService, stopStepService, watchStepCounter } from '../../modules/wellness-native';
import { addSteps } from '../store/actions';

export function deviceStepsSupported(): boolean {
  return hasStepCounter();
}

export async function requestStepPermission(): Promise<boolean> {
  try {
    const res = await Pedometer.requestPermissionsAsync();
    return res.granted;
  } catch {
    return false;
  }
}

/** Credit the steps the native service bucketed per day since the previous drain. */
function drain(): number {
  const days = drainSteps();
  let total = 0;
  for (const [day, n] of Object.entries(days)) {
    const steps = Math.round(n);
    if (steps <= 0) continue;
    addSteps(day, steps);
    total += steps;
  }
  if (total > 0) console.log(`[steps] service: +${total}`);
  return total;
}

/**
 * Start the foreground step service (idempotent). It holds the step-counter registration
 * while the app is closed, which many phones require for the counter to count at all.
 */
export function startDeviceSteps(title: string, text: string): boolean {
  return startStepService(title, text);
}

export function stopDeviceSteps(): void {
  drain();
  stopStepService();
}

/** Pull steps recorded by the service (used on resume, by the poll and the background task). */
export async function syncDeviceSteps(): Promise<number> {
  return drain();
}

/** Live updates while the app is open: each sensor event triggers a drain. */
export function watchDeviceSteps(): (() => void) | null {
  let t: ReturnType<typeof setTimeout> | null = null;
  const stop = watchStepCounter(() => {
    // Let the service's listener store the same event first.
    t ??= setTimeout(() => {
      t = null;
      drain();
    }, 500);
  });
  if (!stop) return null;
  return () => {
    if (t) clearTimeout(t);
    stop();
  };
}
