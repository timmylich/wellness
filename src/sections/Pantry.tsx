/**
 * Pantry ("No Waste"): products at home with purchase and expiry dates, a freshness bar
 * (green → yellow when close → red when expired), how much is left, and composition tags
 * taken from the food database. Optional reminder the day before a product expires.
 */
import { useEffect, useMemo, useState } from 'react';
import { Animated, View } from 'react-native';

import { DateStep } from '../components/DateStep';
import { Sheet } from '../components/Sheet';
import { Slider } from '../components/Slider';
import { Button, Card, Chip, Empty, Field, fmt, H, parseNum, Row, Segmented, T, Tap } from '../components/ui';
import { dur } from '../lib/motion';
import { guessBase, SHELF_BASES, shelfBase } from '../data/shelfLife';
import { useLang, useT } from '../i18n';
import { foodTags } from '../lib/calc';
import { addDays, parseKey, todayKey } from '../lib/date';
import { searchLocal } from '../lib/foodApi';
import { cancelScheduled, scheduleAt } from '../lib/notifications';
import { alpha, useTheme } from '../lib/theme';
import { removePantryItem, savePantryItem, setPantryLeft } from '../store/actions';
import { getState, uid, useStore } from '../store/store';
import type { PantryItem, PantryUnit } from '../store/types';

const DAY_MS = 86_400_000;
const now = () => Date.now();
/** How long a finished (0 %) item stays visible so the user can undo. */
const REMOVE_DELAY_MS = 5000;
/** Slide-out duration at the end of the countdown. */
const FADE_MS = 280;
const omit = <V,>(o: Record<string, V>, k: string) => Object.fromEntries(Object.entries(o).filter(([key]) => key !== k));
/** "Close to expiry": the last quarter of the shelf life, or 2 days, whichever is longer. */
const SOON_FRACTION = 0.25;
const SOON_MIN_DAYS = 2;

export function daysLeft(p: PantryItem, today = todayKey()): number {
  return Math.round((parseKey(p.expiresAt).getTime() - parseKey(today).getTime()) / DAY_MS);
}

export function freshness(p: PantryItem, today = todayKey()): { used: number; state: 'fresh' | 'soon' | 'expired' } {
  const total = Math.max(1, Math.round((parseKey(p.expiresAt).getTime() - parseKey(p.boughtAt).getTime()) / DAY_MS));
  const left = daysLeft(p, today);
  const used = Math.min(1, Math.max(0, 1 - left / total));
  const state = left < 0 ? 'expired' : left <= Math.max(SOON_MIN_DAYS, total * SOON_FRACTION) ? 'soon' : 'fresh';
  return { used, state };
}

/** Reminder at 10:00 the day before expiry, if enabled. */
function scheduleReminder(p: PantryItem, title: string, body: string) {
  const n = getState().settings.notifications;
  cancelScheduled(`pantry-${p.id}`);
  if (!n.enabled || !n.expiry || p.left <= 0) return;
  const at = parseKey(addDays(p.expiresAt, -1));
  at.setHours(10, 0, 0, 0);
  scheduleAt(`pantry-${p.id}`, at.getTime(), title, body);
}

export function Pantry() {
  const c = useTheme();
  const L = useT();
  const items = useStore((s) => s.pantry);
  const [editing, setEditing] = useState<PantryItem | 'new' | null>(null);
  // A finished item (0 %) stays for REMOVE_DELAY_MS with a countdown, so the user can drag it
  // back; then it leaves the list. Value: removal deadline (ms).
  const [zeroed, setZeroed] = useState<Record<string, number>>({});
  const sorted = useMemo(
    () => [...items].filter((p) => p.left > 0 || zeroed[p.id]).sort((a, b) => a.expiresAt.localeCompare(b.expiresAt)),
    [items, zeroed],
  );
  const zero = (id: string) => {
    const deadline = now() + REMOVE_DELAY_MS;
    setZeroed((z) => ({ ...z, [id]: deadline }));
    setTimeout(() => setZeroed((z) => (z[id] === deadline ? omit(z, id) : z)), REMOVE_DELAY_MS);
  };
  const revive = (id: string) => setZeroed((z) => (z[id] ? omit(z, id) : z));

  return (
    <>
      <Card>
        <H icon="pantry" right={<Tap onPress={() => setEditing('new')}><T color={c.accent}>+ {L.pantry.add}</T></Tap>}>
          {L.pantry.title}
        </H>
        {sorted.length ? sorted.map((p) => (
            <PantryRow key={p.id} item={p} removeAt={zeroed[p.id]} onEdit={() => setEditing(p)} onZero={() => zero(p.id)} onRevive={() => revive(p.id)} />
          )) : <Empty text={L.pantry.empty} />}
      </Card>
      <Button title={L.pantry.add} icon="plus" big onPress={() => setEditing('new')} />
      <T dim size={11}>{L.pantry.source}</T>
      <Sheet visible={editing != null} onClose={() => setEditing(null)}>
        {editing != null && <PantryForm item={editing === 'new' ? null : editing} onDone={() => setEditing(null)} />}
      </Sheet>
    </>
  );
}

