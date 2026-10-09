/**
 * Health tips shown in the Tips tab. Content lives in per-language files (tips.ru.ts,
 * tips.en.ts) with identical ids. Keep each claim conservative and backed by mainstream
 * guidance (WHO, IARC, national health services); mention it in `source`.
 */
import type { Lang } from '../i18n';
import { TIPS_EN } from './tips.en';
import { TIPS_RU } from './tips.ru';

export interface Tip {
  id: string;
  title: string;
  body: string[];
  source?: string;
}

/** Icon keys map to components in components/Icon.tsx. */
export type TipIcon = 'air' | 'water' | 'cancer' | 'sun' | 'back' | 'sleep' | 'heart' | 'nutrition';

export interface TipCategory {
  id: string;
  title: string;
  icon: TipIcon;
  tips: Tip[];
}

export function tipsFor(lang: Lang): TipCategory[] {
  return lang === 'ru' ? TIPS_RU : TIPS_EN;
}
