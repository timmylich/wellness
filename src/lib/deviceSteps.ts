/**
 * Step counting from the phone's hardware step counter (Android TYPE_STEP_COUNTER).
 *
 * The sensor hub keeps a cumulative "steps since boot" total even while Wellness is closed.
 * We read it when the app is in the foreground and from a periodic background task
 * (WorkManager, ~every 15+ minutes), and add the difference since the previous reading to
 * the current day.
 *
 * Limitations (explained to the user in the Activity section):
 * - Steps between two readings that span midnight are attributed to the later day.
 * - Aggressive OEM battery savers (MIUI, EMUI…) may delay background readings; the next
 *   reading still catches up, because the counter is cumulative.
 * - After a reboot the counter restarts from 0; steps since boot are counted, steps between
 *   the last reading and the reboot are lost.
 */
import { Pedometer } from 'expo-sensors';

import { elapsedRealtime, hasStepCounter, readStepCounter, watchStepCounter } from '../../modules/wellness-native';
import { addSteps, setStepCounter } from '../store/actions';
import { getState } from '../store/store';
import { todayKey } from './date';

/** Boot-time estimates differing by less than this are the same boot. */
const SAME_BOOT_TOLERANCE_MS = 2 * 60 * 1000;
/** Ignore absurd jumps (sensor glitches): more than this many steps between two readings. */
const MAX_PLAUSIBLE_DELTA = 100_000;

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

let queue: Promise<number> = Promise.resolve(0);

/**
 * Credit new steps from a cumulative counter value to today. All readings go through one
 * queue so the live watcher, the foreground poll and the background task never count the same
 * steps twice. Returns the number of steps added.
 */
export function applyReading(value: number): Promise<number> {
  queue = queue.then(() => apply(value)).catch(() => 0);
  return queue;
}

/** One-shot read of the counter (used on resume and by the background task). */
export async function syncDeviceSteps(): Promise<number> {
  const value = await readStepCounter();
  if (value == null) {
    console.log('[steps] counter read: no value (sensor did not report)');
    return 0;
  }
  return applyReading(value);
}

/** Live updates while the app is open. Returns an unsubscribe function or null. */
export function watchDeviceSteps(): (() => void) | null {
  return watchStepCounter((v) => {
    applyReading(v);
  });
}

function apply(value: number): number {
  const now = Date.now();
  const bootAt = now - elapsedRealtime();
  const prev = getState().meta.stepCounter;
  setStepCounter({ value, bootAt, at: now });

  // First reading ever: only establish a baseline.
  if (!prev) {
    console.log(`[steps] baseline set: ${value}`);
    return 0;
  }
  const rebooted = Math.abs(bootAt - prev.bootAt) > SAME_BOOT_TOLERANCE_MS || value < prev.value;
  const delta = rebooted ? value : value - prev.value;
  if (delta <= 0 || delta > MAX_PLAUSIBLE_DELTA) return 0;
  addSteps(todayKey(), delta);
  console.log(`[steps] counter ${value}: +${delta}${rebooted ? ' (after reboot)' : ''}`);
  return delta;
}
