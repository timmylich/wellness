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

/** Standard portion buttons, grams. */
const PORTIONS = [50, 100, 200, 300, 450];
/** Step for the grams-per-piece buttons. */
const PIECE_G_STEP = 10;

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
  initialPieces?: number;
  submitLabel?: string;
  onSubmit: (grams: number, pieces: number) => void;
  onClose: () => void;
  onDelete?: () => void;
  /** Opens the full editor for this entry's food data. */
  onEditData?: () => void;
};

export function AmountSheet(props: Props) {
  // The sheet stays mounted so it can animate out after the caller clears `food`
  // (Sheet keeps showing the last content while closing).
  const { food } = props;
  return (
    <Sheet visible={!!food} onClose={props.onClose}>
      {/* Re-mount per food so the form state is initialised fresh each time. */}
      {food ? <Inner key={`${food.id}:${props.initialGrams ?? ''}`} {...props} food={food} /> : null}
    </Sheet>
  );
}

function Inner({ food, initialGrams, initialPieces, submitLabel, onSubmit, onClose, onDelete, onEditData }: Props & { food: Food }) {
  const c = useTheme();
  const L = useT();
  // Amount = pieces × grams per piece. One piece by default; editing an entry starts from its weight.
  const startPieces = Math.max(1, initialPieces ?? 1);
  const [pieces, setPieces] = useState(startPieces);
  const [perPiece, setPerPiece] = useState(String(initialGrams != null ? Math.round((initialGrams / startPieces) * 10) / 10 : (food.servingG ?? 100)));
  const pp = parseNum(perPiece) ?? 0;
  const g = Math.round(pieces * pp * 10) / 10;
  const n = entryNutrients({ food, grams: g });
  const quick = [...new Set([...PORTIONS, food.servingG].filter((x): x is number => !!x))].sort((a, b) => a - b);

  return (
    <>
      <Row style={{ flexWrap: 'nowrap', alignItems: 'flex-start' }}>
        <View style={{ flex: 1 }}>
          <T size={18} bold>{food.name}</T>
          {food.brand ? <T dim size={13}>{food.brand}</T> : null}
        </View>
        {onEditData ? (
          <Tap onPress={onEditData} accessibilityLabel={L.food.editData} style={{ flexDirection: 'row', alignItems: 'center', gap: 4, padding: 6, borderRadius: 12, backgroundColor: c.accentSoft }}>
            <Icon name="edit" size={16} color={c.accent} />
            <T size={13} color={c.accent}>{L.food.editData}</T>
          </Tap>
        ) : null}
      </Row>
      {!food.per100g.sodium ? <T size={12} color={c.high}>{L.food.noSalt}</T> : null}
      {/* Pieces: ±1. */}
      <Row style={{ flexWrap: 'nowrap', alignItems: 'flex-end' }}>
        <Tap onPress={() => setPieces(Math.max(1, pieces - 1))} disabled={pieces <= 1} style={[stepBtn, { backgroundColor: c.accentSoft }]}>
          <Icon name="minus" size={22} color={c.accent} />
        </Tap>
        <View style={{ flex: 1, alignItems: 'center' }}>
          <T dim size={12}>{L.food.pieces}</T>
          <T size={22} bold>{String(pieces)}</T>
        </View>
        <Tap onPress={() => setPieces(pieces + 1)} style={[stepBtn, { backgroundColor: c.accentSoft }]}>
          <Icon name="plus" size={22} color={c.accent} />
        </Tap>
      </Row>
      {/* Grams per piece: ±10 g, or type it. */}
      <Row style={{ flexWrap: 'nowrap', alignItems: 'flex-end' }}>
        <Tap onPress={() => setPerPiece(String(Math.max(PIECE_G_STEP, pp - PIECE_G_STEP)))} disabled={pp <= PIECE_G_STEP} style={[stepBtn, { backgroundColor: c.accentSoft }]}>
          <Icon name="minus" size={22} color={c.accent} />
        </Tap>
        <View style={{ flex: 1 }}>
          <Field label={L.food.perPiece} value={perPiece} onChange={setPerPiece} suffix={L.units.g} />
        </View>
        <Tap onPress={() => setPerPiece(String(pp + PIECE_G_STEP))} style={[stepBtn, { backgroundColor: c.accentSoft }]}>
          <Icon name="plus" size={22} color={c.accent} />
        </Tap>
      </Row>
      <Row>
        {quick.map((q) => (
          <Chip key={q} label={q === food.servingG ? L.food.portion(q) : `${q} ${L.units.g}`} active={pp === q} onPress={() => setPerPiece(String(q))} />
        ))}
      </Row>
      <View style={{ backgroundColor: c.cardAlt, borderRadius: 12, padding: 12 }}>
        <T size={13} bold>{L.food.total(g)}</T>
        <T size={13}>{L.food.preview(n.kcal, n.protein, n.fat, n.carbs, n.sugar, (n.sodium * 2.5) / 1000)}</T>
      </View>
      <Row style={{ flexWrap: 'nowrap' }}>
        {onDelete && <Button title={L.common.delete} kind="danger" onPress={onDelete} style={{ flex: 1 }} />}
        <Button title={submitLabel ?? L.common.add} onPress={() => g > 0 && onSubmit(g, pieces)} disabled={g <= 0} style={{ flex: 2 }} />
      </Row>
    </>
  );
}
