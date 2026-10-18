/**
 * Lightweight SVG charts. Intentionally small: bar chart, line chart, range bar and
 * progress ring cover every visualization in the app without a charting dependency.
 */
import { useContext, useEffect, useState } from 'react';
import { Animated, Easing, View, type GestureResponderEvent, type LayoutChangeEvent } from 'react-native';
import Svg, { Circle, ClipPath, Defs, G, Line, Path, Rect, Text as SvgText } from 'react-native-svg';

import { useT } from '../i18n';
import { type Range, rangeStatus } from '../lib/calc';
import { dur, useMotion } from '../lib/motion';
import { alpha, toneColor, useTheme } from '../lib/theme';
import { NestedScrollHost } from '../../modules/wellness-native';
import { PagerLock } from './PagerLock';
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

const easeOut = (t: number) => 1 - Math.pow(1 - t, 3);

/**
 * Morphs a list of numbers from what is currently shown to `target` (no animation on mount).
 * NaN marks a gap: it is never interpolated. Used so charts glide between days instead of
 * replaying from zero.
 */
function useMorph(target: number[], duration = 450): number[] {
  const motion = useMotion();
  const key = target.join(',');
  // Mount at the final values: re-rendering SVG every frame while a section opens costs FPS.
  const [st, setSt] = useState(() => ({ key, from: target, to: target, t: 1 }));
  const shown = (x: typeof st) =>
    x.to.map((v, i) => {
      const f = x.from[i];
      return Number.isNaN(v) || f == null || Number.isNaN(f) ? v : f + (v - f) * easeOut(x.t);
    });
  // Adjust state during render when the target changes (no extra effect pass).
  if (st.key !== key) setSt({ key, from: shown(st), to: target, t: motion ? 0 : 1 });
  useEffect(() => {
    if (st.t >= 1) return;
    const d = dur(duration);
    const start = Date.now();
    let raf = 0;
    const tick = () => {
      const t = d > 0 ? Math.min(1, (Date.now() - start) / d) : 1;
      setSt((x) => (x.key === st.key ? { ...x, t } : x));
      if (t < 1) raf = requestAnimationFrame(tick);
    };
    raf = requestAnimationFrame(tick);
    return () => cancelAnimationFrame(raf);
    // Restart only when a new target arrives.
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [st.key, duration]);
  return st.key === key ? shown(st) : target;
}

function useWidth(): [number, (e: LayoutChangeEvent) => void] {
  const [w, setW] = useState(0);
  return [w, (e) => setW(e.nativeEvent.layout.width)];
}

const HOLD_MS = 180;

/**
 * Press-and-hold scrubbing: after a short hold the chart selects the point under the finger and
 * follows it while dragging. The section pager is locked meanwhile so a sideways drag scrubs.
 */
function useScrub(xToIndex: (x: number) => number) {
  const lock = useContext(PagerLock);
  // Finger x while scrubbing; the index is derived during render so it follows layout changes.
  const [fingerX, setFingerX] = useState<number | null>(null);
  const [handlers] = useState(() => {
    // Gesture state lives in this closure; the handlers are created once.
    let timer: ReturnType<typeof setTimeout> | null = null;
    let active = false;
    const end = () => {
      if (timer) clearTimeout(timer);
      timer = null;
      if (active) lock(false);
      active = false;
      setFingerX(null);
    };
    return {
      onStartShouldSetResponder: () => true,
      onResponderGrant: (e: GestureResponderEvent) => {
        const x = e.nativeEvent.locationX;
        timer = setTimeout(() => {
          active = true;
          lock(true);
          setFingerX(x);
        }, HOLD_MS);
      },
      onResponderMove: (e: GestureResponderEvent) => {
        if (active) setFingerX(e.nativeEvent.locationX);
      },
      // Before the hold fires, let scroll views take over; once scrubbing, keep the gesture.
      onResponderTerminationRequest: () => !active,
      onResponderRelease: end,
      onResponderTerminate: end,
    };
  });
  return { index: fingerX == null ? null : xToIndex(fingerX), handlers };
}

/** Value bubble shown above a selected point. */
/** Value bubble (Google Fit style): a light pill with a pointer, above the selected point. */
function Tip({ x, y, width, label, value, color }: { x: number; y: number; width: number; label: string; value: string; color: string }) {
  const t = useTheme();
  const bg = t.dark ? '#F4F6F5' : '#1F2427';
  const w = 150;
  const h = 32;
  const left = Math.max(0, Math.min(width - w, x - w / 2));
  // Above the point, but never above the chart's top edge.
  const top = Math.max(0, y - h - 14);
  return (
    <View pointerEvents="none" style={{ position: 'absolute', top, left, width: w, alignItems: 'center' }}>
      <View style={{ height: h, paddingHorizontal: 12, borderRadius: h / 2, backgroundColor: bg, flexDirection: 'row', alignItems: 'center', gap: 6, elevation: 3 }}>
        <T size={13} bold color={color}>{value}</T>
        <T size={12} color={t.dark ? '#5F6B70' : '#AEB8BC'} numberOfLines={1}>{label}</T>
      </View>
      <View style={{ position: 'absolute', top: h - 5, left: Math.max(10, Math.min(w - 20, x - left - 5)), width: 10, height: 10, backgroundColor: bg, transform: [{ rotate: '45deg' }] }} />
    </View>
  );
}

/** Smooth path through points (Catmull-Rom as cubic Béziers), like Google Fit's lines. */
function smoothPath(pts: { x: number; y: number }[]): string {
  if (!pts.length) return '';
  let d = `M${pts[0].x},${pts[0].y}`;
  for (let i = 0; i < pts.length - 1; i++) {
    const p0 = pts[i - 1] ?? pts[i];
    const p1 = pts[i];
    const p2 = pts[i + 1];
    const p3 = pts[i + 2] ?? p2;
    const c1x = p1.x + (p2.x - p0.x) / 6;
    const c1y = p1.y + (p2.y - p0.y) / 6;
    const c2x = p2.x - (p3.x - p1.x) / 6;
    const c2y = p2.y - (p3.y - p1.y) / 6;
    d += ` C${c1x},${c1y} ${c2x},${c2y} ${p2.x},${p2.y}`;
  }
  return d;
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
  format,
}: {
  data: { label: string; value: number; color?: string }[];
  /** Formats the value shown while a bar is held (defaults to the number plus `unit`). */
  format?: (v: number) => string;
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
  const rawMax = maxY ?? niceMax(Math.max(...data.map((d) => d.value), target ?? 0, ...bands.map((b) => Math.min(b.to, (target ?? 0) * 1.5 || b.to))) * 1.1);
  const morphed = useMorph([...data.map((d) => d.value), rawMax], 500);
  const max = morphed[data.length] || rawMax;
  const ch = height - padB - padT;
  const y = (v: number) => padT + ch - (Math.min(v, max) / (max || 1)) * ch;
  const slot = data.length ? (width - padL) / data.length : 0;
  const barW = Math.max(2, Math.min(28, slot * 0.6));
  const labelEvery = Math.ceil(data.length / 8);
  const { index: sel, handlers } = useScrub((px) => Math.max(0, Math.min(data.length - 1, Math.floor((px - padL) / (slot || 1)))));
  const show = (v: number) => (format ? format(v) : `${fmt(v)}${unit ? ` ${unit}` : ''}`);

  return (
    <NestedScrollHost mode="hold" onLayout={onLayout} style={{ height }} {...handlers}>
      {width > 0 && (
        <Svg width={width} height={height}>
          {bands.map((b, i) => (
            <Rect key={i} x={padL} width={width - padL} y={y(b.to)} height={Math.max(0, y(b.from) - y(b.to))} fill={b.color} />
          ))}
          {[0, 0.5, 1].map((f) => (
            <G key={f}>
              {f > 0 && <Line x1={padL} x2={width} y1={y(max * f)} y2={y(max * f)} stroke={t.border} strokeWidth={1} strokeDasharray="2 4" />}
              <SvgText x={padL - 4} y={y(max * f) + 4} fontSize={10} fill={t.textDim} textAnchor="end">
                {fmt(max * f)}
              </SvgText>
            </G>
          ))}
          {data.map((d, i) => {
            const x = padL + slot * i + (slot - barW) / 2;
            return (
              <G key={i}>
                <Rect
                  x={x}
                  y={y(Math.max(0, morphed[i]))}
                  width={barW}
                  height={Math.max(0, y(0) - y(Math.max(0, morphed[i])))}
                  rx={Math.min(4, barW / 2)}
                  fill={d.color ?? t.accent}
                  opacity={sel == null || sel === i ? 1 : 0.45}
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
            <Line x1={padL} x2={width} y1={y(target)} y2={y(target)} stroke={t.accent} strokeDasharray="6 5" strokeWidth={1.5} opacity={0.8} />
          )}
          {sel != null && data[sel] && (
            <Circle cx={padL + slot * sel + slot / 2} cy={y(data[sel].value)} r={5} fill={t.card} stroke={data[sel].color ?? t.accent} strokeWidth={2.5} />
          )}
        </Svg>
      )}
      {sel != null && data[sel] && width > 0 ? (
        <Tip x={padL + slot * sel + slot / 2} y={y(data[sel].value)} width={width} label={data[sel].label} value={show(data[sel].value)} color={data[sel].color ?? t.accent} />
      ) : null}
      {unit ? <T dim size={10} style={{ position: 'absolute', top: -2, left: 0 }}>{unit}</T> : null}
    </NestedScrollHost>
  );
}

export function LineChart({
  labels,
  series,
  bands = [],
  height = 180,
  minY,
  maxY,
  format = (v: number) => fmt(v, 1),
}: {
  labels: string[];
  series: { values: (number | null)[]; color: string; name?: string }[];
  bands?: Band[];
  height?: number;
  minY?: number;
  maxY?: number;
  /** Formats a held point's value. */
  format?: (v: number) => string;
}) {
  const t = useTheme();
  const [width, onLayout] = useWidth();
  const padL = 36, padB = 18, padT = 8, padR = 6;
  const cw = width - padL - padR;
  const { index: sel, handlers } = useScrub((px) =>
    labels.length <= 1 ? 0 : Math.max(0, Math.min(labels.length - 1, Math.round(((px - padL) / (cw || 1)) * (labels.length - 1)))),
  );
  const all = series.flatMap((s) => s.values.filter((v): v is number => v != null));
  let lo0 = minY ?? (all.length ? Math.min(...all) : 0);
  let hi0 = maxY ?? (all.length ? Math.max(...all) : 1);
  if (hi0 - lo0 < 1) { lo0 -= 1; hi0 += 1; }
  const pad = (hi0 - lo0) * 0.1;
  lo0 -= minY == null ? pad : 0;
  hi0 += maxY == null ? pad : 0;
  // Values and axis glide from the previous data (e.g. when switching days).
  const flat = useMorph([...series.flatMap((s) => s.values.map((v) => v ?? NaN)), lo0, hi0], 450);
  if (!all.length) return <NoData />;
  // Axis bounds glide too, but never collapse (zero range would produce NaN coordinates).
  const lo = Number.isFinite(flat[flat.length - 2]) ? flat[flat.length - 2] : lo0;
  const hi0m = flat[flat.length - 1];
  const hi = Number.isFinite(hi0m) && hi0m - lo > 0.5 ? hi0m : hi0;
  const shownSeries = series.map((s, si) => {
    const off = series.slice(0, si).reduce((a, x) => a + x.values.length, 0);
    return { ...s, values: s.values.map((_, i) => (Number.isNaN(flat[off + i]) ? null : flat[off + i])) };
  });
  const ch = height - padB - padT;
  const x = (i: number) => padL + (labels.length <= 1 ? cw / 2 : (i / (labels.length - 1)) * cw);
  const y = (v: number) => padT + ch - ((Math.max(lo, Math.min(hi, v)) - lo) / (hi - lo || 1)) * ch;
  const labelEvery = Math.ceil(labels.length / 7);

  return (
    <NestedScrollHost mode="hold" onLayout={onLayout} style={{ height }} {...handlers}>
      {width > 0 && (
        <Svg width={width} height={height}>
          {bands.map((b, i) => (
            <Rect key={i} x={padL} width={cw} y={y(b.to)} height={Math.max(0, y(b.from) - y(b.to))} fill={b.color} />
          ))}
          {[0, 0.5, 1].map((f) => {
            const v = lo + (hi - lo) * f;
            return (
              <G key={f}>
                {f > 0 && <Line x1={padL} x2={width - padR} y1={y(v)} y2={y(v)} stroke={t.border} strokeDasharray="2 4" />}
                <SvgText x={padL - 4} y={y(v) + 4} fontSize={10} fill={t.textDim} textAnchor="end">{fmt(v, 1)}</SvgText>
              </G>
            );
          })}
          {labels.map((l, i) =>
            i % labelEvery === 0 ? (
              <SvgText key={i} x={x(i)} y={height - 4} fontSize={10} fill={t.textDim} textAnchor="middle">{l}</SvgText>
            ) : null,
          )}
          {shownSeries.map((s, si) => {
            // Smooth segments, broken at gaps (null values); single points get a dot.
            const segs: { x: number; y: number }[][] = [[]];
            s.values.forEach((v, i) => {
              if (v == null) {
                if (segs[segs.length - 1].length) segs.push([]);
                return;
              }
              segs[segs.length - 1].push({ x: x(i), y: y(v) });
            });
            return (
              <G key={si}>
                {segs.map((seg, k) =>
                  seg.length > 1 ? (
                    <Path key={k} d={smoothPath(seg)} stroke={s.color} strokeWidth={3} fill="none" strokeLinecap="round" strokeLinejoin="round" />
                  ) : seg.length === 1 ? (
                    <Circle key={k} cx={seg[0].x} cy={seg[0].y} r={3} fill={s.color} />
                  ) : null,
                )}
              </G>
            );
          })}
          {sel != null && (
            <G>
              {/* Dashed drop line from the point, haloed marker (Google Fit style). */}
              <Line x1={x(sel)} x2={x(sel)} y1={y(series.map((s) => s.values[sel]).find((v) => v != null) ?? lo)} y2={padT + ch} stroke={t.textDim} strokeWidth={2} strokeDasharray="4 4" />
              {series.map((s, si) => {
                const v = s.values[sel];
                return v == null ? null : (
                  <G key={si}>
                    <Circle cx={x(sel)} cy={y(v)} r={11} fill={alpha(s.color, 0.3)} />
                    <Circle cx={x(sel)} cy={y(v)} r={6} fill="#fff" stroke={s.color} strokeWidth={3} />
                  </G>
                );
              })}
            </G>
          )}
        </Svg>
      )}
      {sel != null && width > 0 ? (
        <Tip
          x={x(sel)}
          y={y(series.map((s) => s.values[sel]).find((v): v is number => v != null) ?? lo)}
          width={width}
          label={labels[sel]}
          value={series.map((s) => s.values[sel]).filter((v): v is number => v != null).map(format).join(' / ') || '—'}
          color={series.find((s) => s.values[sel] != null)?.color ?? t.accent}
        />
      ) : null}
    </NestedScrollHost>
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
          {!range.lowerIsBetter && <Line x1={px(range.target)} x2={px(range.target)} y1={0} y2={height + 8} stroke={t.text} strokeWidth={2} strokeLinecap="round" />}
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
