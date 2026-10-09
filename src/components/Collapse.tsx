/**
 * Animated expand/collapse. Content is measured off-screen once, then the container height
 * and opacity are tweened, which works reliably on Android (unlike LayoutAnimation).
 */
import { useEffect, useState, type ReactNode } from 'react';
import { Animated, Easing, View } from 'react-native';

import { dur, useMotion } from '../lib/motion';

export function Collapse({ open, children }: { open: boolean; children: ReactNode }) {
  const motion = useMotion();
  const [height, setHeight] = useState(0);
  const [progress] = useState(() => new Animated.Value(open ? 1 : 0));
  // Keep content mounted while closing so it can animate out.
  const [mounted, setMounted] = useState(open);
  // Mount immediately on open (state adjustment during render, no extra effect pass).
  if (open && !mounted) setMounted(true);
  const visible = motion ? mounted || open : open;

  useEffect(() => {
    if (!motion) {
      progress.setValue(open ? 1 : 0);
      return;
    }
    Animated.timing(progress, {
      toValue: open ? 1 : 0,
      duration: dur(open ? 240 : 180),
      easing: Easing.out(Easing.cubic),
      useNativeDriver: false,
    }).start(({ finished }) => finished && !open && setMounted(false));
  }, [open, motion, progress]);

  if (!visible) return null;
  return (
    <Animated.View
      style={{
        height: progress.interpolate({ inputRange: [0, 1], outputRange: [0, height] }),
        opacity: progress,
        overflow: 'hidden',
      }}
    >
      {/* Absolute, so the measured height is the natural content height, not the animated one. */}
      <View
        style={{ position: 'absolute', left: 0, right: 0, top: 0 }}
        onLayout={(e) => {
          const h = Math.ceil(e.nativeEvent.layout.height);
          if (h && Math.abs(h - height) > 1) setHeight(h);
        }}
      >
        {children}
      </View>
    </Animated.View>
  );
}
