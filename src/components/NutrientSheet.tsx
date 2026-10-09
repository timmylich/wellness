/**
 * Details for one nutrient scale: the gauge, today's value vs. the range, explanation,
 * recommendations and the foods that contributed most to it today.
 */
import { View } from 'react-native';

import { useT } from '../i18n';
import { entryNutrients, rangeStatus, type Range } from '../lib/calc';
import { toneColor, useTheme } from '../lib/theme';
import type { FoodEntry, NutrientKey } from '../store/types';
import { RangeBar } from './charts';
import { Sheet } from './Sheet';
import { Divider, fmt, Row, Stat, T } from './ui';

export function nutrientUnit(k: NutrientKey, units: { kcal: string; g: string; mg: string }) {
  return k === 'kcal' ? units.kcal : k === 'sodium' || k === 'potassium' || k === 'cholesterol' ? units.mg : units.g;
}

export function NutrientSheet({
  nutrient,
  value,
  range,
  entries,
  onClose,
}: {
  nutrient: NutrientKey | null;
  value: number;
  range: Range | null;
  entries: FoodEntry[];
  onClose: () => void;
}) {
  const c = useTheme();
  const L = useT();
  const k = nutrient;
  const visible = k != null && range != null;

  // Top contributors today, by absolute amount of this nutrient.
  const top =
    k == null
      ? []
      : entries
          .map((e) => ({ e, v: entryNutrients(e)[k] }))
          .filter((x) => x.v > 0)
          .sort((a, b) => b.v - a.v)
          .slice(0, 6);

  return (
    <Sheet visible={visible} onClose={onClose}>
      {visible && k && range ? (
        <>
          {(() => {
            const u = nutrientUnit(k, L.units);
            const status = rangeStatus(value, range);
            const color = toneColor(c, status === 'high' ? 'danger' : status === 'low' ? 'low' : 'ok');
            const digits = value < 10 && value % 1 ? 1 : 0;
            return (
              <>
                <T size={20} bold>{L.nutrients[k].label}</T>
                <Row style={{ alignItems: 'flex-end' }}>
                  <T size={34} bold color={color}>{fmt(value, digits)}</T>
                  <T dim size={15} style={{ marginBottom: 6 }}>/ {fmt(range.target)} {u}</T>
                </Row>
                <RangeBar value={value} range={range} height={16} />
                <Row>
                  {!range.lowerIsBetter && <Stat label={L.common.min} value={fmt(range.min)} unit={u} color={c.low} />}
                  <Stat label={L.common.target} value={fmt(range.target)} unit={u} color={c.ok} />
                  <Stat label={L.common.max} value={fmt(range.max)} unit={u} color={c.danger} />
                </Row>
                {range.target > 0 ? <T dim size={13}>{L.nutrition.sheet.ofTarget((value / range.target) * 100)}</T> : null}
                <Divider />
                <T bold>{L.nutrition.sheet.about}</T>
                <T size={14}>{L.nutrients[k].hint}</T>
                <T bold>{L.nutrition.sheet.recs}</T>
                <T size={14}>{L.nutrients[k].rec}</T>
                <Divider />
                <T bold>{L.nutrition.sheet.top}</T>
                {top.length ? (
                  top.map(({ e, v }) => (
                    <View key={e.id} style={{ gap: 4 }}>
                      <Row style={{ justifyContent: 'space-between', flexWrap: 'nowrap' }}>
                        <T style={{ flex: 1 }} numberOfLines={1}>{e.food.name}</T>
                        <T bold>{fmt(v, v < 10 ? 1 : 0)} {u}</T>
                      </Row>
                      <View style={{ height: 6, borderRadius: 3, backgroundColor: c.cardAlt, overflow: 'hidden' }}>
                        <View style={{ width: `${Math.min(100, (v / Math.max(value, 0.0001)) * 100)}%`, height: 6, borderRadius: 3, backgroundColor: c.accent }} />
                      </View>
                      <T dim size={12}>{fmt(e.grams)} {L.units.g} · {L.nutrition.sheet.share((v / Math.max(value, 0.0001)) * 100)}</T>
                    </View>
                  ))
                ) : (
                  <T dim>{L.nutrition.sheet.noFoods}</T>
                )}
              </>
            );
          })()}
        </>
      ) : null}
    </Sheet>
  );
}