function PantryRow({
  item,
  removeAt,
  onEdit,
  onZero,
  onRevive,
}: {
  item: PantryItem;
  removeAt?: number;
  onEdit: () => void;
  onZero: () => void;
  onRevive: () => void;
}) {
  const c = useTheme();
  const L = useT();
  const { used, state } = freshness(item);
  const color = state === 'expired' ? c.danger : state === 'soon' ? c.high : c.ok;
  const tags = foodTags(item.per100g);
  // Local copy while dragging; committed on release.
  const [draft, setDraft] = useState<number | null>(null);
  const left = draft ?? item.left;
  // Slide out just before the parent drops a finished item.
  const [fade] = useState(() => new Animated.Value(1));
  useEffect(() => {
    if (!removeAt) {
      fade.setValue(1);
      return;
    }
    const t = setTimeout(() => Animated.timing(fade, { toValue: 0, duration: dur(FADE_MS), useNativeDriver: true }).start(), Math.max(0, removeAt - now() - FADE_MS));
    return () => clearTimeout(t);
  }, [removeAt, fade]);
  return (
    <Animated.View style={{ gap: 6, paddingVertical: 10, borderTopWidth: 0.5, borderColor: c.border, opacity: fade, transform: [{ translateX: fade.interpolate({ inputRange: [0, 1], outputRange: [60, 0] }) }] }}>
      <Tap scaleTo={1} onPress={onEdit} style={{ gap: 4 }}>
        <Row style={{ flexWrap: 'nowrap', justifyContent: 'space-between' }}>
          <T bold style={{ flex: 1 }}>{item.name}</T>
          <T size={13} color={color}>{L.pantry.daysLeft(daysLeft(item))}</T>
        </Row>
        {/* Freshness: how much of the shelf life has passed. */}
        <View style={{ height: 6, borderRadius: 3, backgroundColor: c.cardAlt, overflow: 'hidden' }}>
          <View style={{ width: `${Math.max(4, used * 100)}%`, height: 6, borderRadius: 3, backgroundColor: color }} />
        </View>
        {tags.length ? (
          <Row gap={4}>
            {tags.map((t) => (
              <View key={t} style={{ backgroundColor: alpha(c.accent, 0.12), borderRadius: 8, paddingHorizontal: 6, paddingVertical: 2 }}>
                <T size={11} color={c.accent}>{L.pantry.tags[t]}</T>
              </View>
            ))}
          </Row>
        ) : null}
      </Tap>
      <Row style={{ flexWrap: 'nowrap' }}>
        <T dim size={12} style={{ width: 92 }}>
          {L.pantry.left}: {Math.round(left * 100)}%{item.amountG ? ` · ${fmt(left * item.amountG)} ${L.pantry.units[item.unit ?? 'g']}` : ''}
        </T>
        <View style={{ flex: 1 }}>
          <Slider
            value={left}
            min={0}
            max={1}
            step={0.01}
            haptics={false}
            onChange={setDraft}
            onComplete={(v) => {
              setDraft(null);
              setPantryLeft(item.id, v);
              if (v <= 0) {
                onZero();
                cancelScheduled(`pantry-${item.id}`);
              } else onRevive();
            }}
          />
        </View>
      </Row>
      {removeAt && item.left <= 0 ? <Countdown until={removeAt} /> : null}
    </Animated.View>
  );
}

/** "Removed in N s" under a finished item. */
function Countdown({ until }: { until: number }) {
  const c = useTheme();
  const L = useT();
  const [left, setLeft] = useState(() => Math.ceil((until - now()) / 1000));
  useEffect(() => {
    const t = setInterval(() => setLeft(Math.max(0, Math.ceil((until - now()) / 1000))), 250);
    return () => clearInterval(t);
  }, [until]);
  return <T size={12} color={c.danger}>{L.pantry.removeIn(left)}</T>;
}

