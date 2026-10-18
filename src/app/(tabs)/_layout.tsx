/**
 * Bottom navigation: Tips · Pantry · Overview · Health · More (optional ones hide when off).
 * A swipeable pager (material top tabs placed at the bottom) with a custom bar whose highlight
 * slides between icons on the native thread. Overview has its own section pager, so swiping
 * there moves between sections; the other tabs swipe between each other.
 */
import { Redirect } from 'expo-router';
import { TopTabs, type MaterialTopTabBarProps } from 'expo-router/js-top-tabs';
import { Animated, Pressable, View, useWindowDimensions } from 'react-native';
import { useSafeAreaInsets } from 'react-native-safe-area-context';

import { Icon, type IconName } from '../../components/Icon';
import { T } from '../../components/ui';
import { useT } from '../../i18n';
import { useMotion } from '../../lib/motion';
import { alpha, useTheme } from '../../lib/theme';
import { useStore } from '../../store/store';

const ICONS: Record<string, IconName> = { tips: 'tips', pantry: 'pantry', index: 'overview', health: 'health', more: 'more' };
const BAR_H = 66;
const PILL_W = 56;
const PILL_H = 32;

function BottomBar({ state, descriptors, navigation, position }: MaterialTopTabBarProps) {
  const c = useTheme();
  const insets = useSafeAreaInsets();
  const { width } = useWindowDimensions();
  const routes: { key: string; name: string }[] = state.routes;
  const n = routes.length;
  const slot = width / n;
  const inputRange = routes.map((_, i) => i);
  // Highlight follows the pager position (swipes and taps) without JS work per frame.
  const pillX = n > 1 ? position.interpolate({ inputRange, outputRange: inputRange.map((i) => i * slot + (slot - PILL_W) / 2) }) : (slot - PILL_W) / 2;
  return (
    <View style={{ height: BAR_H + insets.bottom, paddingBottom: insets.bottom, backgroundColor: c.card, borderTopWidth: 1, borderTopColor: c.border }}>
      <Animated.View
        pointerEvents="none"
        style={{ position: 'absolute', top: 8, left: 0, width: PILL_W, height: PILL_H, borderRadius: PILL_H / 2, backgroundColor: alpha(c.accent, 0.16), transform: [{ translateX: pillX }] }}
      />
      <View style={{ flexDirection: 'row', flex: 1 }}>
        {routes.map((r, i) => {
          const focused = state.index === i;
          const title = descriptors[r.key].options.title ?? r.name;
          const opacity = n > 1 ? position.interpolate({ inputRange: [i - 1, i, i + 1], outputRange: [0.55, 1, 0.55], extrapolate: 'clamp' }) : 1;
          return (
            <Pressable
              key={r.key}
              accessibilityRole="tab"
              accessibilityState={{ selected: focused }}
              onPress={() => {
                const e = navigation.emit({ type: 'tabPress', target: r.key, canPreventDefault: true });
                if (!focused && !e.defaultPrevented) navigation.navigate(r.name);
              }}
              style={{ flex: 1, alignItems: 'center', paddingTop: 8 }}
            >
              <Animated.View style={{ height: PILL_H, alignItems: 'center', justifyContent: 'center', opacity }}>
                <Icon name={ICONS[r.name] ?? 'info'} size={r.name === 'index' ? 25 : 23} color={c.accent} />
              </Animated.View>
              {/* Extra gap so the highlight never touches the label. */}
              <T size={11} bold color={focused ? c.accent : c.textDim} numberOfLines={1} style={{ marginTop: 4 }}>
                {title}
              </T>
            </Pressable>
          );
        })}
      </View>
    </View>
  );
}

export default function TabsLayout() {
  const c = useTheme();
  const L = useT();
  const motion = useMotion();
  const insets = useSafeAreaInsets();
  const onboarded = useStore((s) => s.meta.onboarded);
  const sections = useStore((s) => s.settings.sections);
  // First launch: collect the profile before showing anything else.
  if (!onboarded) return <Redirect href="/onboarding" />;
  const page = { paddingTop: insets.top, backgroundColor: c.bg };
  return (
    <TopTabs
      initialRouteName="index"
      tabBarPosition="bottom"
      tabBar={(props: MaterialTopTabBarProps) => <BottomBar {...props} />}
      screenOptions={{
        animationEnabled: motion,
        lazy: true,
        sceneStyle: page,
      }}
    >
      <TopTabs.Protected guard={sections.tips}>
        <TopTabs.Screen name="tips" options={{ title: L.tabs.tips }} />
      </TopTabs.Protected>
      <TopTabs.Protected guard={sections.pantry}>
        <TopTabs.Screen name="pantry" options={{ title: L.tabs.pantry }} />
      </TopTabs.Protected>
      {/* The section pager inside Overview owns horizontal swipes there. */}
      <TopTabs.Screen name="index" options={{ title: L.tabs.overview, swipeEnabled: false, sceneStyle: { backgroundColor: c.bg } }} />
      <TopTabs.Protected guard={sections.health}>
        <TopTabs.Screen name="health" options={{ title: L.tabs.health }} />
      </TopTabs.Protected>
      <TopTabs.Screen name="more" options={{ title: L.tabs.more }} />
    </TopTabs>
  );
}
