/**
 * Overview: sections in a horizontal pager (swipe between them), a transparent tab bar whose
 * highlight follows the swipe, and a pinned "+" button that fans out quick actions around it.
 */
import { router } from 'expo-router';
import { useEffect, useRef, useState } from 'react';
import {
  Animated,
  Pressable,
  ScrollView,
  Text,
  useWindowDimensions,
  View,
  type NativeScrollEvent,
  type NativeSyntheticEvent,
} from 'react-native';
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

type Tab = 'summary' | Exclude<SectionId, 'tips'>;
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

function SectionContent({ tab, onOpen }: { tab: Tab; onOpen: (t: Tab) => void }) {
  switch (tab) {
    case 'nutrition': return <Nutrition />;
    case 'water': return <Water />;
    case 'activity': return <Activity />;
    case 'heart': return <Heart />;
    case 'body': return <Body />;
    default: return <Summary onOpen={(s) => onOpen(s as Tab)} />;
  }
}

const BAR_PAD = 4;
const TAB_BASE = 44; // horizontal padding 12 + icon 20 + padding 12
const TAB_GAP = 2;
const LABEL_GAP = 6;

/**
 * Transparent section bar. Everything is driven by the pager's scroll position: the active
 * label smoothly expands while neighbours collapse, and the highlight pill slides between tabs.
 */
