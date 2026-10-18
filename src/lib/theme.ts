/** Color palette resolved from user settings (light/dark/system + accent color). */
import { useColorScheme } from 'react-native';

import { useStore } from '../store/store';

export interface Theme {
  dark: boolean;
  bg: string;
  card: string;
  cardAlt: string;
  text: string;
  textDim: string;
  border: string;
  accent: string;
  accentSoft: string;
  ok: string;
  low: string;
  high: string;
  danger: string;
  water: string;
}

export const ACCENTS = ['#2E9E6A', '#2F7DE1', '#7A5AF8', '#E0457B', '#F08C2E', '#0FA3A3', '#5C6B7A'];

/** Append 8-bit alpha to a #RRGGBB color. */
export function alpha(hex: string, a: number): string {
  return hex + Math.round(a * 255).toString(16).padStart(2, '0');
}

export function useTheme(): Theme {
  const system = useColorScheme();
  const mode = useStore((s) => s.settings.theme);
  const userAccent = useStore((s) => s.settings.accent);
  const amoled = useStore((s) => s.settings.amoled);
  const dark = mode === 'system' ? system === 'dark' : mode === 'dark';
  const accent = userAccent;
  if (dark && amoled) {
    return {
      dark, accent,
      bg: '#000000', card: '#0C0E0F', cardAlt: '#16191B', text: '#ECEFF1', textDim: '#8D989F',
      border: '#1F2427', accentSoft: alpha(accent, 0.16),
      ok: '#3CC47C', low: '#E8B341', high: '#F08C2E', danger: '#EF5350', water: '#4FA8F7',
    };
  }
  return dark
    ? {
        dark, accent,
        bg: '#0F1214', card: '#1A1F23', cardAlt: '#242A2F', text: '#ECEFF1', textDim: '#8D989F',
        border: '#2C343A', accentSoft: alpha(accent, 0.18),
        ok: '#3CC47C', low: '#E8B341', high: '#F08C2E', danger: '#EF5350', water: '#4FA8F7',
      }
    : {
        dark, accent,
        bg: '#F4F6F5', card: '#FFFFFF', cardAlt: '#F0F3F2', text: '#1B2124', textDim: '#69757B',
        border: '#E4E9E7', accentSoft: alpha(accent, 0.1),
        ok: '#23A15D', low: '#D19A12', high: '#E0761B', danger: '#D93636', water: '#2F8FE6',
      };
}

export function toneColor(t: Theme, tone: 'ok' | 'good' | 'low' | 'high' | 'danger'): string {
  return tone === 'good' ? t.ok : t[tone];
}
