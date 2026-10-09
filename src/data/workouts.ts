/**
 * Built-in activity templates. MET values from the
 * Compendium of Physical Activities (Ainsworth et al., 2011 / 2024 update).
 */
import type { WorkoutTemplate } from '../store/types';

const t = (id: string, name: string, met: number, defaultMinutes: number, description?: string): WorkoutTemplate => ({
  id: `builtin-${id}`, name, met, defaultMinutes, description, builtin: true,
});

/**
 * English names of built-in templates. Templates are stored in user data with the Russian
 * name, so display code calls `workoutName()` to localise unedited built-ins.
 */
export const BUILTIN_WORKOUT_NAMES_EN: Record<string, string> = {
  'builtin-warmup': 'Warm-up',
  'builtin-morning': 'Morning exercises',
  'builtin-stretch': 'Stretching / light yoga',
  'builtin-walk': 'Walking (5 km/h)',
  'builtin-walk-fast': 'Brisk walking (6.5 km/h)',
  'builtin-run': 'Running (8 km/h)',
  'builtin-run-fast': 'Running (11 km/h)',
  'builtin-bike': 'Cycling (leisure)',
  'builtin-bike-fast': 'Cycling (vigorous, 20+ km/h)',
  'builtin-swim': 'Swimming (moderate)',
  'builtin-gym': 'Strength training',
  'builtin-hiit': 'Interval training (HIIT)',
  'builtin-home-bw': 'Bodyweight workout at home',
  'builtin-stairs': 'Stair climbing',
  'builtin-football': 'Football (recreational)',
  'builtin-dance': 'Dancing',
  'builtin-cleaning': 'House cleaning',
  'builtin-garden': 'Gardening',
  'builtin-ski': 'Cross-country skiing (flat)',
  'builtin-jump-rope': 'Jump rope',
};

export function workoutName(tp: WorkoutTemplate, lang: 'ru' | 'en'): string {
  const ruDefault = BUILTIN_WORKOUTS.find((b) => b.id === tp.id)?.name;
  // Only translate if the user has not renamed the built-in template.
  if (lang === 'en' && tp.builtin && tp.name === ruDefault) return BUILTIN_WORKOUT_NAMES_EN[tp.id] ?? tp.name;
  return tp.name;
}

export const BUILTIN_WORKOUTS: WorkoutTemplate[] = [
  t('warmup', 'Разминка', 2.8, 10, 'Суставная гимнастика, вращения, лёгкие наклоны'),
  t('morning', 'Утренняя зарядка', 3.5, 15, 'Приседания, отжимания, махи — в умеренном темпе'),
  t('stretch', 'Растяжка / йога (лёгкая)', 2.5, 20),
  t('walk', 'Ходьба (5 км/ч)', 3.5, 30),
  t('walk-fast', 'Быстрая ходьба (6,5 км/ч)', 5.0, 30),
  t('run', 'Бег (8 км/ч)', 8.3, 30),
  t('run-fast', 'Бег (11 км/ч)', 11.0, 30),
  t('bike', 'Велосипед (прогулочный)', 5.8, 45),
  t('bike-fast', 'Велосипед (активно, 20+ км/ч)', 8.0, 45),
  t('swim', 'Плавание (умеренно)', 6.0, 30),
  t('gym', 'Силовая тренировка', 5.0, 60, 'Тренажёры, свободные веса, средняя интенсивность'),
  t('hiit', 'Интервальная (HIIT)', 8.0, 20),
  t('home-bw', 'Тренировка дома с весом тела', 3.8, 30),
  t('stairs', 'Подъём по лестнице', 8.0, 10),
  t('football', 'Футбол (любительский)', 7.0, 60),
  t('dance', 'Танцы', 5.0, 45),
  t('cleaning', 'Уборка по дому', 3.3, 30),
  t('garden', 'Работа в саду / на даче', 4.0, 60),
  t('ski', 'Лыжи (равнина)', 6.8, 60),
  t('jump-rope', 'Скакалка', 11.0, 10),
];