function SectionBar({ tabs, scrollX, pageW, onSelect }: { tabs: typeof TABS; scrollX: Animated.Value; pageW: number; onSelect: (t: Tab) => void }) {
  const c = useTheme();
  const L = useT();
  // Natural width of each label, measured off-screen once per language.
  const [labelW, setLabelW] = useState<Record<string, number>>({});
  const ready = pageW > 0 && tabs.every((t) => labelW[t.id]);
  const pages = tabs.map((_, i) => i * pageW);
  const around = (i: number) => [(i - 1) * pageW, i * pageW, (i + 1) * pageW];

  return (
    <View style={{ flexGrow: 0, flexShrink: 0 }}>
      {/* Hidden measurers */}
      <View style={{ position: 'absolute', opacity: 0, flexDirection: 'row' }} pointerEvents="none">
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
      <ScrollView horizontal showsHorizontalScrollIndicator={false} style={{ flexGrow: 0 }} contentContainerStyle={{ paddingHorizontal: 12 }}>
        <View style={{ flexDirection: 'row', padding: BAR_PAD, gap: TAB_GAP }}>
          {ready && tabs.length > 1 && (
            <Animated.View
              pointerEvents="none"
              style={{
                position: 'absolute',
                top: BAR_PAD,
                bottom: BAR_PAD,
                left: 0,
                borderRadius: 22,
                backgroundColor: alpha(c.accent, 0.16),
                width: scrollX.interpolate({ inputRange: pages, outputRange: tabs.map((t) => TAB_BASE + LABEL_GAP + labelW[t.id]), extrapolate: 'clamp' }),
                transform: [{ translateX: scrollX.interpolate({ inputRange: pages, outputRange: tabs.map((_, i) => BAR_PAD + i * (TAB_BASE + TAB_GAP)), extrapolate: 'clamp' }) }],
              }}
            />
          )}
          {tabs.map((t, i) => (
            <Tap
              key={t.id}
              onPress={() => onSelect(t.id)}
              accessibilityLabel={L.sections[t.id]}
              style={{ flexDirection: 'row', alignItems: 'center', paddingVertical: 9, paddingHorizontal: 12 }}
            >
              <Icon name={t.icon} size={20} color={c.accent} glow />
              {ready && (
                <Animated.View
                  style={{
                    overflow: 'hidden',
                    width: scrollX.interpolate({ inputRange: around(i), outputRange: [0, LABEL_GAP + labelW[t.id], 0], extrapolate: 'clamp' }),
                    opacity: scrollX.interpolate({ inputRange: around(i), outputRange: [0, 1, 0], extrapolate: 'clamp' }),
                  }}
                >
                  <T bold size={14} color={c.accent} numberOfLines={1} style={{ marginLeft: LABEL_GAP, width: labelW[t.id] }}>
                    {L.sections[t.id]}
                  </T>
                </Animated.View>
              )}
            </Tap>
          ))}
        </View>
      </ScrollView>
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

  // Arc from 160° to 20° (left to right), radius limited by the screen width.
  const radius = Math.min(150, width / 2 - 44);
  const n = items.length;
  const positions = items.map((_, i) => {
    const deg = n === 1 ? 90 : 160 - (140 * i) / (n - 1);
    const rad = (deg * Math.PI) / 180;
    return { x: Math.cos(rad) * radius, y: -Math.sin(rad) * radius - 10 };
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
              width: 84,
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
              style={{ width: 54, height: 54, borderRadius: 27, alignItems: 'center', justifyContent: 'center', backgroundColor: c.card, borderWidth: 1.5, borderColor: alpha(c.accent, 0.5), elevation: 6, shadowColor: c.accent, shadowOpacity: 0.4, shadowRadius: 8 }}
            >
              <Icon name={it.icon} size={24} color={c.accent} glow />
            </Tap>
            <T size={11} bold center color="#fff" numberOfLines={2} style={{ marginTop: 4, textShadowColor: '#000a', textShadowRadius: 4 }}>
              {L.fab[it.kind]}
            </T>
          </Animated.View>
        ))}
        <Tap
          onPress={() => setOpen(!open)}
          accessibilityLabel={L.fab.add}
          style={{ width: 62, height: 62, borderRadius: 31, alignItems: 'center', justifyContent: 'center', backgroundColor: c.accent, elevation: 8, shadowColor: c.accent, shadowOpacity: 0.55, shadowRadius: 14, shadowOffset: { width: 0, height: 4 } }}
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
  const [pageW, setPageW] = useState(0);
  const [tab, setTab] = useState<Tab>('summary');
  // Sections render lazily: only those the user has visited (or is next to).
  const [visited, setVisited] = useState<Set<Tab>>(() => new Set(['summary']));
  const [adding, setAdding] = useState<Exclude<AddKind, 'food'> | null>(null);
  const [workoutTpl, setWorkoutTpl] = useState<WorkoutTemplate | null | undefined>(undefined);
  const [scrollX] = useState(() => new Animated.Value(0));
  const pager = useRef<ScrollView>(null);
  // While a nested horizontal carousel is being touched, the section pager must not steal it.
  const [locked, setLocked] = useState(false);

  const tabs = TABS.filter((t) => t.id === 'summary' || sections[t.id]);
  const index = Math.max(0, tabs.findIndex((t) => t.id === tab));
  const addItems = ADD_ITEMS.filter((i) => sections[i.section]);

  const markVisited = (i: number) =>
    setVisited((prev) => {
      const ids = [tabs[i - 1]?.id, tabs[i]?.id, tabs[i + 1]?.id].filter((x): x is Tab => !!x);
      return ids.every((id) => prev.has(id)) ? prev : new Set([...prev, ...ids]);
    });

  const select = (t: Tab) => {
    const i = tabs.findIndex((x) => x.id === t);
    if (i < 0) return;
    markVisited(i);
    setTab(t);
    pager.current?.scrollTo({ x: i * pageW, animated: motion });
  };

  const onScrollEnd = (e: NativeSyntheticEvent<NativeScrollEvent>) => {
    if (!pageW) return;
    const i = Math.round(e.nativeEvent.contentOffset.x / pageW);
    markVisited(i);
    if (tabs[i] && tabs[i].id !== tab) setTab(tabs[i].id);
  };

  const pick = (kind: AddKind) => {
    if (kind === 'food') return router.push('/food-search');
    setWorkoutTpl(undefined);
    setAdding(kind);
  };
  const close = () => setAdding(null);

  return (
    <View style={{ flex: 1, backgroundColor: c.bg, paddingTop: insets.top + 8 }}>
      <SectionBar tabs={tabs} scrollX={scrollX} pageW={pageW} onSelect={select} />

      <View style={{ flex: 1 }} onLayout={(e) => setPageW(e.nativeEvent.layout.width)}>
        {pageW > 0 && (
          <Animated.ScrollView
            ref={pager}
            horizontal
            pagingEnabled
            showsHorizontalScrollIndicator={false}
            decelerationRate="fast"
            scrollEventThrottle={16}
            contentOffset={{ x: index * pageW, y: 0 }}
            onScroll={Animated.event([{ nativeEvent: { contentOffset: { x: scrollX } } }], { useNativeDriver: false })}
            onMomentumScrollEnd={onScrollEnd}
            keyboardShouldPersistTaps="handled"
            scrollEnabled={!locked}
          >
            {tabs.map((t) => (
              <ScrollView
                key={t.id}
                style={{ width: pageW }}
                contentContainerStyle={{ padding: 16, paddingTop: 12, paddingBottom: 120, gap: 12 }}
                keyboardShouldPersistTaps="handled"
                nestedScrollEnabled
              >
                {visited.has(t.id) ? (
                  <PagerLock.Provider value={setLocked}>
                    <SectionContent tab={t.id} onOpen={select} />
                  </PagerLock.Provider>
                ) : null}
              </ScrollView>
            ))}
          </Animated.ScrollView>
        )}
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
