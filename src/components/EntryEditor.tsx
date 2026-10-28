/**
 * Edit sheets for logged entries (water, workouts, heart, body): every value plus the date and
 * time. Opened by tapping a row in a section's log.
 */
import { useState } from 'react';
import { View } from 'react-native';

import { useT } from '../i18n';
import { atTime } from '../lib/date';
import {
  removeBody,
  removeHeart,
  removeWater,
  removeWorkout,
  updateBody,
  updateHeart,
  updateWater,
  updateWorkout,
} from '../store/actions';
import type { BodyEntry, FluidKind, HeartEntry, WaterEntry, WorkoutEntry } from '../store/types';
import { DateStep } from './DateStep';
import { confirmDialog, showDialog } from './Dialog';
import { Sheet } from './Sheet';
import { Button, Field, parseNum, Row, Segmented, T } from './ui';
import { range, Wheel } from './Wheel';

const HOURS = range(0, 23);
const MINUTES = range(0, 59);
const two = (v: number) => String(v).padStart(2, '0');
const str = (v: number | undefined) => (v == null ? '' : String(v));

/**
 * Sheet state that keeps the last entry while the sheet slides out, so its content does not
 * vanish mid-animation.
 */
export function useEntrySheet<E extends { id: string }>() {
  const [entry, setEntry] = useState<E | null>(null);
  const [open, setOpen] = useState(false);
  // Bumped on every open, so the form starts from the entry's current values each time.
  const [n, setN] = useState(0);
  return {
    entry,
    open,
    key: n,
    edit: (e: E) => {
      setEntry(e);
      setN((x) => x + 1);
      setOpen(true);
    },
    close: () => setOpen(false),
  };
}

/**
 * Date (− / + days) and, unless `dateOnly`, time (hour and minute wheels) of an entry.
 * Date-only entries keep their original time of day.
 */
function useWhen(time: number, date: string, dateOnly = false) {
  const d = new Date(time);
  const [day, setDay] = useState(date);
  const [h, setH] = useState(d.getHours());
  const [m, setM] = useState(d.getMinutes());
  const L = useT();
  const fields = (
    <View style={{ gap: 8 }}>
      <DateStep label={L.common.date} value={day} onChange={setDay} />
      {!dateOnly && (
        <>
          <T dim size={13}>
            {L.common.time}
          </T>
          <Row style={{ flexWrap: 'nowrap', justifyContent: 'center' }}>
            <Wheel values={HOURS} value={h} onChange={setH} width={72} format={two} />
            <T size={26} bold>
              :
            </T>
            <Wheel values={MINUTES} value={m} onChange={setM} width={72} format={two} />
          </Row>
        </>
      )}
    </View>
  );
  return { fields, value: { date: day, time: atTime(day, h, m) } };
}

/** Title, the form, then Save and Delete. */
function Frame({
  children,
  onSave,
  onDelete,
  title,
}: {
  children: React.ReactNode;
  onSave: () => void;
  onDelete: () => void;
  title: string;
}) {
  const L = useT();
  const del = () =>
    confirmDialog({
      title: L.common.deleteEntry,
      message: title,
      cancel: L.common.cancel,
      ok: L.common.delete,
      onOk: onDelete,
    });
  return (
    <View style={{ gap: 12 }}>
      <T size={18} bold>
        {L.common.editEntry}
      </T>
      {children}
      <Button title={L.common.save} onPress={onSave} />
      <Button title={L.common.delete} kind="danger" icon="trash" onPress={del} />
    </View>
  );
}

function WaterEdit({ entry, onDone }: { entry: WaterEntry; onDone: () => void }) {
  const L = useT();
  const [ml, setMl] = useState(str(entry.ml));
  const [kind, setKind] = useState<FluidKind>(entry.kind ?? 'water');
  const when = useWhen(entry.time, entry.date);
  const save = () => {
    const v = parseNum(ml);
    if (!v || v <= 0) return;
    updateWater(entry.id, { ml: Math.round(v), kind, ...when.value });
    onDone();
  };
  return (
    <Frame title={`${entry.ml} ${L.units.ml}`} onSave={save} onDelete={() => (removeWater(entry.id), onDone())}>
      <Field label={L.common.amount} value={ml} onChange={setMl} suffix={L.units.ml} />
      <Segmented
        value={kind}
        options={[
          { value: 'water', label: L.water.kinds.water },
          { value: 'other', label: L.water.kinds.other },
        ]}
        onChange={setKind}
      />
      {when.fields}
    </Frame>
  );
}

function WorkoutEdit({ entry, onDone }: { entry: WorkoutEntry; onDone: () => void }) {
  const L = useT();
  const [name, setName] = useState(entry.name);
  const [minutes, setMinutes] = useState(str(entry.minutes));
  const [kcal, setKcal] = useState(str(entry.kcal));
  const [note, setNote] = useState(entry.note ?? '');
  const when = useWhen(entry.time, entry.date);
  const save = () => {
    const k = parseNum(kcal);
    if (!name.trim() || k == null) return showDialog({ title: L.activity.needNameKcal, icon: 'info' });
    updateWorkout(entry.id, {
      name: name.trim(),
      minutes: parseNum(minutes) ?? 0,
      kcal: Math.round(k),
      note: note.trim() || undefined,
      ...when.value,
    });
    onDone();
  };
  return (
    <Frame title={entry.name} onSave={save} onDelete={() => (removeWorkout(entry.id), onDone())}>
      <Field label={L.activity.name} value={name} onChange={setName} numeric={false} />
      <Row>
        <Field label={L.activity.duration} value={minutes} onChange={setMinutes} suffix={L.units.min} />
        <Field label={L.activity.calories} value={kcal} onChange={setKcal} suffix={L.units.kcal} />
      </Row>
      <Field label={L.activity.note} value={note} onChange={setNote} numeric={false} />
      {when.fields}
    </Frame>
  );
}

