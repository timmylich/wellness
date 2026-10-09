/**
 * Lightweight SVG charts. Intentionally small: bar chart, line chart, range bar and
 * progress ring cover every visualization in the app without a charting dependency.
 */
import { useEffect, useState } from 'react';
import { Animated, Easing, View, type LayoutChangeEvent } from 'react-native';
import Svg, { Circle, ClipPath, Defs, G, Line, Path, Rect, Text as SvgText } from 'react-native-svg';

import { useT } from '../i18n';
import { type Range, rangeStatus } from '../lib/calc';
import { dur, useMotion } from '../lib/motion';
import { alpha, toneColor, useTheme } from '../lib/theme';
import { fmt, T } from './ui';

const AnimatedRect = Animated.createAnimatedComponent(Rect);
const AnimatedCircle = Animated.createAnimatedComponent(Circle);

/** Animated value that eases towards `target`; jumps instantly when motion is off. */
function useTween(target: number, duration = 600): Animated.Value {
  const motion = useMotion();
  const v = useState(() => new Animated.Value(motion ? 0 : target))[0];
  useEffect(() => {
    if (!motion) {
      v.setValue(target);
      return;
    }
    Animated.timing(v, { toValue: target, duration: dur(duration), easing: Easing.out(Easing.cubic), useNativeDriver: false }).start();
  }, [target, motion, v, duration]);
  return v;
}

function useWidth(): [number, (e: LayoutChangeEvent) => void] {
  const [w, setW] = useState(0);
  return [w, (e) => setW(e.nativeEvent.layout.width)];
}

/** Round up to a "nice" axis maximum. */
function niceMax(v: number): number {
  if (v <= 0) return 1;
  const p = Math.pow(10, Math.floor(Math.log10(v)));
  const n = v / p;
  return (n <= 1 ? 1 : n <= 2 ? 2 : n <= 5 ? 5 : 10) * p;
}

export interface Band {
  from: number;
  to: number;
  color: string;
}

export function BarChart({
  data,
  height = 160,
  target,
  bands = [],
  unit = '',
  maxY,
}: {
  data: { label: string; value: number; color?: string }[];
  /** Fixed axis maximum; bands and the target line are clipped to it. */
  maxY?: number;
  height?: number;
  /** Dashed reference line. */
  target?: number;
  /** Colored horizontal background zones (e.g. recommended range). */
  bands?: Band[];
  unit?: string;
}) {
  const t = useTheme();
  const [width, onLayout] = useWidth();
  const padL = 36, padB = 18, padT = 8;
  const max = maxY ?? niceMax(Math.max(...data.map((d) => d.value), target ?? 0, ...bands.map((b) => Math.min(b.to, (target ?? 0) * 1.5 || b.to))) * 1.1);
  const ch = height - padB - padT;
  const y = (v: number) => padT + ch - (Math.min(v, max) / max) * ch;
  const slot = data.length ? (width - padL) / data.length : 0;
  const barW = Math.max(2, Math.min(28, slot * 0.6));
  const labelEvery = Math.ceil(data.length / 8);
  const grow = useTween(1, 500);

  return (
    <View onLayout={onLayout} style={{ height }}>
      {width > 0 && (
        <Svg width={width} height={height}>
          {bands.map((b, i) => (
            <Rect key={i} x={padL} width={width - padL} y={y(b.to)} height={Math.max(0, y(b.from) - y(b.to))} fill={b.color} />
          ))}
          {[0, 0.5, 1].map((f) => (
            <G key={f}>
              <Line x1={padL} x2={width} y1={y(max * f)} y2={y(max * f)} stroke={t.border} strokeWidth={1} />
              <SvgText x={padL - 4} y={y(max * f) + 4} fontSize={10} fill={t.textDim} textAnchor="end">
                {fmt(max * f)}
              </SvgText>
            </G>
          ))}
          {data.map((d, i) => {
            const x = padL + slot * i + (slot - barW) / 2;
            return (
              <G key={i}>
                <AnimatedRect
                  x={x}
                  y={grow.interpolate({ inputRange: [0, 1], outputRange: [y(0), y(d.value)] })}
                  width={barW}
                  height={grow.interpolate({ inputRange: [0, 1], outputRange: [0, Math.max(0, y(0) - y(d.value))] })}
                  rx={Math.min(4, barW / 2)}
                  fill={d.color ?? t.accent}
                />
                {i % labelEvery === 0 && (
                  <SvgText x={x + barW / 2} y={height - 4} fontSize={10} fill={t.textDim} textAnchor="middle">
                    {d.label}
                  </SvgText>
                )}
              </G>
            );
          })}
          {target != null && (
            <Line x1={padL} x2={width} y1={y(target)} y2={y(target)} stroke={t.text} strokeDasharray="4 4" strokeWidth={1} />
          )}
        </Svg>
      )}
      {unit ? <T dim size={10} style={{ position: 'absolute', top: -2, left: 0 }}>{unit}</T> : null}
    </View>
  );
}

