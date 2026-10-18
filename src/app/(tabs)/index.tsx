/**
 * Overview: sections in a horizontal pager (swipe between them), a transparent tab bar whose
 * highlight follows the swipe, and a pinned "+" button that fans out quick actions around it.
 */
import { router } from 'expo-router';
import { memo, useCallback, useEffect, useRef, useState } from 'react';
import { Animated, Easing, Pressable, ScrollView, Text, useWindowDimensions, View } from 'react-native';
import PagerView from 'react-native-pager-view';
import { useSafeAreaInsets } from 'react-native-safe-area-context';

import { BodyForm, HeartForm, StepsForm, TemplateGrid, WaterForm, WorkoutForm } from '../../components/forms';
import { Icon, type IconName } from '../../components/Icon';
import { Sheet } from '../../components/Sheet';
import { Button, Row, T, Tap } from '../../components/ui';
import { useT } from '../../i18n';
import { dur, useMotion } from '../../lib/motion';
import { alpha, useTheme } from '../../lib/theme';
import { PagerLock } from '../../components/PagerLock';
import { Activity } from '../../sections/Activity';
import { Body } from '../../sections/Body';
import { Heart } from '../../sections/Heart';
import { Nutrition } from '../../sections/Nutrition';
import { Summary } from '../../sections/Summary';
import { Water } from '../../sections/Water';
import { useStore } from '../../store/store';
import type { SectionId, WorkoutTemplate } from '../../store/types';

type Tab = 'summary' | Exclude<SectionId, 'tips' | 'pantry' | 'health'>;
const TABS: { id: Tab; icon: IconName }[] = [
  { id: 'summary', icon: 'summary' },
  { id: 'nutrition', icon: 'nutrition' },
  { id: 'water', icon: 'water' },
  { id: 'activity', icon: 'activity' },
  { id: 'heart', icon: 'heart' },
  { id: 'body', icon: 'body' },
];

type AddKind = 'food' | 'water' | 'workout' | 'steps' | 'heart' | 'body';
const ADD_ITEMS: { kind: AddKind; icon: IconName; section: SectionId }[] = [
  { kind: 'food', icon: 'nutrition', section: 'nutrition' },
  { kind: 'water', icon: 'water', section: 'water' },
  { kind: 'workout', icon: 'workout', section: 'activity' },
  { kind: 'steps', icon: 'activity', section: 'activity' },
  { kind: 'heart', icon: 'heart', section: 'heart' },
  { kind: 'body', icon: 'body', section: 'body' },
];

// Memoised so switching tabs (or touching a carousel) does not re-render every section.
const SectionContent = memo(function SectionContent({ tab, onOpen }: { tab: Tab; onOpen: (t: Tab) => void }) {
  switch (tab) {
    case 'nutrition': return <Nutrition />;
    case 'water': return <Water />;
    case 'activity': return <Activity />;
    case 'heart': return <Heart />;
    case 'body': return <Body />;
    default: return <Summary onOpen={(s) => onOpen(s as Tab)} />;
  }
});

const AnimatedPager = Animated.createAnimatedComponent(PagerView);
/** First width is set without animation (module-level: one section bar per app). */
const bgAnimInit = { done: false };

const BAR_PAD = 4;
const TAB_BASE = 44; // horizontal padding 12 + icon 20 + padding 12
const TAB_H = 38; // vertical padding 9 + icon 20 + padding 9
const TAB_GAP = 2;
const LABEL_GAP = 6;
const R = TAB_H / 2;
/** Height of the section bar below the status bar (top padding 8 + bar). */
const BAR_TOTAL = 8 + TAB_H + BAR_PAD * 2;
/** Height of the rounded bar background. */
const BAR_H = TAB_H + BAR_PAD * 2;

/**
 * Transparent section bar, driven entirely by the pager position on the native thread (only
 * transforms and opacity, no layout), so it never lags behind the swipe or jitters.
 * Tabs are absolutely positioned; the active one is followed by its label, and the highlight
 * pill is drawn as two caps plus a stretched middle so it can change width with a transform.
 */
