/**
 * Dependency-free slider built on PanResponder. Supports stepping, colored background zones
 * (e.g. warning ranges) and a center mark. Haptic tick when crossing steps.
 */
import * as Haptics from 'expo-haptics';
import { useEffect, useState } from 'react';
import { PanResponder, View, type LayoutChangeEvent } from 'react-native';

import { alpha, useTheme } from '../lib/theme';

export interface SliderZone {
  from: number;
  to: number;
  color: string;
}

const THUMB = 28;

interface Box {
  width: number;
  value: number;
  min: number;
  max: number;
  step: number;
  startX: number;
  onChange: (v: number) => void;
}

/** Convert a thumb x-position to a stepped value and report it if it changed. */
function applyMove(b: Box, x: number) {
  const t = Math.max(1, b.width - THUMB);
  const raw = b.min + (Math.max(0, Math.min(t, x)) / t) * (b.max - b.min);
  const v = Number((Math.round(raw / b.step) * b.step).toFixed(4));
  if (v !== b.value) {
    b.value = v;
    Haptics.selectionAsync().catch(() => {});
    b.onChange(v);
  }
}

export function Slider({
  value,
  min,
  max,
  step,
  onChange,
  zones = [],
  marks = [],
}: {
  value: number;
  min: number;
  max: number;
  step: number;
  onChange: (v: number) => void;
  zones?: SliderZone[];
  /** Values to draw small tick marks at (e.g. 0 for "maintain"). */
  marks?: number[];
}) {
  const c = useTheme();
  const [width, setWidth] = useState(0);
  const [dragging, setDragging] = useState(false);
  // The PanResponder is created once; this mutable box feeds it the latest props.
  const [box] = useState(() => ({ width, value, onChange, min, max, step, startX: 0 }));
  useEffect(() => {
    Object.assign(box, { width, value, onChange, min, max, step });
  });

  const track = Math.max(1, width - THUMB);
  const toX = (v: number) => ((v - min) / (max - min)) * track;

  const [responder] = useState(() =>
    PanResponder.create({
      onStartShouldSetPanResponder: () => true,
      onMoveShouldSetPanResponder: () => true,
      // Keep the gesture even if a parent ScrollView wants it.
      onPanResponderTerminationRequest: () => false,
      onPanResponderGrant: (e) => {
        setDragging(true);
        box.startX = e.nativeEvent.locationX - THUMB / 2;
        applyMove(box, box.startX);
      },
      onPanResponderMove: (_, g) => applyMove(box, box.startX + g.dx),
      onPanResponderRelease: () => setDragging(false),
      onPanResponderTerminate: () => setDragging(false),
    }),
  );

  const thumbX = toX(value);

  return (
    <View
      onLayout={(e: LayoutChangeEvent) => setWidth(e.nativeEvent.layout.width)}
      style={{ height: THUMB + 12, justifyContent: 'center' }}
      {...responder.panHandlers}
    >
      {width > 0 && (
        <>
          <View style={{ position: 'absolute', left: THUMB / 2, right: THUMB / 2, height: 8, borderRadius: 4, backgroundColor: c.cardAlt, overflow: 'hidden' }}>
            {zones.map((z, i) => (
              <View key={i} style={{ position: 'absolute', left: toX(z.from), width: toX(z.to) - toX(z.from), top: 0, bottom: 0, backgroundColor: z.color }} />
            ))}
          </View>
          {marks.map((m) => (
            <View key={m} style={{ position: 'absolute', left: THUMB / 2 + toX(m) - 1, width: 2, height: 18, borderRadius: 1, backgroundColor: c.textDim }} />
          ))}
          <View
            style={{
              position: 'absolute',
              left: thumbX,
              width: THUMB,
              height: THUMB,
              borderRadius: THUMB / 2,
              backgroundColor: c.accent,
              borderWidth: 4,
              borderColor: c.card,
              transform: [{ scale: dragging ? 1.15 : 1 }],
              shadowColor: c.accent,
              shadowOpacity: 0.5,
              shadowRadius: 8,
              elevation: 4,
            }}
          />
          <View
            pointerEvents="none"
            style={{ position: 'absolute', left: thumbX - 6, width: THUMB + 12, height: THUMB + 12, borderRadius: (THUMB + 12) / 2, backgroundColor: alpha(c.accent, dragging ? 0.18 : 0) }}
          />
        </>
      )}
    </View>
  );
}
