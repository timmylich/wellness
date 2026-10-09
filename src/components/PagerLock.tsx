/**
 * Lets a nested horizontal carousel temporarily disable the overview's section pager, so a
 * sideways swipe moves the carousel instead of switching sections.
 */
import { createContext, useContext } from 'react';

export const PagerLock = createContext<(locked: boolean) => void>(() => {});

/** Touch handlers to spread on the carousel's wrapper View. */
export function usePagerLockHandlers() {
  const setLocked = useContext(PagerLock);
  return {
    onTouchStart: () => setLocked(true),
    onTouchEnd: () => setLocked(false),
    onTouchCancel: () => setLocked(false),
  };
}
