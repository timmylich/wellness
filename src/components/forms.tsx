/**
 * Data-entry forms shared by section screens and the universal "+" quick-add sheet.
 * Each form calls `onDone` after saving so the host can close itself.
 */
import * as Haptics from 'expo-haptics';
import { useMemo, useState } from 'react';
import { View } from 'react-native';
import { showDialog } from './Dialog';

import { useLang, useT } from '../i18n';
import { bpCategory, metKcal, navyBodyFat, weightTrend } from '../lib/calc';
import { shortLabel, todayKey } from '../lib/date';
import { animateLayout } from '../lib/motion';
import { alpha, toneColor, useTheme } from '../lib/theme';
import { workoutName } from '../data/workouts';
import { addBody, addHeart, addWater, addWorkout, deleteWorkoutTemplate, saveWorkoutTemplate, setSteps, updateSettings } from '../store/actions';
import { uid, useStore } from '../store/store';
import type { FluidKind, WorkoutTemplate } from '../store/types';
import { LineChart } from './charts';
import { Icon } from './Icon';
import { Button, Chip, Field, fmt, Hint, parseNum, Row, T, Tap, Toggle } from './ui';
import { range, Wheel } from './Wheel';


export function WaterForm({ date = todayKey(), onDone }: { date?: string; onDone?: () => void }) {
  const L = useT();
  const c = useTheme();
  const presets = useStore((s) => s.settings.waterPresets);
  const current = useStore((s) => s.settings.waterAmountMl);
  const [amount, setAmount] = useState(String(current));
  const ml = parseNum(amount) ?? 0;
  const choose = (v: number) => {
    setAmount(String(v));
    updateSettings({ waterAmountMl: v });
  };
  const add = (kind: FluidKind) => {
    if (ml <= 0) return;
    animateLayout();
    addWater(ml, date, kind);
    Haptics.impactAsync(kind === 'water' ? Haptics.ImpactFeedbackStyle.Medium : Haptics.ImpactFeedbackStyle.Light).catch(() => {});
    onDone?.();
  };
  return (
    <View style={{ gap: 12, alignSelf: 'stretch' }}>
      {/* Big button: plain water. Small one: other drinks (count towards the danger limit only). */}
      <Row gap={8} style={{ flexWrap: 'nowrap', alignItems: 'stretch' }}>
        <Tap
          onPress={() => add('water')}
          accessibilityLabel={L.water.addLabel(ml)}
          style={{ flex: 1, borderRadius: 24, paddingVertical: 22, alignItems: 'center', backgroundColor: c.water }}
        >
          <Row gap={8} style={{ flexWrap: 'nowrap' }}>
            <Icon name="water" size={24} color="#fff" />
            <T size={26} bold color="#fff">+ {fmt(ml)} {L.units.ml}</T>
          </Row>
          <T size={13} color="#ffffffcc">{L.water.tapToAdd}</T>
        </Tap>
        <Tap
          onPress={() => add('other')}
          accessibilityLabel={`${L.water.kinds.other}: ${L.water.addLabel(ml)}`}
          style={{ width: 76, borderRadius: 24, alignItems: 'center', justifyContent: 'center', gap: 4, backgroundColor: alpha(c.high, 0.18) }}
        >
          <Icon name="drink" size={26} color={c.high} />
          <T size={11} bold center color={c.high} numberOfLines={2}>{L.water.kinds.other}</T>
        </Tap>
      </Row>
      <T dim size={12}>{L.water.otherHint}</T>
      <Row style={{ justifyContent: 'center' }}>
        {presets.map((p) => (
          <Chip key={p} label={`${p} ${L.units.ml}`} active={ml === p} onPress={() => choose(p)} />
        ))}
      </Row>
      <Field
        label={L.water.custom}
        value={amount}
        onChange={(v) => {
          setAmount(v);
          const n = parseNum(v);
          if (n && n > 0 && n < 5000) updateSettings({ waterAmountMl: n });
        }}
        suffix={L.units.ml}
      />
    </View>
  );
}

