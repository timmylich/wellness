/** "More": a plain list of settings pages and app information. */
import { router, type Href } from 'expo-router';

import { Icon, type IconName } from '../../components/Icon';
import { Logo } from '../../components/Logo';
import { Card, Row, Screen, T, Tap } from '../../components/ui';
import { SETTINGS_PAGES, type SettingsPage } from '../../data/settingsPages';
import { useT } from '../../i18n';
import { useTheme } from '../../lib/theme';
import { restartOnboarding } from '../../store/actions';

type Item = SettingsPage | 'onboarding' | 'about';

const ICONS: Record<Item, IconName> = {
  profile: 'user', goal: 'target', appearance: 'palette', sections: 'layers', nutrition: 'nutrition',
  waterActivity: 'water', steps: 'activity', notifications: 'bell', data: 'database', support: 'donate',
  onboarding: 'restart', about: 'info',
};

export default function More() {
  const c = useTheme();
  const L = useT();
  const items: { key: Item; open: () => void }[] = [
    ...SETTINGS_PAGES.map((p) => ({ key: p, open: () => router.push(`/settings/${p}` as Href) })),
    {
      key: 'onboarding',
      open: () => {
        // Keep all data; just walk through the setup wizard again.
        restartOnboarding();
        router.replace('/onboarding');
      },
    },
    { key: 'about', open: () => router.push('/about') },
  ];
  return (
    <Screen>
      <Card style={{ paddingVertical: 4, gap: 0 }}>
        {items.map((i, idx) => (
          <Tap
            key={i.key}
            scaleTo={0.98}
            onPress={i.open}
            style={{ flexDirection: 'row', alignItems: 'center', gap: 14, paddingVertical: 12, borderTopWidth: idx ? 0.5 : 0, borderColor: c.border }}
          >
            <Icon name={ICONS[i.key]} size={22} color={c.accent} glow />
            <Row style={{ flex: 1, flexDirection: 'column', alignItems: 'flex-start', gap: 0 }}>
              <T bold>{L.more[i.key]}</T>
              <T dim size={12}>{L.more.subtitles[i.key]}</T>
            </Row>
            <Icon name="right" size={18} color={c.textDim} />
          </Tap>
        ))}
      </Card>
      <Row style={{ justifyContent: 'center', marginTop: 12 }}>
        <Logo size={28} color={c.accent} />
        <T dim size={13}>{L.about.tagline}</T>
      </Row>
    </Screen>
  );
}
