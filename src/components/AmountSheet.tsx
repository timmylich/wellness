/**
 * Bottom sheet for choosing the amount of a food — used both when adding a new entry and
 * when editing an existing one. Shows a live nutrient preview.
 */
import { useState } from 'react';
import { View } from 'react-native';

import { useT } from '../i18n';
import { entryNutrients } from '../lib/calc';
import { useTheme } from '../lib/theme';
import type { Food, MealType } from '../store/types';
import { Sheet } from './Sheet';
import { Icon } from './Icon';
import { Button, Chip, Field, parseNum, Row, T, Tap } from './ui';

const stepBtn = { width: 48, height: 46, borderRadius: 14, alignItems: 'center', justifyContent: 'center' } as const;

/**
 * Meal is no longer chosen in the UI; it is inferred from the time of day and kept in the
 * data model for backups and possible future use.
 */
export function defaultMeal(now = new Date()): MealType {
  const h = now.getHours();
  if (h < 11) return 'breakfast';
  if (h < 16) return 'lunch';
  if (h < 21) return 'dinner';
  return 'snack';
}

type Props = {
  food: Food | null;
  initialGrams?: number;
  submitLabel?: string;
  onSubmit: (grams: number) => void;
  onClose: () => void;
  onDelete?: () => void;
};

export function AmountSheet(props: Props) {
  if (!props.food) return null;
  // Re-mount per food so the form state is initialised fresh each time.
  return <Inner key={`${props.food.id}:${props.initialGrams ?? ''}`} {...props} food={props.food} />;
}

function Inner({ food, initialGrams, submitLabel, onSubmit, onClose, onDelete }: Props & { food: Food }) {
  const c = useTheme();
  const L = useT();
  const [grams, setGrams] = useState(String(initialGrams ?? food.servingG ?? 100));
  const [stepStr, setStepStr] = useState('10');
  const g = parseNum(grams) ?? 0;
  const step = parseNum(stepStr) ?? 0;
  const bump = (sign: 1 | -1) => setGrams(String(Math.max(0, Math.round((g + sign * step) * 10) / 10)));
  const n = entryNutrients({ food, grams: g });
  const quick = [...new Set([50, 100, 150, 200, 250, food.servingG].filter((x): x is number => !!x))].sort((a, b) => a - b);

  return (
    <Sheet visible onClose={onClose}>
      <T size={18} bold>{food.name}</T>
      {food.brand ? <T dim size={13}>{food.brand}</T> : null}
      <Field label={L.food.amount} value={grams} onChange={setGrams} suffix={L.units.g} />
      {/* Adjust by a custom step, e.g. "ate 30 g more". */}
      <Row style={{ flexWrap: 'nowrap', alignItems: 'flex-end' }}>
        <Tap onPress={() => bump(-1)} disabled={step <= 0 || g <= 0} style={[stepBtn, { backgroundColor: c.accentSoft }]}>
          <Icon name="minus" size={22} color={c.accent} />
        </Tap>
        <View style={{ flex: 1 }}>
          <Field label={L.food.step} value={stepStr} onChange={setStepStr} suffix={L.units.g} />
        </View>
        <Tap onPress={() => bump(1)} disabled={step <= 0} style={[stepBtn, { backgroundColor: c.accentSoft }]}>
          <Icon name="plus" size={22} color={c.accent} />
        </Tap>
      </Row>
      <Row>
        {quick.map((q) => (
          <Chip key={q} label={q === food.servingG ? L.food.portion(q) : `${q} ${L.units.g}`} active={g === q} onPress={() => setGrams(String(q))} />
        ))}
      </Row>
      <View style={{ backgroundColor: c.cardAlt, borderRadius: 12, padding: 12 }}>
        <T size={13}>{L.food.preview(n.kcal, n.protein, n.fat, n.carbs, n.sugar, (n.sodium * 2.5) / 1000)}</T>
      </View>
      <Row style={{ flexWrap: 'nowrap' }}>
        {onDelete && <Button title={L.common.delete} kind="danger" onPress={onDelete} style={{ flex: 1 }} />}
        <Button title={submitLabel ?? L.common.add} onPress={() => g > 0 && onSubmit(g)} disabled={g <= 0} style={{ flex: 2 }} />
      </Row>
    </Sheet>
  );
}