export function StepsForm({ date = todayKey(), onDone }: { date?: string; onDone?: () => void }) {
  const L = useT();
  const steps = useStore((s) => s.steps[date] ?? 0);
  const [value, setValue] = useState(String(steps));
  return (
    <View style={{ gap: 12 }}>
      <Field label={L.activity.stepsOfDay} value={value} onChange={setValue} suffix={L.units.steps} />
      <Button
        title={L.common.save}
        onPress={() => {
          setSteps(date, parseNum(value) ?? 0);
          onDone?.();
        }}
      />
    </View>
  );
}

const SYS = range(60, 260);
const DIA = range(30, 160);
const PULSE = range(30, 220);
const KG = range(20, 300);
const TENTHS = range(0, 9);

export function HeartForm({ onDone }: { onDone?: () => void }) {
  const L = useT();
  const c = useTheme();
  // Wheels start from the previous reading (or typical values).
  const last = useStore((st) => st.heart.at(-1));
  const [s, setS] = useState(last?.systolic ?? 120);
  const [d, setD] = useState(last?.diastolic ?? 80);
  const [p, setP] = useState(last?.pulse ?? 70);
  const [note, setNote] = useState('');
  const preview = bpCategory(s, d);
  const save = () => {
    addHeart({ systolic: s, diastolic: d, pulse: p, note: note.trim() || undefined });
    setNote('');
    onDone?.();
  };
  return (
    <View style={{ gap: 12 }}>
      <Row style={{ flexWrap: 'nowrap', justifyContent: 'center', alignItems: 'flex-start' }}>
        <View style={{ alignItems: 'center' }}>
          <T dim size={12}>{L.heart.systolic}</T>
          <Wheel values={SYS} value={s} onChange={setS} />
        </View>
        <T size={22} style={{ marginTop: 108 }}>/</T>
        <View style={{ alignItems: 'center' }}>
          <T dim size={12}>{L.heart.diastolic}</T>
          <Wheel values={DIA} value={d} onChange={setD} />
        </View>
        <View style={{ alignItems: 'center', marginLeft: 12 }}>
          <T dim size={12}>{L.heart.pulse}</T>
          <Wheel values={PULSE} value={p} onChange={setP} />
        </View>
      </Row>
      <Field label={L.heart.note} value={note} onChange={setNote} numeric={false} />
      {(
        <View style={{ backgroundColor: alpha(toneColor(c, preview.tone), 0.15), borderRadius: 12, padding: 12, gap: 4 }}>
          <T bold color={toneColor(c, preview.tone)}>{L.heart.bp[preview.key].label}</T>
          <T size={13}>{L.heart.bp[preview.key].advice}</T>
        </View>
      )}
      <Button title={L.common.save} onPress={save} />
      <Hint title={L.heart.howTitle}>{L.heart.how}</Hint>
    </View>
  );
}

