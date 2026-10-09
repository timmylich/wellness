/**
 * Tiny i18n layer: typed dictionaries, no runtime dependency besides expo-localization.
 * Add a language by creating `xx.ts` exporting a `Dict`, then registering it in `DICTS`.
 */
import { getLocales } from 'expo-localization';

import { useStore } from '../store/store';
import type { LanguageSetting } from '../store/types';
import { en } from './en';
import { ru, type Dict } from './ru';

export type Lang = 'ru' | 'en';
export type { Dict };

const DICTS: Record<Lang, Dict> = { ru, en };

/** Languages whose speakers most likely prefer Russian over English among the ones we ship. */
const RU_LIKE = ['ru', 'uk', 'be', 'kk', 'ky', 'uz', 'tg', 'hy', 'az', 'ka'];

export function systemLang(): Lang {
  const code = getLocales()[0]?.languageCode ?? 'en';
  return RU_LIKE.includes(code) ? 'ru' : 'en';
}

export function resolveLang(setting: LanguageSetting): Lang {
  return setting === 'system' ? systemLang() : setting;
}

export function useLang(): Lang {
  return resolveLang(useStore((s) => s.settings.language));
}

/** Current dictionary. Convention: `const L = useT();` then `L.section.key`. */
export function useT(): Dict {
  return DICTS[useLang()];
}

export function dictFor(lang: Lang): Dict {
  return DICTS[lang];
}