function HeartEdit({ entry, onDone }: { entry: HeartEntry; onDone: () => void }) {
  const L = useT();
  const [sys, setSys] = useState(str(entry.systolic));
  const [dia, setDia] = useState(str(entry.diastolic));
  const [pulse, setPulse] = useState(str(entry.pulse));
  const [note, setNote] = useState(entry.note ?? '');
  const when = useWhen(entry.time, entry.date, true);
  const save = () => {
    const s = parseNum(sys),
      d = parseNum(dia),
      p = parseNum(pulse);
    // Pressure needs both numbers; a reading needs pressure or pulse.
    if ((s == null) !== (d == null) || (s == null && p == null)) return;
    updateHeart(entry.id, {
      systolic: s == null ? undefined : Math.round(s),
      diastolic: d == null ? undefined : Math.round(d),
      pulse: p == null ? undefined : Math.round(p),
      note: note.trim() || undefined,
      ...when.value,
    });
    onDone();
  };
  const title = [
    entry.systolic && entry.diastolic ? `${entry.systolic}/${entry.diastolic}` : null,
    entry.pulse ? `♥ ${entry.pulse}` : null,
  ]
    .filter(Boolean)
    .join('   ');
  return (
    <Frame title={title} onSave={save} onDelete={() => (removeHeart(entry.id), onDone())}>
      <Row>
        <Field label={L.heart.systolic} value={sys} onChange={setSys} />
        <Field label={L.heart.diastolic} value={dia} onChange={setDia} />
      </Row>
      <Field label={L.heart.pulse} value={pulse} onChange={setPulse} />
      <Field label={L.heart.note} value={note} onChange={setNote} numeric={false} />
      {when.fields}
    </Frame>
  );
}

function BodyEdit({ entry, onDone }: { entry: BodyEntry; onDone: () => void }) {
  const L = useT();
  const [f, setF] = useState({
    weight: str(entry.weightKg),
    fat: str(entry.bodyFatPct),
    waist: str(entry.waistCm),
    neck: str(entry.neckCm),
    hip: str(entry.hipCm),
    height: str(entry.heightCm),
  });
  const set = (k: keyof typeof f) => (v: string) => setF({ ...f, [k]: v });
  const when = useWhen(entry.time, entry.date, true);
  const save = () => {
    const values = {
      weightKg: parseNum(f.weight),
      bodyFatPct: parseNum(f.fat),
      waistCm: parseNum(f.waist),
      neckCm: parseNum(f.neck),
      hipCm: parseNum(f.hip),
      heightCm: parseNum(f.height),
    };
    if (Object.values(values).every((v) => v == null)) return;
    updateBody(entry.id, { ...values, ...when.value });
    onDone();
  };
  return (
    <Frame title={L.body.weight} onSave={save} onDelete={() => (removeBody(entry.id), onDone())}>
      <Row>
        <Field label={L.body.weight} value={f.weight} onChange={set('weight')} suffix={L.units.kg} />
        <Field label={L.body.fat} value={f.fat} onChange={set('fat')} suffix="%" />
      </Row>
      <Row>
        <Field label={L.body.waist} value={f.waist} onChange={set('waist')} suffix={L.units.cm} />
        <Field label={L.body.neck} value={f.neck} onChange={set('neck')} suffix={L.units.cm} />
      </Row>
      <Row>
        <Field label={L.body.hip} value={f.hip} onChange={set('hip')} suffix={L.units.cm} />
        <Field label={L.body.height} value={f.height} onChange={set('height')} suffix={L.units.cm} />
      </Row>
      {when.fields}
    </Frame>
  );
}

type EditState<E> = { entry: E | null; open: boolean; key: number; close: () => void };

/** One sheet per section; the form remounts for every opening. */
export function WaterEditSheet({ sheet }: { sheet: EditState<WaterEntry> }) {
  return (
    <Sheet visible={sheet.open} onClose={sheet.close}>
      {sheet.entry && <WaterEdit key={sheet.key} entry={sheet.entry} onDone={sheet.close} />}
    </Sheet>
  );
}

export function WorkoutEditSheet({ sheet }: { sheet: EditState<WorkoutEntry> }) {
  return (
    <Sheet visible={sheet.open} onClose={sheet.close}>
      {sheet.entry && <WorkoutEdit key={sheet.key} entry={sheet.entry} onDone={sheet.close} />}
    </Sheet>
  );
}

export function HeartEditSheet({ sheet }: { sheet: EditState<HeartEntry> }) {
  return (
    <Sheet visible={sheet.open} onClose={sheet.close}>
      {sheet.entry && <HeartEdit key={sheet.key} entry={sheet.entry} onDone={sheet.close} />}
    </Sheet>
  );
}

export function BodyEditSheet({ sheet }: { sheet: EditState<BodyEntry> }) {
  return (
    <Sheet visible={sheet.open} onClose={sheet.close}>
      {sheet.entry && <BodyEdit key={sheet.key} entry={sheet.entry} onDone={sheet.close} />}
    </Sheet>
  );
}
