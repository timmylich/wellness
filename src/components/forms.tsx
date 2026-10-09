/**
 * Data-entry forms shared by section screens and the universal "+" quick-add sheet.
 * Each form calls `onDone` after saving so the host can close itself.
 */
import * as Haptics from 'expo-haptics';
import { useState } from 'react';
import { Alert, View } from 'react-native';

import { useLang, useT } from '../i18n';
import { bpCategory, metKcal, navyBodyFat } from '../lib/calc';
import { todayKey } from '../lib/date';
import { animateLayout } from '../lib/motion';
import { alpha, toneColor, useTheme } from '../lib/theme';
import { workoutName } from '../data/workouts';
import { addBody, addHeart, addWater, addWorkout, deleteWorkoutTemplate, saveWorkoutTemplate, setSteps, updateSettings } from '../store/actions';
import { uid, useStore } from '../store/store';
import type { FluidKind, WorkoutTemplate } from '../store/types';
import { Icon } from './Icon';
import { Button, Chip, Field, fmt, Hint, parseNum, Row, Segmented, T, Tap } from './ui';


export function WaterForm({ date = todayKey(), onDone }: { date?: string; onDone?: () => void }) {
  const L = useT();
  const c = useTheme();
  const presets = useStore((s) => s.settings.waterPresets);
  const current = useStore((s) => s.settings.waterAmountMl);
  const [amount, setAmount] = useState(String(current));
  const [kind, setKind] = useState<FluidKind>('water');
  const ml = parseNum(amount) ?? 0;
  const color = kind === 'water' ? c.water : c.high;
  const choose = (v: number) => {
    setAmount(String(v));
    updateSettings({ waterAmountMl: v });
  };
  return (
    <View style={{ gap: 12, alignSelf: 'stretch' }}>
      <Segmented
        value={kind}
        onChange={setKind}
        options={[
          { value: 'water', label: L.water.kinds.water },
          { value: 'other', label: L.water.kinds.other },
        ]}
      />
      {kind === 'other' ? <T dim size={12}>{L.water.otherHint}</T> : null}
      <Tap
        onPress={() => {
          if (ml <= 0) return;
          animateLayout();
          addWater(ml, date, kind);
          Haptics.impactAsync(Haptics.ImpactFeedbackStyle.Medium).catch(() => {});
          onDone?.();
        }}
        accessibilityLabel={L.water.addLabel(ml)}
        style={{ borderRadius: 24, paddingVertical: 22, alignItems: 'center', backgroundColor: color }}
      >
        <Row gap={8} style={{ flexWrap: 'nowrap' }}>
          <Icon name={kind === 'water' ? 'water' : 'drink'} size={24} color="#fff" />
          <T size={26} bold color="#fff">+ {fmt(ml)} {L.units.ml}</T>
        </Row>
        <T size={13} color="#ffffffcc">{L.water.tapToAdd}</T>
      </Tap>
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

export function HeartForm({ onDone }: { onDone?: () => void }) {
  const L = useT();
  const c = useTheme();
  const [sys, setSys] = useState('');
  const [dia, setDia] = useState('');
  const [pulse, setPulse] = useState('');
  const [note, setNote] = useState('');
  const s = parseNum(sys), d = parseNum(dia), p = parseNum(pulse);
  const preview = s && d ? bpCategory(s, d) : null;
  const save = () => {
    if (!(s && d) && !p) return;
    animateLayout();
    addHeart({ systolic: s, diastolic: d, pulse: p, note: note.trim() || undefined });
    setSys(''); setDia(''); setPulse(''); setNote('');
    onDone?.();
  };
  return (
    <View style={{ gap: 12 }}>
      <Row>
        <Field label={L.heart.systolic} value={sys} onChange={setSys} suffix={L.units.mmHg} />
        <Field label={L.heart.diastolic} value={dia} onChange={setDia} suffix={L.units.mmHg} />
        <Field label={L.heart.pulse} value={pulse} onChange={setPulse} suffix={L.units.bpm} />
      </Row>
      <Field label={L.heart.note} value={note} onChange={setNote} numeric={false} />
      {preview && (
        <View style={{ backgroundColor: alpha(toneColor(c, preview.tone), 0.15), borderRadius: 12, padding: 12, gap: 4 }}>
          <T bold color={toneColor(c, preview.tone)}>{L.heart.bp[preview.key].label}</T>
          <T size={13}>{L.heart.bp[preview.key].advice}</T>
        </View>
      )}
      <Button title={L.common.save} onPress={save} disabled={!(s && d) && !p} />
      <Hint title={L.heart.howTitle}>{L.heart.how}</Hint>
    </View>
  );
}

export function BodyForm({ onDone }: { onDone?: () => void }) {
  const L = useT();
  const profile = useStore((s) => s.profile);
  const [f, setF] = useState({ weight: '', fat: '', waist: '', neck: '', hip: '', height: '' });
  const set = (k: keyof typeof f) => (v: string) => setF({ ...f, [k]: v });
  const waist = parseNum(f.waist), neck = parseNum(f.neck), hip = parseNum(f.hip);
  const navy = waist && neck ? navyBodyFat(profile.sex, profile.heightCm, waist, neck, hip) : null;
  const save = () => {
    const entry = {
      weightKg: parseNum(f.weight), bodyFatPct: parseNum(f.fat), waistCm: waist, neckCm: neck, hipCm: hip, heightCm: parseNum(f.height),
    };
    if (Object.values(entry).every((v) => v == null)) return;
    animateLayout();
    addBody(entry);
    setF({ weight: '', fat: '', waist: '', neck: '', hip: '', height: '' });
    onDone?.();
  };
  return (
    <View style={{ gap: 12 }}>
      <T dim size={13}>{L.body.measureNote}</T>
      <Row>
        <Field label={L.body.weight} value={f.weight} onChange={set('weight')} suffix={L.units.kg} />
        <Field label={L.body.fatInput} value={f.fat} onChange={set('fat')} suffix="%" />
      </Row>
      <Row>
        <Field label={L.body.waist} value={f.waist} onChange={set('waist')} suffix={L.units.cm} />
        <Field label={L.body.neck} value={f.neck} onChange={set('neck')} suffix={L.units.cm} />
      </Row>
      <Row>
        {profile.sex === 'female' && <Field label={L.body.hip} value={f.hip} onChange={set('hip')} suffix={L.units.cm} />}
        <Field label={L.body.heightChanged} value={f.height} onChange={set('height')} suffix={L.units.cm} />
      </Row>
      {navy && parseNum(f.fat) == null ? <T dim size={13}>{L.body.navy(navy)}</T> : null}
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
    if (!title || k == null) return Alert.alert(L.activity.needNameKcal);
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
    if (!name.trim() || !m) return Alert.alert(L.activity.needNameMet);
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
    <Row>
      {templates.map((tp) => (
        <Tap
          key={tp.id}
          onPress={() => onPick(tp)}
          onLongPress={onEdit ? () => onEdit(tp) : undefined}
          style={{ borderRadius: 14, padding: 10, backgroundColor: c.cardAlt, flexBasis: '47%', flexGrow: 1 }}
        >
          <T size={14} bold>{workoutName(tp, lang)}</T>
          <T dim size={12}>{L.activity.perHour(metKcal(tp.met, weight, 60))}</T>
        </Tap>
      ))}
    </Row>
  );
}