export function BodyForm({ onDone }: { onDone?: () => void }) {
  const L = useT();
  const c = useTheme();
  const profile = useStore((s) => s.profile);
  const lastWeight = useStore((st) => st.body.findLast((e) => e.weightKg)?.weightKg) ?? profile.weightKg;
  // Weight: whole kilograms + tenths on two wheels, starting from the last weigh-in.
  const [kg, setKg] = useState(Math.floor(lastWeight));
  const [tenth, setTenth] = useState(Math.round((lastWeight % 1) * 10) % 10);
  const [withWeight, setWithWeight] = useState(true);
  const [f, setF] = useState({ fat: '', waist: '', neck: '', hip: '', height: '' });
  const set = (k: keyof typeof f) => (v: string) => setF({ ...f, [k]: v });
  const waist = parseNum(f.waist), neck = parseNum(f.neck), hip = parseNum(f.hip);
  const navy = waist && neck ? navyBodyFat(profile.sex, profile.heightCm, waist, neck, hip) : null;
  // Recent weigh-ins plus the one being typed, with the weighted average line.
  const body = useStore((s) => s.body);
  const draftW = withWeight ? kg + tenth / 10 : undefined;
  const recent = useMemo(
    () => body.filter((e) => e.weightKg).sort((a, b) => a.time - b.time).slice(-30).map((e) => ({ time: e.time, weightKg: e.weightKg!, date: e.date })),
    [body],
  );
  const [now] = useState(() => Date.now());
  const pts = draftW ? [...recent, { time: now, weightKg: draftW, date: todayKey() }] : recent;
  const trend = weightTrend(pts);
  const prevAvg = recent.length ? weightTrend(recent).at(-1)! : null;
  const save = () => {
    const entry = {
      weightKg: draftW, bodyFatPct: parseNum(f.fat), waistCm: waist, neckCm: neck, hipCm: hip, heightCm: parseNum(f.height),
    };
    if (Object.values(entry).every((v) => v == null)) return;
    addBody(entry);
    setF({ fat: '', waist: '', neck: '', hip: '', height: '' });
    onDone?.();
  };
  return (
    <View style={{ gap: 12 }}>
      <T dim size={13}>{L.body.measureNote}</T>
      <Toggle label={L.body.weight} value={withWeight} onChange={setWithWeight} />
      {withWeight && (
        <Row style={{ flexWrap: 'nowrap', justifyContent: 'center' }}>
          <Wheel values={KG} value={kg} onChange={setKg} width={96} />
          <T size={26} bold>,</T>
          <Wheel values={TENTHS} value={tenth} onChange={setTenth} width={56} />
          <T size={18} dim>{L.units.kg}</T>
        </Row>
      )}
      <Field label={L.body.fatInput} value={f.fat} onChange={set('fat')} suffix="%" />
      <Row>
        <Field label={L.body.waist} value={f.waist} onChange={set('waist')} suffix={L.units.cm} />
        <Field label={L.body.neck} value={f.neck} onChange={set('neck')} suffix={L.units.cm} />
      </Row>
      <Row>
        {profile.sex === 'female' && <Field label={L.body.hip} value={f.hip} onChange={set('hip')} suffix={L.units.cm} />}
        <Field label={L.body.heightChanged} value={f.height} onChange={set('height')} suffix={L.units.cm} />
      </Row>
      {navy && parseNum(f.fat) == null ? <T dim size={13}>{L.body.navy(navy)}</T> : null}
      {pts.length >= 2 ? (
        <>
          <LineChart
            height={140}
            labels={pts.map((p) => shortLabel(p.date))}
            series={[
              { values: pts.map((p) => p.weightKg), color: alpha(c.accent, 0.4) },
              { values: trend, color: c.accent },
            ]}
          />
          {prevAvg != null ? <T dim size={13}>{L.body.avgLine(prevAvg, draftW ? trend.at(-1)! : null)}</T> : null}
        </>
      ) : null}
      <Button title={L.common.save} onPress={save} />
      <Hint title={L.body.weighTitle}>{L.body.weigh}</Hint>
    </View>
  );
}

