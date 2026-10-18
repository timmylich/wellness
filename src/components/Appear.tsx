/** Fades and slides its children in when mounted. Give it a `key` to replay (e.g. per date). */
import { useEffect, useState, type ReactNode } from 'react';
import { Animated, Easing } from 'react-native';

import { dur, useMotion } from '../lib/motion';

export function Appear({ children, delay = 0 }: { children: ReactNode; delay?: number }) {
  const motion = useMotion();
  const [v] = useState(() => new Animated.Value(motion ? 0 : 1));
  useEffect(() => {
    if (!motion) return;
    Animated.timing(v, { toValue: 1, duration: dur(260), delay: dur(delay), easing: Easing.out(Easing.cubic), useNativeDriver: true }).start();
  }, [motion, v, delay]);
  return (
    <Animated.View style={{ gap: 8, opacity: v, transform: [{ translateY: v.interpolate({ inputRange: [0, 1], outputRange: [12, 0] }) }] }}>
      {children}
    </Animated.View>
  );
}
