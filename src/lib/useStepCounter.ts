/**
 * Step tracking while the app runs, plus background-task registration.
 * - `device`: poll the hardware step counter every few seconds in the foreground; the
 *   background task (lib/backgroundSteps.ts) covers the time the app is closed.
 * - `healthConnect`: pull daily totals from Health Connect on launch, on resume and every
 *   5 minutes; other apps/the OS keep recording into Health Connect in the background.
 *   While Health Connect is missing or not connected, the device counter is used instead.
 */
import { useEffect } from 'react';
import { AppState } from 'react-native';

import type { StepsSource } from '../store/types';
import { configureBackgroundSteps } from './backgroundSteps';
import { dictFor, resolveLang } from '../i18n';
import { getState } from '../store/store';
import { requestStepPermission, startDeviceSteps, stopDeviceSteps, syncDeviceSteps, watchDeviceSteps } from './deviceSteps';
import { hcSyncSteps } from './healthConnect';

// The live watcher does the work in the foreground; the poll is only a safety net.
const DEVICE_POLL_MS = 60 * 1000;
const HC_INTERVAL_MS = 5 * 60 * 1000;

export function useStepTracking(enabled: boolean, source: StepsSource) {
  useEffect(() => {
    configureBackgroundSteps(enabled);
    if (!enabled) {
      stopDeviceSteps();
      return;
    }

    // Health Connect falls back to the device counter while it is missing or not connected.
    const sync = () =>
      (source === 'device' ? syncDeviceSteps() : hcSyncSteps().then((ok) => (ok ? 0 : syncDeviceSteps()))).catch((e) =>
        console.warn('Step sync failed', e),
      );
    let timer: ReturnType<typeof setInterval> | null = null;
    const start = () => {
      sync();
      timer ??= setInterval(sync, source === 'device' ? DEVICE_POLL_MS : HC_INTERVAL_MS);
    };
    const stop = () => {
      if (timer) clearInterval(timer);
      timer = null;
    };

    // Live sensor subscription while the app is open (device source, or HC fallback).
    let unwatch: (() => void) | null = null;
    const watch = () => {
      unwatch ??= watchDeviceSteps();
    };
    const unwatchNow = () => {
      unwatch?.();
      unwatch = null;
    };

    requestStepPermission().then((granted) => {
      if (!granted) console.log('[steps] activity recognition permission denied');
      // The service also backs up Health Connect until it is connected (the drain is cheap).
      const L = dictFor(resolveLang(getState().settings.language));
      if (granted) startDeviceSteps(L.activity.serviceTitle, L.activity.serviceText);
      start();
      watch();
    });
    const sub = AppState.addEventListener('change', (s) => {
      if (s === 'active') {
        start();
        watch();
      } else {
        stop();
        unwatchNow();
      }
    });
    return () => {
      stop();
      unwatchNow();
      sub.remove();
    };
  }, [enabled, source]);
}
