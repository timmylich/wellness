/**
 * Vertical wheel picker (like the Android time picker): snaps to an item, the centred one is
 * bright and the neighbours fade. Scrolling runs on the native thread; JS only hears the final
 * value when the wheel stops. Rows are virtualised, so long ranges (e.g. 20–300 kg) are cheap.
 */
import * as Haptics from 'expo-haptics';
import { memo, useCallback, useState } from 'react';
import { Animated, View, type NativeScrollEvent, type NativeSyntheticEvent } from 'react-native';

import { useTheme } from '../lib/theme';
import { blockSheetDrag } from './Sheet';

const ITEM_H = 44;
const VISIBLE = 5;

type Props = {
  values: number[];
  value: number;
  onChange: (v: number) => void;
  width?: number;
  format?: (v: number) => string;
};

export const Wheel = memo(function Wheel({ values, value, onChange, width = 84, format = String }: Props) {
  const c = useTheme();
  const [scrollY] = useState(() => new Animated.Value(0));
  const start = Math.max(0, values.indexOf(value));
  const settle = useCallback(
    (e: NativeSyntheticEvent<NativeScrollEvent>) => {
      const i = Math.max(0, Math.min(values.length - 1, Math.round(e.nativeEvent.contentOffset.y / ITEM_H)));
      if (values[i] !== value) {
        Haptics.selectionAsync().catch(() => {});
        onChange(values[i]);
      }
    },
    [values, value, onChange],
  );
  const pad = ((VISIBLE - 1) / 2) * ITEM_H;

  return (
    <View
      style={{ width, height: ITEM_H * VISIBLE }}
      // Vertical drags here spin the wheel, never the surrounding bottom sheet.
      onTouchStart={() => blockSheetDrag(true)}
      onTouchEnd={() => blockSheetDrag(false)}
      onTouchCancel={() => blockSheetDrag(false)}
    >
      {/* Selection lines above and below the centre row. */}
      <View pointerEvents="none" style={{ position: 'absolute', left: 6, right: 6, top: pad, height: ITEM_H, borderTopWidth: 2, borderBottomWidth: 2, borderColor: c.textDim }} />
      <Animated.FlatList
        data={values}
        keyExtractor={(v) => String(v)}
        getItemLayout={(_, i) => ({ length: ITEM_H, offset: ITEM_H * i, index: i })}
        initialScrollIndex={start}
        initialNumToRender={VISIBLE + 4}
        windowSize={5}
        showsVerticalScrollIndicator={false}
        snapToInterval={ITEM_H}
        decelerationRate="fast"
        nestedScrollEnabled
        contentContainerStyle={{ paddingVertical: pad }}
        onScroll={Animated.event([{ nativeEvent: { contentOffset: { y: scrollY } } }], { useNativeDriver: true })}
        scrollEventThrottle={16}
        onMomentumScrollEnd={settle}
        onScrollEndDrag={(e) => {
          // No fling: the wheel stops right here.
          if (!e.nativeEvent.velocity?.y) settle(e);
        }}
        renderItem={({ item, index }) => {
          const center = index * ITEM_H;
          const near = [center - 2 * ITEM_H, center - ITEM_H, center, center + ITEM_H, center + 2 * ITEM_H];
          return (
            <Animated.View
              style={{
                height: ITEM_H,
                alignItems: 'center',
                justifyContent: 'center',
                opacity: scrollY.interpolate({ inputRange: near, outputRange: [0.25, 0.5, 1, 0.5, 0.25], extrapolate: 'clamp' }),
                transform: [{ scale: scrollY.interpolate({ inputRange: near, outputRange: [0.8, 0.9, 1.1, 0.9, 0.8], extrapolate: 'clamp' }) }],
              }}
            >
              <Animated.Text style={{ fontSize: 22, color: c.text, fontVariant: ['tabular-nums'] }}>{format(item)}</Animated.Text>
            </Animated.View>
          );
        }}
      />
    </View>
  );
});

/** Inclusive integer range helper. */
export function range(from: number, to: number, step = 1): number[] {
  const out: number[] = [];
  for (let v = from; v <= to; v += step) out.push(Math.round(v * 100) / 100);
  return out;
}
