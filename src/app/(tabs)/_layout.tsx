/**
 * Bottom navigation: Tips · Pantry · Overview · Health · More (optional ones hide when off).
 * A swipeable pager (material top tabs placed at the bottom) with a custom bar whose highlight
 * slides between icons on the native thread. Overview has its own section pager, so swiping
 * there moves between sections; the other tabs swipe between each other.
 */
import { Redirect } from 'expo-router';
import { TopTabs, type MaterialTopTabBarProps } from 'expo-router/js-top-tabs';
import { useState } from 'react';
import { Animated, Easing, Pressable, View, useWindowDimensions } from 'react-native';
import { useSafeAreaInsets } from 'react-native-safe-area-context';

import { Icon, type IconName } from '../../components/Icon';
import { T } from '../../components/ui';
import { useT } from '../../i18n';
import { dur, useMotion } from '../../lib/motion';
import { alpha, useTheme } from '../../lib/theme';
import { useStore } from '../../store/store';

const ICONS: Record<string, IconName> = { tips: 'tips', pantry: 'pantry', index: 'overview', health: 'health', more: 'more' };
const BAR_H = 66;
const PILL_W = 56;
const PILL_H = 32;
const JUMP_MS = 300;

/** A tap on a distant tab: the highlight goes straight from `from` to `to` while `p` runs 0 → 1. */
type Jump = { from: number; to: number; p: Animated.Value };

function BottomBar({ state, descriptors, navigation, position, setInstant }: MaterialTopTabBarProps & { setInstant: (on: boolean) => void }) {
  const c = useTheme();
  const insets = useSafeAreaInsets();
  const { width } = useWindowDimensions();
  const routes: { key: string; name: string }[] = state.routes;
  const n = routes.length;
  const slot = width / n;
  const motion = useMotion();
  const [jump, setJump] = useState<Jump | null>(null);
  const inputRange = routes.map((_, i) => i);
  // Highlight follows the pager position (swipes and taps) without JS work per frame; on a
  // distant jump it moves straight to the target instead of stopping at every tab between.
  const along = (f: (i: number) => number, clamp?: boolean) =>
    jump
      ? jump.p.interpolate({ inputRange: [0, 1], outputRange: [f(jump.from), f(jump.to)] })
      : n > 1
        ? position.interpolate({ inputRange, outputRange: inputRange.map(f), extrapolate: clamp ? 'clamp' : 'extend' })
        : f(0);
  const pillX = along((i) => i * slot + (slot - PILL_W) / 2);

  const go = (i: number) => {
    const from = state.index;
    if (!motion || Math.abs(i - from) < 2) return navigation.navigate(routes[i].name);
    // Distant tab: switch the page at once (batched with `instant`, so the pager skips its
    // slide and the tabs in between are never shown or mounted); the highlight glides there.
    const j: Jump = { from, to: i, p: new Animated.Value(0) };
    setJump(j);
    setInstant(true);
    navigation.navigate(routes[i].name);
    Animated.timing(j.p, { toValue: 1, duration: dur(JUMP_MS), easing: Easing.out(Easing.cubic), useNativeDriver: true }).start(() => {
      setInstant(false);
      setJump((cur) => (cur === j ? null : cur));
    });
  };
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
          const opacity = along((k) => (k === i ? 1 : 0.55), true);
          return (
            <Pressable
              key={r.key}
              accessibilityRole="tab"
              accessibilityState={{ selected: focused }}
              onPress={() => {
                const e = navigation.emit({ type: 'tabPress', target: r.key, canPreventDefault: true });
                if (!focused && !e.defaultPrevented) go(i);
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
  // True for the duration of a distant tab jump: the pager switches without sliding.
  const [instant, setInstant] = useState(false);
  const sections = useStore((s) => s.settings.sections);
  // First launch: collect the profile before showing anything else.
  if (!onboarded) return <Redirect href="/onboarding" />;
  const page = { paddingTop: insets.top, backgroundColor: c.bg };
  return (
    <TopTabs
      initialRouteName="index"
      tabBarPosition="bottom"
      tabBar={(props: MaterialTopTabBarProps) => <BottomBar {...props} setInstant={setInstant} />}
      screenOptions={{
        animationEnabled: motion && !instant,
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