function SectionBar({
  tabs,
  progress,
  index,
  onSelect,
}: {
  tabs: typeof TABS;
  progress: Animated.AnimatedInterpolation<number>;
  index: number;
  onSelect: (t: Tab) => void;
}) {
  const c = useTheme();
  const L = useT();
  // Natural width of each label, measured off-screen once per language.
  const [labelW, setLabelW] = useState<Record<string, number>>({});
  const ready = tabs.every((t) => labelW[t.id]);
  const { width: winW } = useWindowDimensions();
  const n = tabs.length;
  const pages = tabs.map((_, i) => i);
  // Too many sections for labels: show icons only so the bar always fits and stays centred.
  const iconsW = BAR_PAD * 2 + n * TAB_BASE + (n - 1) * TAB_GAP;
  const compact = iconsW + LABEL_GAP + Math.max(0, ...tabs.map((t) => labelW[t.id] ?? 0)) > winW - 24;
  const W = (k: number) => (compact ? 0 : LABEL_GAP + (labelW[tabs[k].id] ?? 0));
  // x of tab i while page k is active: tabs after k are pushed right by k's label.
  const xAt = (i: number, k: number) => BAR_PAD + i * (TAB_BASE + TAB_GAP) + (i > k ? W(k) : 0);
  const along = (f: (k: number) => number) =>
    n > 1 ? progress.interpolate({ inputRange: pages, outputRange: pages.map(f), extrapolate: 'clamp' }) : f(0);
  const maxLabel = Math.max(0, ...tabs.map((_, k) => W(k)));
  const totalW = BAR_PAD * 2 + n * TAB_BASE + (n - 1) * TAB_GAP + maxLabel;
  const pillX = along((k) => xAt(k, k));
  const pillW = (k: number) => TAB_BASE + W(k);
  // Blurred background: a rounded rectangle as wide as the visible tabs. Its width springs to
  // the selected tab (a layout property, so it cannot follow the swipe on the native thread).
  const bgW = iconsW + W(Math.min(index, n - 1));
  const motion = useMotion();
  const [bgAnim] = useState(() => new Animated.Value(0));
  useEffect(() => {
    if (!ready) return;
    if (!motion || bgAnimInit.done !== true) {
      bgAnim.setValue(bgW);
      bgAnimInit.done = true;
    } else Animated.timing(bgAnim, { toValue: bgW, duration: dur(160), easing: Easing.out(Easing.cubic), useNativeDriver: false }).start();
  }, [bgW, ready, motion, bgAnim]);

  return (
    <View pointerEvents="box-none" style={{ flexGrow: 0, flexShrink: 0 }}>
      {/* Hidden measurers */}
      <View style={{ position: 'absolute', opacity: 0 }} pointerEvents="none">
        {tabs.map((t) => (
          <Text
            key={t.id}
            style={{ fontSize: 14, fontWeight: '700', position: 'absolute' }}
            onLayout={(e) => {
              const w = Math.ceil(e.nativeEvent.layout.width);
              setLabelW((prev) => (prev[t.id] === w ? prev : { ...prev, [t.id]: w }));
            }}
          >
            {L.sections[t.id]}
          </Text>
        ))}
      </View>
      <View pointerEvents="box-none" style={{ alignItems: 'center', paddingHorizontal: 12 }}>
        {ready && (
          <View style={{ width: totalW, height: TAB_H + BAR_PAD * 2 }}>
            <View pointerEvents="none" style={{ position: 'absolute', left: 0, right: 0, top: 0, height: BAR_H, alignItems: 'center' }}>
              {/* Solid, slightly translucent pill (Android blur is unreliable over a native pager). */}
              <Animated.View style={{ width: bgAnim, height: BAR_H, borderRadius: BAR_H / 2, backgroundColor: alpha(c.card, 0.94), borderWidth: 1, borderColor: alpha(c.border, 0.8) }} />
            </View>
            {/* Shift everything so the visible tabs stay centred whatever label is open. */}
            <Animated.View style={{ position: 'absolute', left: 0, top: 0, bottom: 0, width: totalW, transform: [{ translateX: along((k) => (maxLabel - W(k)) / 2) }] }}>
            {n > 1 && (
              // Solid pieces composited as one layer, then faded: overlaps never look darker.
              <View
                pointerEvents="none"
                needsOffscreenAlphaCompositing
                renderToHardwareTextureAndroid
                style={{ position: 'absolute', left: 0, right: 0, top: BAR_PAD, height: TAB_H, opacity: 0.16 }}
              >
                {/* Left cap, stretched middle (1 px scaled), right cap. */}
                <Animated.View style={{ position: 'absolute', width: TAB_H, height: TAB_H, borderRadius: R, backgroundColor: c.accent, transform: [{ translateX: pillX }] }} />
                <Animated.View
                  style={{
                    position: 'absolute',
                    width: 1,
                    height: TAB_H,
                    backgroundColor: c.accent,
                    transform: [{ translateX: along((k) => xAt(k, k) + pillW(k) / 2 - 0.5) }, { scaleX: along((k) => pillW(k) - TAB_H - 2) }],
                  }}
                />
                <Animated.View style={{ position: 'absolute', width: TAB_H, height: TAB_H, borderRadius: R, backgroundColor: c.accent, transform: [{ translateX: along((k) => xAt(k, k) + pillW(k) - TAB_H) }] }} />
              </View>
            )}
            {tabs.map((t, i) => (
              <Animated.View key={t.id} style={{ position: 'absolute', top: BAR_PAD, left: 0, transform: [{ translateX: along((k) => xAt(i, k)) }] }}>
                <Tap
                  onPress={() => onSelect(t.id)}
                  accessibilityLabel={L.sections[t.id]}
                  style={{ flexDirection: 'row', alignItems: 'center', height: TAB_H, paddingHorizontal: 12 }}
                >
                  <Icon name={t.icon} size={20} color={c.accent} glow />
                  {!compact && (
                  <Animated.View
                    style={{
                      marginLeft: LABEL_GAP,
                      // Fade quickly so a fading label never overlaps the next icon.
                      opacity: n > 1 ? progress.interpolate({ inputRange: [i - 1, i - 0.4, i, i + 0.4, i + 1], outputRange: [0, 0, 1, 0, 0], extrapolate: 'clamp' }) : 1,
                    }}
                  >
                    <T bold size={14} color={c.accent} numberOfLines={1} style={{ width: labelW[t.id] }}>
                      {L.sections[t.id]}
                    </T>
                  </Animated.View>
                  )}
                </Tap>
              </Animated.View>
            ))}
            </Animated.View>
          </View>
        )}
      </View>
    </View>
  );
}

