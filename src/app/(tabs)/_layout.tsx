/**
 * Bottom navigation: Tips · Overview · More. Overview sits in the centre and is the default.
 * Icons are drawn in the accent color; the active one glows.
 */
import { Redirect } from 'expo-router';
import { Tabs } from 'expo-router/js-tabs';
import { View } from 'react-native';

import { Icon, type IconName } from '../../components/Icon';
import { useT } from '../../i18n';
import { useMotion } from '../../lib/motion';
import { alpha, useTheme } from '../../lib/theme';
import { useStore } from '../../store/store';

function TabIcon({ name, focused, center }: { name: IconName; focused: boolean; center?: boolean }) {
  const c = useTheme();
  const size = center ? 26 : 23;
  return (
    <View
      style={{
        alignItems: 'center',
        justifyContent: 'center',
        width: center ? 52 : 44,
        height: center ? 34 : 30,
        borderRadius: 17,
        backgroundColor: focused ? alpha(c.accent, 0.14) : 'transparent',
      }}
    >
      <View style={{ opacity: focused ? 1 : 0.55 }}>
        <Icon name={name} size={size} color={c.accent} glow={focused} />
      </View>
    </View>
  );
}

const icon = (name: IconName, center?: boolean) =>
  function TabBarIcon({ focused }: { focused: boolean }) {
    return <TabIcon name={name} focused={focused} center={center} />;
  };

export default function TabsLayout() {
  const c = useTheme();
  const L = useT();
  const motion = useMotion();
  const onboarded = useStore((s) => s.meta.onboarded);
  const tipsEnabled = useStore((s) => s.settings.sections.tips);
  // First launch: collect the profile before showing anything else.
  if (!onboarded) return <Redirect href="/onboarding" />;
  return (
    <Tabs
      initialRouteName="index"
      screenOptions={{
        headerStyle: { backgroundColor: c.card },
        headerTintColor: c.text,
        headerShadowVisible: false,
        tabBarStyle: { backgroundColor: c.card, borderTopColor: c.border, height: 64, paddingTop: 6 },
        tabBarActiveTintColor: c.accent,
        tabBarInactiveTintColor: c.textDim,
        tabBarLabelStyle: { fontSize: 12, fontWeight: '600' },
        sceneStyle: { backgroundColor: c.bg },
        // Horizontal shift between tabs (native-driven); off when animations are disabled.
        animation: motion ? 'shift' : 'none',
      }}
    >
      <Tabs.Screen name="tips" options={{ title: L.tabs.tips, tabBarIcon: icon('tips'), href: tipsEnabled ? undefined : null }} />
      <Tabs.Screen name="index" options={{ title: L.tabs.overview, headerShown: false, tabBarIcon: icon('overview', true) }} />
      <Tabs.Screen name="more" options={{ title: L.tabs.more, tabBarIcon: icon('more') }} />
    </Tabs>
  );
}