export function PantryForm({ item, onDone }: { item: PantryItem | null; onDone: () => void }) {
  const L = useT();
  const lang = useLang();
  const userFoods = useStore((s) => s.foods);
  const [name, setName] = useState(item?.name ?? '');
  const [baseId, setBaseId] = useState<string | undefined>(item?.baseId);
  const [per100g, setPer100g] = useState(item?.per100g);
  const [bought, setBought] = useState(item?.boughtAt ?? todayKey());
  const [expires, setExpires] = useState<string | null>(item?.expiresAt ?? null);
  const [amount, setAmount] = useState(item?.amountG ? String(item.amountG) : '');
  const [unit, setUnit] = useState<PantryUnit>(item?.unit ?? 'g');
  const [showDb, setShowDb] = useState(false);

  const base = shelfBase(baseId) ?? guessBase(name) ?? shelfBase('other')!;
  // Until the user sets a date, it follows the product type.
  const expiry = expires ?? addDays(bought, base.days);
  const matches = useMemo(() => (showDb && name.trim().length >= 2 ? searchLocal(name.trim(), userFoods, lang).slice(0, 5) : []), [showDb, name, userFoods, lang]);

  const save = () => {
    if (!name.trim()) return;
    const p: PantryItem = {
      id: item?.id ?? uid(),
      name: name.trim(),
      baseId: base.id,
      per100g,
      boughtAt: bought,
      expiresAt: expiry,
      amountG: parseNum(amount),
      unit,
      left: item?.left ?? 1,
      createdAt: item?.createdAt ?? now(),
    };
    savePantryItem(p);
    scheduleReminder(p, L.pantry.notifTitle, L.pantry.notifBody(p.name));
    onDone();
  };

  return (
    <>
      <T size={18} bold>{item ? L.pantry.edit : L.pantry.add}</T>
      <Field
        label={L.pantry.name}
        value={name}
        onChange={(v) => {
          setName(v);
          setShowDb(true);
        }}
        numeric={false}
        placeholder={L.pantry.namePh}
        search
      />
      {matches.length ? (
        <View style={{ gap: 2 }}>
          <T dim size={12}>{L.pantry.fromDb}</T>
          {matches.map((f) => (
            <Tap
              key={f.id}
              scaleTo={1}
              onPress={() => {
                setName(f.name);
                setPer100g(f.per100g);
                setShowDb(false);
              }}
              style={{ paddingVertical: 6 }}
            >
              <T size={14}>{f.name}</T>
            </Tap>
          ))}
        </View>
      ) : null}
      <T dim size={13}>{L.pantry.base}</T>
      <Row gap={6}>
        {SHELF_BASES.map((b) => (
          <Chip
            key={b.id}
            label={lang === 'ru' ? b.ru : b.en}
            active={base.id === b.id}
            onPress={() => {
              setBaseId(b.id);
              // Empty name: use the product type as the name.
              if (!name.trim()) setName(lang === 'ru' ? b.ru : b.en);
              setExpires(null);
            }}
          />
        ))}
      </Row>
      <T dim size={12}>{L.pantry.baseHint(base.days)}</T>
      <DateStep label={L.pantry.bought} value={bought} onChange={setBought} />
      <DateStep label={L.pantry.expires} value={expiry} onChange={setExpires} />
      <Row style={{ flexWrap: 'nowrap', alignItems: 'flex-end' }}>
        <View style={{ flex: 1 }}>
          <Field label={L.pantry.amount} value={amount} onChange={setAmount} suffix={L.pantry.units[unit]} />
        </View>
        <Segmented value={unit} onChange={setUnit} options={(['g', 'ml', 'pcs'] as const).map((u) => ({ value: u, label: L.pantry.units[u] }))} />
      </Row>
      <Row style={{ flexWrap: 'nowrap' }}>
        {item && (
          <Button
            title={L.pantry.remove}
            kind="danger"
            style={{ flex: 1 }}
            onPress={() => {
              cancelScheduled(`pantry-${item.id}`);
              removePantryItem(item.id);
              onDone();
            }}
          />
        )}
        <Button title={L.pantry.save} onPress={save} disabled={!name.trim()} style={{ flex: 2 }} />
      </Row>
    </>
  );
}
