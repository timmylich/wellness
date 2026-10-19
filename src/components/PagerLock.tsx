/**
 * Lets a chart being scrubbed temporarily disable the overview's section pager. Carousels don't
 * need it: NestedScrollHost keeps their sideways drags (and hands edge swipes to the pager).
 */
import { createContext } from 'react';

export const PagerLock = createContext<(locked: boolean) => void>(() => {});