export function LineChart({
  labels,
  series,
  bands = [],
  height = 180,
  minY,
  maxY,
}: {
  labels: string[];
  series: { values: (number | null)[]; color: string; name?: string }[];
  bands?: Band[];
  height?: number;
  minY?: number;
  maxY?: number;
}) {
  const t = useTheme();
  const [width, onLayout] = useWidth();
  const padL = 36, padB = 18, padT = 8, padR = 6;
  const all = series.flatMap((s) => s.values.filter((v): v is number => v != null));
  if (!all.length) return <NoData />;
  let lo = minY ?? Math.min(...all);
  let hi = maxY ?? Math.max(...all);
  if (hi - lo < 1) { lo -= 1; hi += 1; }
  const pad = (hi - lo) * 0.1;
  lo -= minY == null ? pad : 0;
  hi += maxY == null ? pad : 0;
  const ch = height - padB - padT;
  const cw = width - padL - padR;
  const x = (i: number) => padL + (labels.length <= 1 ? cw / 2 : (i / (labels.length - 1)) * cw);
  const y = (v: number) => padT + ch - ((Math.max(lo, Math.min(hi, v)) - lo) / (hi - lo)) * ch;
  const labelEvery = Math.ceil(labels.length / 7);

  return (
    <View onLayout={onLayout} style={{ height }}>
      {width > 0 && (
        <Svg width={width} height={height}>
          {bands.map((b, i) => (
            <Rect key={i} x={padL} width={cw} y={y(b.to)} height={Math.max(0, y(b.from) - y(b.to))} fill={b.color} />
          ))}
          {[0, 0.5, 1].map((f) => {
            const v = lo + (hi - lo) * f;
            return (
              <G key={f}>
                <Line x1={padL} x2={width - padR} y1={y(v)} y2={y(v)} stroke={t.border} />
                <SvgText x={padL - 4} y={y(v) + 4} fontSize={10} fill={t.textDim} textAnchor="end">{fmt(v, 1)}</SvgText>
              </G>
            );
          })}
          {labels.map((l, i) =>
            i % labelEvery === 0 ? (
              <SvgText key={i} x={x(i)} y={height - 4} fontSize={10} fill={t.textDim} textAnchor="middle">{l}</SvgText>
            ) : null,
          )}
          {series.map((s, si) => {
            // Break the line at gaps (null values).
            let d = '';
            let pen = false;
            s.values.forEach((v, i) => {
              if (v == null) { pen = false; return; }
              d += `${pen ? 'L' : 'M'}${x(i)},${y(v)} `;
              pen = true;
            });
            return (
              <G key={si}>
                <Path d={d} stroke={s.color} strokeWidth={2.5} fill="none" strokeLinejoin="round" />
                {s.values.map((v, i) => (v == null ? null : <Circle key={i} cx={x(i)} cy={y(v)} r={3} fill={s.color} />))}
              </G>
            );
          })}
        </Svg>
      )}
    </View>
  );
}

function NoData() {
  const L = useT();
  return <T dim center>{L.common.noData}</T>;
}

/**
 * Horizontal gauge for a nutrient range: shows the low zone, the recommended zone,
 * the danger zone above max, a target tick and the current value marker.
 */
export function RangeBar({ value, range, height = 10 }: { value: number; range: Range; height?: number }) {
  const t = useTheme();
  const [width, onLayout] = useWidth();
  const scale = Math.max(range.max * 1.25, value * 1.05, 1);
  const px = (v: number) => (Math.min(v, scale) / scale) * width;
  const status = rangeStatus(value, range);
  const marker = toneColor(t, status === 'low' ? 'low' : status === 'high' ? 'danger' : 'ok');
  const lowEnd = range.lowerIsBetter ? 0 : range.min;
  const animated = useTween(px(value));

  const r = height / 2;
  // Unique clip id per instance so several gauges on one screen don't share a mask.
  const [clipId] = useState(() => `rb${Math.random().toString(36).slice(2, 8)}`);

  return (
    <View onLayout={onLayout} style={{ height: height + 8, justifyContent: 'center' }}>
      {width > 0 && (
        <Svg width={width} height={height + 8}>
          <Defs>
            {/* Every layer is clipped to one rounded track, so zones and fill share the same shape. */}
            <ClipPath id={clipId}>
              <Rect x={0} y={4} width={width} height={height} rx={r} />
            </ClipPath>
          </Defs>
          <G clipPath={`url(#${clipId})`}>
            <Rect x={0} y={4} width={width} height={height} fill={t.cardAlt} />
            {lowEnd > 0 && <Rect x={0} y={4} width={px(lowEnd)} height={height} fill={alpha(t.low, 0.35)} />}
            <Rect x={px(lowEnd)} y={4} width={px(range.max) - px(lowEnd)} height={height} fill={alpha(t.ok, 0.3)} />
            <Rect x={px(range.max)} y={4} width={width - px(range.max)} height={height} fill={alpha(t.danger, 0.3)} />
            <AnimatedRect x={0} y={4} width={animated} height={height} fill={alpha(marker, 0.85)} />
          </G>
          <Line x1={px(range.target)} x2={px(range.target)} y1={0} y2={height + 8} stroke={t.text} strokeWidth={2} strokeLinecap="round" />
        </Svg>
      )}
    </View>
  );
}

export function Ring({
  value,
  max,
  size = 180,
  stroke = 16,
  color,
  children,
}: {
  value: number;
  max: number;
  size?: number;
  stroke?: number;
  color: string;
  children?: React.ReactNode;
}) {
  const t = useTheme();
  const r = (size - stroke) / 2;
  const c = 2 * Math.PI * r;
  const frac = Math.min(1, max > 0 ? value / max : 0);
  const offset = useTween(c * (1 - frac), 700);
  return (
    <View style={{ width: size, height: size, alignItems: 'center', justifyContent: 'center' }}>
      <Svg width={size} height={size} style={{ position: 'absolute' }}>
        <Circle cx={size / 2} cy={size / 2} r={r} stroke={t.cardAlt} strokeWidth={stroke} fill="none" />
        <AnimatedCircle
          cx={size / 2}
          cy={size / 2}
          r={r}
          stroke={color}
          strokeWidth={stroke}
          fill="none"
          strokeDasharray={`${c} ${c}`}
          strokeDashoffset={offset}
          strokeLinecap="round"
          transform={`rotate(-90 ${size / 2} ${size / 2})`}
        />
      </Svg>
      {children}
    </View>
  );
}
