/**
 * Nutrition: a swipeable stack of stat cards (max ~half the screen), then a single
 * "Add food" button and a flat, time-ordered log for the day.
 */
import { router } from 'expo-router';
import { useMemo, useState } from 'react';
import { Animated, ScrollView, useWindowDimensions, View, type NativeScrollEvent, type NativeSyntheticEvent } from 'react-native';

import { AmountSheet } from '../components/AmountSheet';
import { NutrientSheet } from '../components/NutrientSheet';
import { usePagerLockHandlers } from '../components/PagerLock';
import { BarChart, LineChart, RangeBar, Ring } from '../components/charts';
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
import { animateLayout } from '../lib/motion';
import { alpha, toneColor, useTheme } from '../lib/theme';
import { removeFoodEntry, updateFoodEntry } from '../store/actions';
import { getState, useStore } from '../store/store';
import type { FoodEntry, NutrientKey, Nutrients } from '../store/types';

function unitOf(k: NutrientKey, L: Dict) {
  return k === 'kcal' ? L.units.kcal : k === 'sodium' || k === 'potassium' || k === 'cholesterol' ? L.units.mg : L.units.g;
}

/** One nutrient line: label, value/target, gauge; tap opens the details sheet. */
function NutrientRow({ k, value, range, onOpen }: { k: NutrientKey; value: number; range: Range; onOpen: (k: NutrientKey) => void }) {
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
          <T dim size={13}> / {fmt(range.target)} {u}</T>
        </T>
      </Row>
      <RangeBar value={value} range={range} height={8} />
    </Tap>
  );
}

export function Nutrition() {
  const c = useTheme();
  const L = useT();
  const { height: winH } = useWindowDimensions();
  const [date, setDate] = useState(todayKey());
  const [editing, setEditing] = useState<FoodEntry | null>(null);
  const [detail, setDetail] = useState<NutrientKey | null>(null);
  const lockHandlers = usePagerLockHandlers();
  const [page, setPage] = useState(0);
  const [pageW, setPageW] = useState(0);
  const [pageHeights, setPageHeights] = useState<number[]>([]);
  const [scrollX] = useState(() => new Animated.Value(0));
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
    return lastDays(period, date).map((d) => {
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
          <ScrollView horizontal showsHorizontalScrollIndicator={false} nestedScrollEnabled contentContainerStyle={{ gap: 6 }}>
            {NUTRIENT_KEYS.map((k) => (
              <Chip key={k} label={L.nutrients[k].short} active={chartKey === k} onPress={() => setChartKey(k)} />
            ))}
          </ScrollView>
          <BarChart
            data={trend.map((x) => ({
              label: shortLabel(x.d),
              value: x.n[chartKey],
              color: rangeStatus(x.n[chartKey], x.target) === 'high' ? c.danger : x.logged ? c.accent : c.border,
            }))}
            bands={[{ from: lastTarget.lowerIsBetter ? 0 : lastTarget.min, to: lastTarget.max, color: alpha(c.ok, 0.12) }]}
            target={lastTarget.target}
            height={130}
          />
          <T size={12} dim>{L.nutrition.periodBalance(logged.length, periodBalance, Math.abs(periodBalance) / KCAL_PER_KG_FAT)}</T>
        </>
      ),
    },
  ];

  const maxH = winH * 0.5;
  // Card height interpolates between the measured heights of neighbouring pages while swiping,
  // so short pages don't leave empty space and transitions stay smooth.
  const heightAnim =
    pageW > 0 && pageHeights.length === pages.length && pageHeights.every(Boolean)
      ? scrollX.interpolate({
          inputRange: pages.map((_, i) => i * pageW),
          outputRange: pageHeights.map((h) => Math.min(h, maxH)),
          extrapolate: 'clamp',
        })
      : undefined;

  return (
    <>
      <DateNav date={date} onChange={setDate} />

      <Card style={{ padding: 0, gap: 0, overflow: 'hidden' }}>
        <View onLayout={(e) => setPageW(e.nativeEvent.layout.width)} {...lockHandlers}>
          <Animated.ScrollView
            horizontal
            pagingEnabled
            showsHorizontalScrollIndicator={false}
            decelerationRate="fast"
            scrollEventThrottle={16}
            onScroll={Animated.event([{ nativeEvent: { contentOffset: { x: scrollX } } }], {
              useNativeDriver: false,
              listener: (e: NativeSyntheticEvent<NativeScrollEvent>) => {
                if (!pageW) return;
                const p = Math.round(e.nativeEvent.contentOffset.x / pageW);
                if (p !== page) setPage(p);
              },
            })}
            style={{ height: heightAnim ?? maxH }}
            contentContainerStyle={{ alignItems: 'flex-start' }}
          >
            {pageW > 0 &&
              pages.map((p, idx) => (
                <ScrollView
                  key={p.title}
                  style={{ width: pageW, height: pageHeights[idx] ? Math.min(pageHeights[idx], maxH) : undefined }}
                  nestedScrollEnabled
                  showsVerticalScrollIndicator={false}
                >
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
                </ScrollView>
              ))}
          </Animated.ScrollView>
        </View>
        <Row gap={6} style={{ justifyContent: 'center', paddingVertical: 10 }}>
          {pages.map((p, i) => (
            <Animated.View
              key={p.title}
              style={{
                height: 6,
                borderRadius: 3,
                backgroundColor: c.accent,
                width: pageW ? scrollX.interpolate({ inputRange: [(i - 1) * pageW, i * pageW, (i + 1) * pageW], outputRange: [6, 20, 6], extrapolate: 'clamp' }) : 6,
                opacity: pageW ? scrollX.interpolate({ inputRange: [(i - 1) * pageW, i * pageW, (i + 1) * pageW], outputRange: [0.3, 1, 0.3], extrapolate: 'clamp' }) : 0.3,
              }}
            />
          ))}
        </Row>
      </Card>

      <Button title={L.nutrition.addFood} icon="plus" big onPress={() => router.push({ pathname: '/food-search', params: { date } })} />

      <Card>
        <T size={17} bold>{L.nutrition.log}</T>
        {sorted.length ? (
          sorted.map((e) => {
            const n = entryNutrients(e);
            return (
              <ListItem
                key={e.id}
                title={e.food.name}
                subtitle={`${timeLabel(e.time)} · ${fmt(e.grams)} ${L.units.g} · ${L.nutrients.protein.short} ${fmt(n.protein)} ${L.nutrients.fat.short} ${fmt(n.fat)} ${L.nutrients.carbs.short} ${fmt(n.carbs)}`}
                right={<T bold>{fmt(n.kcal)}</T>}
                onPress={() => setEditing(e)}
                onDelete={() => removeFoodEntry(e.id)}
              />
            );
          })
        ) : (
          <Empty text={L.nutrition.emptyLog} />
        )}
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
        submitLabel={L.common.save}
        onClose={() => setEditing(null)}
        onSubmit={(grams) => {
          if (editing) updateFoodEntry(editing.id, { grams });
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