/** "+" button that fans quick actions out on an arc above itself. */
function FabMenu({ items, onPick }: { items: typeof ADD_ITEMS; onPick: (k: AddKind) => void }) {
  const c = useTheme();
  const L = useT();
  const motion = useMotion();
  const { width } = useWindowDimensions();
  const [open, setOpen] = useState(false);
  const [progress] = useState(() => new Animated.Value(0));
  useEffect(() => {
    if (!motion) {
      progress.setValue(open ? 1 : 0);
      return;
    }
    const anim = open
      ? Animated.spring(progress, { toValue: 1, useNativeDriver: true, speed: 18, bounciness: 6 })
      : Animated.timing(progress, { toValue: 0, duration: dur(140), useNativeDriver: true });
    anim.start();
  }, [open, motion, progress]);

  const n = items.length;
  // Up to 4 actions fan out on an arc; more go into a grid of rows above the button, so
  // buttons and labels never collide.
  const grid = n > 4;
  const cols = 3;
  const cellW = Math.min(112, (width - 32) / cols);
  const radius = Math.min(135, width / 2 - 40);
  const stepRad = n > 1 ? ((140 / (n - 1)) * Math.PI) / 180 : Math.PI;
  const labelW = grid ? cellW - 8 : Math.max(56, Math.min(96, 2 * radius * Math.sin(stepRad / 2) - 6));
  const rows = Math.ceil(n / cols);
  const positions = items.map((_, i) => {
    if (grid) {
      const row = Math.floor(i / cols);
      const inRow = Math.min(cols, n - row * cols);
      const col = i % cols;
      return { x: (col - (inRow - 1) / 2) * cellW, y: -(rows - row) * 104 };
    }
    const deg = n === 1 ? 90 : 160 - (140 * i) / (n - 1);
    const rad = (deg * Math.PI) / 180;
    return { x: Math.cos(rad) * radius, y: -Math.sin(rad) * radius + 6 };
  });
  const pick = (k: AddKind) => {
    setOpen(false);
    onPick(k);
  };

  return (
    <>
      {/* Dim backdrop; tap to close. */}
      <Animated.View pointerEvents={open ? 'auto' : 'none'} style={{ position: 'absolute', left: 0, right: 0, top: -1000, bottom: 0, backgroundColor: '#000', opacity: progress.interpolate({ inputRange: [0, 1], outputRange: [0, 0.45] }) }}>
        <Pressable style={{ flex: 1 }} onPress={() => setOpen(false)} />
      </Animated.View>
      <View pointerEvents="box-none" style={{ position: 'absolute', left: 0, right: 0, bottom: 18, alignItems: 'center' }}>
        {items.map((it, i) => (
          <Animated.View
            key={it.kind}
            pointerEvents={open ? 'auto' : 'none'}
            style={{
              position: 'absolute',
              bottom: 4,
              alignItems: 'center',
              width: labelW,
              opacity: progress,
              transform: [
                { translateX: progress.interpolate({ inputRange: [0, 1], outputRange: [0, positions[i].x] }) },
                { translateY: progress.interpolate({ inputRange: [0, 1], outputRange: [0, positions[i].y] }) },
                { scale: progress.interpolate({ inputRange: [0, 1], outputRange: [0.3, 1] }) },
              ],
            }}
          >
            <Tap
              onPress={() => pick(it.kind)}
              accessibilityLabel={L.fab[it.kind]}
              style={{ width: 54, height: 54, borderRadius: 27, alignItems: 'center', justifyContent: 'center', backgroundColor: c.card, borderWidth: 1.5, borderColor: alpha(c.accent, 0.5) }}
            >
              <Icon name={it.icon} size={24} color={c.accent} />
            </Tap>
            <T size={11} bold center color="#fff" numberOfLines={3} style={{ marginTop: 4, width: labelW, lineHeight: 13, textShadowColor: '#000a', textShadowRadius: 4 }}>
              {L.fab[it.kind]}
            </T>
          </Animated.View>
        ))}
        <Tap
          onPress={() => setOpen(!open)}
          accessibilityLabel={L.fab.add}
          style={{ width: 62, height: 62, borderRadius: 31, alignItems: 'center', justifyContent: 'center', backgroundColor: c.accent }}
        >
          <Animated.View style={{ transform: [{ rotate: progress.interpolate({ inputRange: [0, 1], outputRange: ['0deg', '135deg'] }) }] }}>
            <Icon name="plus" size={30} color="#fff" strokeWidth={2.6} />
          </Animated.View>
        </Tap>
      </View>
    </>
  );
}

