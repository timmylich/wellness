/**
 * Periodic background task that reads the hardware step counter while the app is closed.
 * Must be imported at app start (module scope) so the task is defined before Android runs it.
 */
import * as BackgroundTask from 'expo-background-task';
import * as TaskManager from 'expo-task-manager';

import { flushSave, getState, hydrate } from '../store/store';
import { syncDeviceSteps } from './deviceSteps';
import { hcStatus } from './healthConnect';

export const STEPS_TASK = 'wellness-steps-sync';

TaskManager.defineTask(STEPS_TASK, async () => {
  try {
    await hydrate();
    const s = getState().settings;
    // The device counter also backs up Health Connect when it is missing or not connected.
    if (s.sections.activity && (s.stepsSource === 'device' || (await hcStatus()) !== 'connected')) {
      const added = await syncDeviceSteps();
      await flushSave();
      console.log(`[steps] background sync: +${added}`);
    }
    return BackgroundTask.BackgroundTaskResult.Success;
  } catch (e) {
    console.warn('[steps] background sync failed', e);
    return BackgroundTask.BackgroundTaskResult.Failed;
  }
});

/** Register or unregister the background task to match the current settings. */
export async function configureBackgroundSteps(enabled: boolean): Promise<void> {
  try {
    const registered = await TaskManager.isTaskRegisteredAsync(STEPS_TASK);
    if (enabled && !registered) {
      await BackgroundTask.registerTaskAsync(STEPS_TASK, { minimumInterval: 15 });
      console.log('[steps] background task registered');
    }
    if (!enabled && registered) {
      await BackgroundTask.unregisterTaskAsync(STEPS_TASK);
      console.log('[steps] background task unregistered');
    }
  } catch (e) {
    console.warn('[steps] could not configure background task', e);
  }
}

export async function backgroundTasksAvailable(): Promise<boolean> {
  try {
    return (await BackgroundTask.getStatusAsync()) === BackgroundTask.BackgroundTaskStatus.Available;
  } catch {
    return false;
  }
}

/**
 * Whether the background step task should be registered. It runs for both sources: with
 * Health Connect it is a fallback that does nothing once Health Connect is connected.
 */
export function backgroundStepsWanted(): boolean {
  const { meta, settings } = getState();
  return meta.onboarded && settings.sections.activity;
}

// Register as soon as the JS bundle loads (also in headless/background starts), without
// waiting for any screen to render.
hydrate()
  .then(() => {
    const { meta, settings } = getState();
    console.log(`[steps] startup: onboarded=${meta.onboarded} activity=${settings.sections.activity} source=${settings.stepsSource}`);
    return configureBackgroundSteps(backgroundStepsWanted());
  })
  .catch((e) => console.warn('[steps] startup failed', e));
