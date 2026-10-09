/** Blood pressure and pulse log with classification, statistics and trends. */
import { useMemo, useState } from 'react';

import { LineChart } from '../components/charts';
import { HeartForm } from '../components/forms';
import { Card, Empty, fmt, H, Hint, ListItem, Row, Segmented, Stat, T } from '../components/ui';
import { useT } from '../i18n';
import { bpCategory, maxHeartRate, pulseCategory, type BpKey, type Tone } from '../lib/calc';
import { humanDate, shortLabel, timeLabel } from '../lib/date';
import { alpha, toneColor, useTheme } from '../lib/theme';
import { removeHeart } from '../store/actions';
import { useStore } from '../store/store';

const avg = (xs: number[]) => (xs.length ? xs.reduce((a, b) => a + b, 0) / xs.length : 0);

const CLASSES: [BpKey, string, Tone][] = [
  ['normal', '< 120 / < 80', 'ok'],
  ['elevated', '120–129 / < 80', 'high'],
  ['stage1', '130–139 / 80–89', 'high'],
  ['stage2', '≥ 140 / ≥ 90', 'danger'],
  ['crisis', '> 180 / > 120', 'danger'],
  ['low', '< 90 / < 60', 'low'],
];

const ZONES = [[0.5, 0.6], [0.6, 0.7], [0.7, 0.8], [0.8, 0.9], [0.9, 1]];

export function Heart() {
  const c = useTheme();
  const L = useT();
  const entries = useStore((s) => s.heart);
  const profile = useStore((s) => s.profile);
  const [count, setCount] = useState<14 | 30 | 90>(14);

  const sorted = useMemo(() => [...entries].sort((a, b) => a.time - b.time), [entries]);
  const recent = sorted.slice(-count);
  const bp = recent.filter((e) => e.systolic && e.diastolic);
  const pulses = recent.filter((e) => e.pulse).map((e) => e.pulse!);
  const avgBp = bp.length ? bpCategory(avg(bp.map((e) => e.systolic!)), avg(bp.map((e) => e.diastolic!))) : null;
  const hrMax = maxHeartRate(profile);

  return (
    <>
      <Card>
        <H icon="heart">{L.heart.newMeasurement}</H>
        <HeartForm />
      </Card>

      <Card>
        <H right={<Segmented value={count} onChange={setCount} options={[{ value: 14, label: '14' }, { value: 30, label: '30' }, { value: 90, label: '90' }]} />}>
          {L.heart.stats}
        </H>
        <Row>
          <Stat label={L.heart.avgBp} value={bp.length ? `${fmt(avg(bp.map((e) => e.systolic!)))}/${fmt(avg(bp.map((e) => e.diastolic!)))}` : '—'} />
          <Stat label={L.heart.avgPulse} value={pulses.length ? fmt(avg(pulses)) : '—'} unit={L.units.bpm} />
          <Stat label={L.heart.count} value={recent.length} />
        </Row>
        {avgBp && <T color={toneColor(c, avgBp.tone)} bold>{L.heart.onAverage(L.heart.bp[avgBp.key].label)}</T>}
        {pulses.length > 0 && <T size={13} dim>{L.heart.restingPulse(L.heart.pulseCat[pulseCategory(avg(pulses)).key])}</T>}

        <T dim size={13} style={{ marginTop: 6 }}>{L.heart.bpChart(count)}</T>
        <LineChart
          labels={recent.map((e) => shortLabel(e.date))}
          series={[
            { values: recent.map((e) => e.systolic ?? null), color: c.danger },
            { values: recent.map((e) => e.diastolic ?? null), color: c.water },
          ]}
          bands={[
            { from: 60, to: 80, color: alpha(c.ok, 0.1) },
            { from: 90, to: 120, color: alpha(c.ok, 0.1) },
            { from: 140, to: 220, color: alpha(c.danger, 0.08) },
          ]}
        />
        <Row>
          <T size={12} color={c.danger}>● {L.heart.upper}</T>
          <T size={12} color={c.water}>● {L.heart.lower}</T>
          <T size={12} dim>{L.heart.normBands}</T>
        </Row>
        <T dim size={13} style={{ marginTop: 6 }}>{L.heart.pulseChart}</T>
        <LineChart
          labels={recent.map((e) => shortLabel(e.date))}
          series={[{ values: recent.map((e) => e.pulse ?? null), color: c.accent }]}
          bands={[{ from: 60, to: 100, color: alpha(c.ok, 0.1) }]}
          height={140}
        />
      </Card>

      <Card>
        <H>{L.heart.classification}</H>
        {CLASSES.map(([k, v, tone]) => (
          <Row key={k} style={{ justifyContent: 'space-between' }}>
            <T color={toneColor(c, tone)}>{L.heart.bp[k].label}</T>
            <T dim>{v}</T>
          </Row>
        ))}
        <T dim size={12}>{L.heart.classNote}</T>
      </Card>

      <Card>
        <H icon="pulse">{L.heart.zones}</H>
        <T dim size={13}>{L.heart.maxHr(hrMax)}</T>
        {ZONES.map(([a, b], i) => (
          <Row key={i} style={{ justifyContent: 'space-between' }}>
            <T>{L.heart.zoneNames[i]}</T>
            <T dim>{Math.round(hrMax * a)}–{Math.round(hrMax * b)}</T>
          </Row>
        ))}
      </Card>

      <Card>
        <H>{L.heart.log}</H>
        {sorted.length ? (
          [...sorted].reverse().slice(0, 50).map((e) => {
            const cat = e.systolic && e.diastolic ? bpCategory(e.systolic, e.diastolic) : null;
            return (
              <ListItem
                key={e.id}
                title={[e.systolic && e.diastolic ? `${e.systolic}/${e.diastolic}` : null, e.pulse ? `♥ ${e.pulse}` : null].filter(Boolean).join('   ')}
                subtitle={`${humanDate(e.date, L)}, ${timeLabel(e.time)}${e.note ? ` · ${e.note}` : ''}`}
                right={cat ? <T size={12} color={toneColor(c, cat.tone)}>{L.heart.bp[cat.key].label}</T> : null}
                onDelete={() => removeHeart(e.id)}
              />
            );
          })
        ) : (
          <Empty text={L.common.noRecords} />
        )}
      </Card>
      <Hint title={L.heart.urgentTitle} force>{L.heart.urgent}</Hint>
    </>
  );
}