/** Log a workout from a template or as a custom entry. */
export function WorkoutForm({
  template,
  date = todayKey(),
  onDone,
}: {
  template: WorkoutTemplate | null;
  date?: string;
  onDone?: () => void;
}) {
  const L = useT();
  const lang = useLang();
  const weight = useStore((s) => s.profile.weightKg);
  const [name, setName] = useState('');
  const [minutes, setMinutes] = useState(String(template?.defaultMinutes ?? 30));
  const [kcal, setKcal] = useState('');
  const [note, setNote] = useState('');
  const min = parseNum(minutes) ?? 0;
  const estimated = template ? metKcal(template.met, weight, min) : undefined;
  const save = () => {
    const k = parseNum(kcal) ?? estimated;
    const title = template ? workoutName(template, lang) : name.trim();
    if (!title || k == null) return showDialog({ title: L.activity.needNameKcal, icon: 'info' });
    animateLayout();
    addWorkout({ date, name: title, minutes: min, kcal: Math.round(k), note: note.trim() || undefined });
    onDone?.();
  };
  return (
    <View style={{ gap: 12 }}>
      <T size={18} bold>{template ? workoutName(template, lang) : L.activity.custom}</T>
      {!template && <Field label={L.activity.name} value={name} onChange={setName} numeric={false} />}
      <Row>
        <Field label={L.activity.duration} value={minutes} onChange={setMinutes} suffix={L.units.min} />
        <Field label={L.activity.calories} value={kcal} onChange={setKcal} suffix={L.units.kcal} placeholder={estimated != null ? String(estimated) : '—'} />
      </Row>
      {estimated != null && <T dim size={13}>{L.activity.estimate(estimated)}</T>}
      <Field label={L.activity.note} value={note} onChange={setNote} numeric={false} />
      <Button title={L.common.save} onPress={save} />
    </View>
  );
}

export function TemplateForm({ template, onDone }: { template: WorkoutTemplate | null; onDone?: () => void }) {
  const L = useT();
  const lang = useLang();
  const [name, setName] = useState(template ? workoutName(template, lang) : '');
  const [met, setMet] = useState(template ? String(template.met) : '');
  const [minutes, setMinutes] = useState(String(template?.defaultMinutes ?? 30));
  const save = () => {
    const m = parseNum(met);
    if (!name.trim() || !m) return showDialog({ title: L.activity.needNameMet, icon: 'info' });
    saveWorkoutTemplate({ id: template?.id ?? `custom-${uid()}`, name: name.trim(), met: m, defaultMinutes: parseNum(minutes) || 30, builtin: template?.builtin });
    onDone?.();
  };
  return (
    <View style={{ gap: 12 }}>
      <T size={18} bold>{template ? L.activity.editTemplate : L.activity.createTemplate}</T>
      <Field label={L.activity.name} value={name} onChange={setName} numeric={false} />
      <Row>
        <Field label={L.activity.met} value={met} onChange={setMet} />
        <Field label={L.activity.defaultDuration} value={minutes} onChange={setMinutes} suffix={L.units.min} />
      </Row>
      <Hint title={L.activity.metHintTitle}>{L.activity.metHint}</Hint>
      <Button title={L.common.save} onPress={save} />
      {template && (
        <Button
          title={L.activity.deleteTemplate}
          kind="danger"
          onPress={() => {
            deleteWorkoutTemplate(template.id);
            onDone?.();
          }}
        />
      )}
    </View>
  );
}

/** Grid of workout templates; tap logs, long-press edits. */
export function TemplateGrid({ onPick, onEdit }: { onPick: (t: WorkoutTemplate) => void; onEdit?: (t: WorkoutTemplate) => void }) {
  const L = useT();
  const lang = useLang();
  const c = useTheme();
  const templates = useStore((s) => s.workoutTemplates);
  const weight = useStore((s) => s.profile.weightKg);
  return (
    // Stretch: cards in one row share the tallest height, so gaps stay even.
    <View style={{ flexDirection: 'row', flexWrap: 'wrap', alignItems: 'stretch', gap: 8 }}>
      {templates.map((tp) => (
        <Tap
          key={tp.id}
          onPress={() => onPick(tp)}
          onLongPress={onEdit ? () => onEdit(tp) : undefined}
          style={{ borderRadius: 14, padding: 10, backgroundColor: c.cardAlt, flexBasis: '47%', flexGrow: 1, justifyContent: 'space-between' }}
        >
          <T size={14} bold>{workoutName(tp, lang)}</T>
          <T dim size={12}>{L.activity.perHour(metKcal(tp.met, weight, 60))}</T>
        </Tap>
      ))}
    </View>
  );
}
