/**
 * Water tracker. Plain water fills the daily target; other drinks are logged separately and
 * only count towards the danger limit (total fluid).
 */
import { useMemo, useState } from 'react';
import { View } from 'react-native';

import { BarChart, Ring } from '../components/charts';
import { DateNav } from '../components/DateNav';
import { useEntrySheet, WaterEditSheet } from '../components/EntryEditor';
import { WaterForm } from '../components/forms';
import { Icon } from '../components/Icon';
import { Card, Empty, fmt, H, Hint, ListItem, Row, Segmented, Stat, T } from '../components/ui';
import { useT } from '../i18n';
import { lastDays, shortLabel, timeLabel, todayKey } from '../lib/date';
import { useWaterDay } from '../lib/hooks';
import { alpha, useTheme } from '../lib/theme';
import { removeWater } from '../store/actions';
import type { WaterEntry } from '../store/types';
import { useStore } from '../store/store';

export function Water() {
  const c = useTheme();
  const L = useT();
  const [date, setDate] = useState(todayKey());
  const editor = useEntrySheet<WaterEntry>();
  const allWater = useStore((s) => s.water);
  const [period, setPeriod] = useState<7 | 30>(7);
  const { entries, total, other, fluid, range } = useWaterDay(date);

  // Target progress uses plain water; the danger state uses all fluid.
  const status =
    fluid >= range.danger ? 'danger' : total > range.max ? 'high' : total >= range.target ? 'good' : total >= range.min ? 'ok' : 'low';
  const color = status === 'danger' ? c.danger : status === 'high' ? c.high : status === 'low' ? c.low : c.water;

  const chart = useMemo(
    () =>
      lastDays(period).map((d) => {
        const day = allWater.filter((w) => w.date === d);
        return {
          label: shortLabel(d),
          value: day.reduce((a, w) => a + ((w.kind ?? 'water') === 'water' ? w.ml : 0), 0),
          fluid: day.reduce((a, w) => a + w.ml, 0),
        };
      }),
    [period, allWater],
  );
  const withData = chart.filter((x) => x.value);
  const avg = withData.length ? withData.reduce((a, x) => a + x.value, 0) / withData.length : 0;

  return (
    <>
      <DateNav date={date} onChange={setDate} />

      <Card>
        <View style={{ alignItems: 'center', gap: 6 }}>
          <Ring value={total} max={range.target} size={180} stroke={16} color={color}>
            <T size={32} bold>{fmt(total / 1000, 2)}</T>
            <T dim>{L.water.of(range.target / 1000)}</T>
          </Ring>
          <T color={color} bold center>{L.water.status[status]}</T>
        </View>
        <Row style={{ justifyContent: 'space-between' }}>
          <Stat label={L.water.pure} value={fmt(total / 1000, 2)} unit={L.units.l} color={c.water} />
          <Stat label={L.water.otherDrunk} value={fmt(other / 1000, 2)} unit={L.units.l} color={c.high} />
          <Stat label={L.water.totalFluid} value={fmt(fluid / 1000, 2)} unit={L.units.l} />
        </Row>
        <WaterForm date={date} />
      </Card>

      <Card>
        <H icon="glass">{L.water.journal}</H>
        {entries.length ? (
          entries.map((e) => {
            const isOther = e.kind === 'other';
            return (
              <ListItem
                key={e.id}
                left={<Icon name={isOther ? 'drink' : 'water'} size={20} color={isOther ? c.high : c.water} />}
                title={`${fmt(e.ml)} ${L.units.ml}`}
                subtitle={`${timeLabel(e.time)} · ${L.water.kinds[isOther ? 'other' : 'water']}`}
                onPress={() => editor.edit(e)}
                onDelete={() => removeWater(e.id)}
              />
            );
          })
        ) : (
          <Empty text={L.water.empty} />
        )}
        {entries.length ? <T dim size={12}>{L.common.longPressDelete}</T> : null}
        <WaterEditSheet sheet={editor} />
      </Card>

      <Card>
        <H icon="target">{L.water.norms}</H>
        <Row>
          <Stat label={L.common.min} value={fmt(range.min / 1000, 1)} unit={L.units.l} color={c.low} />
          <Stat label={L.common.target} value={fmt(range.target / 1000, 1)} unit={L.units.l} color={c.water} />
          <Stat label={L.common.max} value={fmt(range.max / 1000, 1)} unit={L.units.l} color={c.high} />
          <Stat label={L.common.danger} value={`>${fmt(range.danger / 1000, 1)}`} unit={L.units.l} color={c.danger} />
        </Row>
        <T dim size={12}>{L.water.dangerNote(fluid / 1000)}</T>
        <Hint title={L.water.hintTitle}>
          {L.water.hint.map((p, i) => <T key={i} size={14}>{p}</T>)}
        </Hint>
      </Card>

      <Card>
        <H right={<Segmented value={period} onChange={setPeriod} options={[{ value: 7, label: L.common.days7 }, { value: 30, label: L.common.days30 }]} />}>
          {L.water.chart}
        </H>
        <BarChart
          data={chart.map((x) => ({
            label: x.label,
            value: x.value,
            color: x.fluid >= range.danger ? c.danger : x.value > range.max ? c.high : x.value >= range.min ? c.water : alpha(c.water, 0.4),
          }))}
          bands={[
            { from: range.min, to: range.max, color: alpha(c.ok, 0.12) },
            { from: range.danger, to: range.danger * 2, color: alpha(c.danger, 0.12) },
          ]}
          target={range.target}
          // Axis ends at the recommended maximum; once values get close, it grows to value + 1 L.
          maxY={Math.ceil(Math.max(range.max, Math.max(...chart.map((x) => x.value)) + 1000) / 500) * 500}
          format={(v) => `${fmt(v / 1000, 2)} ${L.units.l}`}
        />
        <T dim size={13}>{L.water.avg(avg / 1000)}</T>
      </Card>
    </>
  );
}
