/**
 * Nutrition: a swipeable stack of stat cards (max ~half the screen), then a single
 * "Add food" button and a flat, time-ordered log for the day.
 */
import { router } from 'expo-router';
import { memo, useDeferredValue, useEffect, useMemo, useRef, useState } from 'react';
import { Animated, ScrollView, useWindowDimensions, View, type NativeScrollEvent, type NativeSyntheticEvent } from 'react-native';

import { AmountSheet } from '../components/AmountSheet';
import { NutrientSheet } from '../components/NutrientSheet';
import { BarChart, LineChart, RangeBar, Ring } from '../components/charts';
import { NestedScrollHost } from '../../modules/wellness-native';
import { Appear } from '../components/Appear';
import { DateNav } from '../components/DateNav';
import { Button, Card, Chip, Empty, fmt, Hint, ListItem, Row, Segmented, Stat, T, Tap } from '../components/ui';
import { useT, type Dict } from '../i18n';
import { nutritionAdvice, wakingDayProgress } from '../lib/advice';
import {
  adaptiveCorrection,
  entryNutrients,
  expenditureFor,
  KCAL_PER_KG_FAT,
  NUTRIENT_KEYS,
  rangeStatus,
  sumEntries,
  targetsFor,
  type Range,
} from '../lib/calc';
import { lastDays, shortLabel, timeLabel, todayKey } from '../lib/date';
import { useDaySummary } from '../lib/hooks';
import { animateLayout, useMotion } from '../lib/motion';
import { alpha, toneColor, useTheme } from '../lib/theme';
import { removeFoodEntry, updateFoodEntry } from '../store/actions';
import { getState, useStore } from '../store/store';
import type { FoodEntry, NutrientKey, Nutrients } from '../store/types';

function unitOf(k: NutrientKey, L: Dict) {
  return k === 'kcal' ? L.units.kcal : k === 'sodium' || k === 'potassium' || k === 'cholesterol' ? L.units.mg : L.units.g;
}

/** One nutrient line: label, value/target, gauge; tap opens the details sheet. */
const NutrientRow = memo(function NutrientRow({ k, value, range, onOpen }: { k: NutrientKey; value: number; range: Range; onOpen: (k: NutrientKey) => void }) {
  const c = useTheme();
  const L = useT();
  const status = rangeStatus(value, range);
  const u = unitOf(k, L);
  return (
    <Tap scaleTo={0.98} onPress={() => onOpen(k)} style={{ gap: 2 }}>
      <Row style={{ justifyContent: 'space-between', flexWrap: 'nowrap' }}>
        <T>{L.nutrients[k].label}</T>
        <T>
          <T bold color={toneColor(c, status === 'high' ? 'danger' : status === 'low' ? 'low' : 'ok')}>{fmt(value, value < 10 && value % 1 ? 1 : 0)}</T>
          <T dim size={13}> / {range.lowerIsBetter ? `≤ ${fmt(range.max)}` : fmt(range.target)} {u}</T>
        </T>
      </Row>
      <RangeBar value={value} range={range} height={8} />
    </Tap>
  );
});

