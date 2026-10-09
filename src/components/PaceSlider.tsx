/**
 * Weekly weight-change slider: left = lose, centre = maintain, right = gain.
 * Warning zones are coloured relative to the user's body weight.
 */
import { View } from 'react-native';

import { useT } from '../i18n';
import { goalDelta, PACE_MAX, PACE_MIN, paceWarning, weeksToTarget } from '../lib/calc';
import { alpha, useTheme } from '../lib/theme';
import type { Profile } from '../store/types';
import { Slider } from './Slider';
import { fmt, Hint, Row, T } from './ui';

export function PaceSlider({ profile, onChange }: { profile: Profile; onChange: (v: number) => void }) {
  const c = useTheme();
  const L = useT();
  const v = profile.weeklyChangeKg;
  const delta = goalDelta(profile);
  const warning = paceWarning(profile);
  const weeks = weeksToTarget(profile);
  const diff = profile.targetWeightKg - profile.weightKg;
  const mismatch = v !== 0 && Math.abs(diff) >= 0.3 && Math.sign(diff) !== Math.sign(v);
  // Loss faster than 1 % / 1.25 % of body weight per week is flagged.
  const warnLoss = -profile.weightKg * 0.01;
  const dangerLoss = -profile.weightKg * 0.0125;

  return (
    <View style={{ gap: 8 }}>
      <T size={18} bold color={warning ? (warning === 'lossVeryFast' ? c.danger : c.high) : c.accent} center>
        {v === 0 ? L.pace.maintain : v < 0 ? L.pace.lose(-v) : L.pace.gain(v)}
      </T>
      <Slider
        value={v}
        min={PACE_MIN}
        max={PACE_MAX}
        step={0.05}
        onChange={onChange}
        marks={[0]}
        zones={[
          { from: PACE_MIN, to: Math.max(PACE_MIN, dangerLoss), color: alpha(c.danger, 0.55) },
          { from: Math.max(PACE_MIN, dangerLoss), to: Math.max(PACE_MIN, warnLoss), color: alpha(c.high, 0.5) },
          { from: Math.max(PACE_MIN, warnLoss), to: 0.5, color: alpha(c.ok, 0.45) },
          { from: 0.5, to: PACE_MAX, color: alpha(c.high, 0.5) },
        ]}
      />
      <Row style={{ justifyContent: 'space-between' }}>
        <T dim size={12}>−1,5 · {L.pace.loseLabel}</T>
        <T dim size={12}>0</T>
        <T dim size={12}>{L.pace.gainLabel} · +1,5</T>
      </Row>
      {delta !== 0 && (
        <T center>
          {delta < 0 ? L.pace.deficit : L.pace.surplus}: <T bold>{fmt(Math.abs(delta))} {L.pace.perDay}</T>
        </T>
      )}
      {weeks != null ? <T dim center size={13}>{L.pace.eta(weeks)}</T> : null}
      {mismatch ? <T center size={13} color={c.high}>{L.pace.mismatch}</T> : null}
      {warning ? <T center size={13} color={warning === 'lossVeryFast' ? c.danger : c.high}>⚠ {L.pace.warnings[warning]}</T> : null}
      <Hint title={L.pace.hintTitle}>{L.pace.hint}</Hint>
    </View>
  );
}
