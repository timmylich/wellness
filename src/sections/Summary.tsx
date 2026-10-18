/** Summary: a compact overview of every enabled section. Cards open their section. */
import * as Haptics from 'expo-haptics';
import { useMemo } from 'react';
import { View } from 'react-native';

import { BarChart, RangeBar, Ring } from '../components/charts';
import { Icon } from '../components/Icon';
import { Card, fmt, H, Hint, Row, Stat, T, Tap } from '../components/ui';
import { useT } from '../i18n';
import { bpCategory, expenditureFor, intakeKcalFor, KCAL_PER_KG_FAT } from '../lib/calc';
import { lastDays, todayKey, weekdayLabel } from '../lib/date';
import { useDaySummary, useWaterDay } from '../lib/hooks';
import { alpha, toneColor, useTheme } from '../lib/theme';
import { addWater } from '../store/actions';
import { getState, useStore } from '../store/store';
import type { SectionId } from '../store/types';

export function Summary({ onOpen }: { onOpen: (s: SectionId) => void }) {
  const c = useTheme();
  const L = useT();
  const today = todayKey();
  const sections = useStore((s) => s.settings.sections);
  const stepGoal = useStore((s) => s.settings.stepGoal);
  const steps = useStore((s) => s.steps[today] ?? 0);
  const waterMl = useStore((s) => s.settings.waterAmountMl);
  const lastHeart = useStore((s) => s.heart.findLast((h) => h.systolic != null));
  const weight = useStore((s) => s.profile.weightKg);
  const foodLog = useStore((s) => s.foodLog);
  const day = useDaySummary(today);
  const water = useWaterDay(today);

  const week = useMemo(() => {
    const data = getState();
    return lastDays(7).map((d) => {
      const intake = intakeKcalFor(data, d);
      return { d, balance: intake ? intake - expenditureFor(data, d) : 0, logged: intake > 0 };
    });
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [foodLog, day.expenditure]);
  const weekBalance = week.reduce((a, w) => a + w.balance, 0);
  const kcal = day.targets.kcal;
  const left = kcal.target - day.totals.kcal;
  const bp = lastHeart ? bpCategory(lastHeart.systolic!, lastHeart.diastolic ?? 0) : null;

  return (
    <>
      {sections.nutrition && (
        <Tap scaleTo={0.98} onPress={() => onOpen('nutrition')}>
          <Card>
            <H icon="nutrition">{L.summary.calories}</H>
            <Row style={{ justifyContent: 'space-between', flexWrap: 'nowrap' }}>
              <Stat label={L.summary.eaten} value={fmt(day.totals.kcal)} unit={L.units.kcal} />
              <Stat label={left >= 0 ? L.summary.left : L.summary.over} value={fmt(Math.abs(left))} unit={L.units.kcal} color={left >= 0 ? c.ok : c.danger} />
              <Stat label={L.summary.spent} value={fmt(day.expenditure)} unit={L.units.kcal} />
            </Row>
            <RangeBar value={day.totals.kcal} range={kcal} height={14} />
            <T dim size={12}>
              {L.summary.recommended(kcal.min, kcal.max, kcal.target)}
              {day.activityKcal ? L.summary.withActivity(day.activityKcal) : ''}
            </T>
            <Row style={{ justifyContent: 'space-between', flexWrap: 'nowrap' }}>
              {(['protein', 'fat', 'carbs'] as const).map((k) => (
                <View key={k} style={{ flex: 1 }}>
                  <T dim size={12}>{L.nutrients[k].label}</T>
                  <T bold>{fmt(day.totals[k])} / {fmt(day.targets[k].target)} {L.units.g}</T>
                  <RangeBar value={day.totals[k]} range={day.targets[k]} height={6} />
                </View>
              ))}
            </Row>
          </Card>
        </Tap>
      )}

      {sections.water && (
        <Tap scaleTo={0.98} onPress={() => onOpen('water')}>
        <Card>
          <H icon="water">{L.summary.water}</H>
          <Row style={{ flexWrap: 'nowrap', gap: 16 }}>
            <Ring value={water.total} max={water.range.target} size={100} stroke={10} color={c.water}>
              <T bold>{fmt(water.total / 1000, 2)} {L.units.l}</T>
            </Ring>
            <View style={{ flex: 1, gap: 8 }}>
              <T dim size={13}>{L.summary.waterGoal(water.range.target / 1000)}</T>
              {/* Big button: plain water. Small one: other drinks (count towards the danger limit only). */}
              <Row gap={8} style={{ flexWrap: 'nowrap', alignItems: 'stretch' }}>
                <Tap
                  onPress={() => {
                    addWater(waterMl);
                    Haptics.impactAsync(Haptics.ImpactFeedbackStyle.Medium).catch(() => {});
                  }}
                  accessibilityLabel={L.water.addLabel(waterMl)}
                  style={{ flex: 1, borderRadius: 18, paddingVertical: 16, alignItems: 'center', backgroundColor: c.water }}
                >
                  <Row gap={6} style={{ flexWrap: 'nowrap' }}>
                    <Icon name="water" size={20} color="#fff" />
                    <T size={18} bold color="#fff">+ {fmt(waterMl)} {L.units.ml}</T>
                  </Row>
                </Tap>
                <Tap
                  onPress={() => {
                    addWater(waterMl, todayKey(), 'other');
                    Haptics.impactAsync(Haptics.ImpactFeedbackStyle.Light).catch(() => {});
                  }}
                  accessibilityLabel={`${L.water.kinds.other}: ${L.water.addLabel(waterMl)}`}
                  style={{ width: 58, borderRadius: 18, alignItems: 'center', justifyContent: 'center', backgroundColor: alpha(c.high, 0.18) }}
                >
                  <Icon name="drink" size={22} color={c.high} />
                </Tap>
              </Row>
            </View>
          </Row>
        </Card>
        </Tap>
      )}

      {sections.activity && (
        <Tap scaleTo={0.98} onPress={() => onOpen('activity')}>
          <Card>
            <H icon="activity">{L.summary.activity}</H>
            <Row>
              <Stat label={L.summary.steps} value={fmt(steps)} unit={`/ ${fmt(stepGoal)}`} color={steps >= stepGoal ? c.ok : undefined} />
              <Stat label={L.summary.burned} value={fmt(day.activityKcal)} unit={L.units.kcal} />
            </Row>
            <View style={{ height: 8, borderRadius: 4, backgroundColor: c.cardAlt, overflow: 'hidden' }}>
              <View style={{ width: `${Math.min(100, (steps / stepGoal) * 100)}%`, height: 8, backgroundColor: c.accent }} />
            </View>
          </Card>
        </Tap>
      )}

      {sections.nutrition && (
        <Card>
          <H icon="flame">{L.summary.week}</H>
          <BarChart
            data={week.map((w) => ({
              label: weekdayLabel(w.d, L),
              value: Math.abs(w.balance),
              color: !w.logged ? c.border : w.balance > 0 ? alpha(c.high, 0.85) : alpha(c.ok, 0.85),
            }))}
            height={120}
          />
          <T>
            {L.summary.weekTotal}:{' '}
            <T bold color={weekBalance > 0 ? c.high : c.ok}>
              {weekBalance > 0 ? '+' : '−'}{fmt(Math.abs(weekBalance))} {L.units.kcal}
            </T>{' '}
            {L.summary.fatEq((Math.abs(weekBalance) / KCAL_PER_KG_FAT) * 1000, weekBalance > 0)}
          </T>
          <Hint title={L.summary.weekHintTitle}>{L.summary.weekHint}</Hint>
        </Card>
      )}

      <Row style={{ flexWrap: 'nowrap' }}>
        {sections.heart && (
          <Tap scaleTo={0.97} style={{ flex: 1 }} onPress={() => onOpen('heart')}>
            <Card>
              <Row gap={6}><Icon name="heart" size={16} color={c.accent} /><T dim size={12}>{L.summary.pressure}</T></Row>
              {lastHeart && bp ? (
                <>
                  <T size={20} bold>{lastHeart.systolic}/{lastHeart.diastolic}</T>
                  <T size={12} color={toneColor(c, bp.tone)}>{L.heart.bp[bp.key].label}</T>
                </>
              ) : (
                <T dim>{L.summary.noEntries}</T>
              )}
            </Card>
          </Tap>
        )}
        {sections.body && (
          <Tap scaleTo={0.97} style={{ flex: 1 }} onPress={() => onOpen('body')}>
            <Card>
              <Row gap={6}><Icon name="body" size={16} color={c.accent} /><T dim size={12}>{L.summary.weight}</T></Row>
              <T size={20} bold>{fmt(weight, 1)} {L.units.kg}</T>
              <T size={12} dim>{L.summary.tapToUpdate}</T>
            </Card>
          </Tap>
        )}
      </Row>
    </>
  );
}
