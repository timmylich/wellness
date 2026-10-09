/** Settings sub-pages listed in the "More" tab; each renders at /settings/[page]. */
export const SETTINGS_PAGES = ['profile', 'goal', 'appearance', 'sections', 'nutrition', 'waterActivity', 'steps', 'notifications', 'data', 'support'] as const;
export type SettingsPage = (typeof SETTINGS_PAGES)[number];