export default function Overview() {
  const c = useTheme();
  const L = useT();
  const insets = useSafeAreaInsets();
  const motion = useMotion();
  const sections = useStore((s) => s.settings.sections);
  const [tab, setTab] = useState<Tab>('summary');
  // Sections render lazily: only those the user has visited (or is next to).
  const [near, setNear] = useState(0);
  const [visited, setVisited] = useState<Set<Tab>>(() => new Set(['summary']));
  const [adding, setAdding] = useState<Exclude<AddKind, 'food'> | null>(null);
  const [workoutTpl, setWorkoutTpl] = useState<WorkoutTemplate | null | undefined>(undefined);
  const [position] = useState(() => new Animated.Value(0));
  const [offset] = useState(() => new Animated.Value(0));
  const [progress] = useState(() => Animated.add(position, offset));
  const pager = useRef<PagerView>(null);

  const tabs = TABS.filter((t) => t.id === 'summary' || sections[t.id]);
  const index = Math.max(0, tabs.findIndex((t) => t.id === tab));
  const addItems = ADD_ITEMS.filter((i) => sections[i.section]);

  const mount = (ids: (Tab | undefined)[]) =>
    setVisited((prev) => {
      const add = ids.filter((x): x is Tab => !!x && !prev.has(x));
      return add.length ? new Set([...prev, ...add]) : prev;
    });

  const select = (t: Tab) => {
    const i = tabs.findIndex((x) => x.id === t);
    if (i < 0) return;
    mount([t]);
    setTab(t);
    if (motion) pager.current?.setPage(i);
    else pager.current?.setPageWithoutAnimation(i);
  };
  // Stable callback for the memoised sections.
  const selectRef = useRef(select);
  useEffect(() => {
    selectRef.current = select;
  });
  const onOpen = useCallback((t: Tab) => selectRef.current(t), []);
  // Pre-mount the first neighbour shortly after launch, so the first swipe is not blank.
  useEffect(() => {
    const id = setTimeout(() => mount([tabs[index + 1]?.id, tabs[index - 1]?.id]), 600);
    return () => clearTimeout(id);
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, []);
  // A nested carousel disables paging imperatively while touched (no re-render).
  const setLocked = useCallback((locked: boolean) => pager.current?.setScrollEnabled(!locked), []);

  const pick = (kind: AddKind) => {
    if (kind === 'food') return router.push('/food-search');
    setWorkoutTpl(undefined);
    setAdding(kind);
  };
  const close = () => setAdding(null);

  return (
    <View style={{ flex: 1, backgroundColor: c.bg }}>

      <AnimatedPager
        ref={pager}
        style={{ flex: 1 }}
        initialPage={index}
        offscreenPageLimit={1}
        onPageScroll={Animated.event([{ nativeEvent: { position, offset } }], {
          useNativeDriver: true,
          // The bar background starts resizing as soon as the swipe passes the middle.
          listener: (e: { nativeEvent: { position: number; offset: number } }) => {
            const i = Math.round(e.nativeEvent.position + e.nativeEvent.offset);
            if (i !== near) setNear(i);
          },
        })}
        onPageSelected={(e) => {
          const i = e.nativeEvent.position;
          if (tabs[i] && tabs[i].id !== tab) setTab(tabs[i].id);
          mount([tabs[i]?.id]);
        }}
        onPageScrollStateChanged={(e) => {
          // Mount neighbours only once the pager is at rest, so a swipe never stalls on a mount.
          if (e.nativeEvent.pageScrollState === 'idle') mount([tabs[index - 1]?.id, tabs[index + 1]?.id]);
        }}
      >
        {tabs.map((t) => (
          <ScrollView
            key={t.id}
            contentContainerStyle={{ padding: 16, paddingTop: insets.top + BAR_TOTAL + 12, paddingBottom: 120, gap: 12 }}
            keyboardShouldPersistTaps="handled"
            nestedScrollEnabled
            showsVerticalScrollIndicator={false}
          >
            {visited.has(t.id) ? (
              <PagerLock.Provider value={setLocked}>
                <SectionContent tab={t.id} onOpen={onOpen} />
              </PagerLock.Provider>
            ) : null}
          </ScrollView>
        ))}
      </AnimatedPager>
      {/* Floating section bar: pages scroll under it; only the tabs themselves have a background. */}
      <View pointerEvents="box-none" style={{ position: 'absolute', left: 0, right: 0, top: insets.top + 8 }}>
        <SectionBar tabs={tabs} progress={progress} index={near} onSelect={select} />
      </View>

      {addItems.length > 0 && <FabMenu items={addItems} onPick={pick} />}

      <Sheet visible={adding != null} onClose={close}>
        {adding === 'water' && <><T size={18} bold>{L.fab.water}</T><WaterForm onDone={close} /></>}
        {adding === 'steps' && <><T size={18} bold>{L.fab.steps}</T><StepsForm onDone={close} /></>}
        {adding === 'heart' && <><T size={18} bold>{L.fab.heart}</T><HeartForm onDone={close} /></>}
        {adding === 'body' && <><T size={18} bold>{L.fab.body}</T><BodyForm onDone={close} /></>}
        {adding === 'workout' &&
          (workoutTpl === undefined ? (
            <>
              <T size={18} bold>{L.fab.workout}</T>
              <TemplateGrid onPick={setWorkoutTpl} />
              <Row>
                <Button title={L.activity.custom} icon="plus" kind="secondary" onPress={() => setWorkoutTpl(null)} style={{ flex: 1 }} />
              </Row>
            </>
          ) : (
            <WorkoutForm template={workoutTpl} onDone={close} />
          ))}
      </Sheet>
    </View>
  );
}
