/** Steps and workouts: pedometer / Health Connect, templates with MET estimates, history. */
import { router } from 'expo-router';
import { useMemo, useState } from 'react';
import { View } from 'react-native';

import { BarChart } from '../components/charts';
import { DateNav } from '../components/DateNav';
import { StepsForm, TemplateForm, TemplateGrid, WorkoutForm } from '../components/forms';
import { Sheet } from '../components/Sheet';
import { Button, Card, Empty, fmt, H, Hint, ListItem, Row, Segmented, Stat, T, Tap } from '../components/ui';
import { useT } from '../i18n';
import { BASELINE_STEPS, stepsKcal } from '../lib/calc';
import { lastDays, shortLabel, timeLabel, todayKey } from '../lib/date';
import { hcSyncSteps } from '../lib/healthConnect';
import { useTheme } from '../lib/theme';
import { removeWorkout, updateSettings } from '../store/actions';
import { openAutostartSettings } from '../lib/battery';
import { useStore } from '../store/store';
import type { WorkoutTemplate } from '../store/types';

type SheetState = { kind: 'log'; template: WorkoutTemplate | null } | { kind: 'template'; template: WorkoutTemplate | null } | { kind: 'steps' };

export function Activity() {
  const c = useTheme();
  const L = useT();
  const [date, setDate] = useState(todayKey());
  const [sheet, setSheet] = useState<SheetState | null>(null);
  const [period, setPeriod] = useState<7 | 30>(7);
  const [syncedAt, setSyncedAt] = useState<number | null>(null);
  const weight = useStore((s) => s.profile.weightKg);
  const stepGoal = useStore((s) => s.settings.stepGoal);
  const source = useStore((s) => s.settings.stepsSource);
  const infoDismissed = useStore((s) => s.settings.stepsInfoDismissed);
  const allSteps = useStore((s) => s.steps);
  const allWorkouts = useStore((s) => s.workouts);
  const steps = allSteps[date] ?? 0;
  const workouts = allWorkouts.filter((w) => w.date === date);
  const workoutKcal = workouts.reduce((a, w) => a + w.kcal, 0);
  const chart = useMemo(() => lastDays(period, date).map((d) => ({ label: shortLabel(d), value: allSteps[d] ?? 0 })), [period, date, allSteps]);

  const sync = async () => {
    if (await hcSyncSteps(30)) setSyncedAt(Date.now());
    else router.push('/settings/steps');
  };

  return (
    <>
      <DateNav date={date} onChange={setDate} />

      {!infoDismissed && (
        <Card style={{ borderColor: c.accent }}>
          <H icon="info">{L.activity.info.title}</H>
          {(source === 'device' ? L.activity.info.device : L.activity.info.hc).map((p, i) => (
            <T key={i} size={14}>{p}</T>
          ))}
          <Row>
            {source === 'device' && <Button title={L.activity.info.battery} icon="battery" kind="secondary" onPress={openAutostartSettings} />}
            <Button title={L.activity.info.configure} kind="secondary" onPress={() => router.push('/settings/steps')} />
            <Button title={L.activity.info.ok} onPress={() => updateSettings({ stepsInfoDismissed: true })} />
          </Row>
        </Card>
      )}

      <Card>
        <H icon="activity" right={<Tap onPress={() => setSheet({ kind: 'steps' })}><T color={c.accent}>{L.common.edit}</T></Tap>}>
          {L.activity.steps}
        </H>
        <T size={40} bold>{fmt(steps)}</T>
        <View style={{ height: 10, borderRadius: 5, backgroundColor: c.cardAlt, overflow: 'hidden' }}>
          <View style={{ width: `${Math.min(100, (steps / stepGoal) * 100)}%`, height: 10, backgroundColor: steps >= stepGoal ? c.ok : c.accent }} />
        </View>
        <Row>
          <Stat label={L.activity.goal} value={fmt(stepGoal)} />
          <Stat label={L.activity.distance} value={fmt(steps / 1300, 1)} unit={L.units.km} />
          <Stat label={L.activity.extra} value={fmt(stepsKcal(steps, weight))} unit={L.units.kcal} />
        </Row>
        {source === 'healthConnect' && (
          <Row style={{ justifyContent: 'space-between' }}>
            <T dim size={12}>{syncedAt ? L.activity.synced(timeLabel(syncedAt)) : 'Health Connect'}</T>
            <Button title={L.activity.sync} icon="refresh" kind="secondary" onPress={sync} />
          </Row>
        )}
        <Hint title={L.activity.stepsHintTitle}>
          {(source === 'device' ? L.activity.info.device : L.activity.info.hc).map((p, i) => (
            <T key={i} size={14}>{p}</T>
          ))}
          <T size={14}>{L.activity.stepsHintBaseline(BASELINE_STEPS)}</T>
        </Hint>
      </Card>

      <Card>
        <H icon="workout" right={<T dim size={13}>{fmt(workoutKcal)} {L.units.kcal}</T>}>{L.activity.workouts}</H>
        {workouts.length ? (
          workouts.map((w) => (
            <ListItem key={w.id} title={w.name} subtitle={`${fmt(w.minutes)} ${L.units.min}${w.note ? ` · ${w.note}` : ''}`} right={<T bold>{fmt(w.kcal)} {L.units.kcal}</T>} onDelete={() => removeWorkout(w.id)} />
          ))
        ) : (
          <Empty text={L.activity.workoutsEmpty} />
        )}
        <Button title={L.activity.custom} icon="plus" kind="secondary" onPress={() => setSheet({ kind: 'log', template: null })} />
      </Card>

      <Card>
        <H right={<Tap onPress={() => setSheet({ kind: 'template', template: null })}><T color={c.accent}>+ {L.activity.newTemplate}</T></Tap>}>
          {L.activity.templates}
        </H>
        <T dim size={13}>{L.activity.templatesHint}</T>
        <TemplateGrid onPick={(tp) => setSheet({ kind: 'log', template: tp })} onEdit={(tp) => setSheet({ kind: 'template', template: tp })} />
      </Card>

      <Card>
        <H right={<Segmented value={period} onChange={setPeriod} options={[{ value: 7, label: L.common.days7 }, { value: 30, label: L.common.days30 }]} />}>
          {L.activity.byDay}
        </H>
        <BarChart data={chart.map((x) => ({ ...x, color: x.value >= stepGoal ? c.ok : c.accent }))} target={stepGoal} />
      </Card>

      <Sheet visible={!!sheet} onClose={() => setSheet(null)}>
        {sheet?.kind === 'steps' && <StepsForm date={date} onDone={() => setSheet(null)} />}
        {sheet?.kind === 'log' && <WorkoutForm template={sheet.template} date={date} onDone={() => setSheet(null)} />}
        {sheet?.kind === 'template' && <TemplateForm template={sheet.template} onDone={() => setSheet(null)} />}
      </Sheet>
    </>
  );
}
