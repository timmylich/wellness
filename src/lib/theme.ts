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
  const accent = useStore((s) => s.settings.accent);
  const dark = mode === 'system' ? system === 'dark' : mode === 'dark';
  return dark
    ? {
        dark, accent,
        bg: '#0F1214', card: '#1A1F23', cardAlt: '#242A2F', text: '#ECEFF1', textDim: '#8D989F',
        border: '#2C343A', accentSoft: alpha(accent, 0.18),
        ok: '#3CC47C', low: '#E8B341', high: '#F08C2E', danger: '#EF5350', water: '#4FA8F7',
      }
    : {
        dark, accent,
        bg: '#F3F5F4', card: '#FFFFFF', cardAlt: '#EEF1F0', text: '#1B2124', textDim: '#69757B',
        border: '#E1E6E4', accentSoft: alpha(accent, 0.12),
        ok: '#23A15D', low: '#D19A12', high: '#E0761B', danger: '#D93636', water: '#2F8FE6',
      };
}

export function toneColor(t: Theme, tone: 'ok' | 'good' | 'low' | 'high' | 'danger'): string {
  return tone === 'good' ? t.ok : t[tone];
}
