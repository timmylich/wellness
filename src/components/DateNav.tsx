/** "‹ Today ›" day switcher used by per-day sections. The label slides in the travel direction. */
import { useEffect, useState } from 'react';
import { Animated, Easing, View } from 'react-native';

import { useT } from '../i18n';
import { addDays, humanDate, todayKey } from '../lib/date';
import { dur, useMotion } from '../lib/motion';
import { useTheme } from '../lib/theme';
import { Icon } from './Icon';
import { Row, T, Tap } from './ui';

const SLIDE = 48;

export function DateNav({ date, onChange }: { date: string; onChange: (d: string) => void }) {
  const c = useTheme();
  const L = useT();
  const motion = useMotion();
  const isToday = date >= todayKey();
  // Current and previous label; both are drawn while sliding.
  const [shown, setShown] = useState({ date, prev: null as string | null, dir: 1 });
  const [progress] = useState(() => new Animated.Value(1));
  if (shown.date !== date) setShown({ date, prev: shown.date, dir: date > shown.date ? 1 : -1 });

  useEffect(() => {
    if (!shown.prev || !motion) {
      progress.setValue(1);
      return;
    }
    progress.setValue(0);
    Animated.timing(progress, { toValue: 1, duration: dur(260), easing: Easing.out(Easing.cubic), useNativeDriver: true }).start();
  }, [shown, motion, progress]);

  const slide = (from: number, to: number) => progress.interpolate({ inputRange: [0, 1], outputRange: [from, to] });
  return (
    <Row style={{ justifyContent: 'space-between', flexWrap: 'nowrap' }}>
      <Tap onPress={() => onChange(addDays(date, -1))} hitSlop={12} style={{ padding: 6 }}>
        <Icon name="left" color={c.accent} glow />
      </Tap>
      <View style={{ flex: 1, height: 26, overflow: 'hidden', alignItems: 'center', justifyContent: 'center' }}>
        {shown.prev && motion ? (
          <Animated.View
            pointerEvents="none"
            style={{ position: 'absolute', opacity: slide(1, 0), transform: [{ translateX: slide(0, -SLIDE * shown.dir) }] }}
          >
            <T bold size={16}>{humanDate(shown.prev, L)}</T>
          </Animated.View>
        ) : null}
        <Animated.View style={{ opacity: progress, transform: [{ translateX: slide(SLIDE * shown.dir, 0) }] }}>
          <Tap onPress={() => onChange(todayKey())} scaleTo={1}>
            <T bold size={16}>{humanDate(date, L)}</T>
          </Tap>
        </Animated.View>
      </View>
      <Tap onPress={() => onChange(addDays(date, 1))} disabled={isToday} hitSlop={12} style={{ padding: 6 }}>
        <Icon name="right" color={c.accent} glow={!isToday} />
      </Tap>
    </Row>
  );
}
