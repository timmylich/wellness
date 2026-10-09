/** Body composition: weight trend, body fat (measured or US Navy estimate), BMI, healthy weight. */
import { useMemo } from 'react';

import { LineChart } from '../components/charts';
import { BodyForm } from '../components/forms';
import { Card, Empty, fmt, H, Hint, ListItem, Row, Stat, T } from '../components/ui';
import { useT } from '../i18n';
import { bmi, bmiCategory, bodyFatCategory, healthyWeight, navyBodyFat, referenceWeight } from '../lib/calc';
import { humanDate, shortLabel } from '../lib/date';
import { alpha, toneColor, useTheme } from '../lib/theme';
import { removeBody } from '../store/actions';
import { useStore } from '../store/store';

export function Body() {
  const c = useTheme();
  const L = useT();
  const profile = useStore((s) => s.profile);
  const entries = useStore((s) => s.body);

  const sorted = useMemo(() => [...entries].sort((a, b) => a.time - b.time), [entries]);
  const last = <K extends 'waistCm' | 'neckCm' | 'hipCm' | 'bodyFatPct'>(k: K) => sorted.findLast((e) => e[k] != null)?.[k];
  const waist = last('waistCm');
  const neck = last('neckCm');
  const navy = waist && neck ? navyBodyFat(profile.sex, profile.heightCm, waist, neck, last('hipCm')) : null;
  const measuredFat = last('bodyFatPct');
  const fatPct = measuredFat ?? navy ?? undefined;

  const b = bmi(profile.weightKg, profile.heightCm);
  const cat = bmiCategory(b);
  const hw = healthyWeight(profile.heightCm, profile.sex);
  const whtr = waist ? waist / profile.heightCm : null;
  const ref = referenceWeight(profile);

  const weights = sorted.filter((e) => e.weightKg).slice(-60);
  // 7-point moving average smooths daily water fluctuations.
  const smooth = weights.map((_, i) => {
    const w = weights.slice(Math.max(0, i - 6), i + 1);
    return w.reduce((a, e) => a + e.weightKg!, 0) / w.length;
  });
  const P = L.body.logParts;

  return (
    <>
      <Card>
        <H icon="body">{L.body.now}</H>
        <Row>
          <Stat label={L.body.weight} value={fmt(profile.weightKg, 1)} unit={L.units.kg} />
          <Stat label={L.body.target} value={fmt(profile.targetWeightKg, 1)} unit={L.units.kg} />
          <Stat label={L.body.height} value={fmt(profile.heightCm)} unit={L.units.cm} />
        </Row>
        <Row>
          <Stat label={L.body.bmi} value={fmt(b, 1)} color={toneColor(c, cat.tone)} />
          <Stat label={L.body.fat} value={fatPct ? fmt(fatPct, 1) : '—'} unit="%" />
          <Stat label={L.body.whtr} value={whtr ? fmt(whtr, 2) : '—'} color={whtr ? (whtr < 0.5 ? c.ok : whtr < 0.6 ? c.high : c.danger) : undefined} />
        </Row>
        <T color={toneColor(c, cat.tone)}>{L.body.bmiLine(L.body.bmiCat[cat.key])}</T>
        {fatPct ? <T dim size={13}>{L.body.fatLine(L.body.fatCat[bodyFatCategory(profile.sex, fatPct)], measuredFat == null && !!navy)}</T> : null}
      </Card>

      <Card>
        <H icon="target">{L.body.healthy}</H>
        <T>{L.body.healthyLine(profile.heightCm, hw.min, hw.max)}</T>
        <T dim size={13}>{L.body.devine(hw.devine)}</T>
        {Math.abs(ref - profile.weightKg) > 0.5 ? <T dim size={13}>{L.body.refWeight(ref)}</T> : null}
        <Hint title={L.body.whyTitle}>{L.body.why}</Hint>
      </Card>

      <Card>
        <H icon="ruler">{L.body.measure}</H>
        <BodyForm />
      </Card>

      <Card>
        <H>{L.body.trend}</H>
        {weights.length ? (
          <>
            <LineChart
              labels={weights.map((e) => shortLabel(e.date))}
              series={[
                { values: weights.map((e) => e.weightKg!), color: alpha(c.accent, 0.4) },
                { values: smooth, color: c.accent },
              ]}
              bands={[{ from: hw.min, to: hw.max, color: alpha(c.ok, 0.1) }]}
            />
            <T dim size={12}>{L.body.trendNote}</T>
            {weights.length > 1 && <T>{L.body.change(smooth[smooth.length - 1] - smooth[0], humanDate(weights[0].date, L))}</T>}
          </>
        ) : (
          <Empty text={L.body.first} />
        )}
      </Card>

      <Card>
        <H>{L.body.log}</H>
        {sorted.length ? (
          [...sorted].reverse().slice(0, 50).map((e) => (
            <ListItem
              key={e.id}
              title={[
                e.weightKg && `${fmt(e.weightKg, 1)} ${P.kg}`,
                e.bodyFatPct && `${fmt(e.bodyFatPct, 1)} ${P.fat}`,
                e.waistCm && `${P.waist} ${fmt(e.waistCm)}`,
                e.neckCm && `${P.neck} ${fmt(e.neckCm)}`,
                e.hipCm && `${P.hip} ${fmt(e.hipCm)}`,
                e.heightCm && `${P.height} ${fmt(e.heightCm)}`,
              ].filter(Boolean).join(' · ')}
              subtitle={humanDate(e.date, L)}
              onDelete={() => removeBody(e.id)}
            />
          ))
        ) : (
          <Empty text={L.common.noRecords} />
        )}
      </Card>
    </>
  );
}
