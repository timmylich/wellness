/**
 * Overview: sections in a horizontal pager (swipe between them), a transparent tab bar whose
 * highlight follows the swipe, and a pinned "+" button that fans out quick actions around it.
 */
import { router } from 'expo-router';
import { memo, useCallback, useEffect, useRef, useState } from 'react';
import { Animated, Easing, Pressable, Text, useWindowDimensions, View } from 'react-native';
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
/** A tap on a distant tab: the bar goes straight from `from` to `to` while `p` runs 0 → 1. */
type Jump = { from: number; to: number; p: Animated.Value };
const JUMP_MS = 300;

/** Solid colour of `hex` at opacity `a` over `under` (both #rrggbb). */
function mix(hex: string, under: string, a: number): string {
  const ch = (h: string, i: number) => parseInt(h.slice(1 + 2 * i, 3 + 2 * i), 16);
  return '#' + [0, 1, 2].map((i) => Math.round(ch(hex, i) * a + ch(under, i) * (1 - a)).toString(16).padStart(2, '0')).join('');
}

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
  jump,
  onSelect,
}: {
  tabs: typeof TABS;
  progress: Animated.AnimatedInterpolation<number>;
  jump: Jump | null;
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
  // Value f(k) for the active page k, following the swipe, or the tap jump (which skips the
  // tabs in between instead of sliding over each of them).
  const along = (f: (k: number) => number) =>
    jump
      ? jump.p.interpolate({ inputRange: [0, 1], outputRange: [f(jump.from), f(jump.to)] })
      : n > 1
        ? progress.interpolate({ inputRange: pages, outputRange: pages.map(f), extrapolate: 'clamp' })
        : f(0);
  const labelOpacity = (i: number) =>
    jump
      ? jump.p.interpolate({ inputRange: [0, 0.4, 0.6, 1], outputRange: i === jump.from ? [1, 0, 0, 0] : i === jump.to ? [0, 0, 0, 1] : [0, 0, 0, 0] })
      : n > 1
        ? progress.interpolate({ inputRange: [i - 1, i - 0.4, i, i + 0.4, i + 1], outputRange: [0, 0, 1, 0, 0], extrapolate: 'clamp' })
        : 1;
  const maxLabel = Math.max(0, ...tabs.map((_, k) => W(k)));
  const totalW = BAR_PAD * 2 + n * TAB_BASE + (n - 1) * TAB_GAP + maxLabel;
  const pillX = along((k) => xAt(k, k));
  const pillW = (k: number) => TAB_BASE + W(k);
  // Bar background, as wide as the visible tabs and centred. Built like the pill (two circles
  // plus a 1 px middle stretched by scaleX), so its width follows the swipe on the native thread.
  const bgW = (k: number) => iconsW + W(k);
  const bgLeft = along((k) => (totalW - bgW(k)) / 2);
  const bgRight = along((k) => (totalW + bgW(k)) / 2);
  const bgMid = totalW / 2 - 0.5;
  const bgSpan = along((k) => bgW(k) - BAR_H + 2);
  const pill = mix(c.accent, c.card, 0.16);

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
            {/* Bar background: solid border-coloured shape, then the card-coloured shape 1 px
                inside it. Only solid colours, so overlapping pieces never show seams. */}
            <View pointerEvents="none" style={{ position: 'absolute', left: 0, right: 0, top: 0, height: BAR_H }}>
              {[0, 1].map((inset) => {
                const color = inset ? c.card : c.border;
                const d = BAR_H - 2 * inset;
                return [
                  <Animated.View key={`l${inset}`} style={{ position: 'absolute', top: inset, left: 0, width: d, height: d, borderRadius: d / 2, backgroundColor: color, transform: [{ translateX: Animated.add(bgLeft, inset) }] }} />,
                  <Animated.View key={`r${inset}`} style={{ position: 'absolute', top: inset, left: 0, width: d, height: d, borderRadius: d / 2, backgroundColor: color, transform: [{ translateX: Animated.add(bgRight, inset - BAR_H) }] }} />,
                  <Animated.View
                    key={`m${inset}`}
                    style={{ position: 'absolute', top: inset, left: 0, width: 1, height: d, backgroundColor: color, transform: [{ translateX: bgMid }, { scaleX: bgSpan }] }}
                  />,
                ];
              })}
            </View>
            {/* Shift everything so the visible tabs stay centred whatever label is open. */}
            <Animated.View style={{ position: 'absolute', left: 0, top: 0, bottom: 0, width: totalW, transform: [{ translateX: along((k) => (maxLabel - W(k)) / 2) }] }}>
            {n > 1 && (
              // Solid colour (accent blended over the card), so overlaps never look darker.
              <View pointerEvents="none" style={{ position: 'absolute', left: 0, right: 0, top: BAR_PAD, height: TAB_H }}>
                {/* Left cap, stretched middle (1 px scaled), right cap. */}
                <Animated.View style={{ position: 'absolute', width: TAB_H, height: TAB_H, borderRadius: R, backgroundColor: pill, transform: [{ translateX: pillX }] }} />
                <Animated.View
                  style={{
                    position: 'absolute',
                    width: 1,
                    height: TAB_H,
                    backgroundColor: pill,
                    transform: [{ translateX: along((k) => xAt(k, k) + pillW(k) / 2 - 0.5) }, { scaleX: along((k) => pillW(k) - TAB_H - 2) }],
                  }}
                />
                <Animated.View style={{ position: 'absolute', width: TAB_H, height: TAB_H, borderRadius: R, backgroundColor: pill, transform: [{ translateX: along((k) => xAt(k, k) + pillW(k) - TAB_H) }] }} />
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
                      opacity: labelOpacity(i),
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
  const [visited, setVisited] = useState<Set<Tab>>(() => new Set(['summary']));
  const [adding, setAdding] = useState<Exclude<AddKind, 'food'> | null>(null);
  const [workoutTpl, setWorkoutTpl] = useState<WorkoutTemplate | null | undefined>(undefined);
  const [position] = useState(() => new Animated.Value(0));
  const [offset] = useState(() => new Animated.Value(0));
  const [progress] = useState(() => Animated.add(position, offset));
  const pager = useRef<PagerView>(null);
  const [jump, setJump] = useState<Jump | null>(null);
  const jumpRef = useRef<Jump | null>(null);

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
    if (i < 0 || i === index) return;
    mount([t]);
    setTab(t);
    if (!motion) return pager.current?.setPageWithoutAnimation(i);
    if (Math.abs(i - index) < 2) return pager.current?.setPage(i);
    // Distant tab: switch the page at once (the pages in between are never shown or mounted)
    // and fade it in, while the bar highlight glides straight to the target.
    const j: Jump = { from: index, to: i, p: new Animated.Value(0) };
    jumpRef.current = j;
    setJump(j);
    pager.current?.setPageWithoutAnimation(i);
    Animated.timing(j.p, { toValue: 1, duration: dur(JUMP_MS), easing: Easing.out(Easing.cubic), useNativeDriver: true }).start(endJump);
  };
  // Back to following the pager once the jump animation is over.
  function endJump() {
    if (!jumpRef.current) return;
    jumpRef.current = null;
    setJump(null);
  }
  // Stable callback for the memoised sections.
  const selectRef = useRef(select);
  useEffect(() => {
    selectRef.current = select;
  });
  const onOpen = useCallback((t: Tab) => selectRef.current(t), []);
  // Once idle, mount the remaining sections one at a time (first the neighbours), so later
  // switches never stall the transition while a heavy section renders.
  const pending = [tabs[index + 1], tabs[index - 1], ...tabs].find((t) => t && !visited.has(t.id))?.id;
  useEffect(() => {
    if (!pending || jump) return;
    const id = setTimeout(() => mount([pending]), visited.size <= 1 ? 600 : 350);
    return () => clearTimeout(id);
  }, [pending, jump, visited.size]);
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
        onPageScroll={Animated.event([{ nativeEvent: { position, offset } }], { useNativeDriver: true })}
        onPageSelected={(e) => {
          const i = e.nativeEvent.position;
          if (tabs[i] && tabs[i].id !== tab) setTab(tabs[i].id);
          mount([tabs[i]?.id]);
        }}
        onPageScrollStateChanged={(e) => {
          // A drag interrupts a jump.
          if (e.nativeEvent.pageScrollState === 'dragging') endJump();
        }}
      >
        {tabs.map((t, i) => (
          <Animated.ScrollView
            key={t.id}
            style={jump?.to === i ? { opacity: jump.p.interpolate({ inputRange: [0, 1], outputRange: [0.2, 1] }) } : undefined}
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
          </Animated.ScrollView>
        ))}
      </AnimatedPager>
      {/* Floating section bar: pages scroll under it; only the tabs themselves have a background. */}
      <View pointerEvents="box-none" style={{ position: 'absolute', left: 0, right: 0, top: insets.top + 8 }}>
        <SectionBar tabs={tabs} progress={progress} jump={jump} onSelect={select} />
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
