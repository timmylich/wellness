/**
 * Motion settings. Animations run only when enabled in settings AND the OS does not ask to
 * reduce motion. Durations follow Android's "Animator duration scale" developer option, so
 * a phone set to ×0.5 gets animations twice as fast, and ×0 (off) disables them.
 */
import { useEffect, useState } from 'react';
import { AccessibilityInfo, LayoutAnimation } from 'react-native';

import { animatorDurationScale } from '../../modules/wellness-native';
import { getState, useStore } from '../store/store';

let systemReduceMotion = false;
AccessibilityInfo.isReduceMotionEnabled()
  .then((v) => (systemReduceMotion = v))
  .catch(() => {});
AccessibilityInfo.addEventListener('reduceMotionChanged', (v) => (systemReduceMotion = v));

/** Read once per launch; the setting rarely changes and the call is synchronous. */
const SYSTEM_SCALE = animatorDurationScale();

export function motionEnabled(): boolean {
  return getState().settings.animations && !systemReduceMotion && SYSTEM_SCALE > 0;
}

/** Scale a base duration (ms) by the system animator setting. */
export function dur(ms: number): number {
  return Math.round(ms * (SYSTEM_SCALE > 0 ? SYSTEM_SCALE : 1));
}

export function useMotion(): boolean {
  const setting = useStore((s) => s.settings.animations);
  const [reduce, setReduce] = useState(systemReduceMotion);
  useEffect(() => {
    const sub = AccessibilityInfo.addEventListener('reduceMotionChanged', setReduce);
    return () => sub.remove();
  }, []);
  return setting && !reduce && SYSTEM_SCALE > 0;
}

/** Animate the next layout change (list insert/remove). No-op when motion is off. */
export function animateLayout(duration = 200) {
  if (!motionEnabled()) return;
  LayoutAnimation.configureNext(LayoutAnimation.create(dur(duration), 'easeInEaseOut', 'opacity'));
}