export function Nutrition() {
  const c = useTheme();
  const L = useT();
  const { height: winH } = useWindowDimensions();
  const [navDate, setDate] = useState(todayKey());
  // The date label animates immediately; the heavy day data follows a frame later.
  const date = useDeferredValue(navDate);
  const [editing, setEditing] = useState<FoodEntry | null>(null);
  const [detail, setDetail] = useState<NutrientKey | null>(null);
  const [period, setPeriod] = useState<7 | 30>(7);
  const [chartKey, setChartKey] = useState<NutrientKey>('kcal');
  const adaptive = useStore((s) => s.settings.adaptiveTargets);
  const foodLog = useStore((s) => s.foodLog);
  const { entries, totals, targets, expenditure, balance } = useDaySummary(date);

  const advice = nutritionAdvice(totals, targets, date === todayKey() ? wakingDayProgress() : 1);
  // getState() is read inside; foodLog/adaptive are listed so the memo recomputes on change.
  // eslint-disable-next-line react-hooks/exhaustive-deps
  const correction = useMemo(() => adaptiveCorrection(getState(), date), [date, foodLog, adaptive]);
  const trend = useMemo(() => {
    const data = getState();
    // Trend charts always end today: they show history, not the selected day.
    return lastDays(period, todayKey()).map((d) => {
      const day = data.foodLog.filter((e) => e.date === d);
      return { d, n: sumEntries(day), logged: day.length > 0, exp: expenditureFor(data, d), target: targetsFor(data, d)[chartKey] };
    });
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [period, date, foodLog, chartKey]);
  const logged = trend.filter((x) => x.logged);
  const periodBalance = logged.reduce((a, x) => a + x.n.kcal - x.exp, 0);
  const sorted = [...entries].sort((a, b) => a.time - b.time);

  const rows = (keys: NutrientKey[], n: Nutrients = totals) =>
    keys.map((k) => <NutrientRow key={k} k={k} value={n[k]} range={targets[k]} onOpen={setDetail} />);
  const kcal = targets.kcal;
  const left = kcal.target - totals.kcal;
  const lastTarget = trend[trend.length - 1].target;

  const pages: { title: string; body: React.ReactNode }[] = [
    {
      title: L.nutrition.pages.calories,
      body: (
        <>
          <Row style={{ flexWrap: 'nowrap', gap: 16 }}>
            <Ring value={totals.kcal} max={kcal.target} size={120} stroke={12} color={totals.kcal > kcal.max ? c.danger : c.accent}>
              <T size={20} bold>{fmt(totals.kcal)}</T>
              <T dim size={12}>/ {fmt(kcal.target)}</T>
            </Ring>
            <View style={{ flex: 1, gap: 6 }}>
              <Stat label={left >= 0 ? L.summary.left : L.summary.over} value={fmt(Math.abs(left))} unit={L.units.kcal} color={left >= 0 ? c.ok : c.danger} />
              <T dim size={12}>{L.summary.recommended(kcal.min, kcal.max, kcal.target)}</T>
            </View>
          </Row>
          {rows(['protein', 'fat', 'carbs'])}
          {adaptive && correction !== 0 ? <T size={12} color={c.accent}>{L.nutrition.adaptive(correction)}</T> : null}
        </>
      ),
    },
    {
      title: L.nutrition.pages.quality,
      body: (
        <>
          {rows(['sugar', 'addedSugar', 'fiber', 'satFat', 'transFat', 'cholesterol', 'sodium', 'potassium'])}
          <Hint title={L.nutrition.scalesHintTitle}>{L.nutrition.scalesHint}</Hint>
        </>
      ),
    },
    {
      title: L.nutrition.pages.balance,
      body: (
        <>
          <Row>
            <Stat label={L.summary.eaten} value={fmt(totals.kcal)} unit={L.units.kcal} />
            <Stat label={L.summary.spent} value={fmt(expenditure)} unit={L.units.kcal} />
            <Stat label={balance > 0 ? L.nutrition.surplus : L.nutrition.deficit} value={fmt(Math.abs(balance))} unit={L.units.kcal} color={balance > 0 ? c.high : c.ok} />
          </Row>
          <T dim size={13}>{L.nutrition.fatMass((Math.abs(balance) / KCAL_PER_KG_FAT) * 1000)}</T>
          <T dim size={13}>{L.nutrition.intakeVsSpent}</T>
          <LineChart
            labels={trend.slice(-7).map((x) => shortLabel(x.d))}
            series={[
              { values: trend.slice(-7).map((x) => (x.logged ? Math.round(x.n.kcal) : null)), color: c.accent },
              { values: trend.slice(-7).map((x) => x.exp), color: c.textDim },
            ]}
            height={130}
          />
          <Row>
            <T size={12} color={c.accent}>● {L.nutrition.eatenLegend}</T>
            <T size={12} dim>● {L.nutrition.spentLegend}</T>
          </Row>
          {advice.map((a) => (
            <Row key={a.key} style={{ flexWrap: 'nowrap', alignItems: 'flex-start' }}>
              <View style={{ width: 4, alignSelf: 'stretch', borderRadius: 2, backgroundColor: toneColor(c, a.tone) }} />
              <T size={14} style={{ flex: 1 }}>{L.advice[a.key]}</T>
            </Row>
          ))}
        </>
      ),
    },
    {
      title: L.nutrition.pages.trends,
      body: (
        <>
          <Segmented value={period} onChange={setPeriod} options={[{ value: 7, label: L.common.days7 }, { value: 30, label: L.common.days30 }]} />
          <NestedScrollHost mode="horizontal">
          <ScrollView horizontal showsHorizontalScrollIndicator={false} nestedScrollEnabled contentContainerStyle={{ gap: 6 }}>
            {NUTRIENT_KEYS.map((k) => (
              <Chip key={k} label={L.nutrients[k].short} active={chartKey === k} onPress={() => setChartKey(k)} />
            ))}
          </ScrollView>
          </NestedScrollHost>
          <BarChart
            data={trend.map((x) => ({
              label: shortLabel(x.d),
              value: x.n[chartKey],
              color: rangeStatus(x.n[chartKey], x.target) === 'high' ? c.danger : x.logged ? c.accent : c.border,
            }))}
            bands={[{ from: lastTarget.lowerIsBetter ? 0 : lastTarget.min, to: lastTarget.max, color: alpha(c.ok, 0.12) }]}
            target={lastTarget.lowerIsBetter ? undefined : lastTarget.target}
            height={130}
          />
          <T size={12} dim>{L.nutrition.periodBalance(logged.length, periodBalance, Math.abs(periodBalance) / KCAL_PER_KG_FAT)}</T>
        </>
      ),
    },
  ];


  return (
    <>
      <DateNav date={navDate} onChange={setDate} />

      <StatCarousel pages={pages} maxH={winH * 0.5} />

      <Button title={L.nutrition.addFood} icon="plus" big onPress={() => router.push({ pathname: '/food-search', params: { date } })} />

      <Card>
        <T size={17} bold>{L.nutrition.log}</T>
        {/* Re-keyed per day: switching days replays the entrance animation. */}
        <Appear key={date}>
        {sorted.length ? (
          sorted.map((e) => {
            const n = entryNutrients(e);
            return (
              <ListItem
                key={e.id}
                title={e.food.name}
                subtitle={`${timeLabel(e.time)} · ${e.pieces ? `${e.pieces} × ${fmt(e.grams / e.pieces)}` : fmt(e.grams)} ${L.units.g} · ${L.nutrients.protein.short} ${fmt(n.protein)} ${L.nutrients.fat.short} ${fmt(n.fat)} ${L.nutrients.carbs.short} ${fmt(n.carbs)}`}
                right={<T bold>{fmt(n.kcal)}</T>}
                onPress={() => setEditing(e)}
                onDelete={() => removeFoodEntry(e.id)}
              />
            );
          })
        ) : (
          <Empty text={L.nutrition.emptyLog} />
        )}
        </Appear>
        {sorted.length ? <T dim size={12}>{L.common.longPressDelete}</T> : null}
      </Card>

      <NutrientSheet
        nutrient={detail}
        value={detail ? totals[detail] : 0}
        range={detail ? targets[detail] : null}
        entries={entries}
        onClose={() => setDetail(null)}
      />

      <AmountSheet
        food={editing?.food ?? null}
        initialGrams={editing?.grams}
        initialPieces={editing?.pieces}
        submitLabel={L.common.save}
        onClose={() => setEditing(null)}
        onEditData={() => {
          const id = editing?.id;
          setEditing(null);
          if (id) setTimeout(() => router.push({ pathname: '/food-edit', params: { entry: id } }), 50);
        }}
        onSubmit={(grams, pieces) => {
          if (editing) updateFoodEntry(editing.id, { grams, pieces: pieces > 1 ? pieces : undefined });
          setEditing(null);
        }}
        onDelete={() => {
          if (editing) {
            animateLayout();
            removeFoodEntry(editing.id);
          }
          setEditing(null);
        }}
      />
    </>
  );
}

/**
 * Swipeable stat cards. Kept in its own component so measuring page heights (e.g. while a hint
 * inside expands) re-renders only the carousel, not the whole Food section. The card springs to
 * the height of the page being shown; height changes within the same page follow instantly.
 */
const DOT = 7;
const DOT_GAP = 9;
const DOT_ACTIVE = 18;
/** Fraction of the card width, or fling speed, that is enough to change the card. */
const SWIPE_FRACTION = 0.12;
const SWIPE_VELOCITY = 0.15;

function StatCarousel({ pages, maxH }: { pages: { title: string; body: React.ReactNode }[]; maxH: number }) {
  const scrollRef = useRef<ScrollView>(null);
  // Page where the current drag started (closure state: read in scroll callbacks only).
  const [dragStart] = useState(() => {
    let page = 0;
    return { set: (p: number) => (page = p), get: () => page };
  });
  const c = useTheme();
  const [page, setPage] = useState(0);
  const [pageW, setPageW] = useState(0);
  const [pageHeights, setPageHeights] = useState<number[]>([]);
  const [scrollX] = useState(() => new Animated.Value(0));
  // Card height: jumps to the first measured height, springs afterwards.
  const [card] = useState(() => {
    const height = new Animated.Value(0);
    let ready = false;
    let current = 0;
    return {
      height,
      to(h: number, animate: boolean, page: number) {
        // Spring only when switching pages; growth inside a page (a hint opening) follows it.
        const switched = page !== current;
        current = page;
        if (!ready || !animate || !switched) height.setValue(h);
        else Animated.spring(height, { toValue: h, useNativeDriver: false, speed: 18, bounciness: 2 }).start();
        ready = true;
      },
    };
  });
  const motion = useMotion();
  // The card springs to the height of the page being shown (decided at the swipe midpoint).
  // One animation instead of tracking every scroll event, so content below never lags behind
  // and jumps at the end of a fast swipe.
  const targetH = pageHeights[page] ?? null;
  const [shownH, setShownH] = useState<number | null>(null);
  if (targetH != null && targetH !== shownH) setShownH(targetH);
  useEffect(() => {
    if (shownH != null) card.to(shownH, motion, page);
  }, [shownH, motion, card, page]);

  return (
      <Card style={{ padding: 0, gap: 0, overflow: 'hidden' }}>
        <NestedScrollHost mode="horizontal" onLayout={(e) => setPageW(e.nativeEvent.layout.width)}>
          <Animated.ScrollView
            ref={scrollRef}
            horizontal
            showsHorizontalScrollIndicator={false}
            decelerationRate="fast"
            // Own snapping: a short, light swipe is enough to change the card.
            onScrollBeginDrag={(e) => {
              dragStart.set(pageW ? Math.round(e.nativeEvent.contentOffset.x / pageW) : 0);
            }}
            onScrollEndDrag={(e) => {
              if (!pageW) return;
              const start = dragStart.get();
              const dx = e.nativeEvent.contentOffset.x - start * pageW;
              const vx = -(e.nativeEvent.velocity?.x ?? 0);
              let to = start;
              if (dx > pageW * SWIPE_FRACTION || vx > SWIPE_VELOCITY) to += 1;
              else if (dx < -pageW * SWIPE_FRACTION || vx < -SWIPE_VELOCITY) to -= 1;
              to = Math.max(0, Math.min(pages.length - 1, to));
              scrollRef.current?.scrollTo({ x: to * pageW, animated: true });
            }}
            scrollEventThrottle={16}
            onScroll={Animated.event([{ nativeEvent: { contentOffset: { x: scrollX } } }], {
              // Dots follow the swipe on the native thread; JS only hears about page changes.
              useNativeDriver: true,
              listener: (e: NativeSyntheticEvent<NativeScrollEvent>) => {
                if (!pageW) return;
                const p = Math.round(e.nativeEvent.contentOffset.x / pageW);
                if (p !== page) setPage(p);
              },
            })}
            style={{ height: shownH == null ? maxH : card.height }}
            contentContainerStyle={{ alignItems: 'flex-start' }}
          >
            {pageW > 0 &&
              pages.map((p, idx) => (
                <View key={p.title} style={{ width: pageW }}>
                  <View
                    style={{ padding: 16, gap: 8 }}
                    onLayout={(e) => {
                      const h = Math.ceil(e.nativeEvent.layout.height);
                      setPageHeights((prev) => (prev[idx] === h ? prev : Object.assign([...prev], { [idx]: h })));
                    }}
                  >
                    <T size={17} bold>{p.title}</T>
                    {p.body}
                  </View>
                </View>
              ))}
          </Animated.ScrollView>
        </NestedScrollHost>
        {/* Page dots with one sliding indicator (native-driven). */}
        <View style={{ alignSelf: 'center', flexDirection: 'row', gap: DOT_GAP, paddingVertical: 12 }}>
          {pages.map((p) => (
            <View key={p.title} style={{ width: DOT, height: DOT, borderRadius: DOT / 2, backgroundColor: alpha(c.accent, 0.3) }} />
          ))}
          <Animated.View
            style={{
              position: 'absolute',
              top: 12 - 0.5,
              left: (DOT - DOT_ACTIVE) / 2,
              width: DOT_ACTIVE,
              height: DOT + 1,
              borderRadius: (DOT + 1) / 2,
              backgroundColor: c.accent,
              transform: [{ translateX: pageW ? Animated.multiply(scrollX, (DOT + DOT_GAP) / pageW) : 0 }],
            }}
          />
        </View>
      </Card>
  );
}
