/** "More": settings pages and app information, grouped into Data, Settings and App. */
import { router, type Href } from 'expo-router';
import { View } from 'react-native';

import { Icon, type IconName } from '../../components/Icon';
import { Logo } from '../../components/Logo';
import { Card, Row, Screen, T, Tap } from '../../components/ui';
import type { SettingsPage } from '../../data/settingsPages';
import { useT } from '../../i18n';
import { useTheme } from '../../lib/theme';
import { restartOnboarding } from '../../store/actions';

type Item = SettingsPage | 'onboarding' | 'about';

const ICONS: Record<Item, IconName> = {
  profile: 'user', goal: 'target', appearance: 'palette', sections: 'layers', nutrition: 'nutrition',
  waterActivity: 'water', steps: 'activity', notifications: 'bell', data: 'database', support: 'donate',
  onboarding: 'restart', about: 'info',
};

/** Data: what describes the user. Settings: app behaviour. App: information and support. */
const GROUPS: { key: 'data' | 'settings' | 'app'; items: Item[] }[] = [
  { key: 'data', items: ['profile', 'goal', 'data', 'onboarding'] },
  { key: 'settings', items: ['appearance', 'sections', 'nutrition', 'waterActivity', 'steps', 'notifications'] },
  { key: 'app', items: ['support', 'about'] },
];

function open(item: Item) {
  if (item === 'about') return router.push('/about');
  if (item === 'onboarding') {
    // Keep all data; just walk through the setup wizard again.
    restartOnboarding();
    return router.replace('/onboarding');
  }
  router.push(`/settings/${item}` as Href);
}

export default function More() {
  const c = useTheme();
  const L = useT();
  return (
    <Screen title={L.tabs.more}>
      {GROUPS.map((g) => (
        <View key={g.key} style={{ gap: 8 }}>
          <T dim size={12} bold style={{ textTransform: 'uppercase', letterSpacing: 0.8, marginTop: 4, marginHorizontal: 4 }}>
            {L.more.groups[g.key]}
          </T>
          <Card style={{ paddingVertical: 4, gap: 0 }}>
            {g.items.map((i, idx) => (
              <Tap
                key={i}
                scaleTo={0.985}
                onPress={() => open(i)}
                style={{ flexDirection: 'row', alignItems: 'center', gap: 12, paddingVertical: 11, borderTopWidth: idx ? 0.5 : 0, borderColor: c.border }}
              >
                <View style={{ width: 36, height: 36, borderRadius: 11, backgroundColor: c.accentSoft, alignItems: 'center', justifyContent: 'center' }}>
                  <Icon name={ICONS[i]} size={19} color={c.accent} />
                </View>
                <View style={{ flex: 1 }}>
                  <T bold>{L.more[i]}</T>
                  <T dim size={12}>{L.more.subtitles[i]}</T>
                </View>
                <Icon name="right" size={18} color={c.textDim} />
              </Tap>
            ))}
          </Card>
        </View>
      ))}
      <Row style={{ justifyContent: 'center', marginTop: 12 }}>
        <Logo size={28} color={c.accent} />
        <T dim size={13}>{L.about.tagline}</T>
      </Row>
    </Screen>
  );
}
