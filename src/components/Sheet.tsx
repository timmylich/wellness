/**
 * Bottom sheet with a dimmed backdrop. Uses its own spring animation (Modal's built-in slide is
 * slow and ignores the system animation scale) and extends its background below the screen
 * edge so nothing shows through under rounded corners or the navigation bar.
 */
import { useEffect, useState, type ReactNode } from 'react';
import { Animated, Keyboard, Modal, Pressable, ScrollView, useWindowDimensions, View } from 'react-native';
import { useSafeAreaInsets } from 'react-native-safe-area-context';

import { dur, useMotion } from '../lib/motion';
import { useTheme } from '../lib/theme';

export function Sheet({ visible, onClose, children }: { visible: boolean; onClose: () => void; children: ReactNode }) {
  const c = useTheme();
  const motion = useMotion();
  const insets = useSafeAreaInsets();
  const { height: winH } = useWindowDimensions();
  const [mounted, setMounted] = useState(visible);
  const [progress] = useState(() => new Animated.Value(0));
  const [keyboard, setKeyboard] = useState(0);

  // Mount as soon as it becomes visible; unmount after the closing animation.
  if (visible && !mounted) setMounted(true);
  const shown = motion ? mounted || visible : visible;

  useEffect(() => {
    if (!motion) {
      progress.setValue(visible ? 1 : 0);
      return;
    }
    const anim = visible
      ? Animated.spring(progress, { toValue: 1, useNativeDriver: true, speed: 22, bounciness: 3 })
      : Animated.timing(progress, { toValue: 0, duration: dur(160), useNativeDriver: true });
    anim.start(({ finished }) => finished && !visible && setMounted(false));
  }, [visible, motion, progress]);

  useEffect(() => {
    const show = Keyboard.addListener('keyboardDidShow', (e) => setKeyboard(e.endCoordinates.height));
    const hide = Keyboard.addListener('keyboardDidHide', () => setKeyboard(0));
    return () => {
      show.remove();
      hide.remove();
    };
  }, []);

  if (!shown) return null;
  return (
    <Modal transparent animationType="none" visible onRequestClose={onClose} statusBarTranslucent navigationBarTranslucent>
      <Animated.View style={{ flex: 1, backgroundColor: '#0008', opacity: progress }}>
        <Pressable style={{ flex: 1 }} onPress={onClose} />
      </Animated.View>
      <Animated.View
        style={{
          position: 'absolute',
          left: 0,
          right: 0,
          bottom: keyboard,
          maxHeight: (winH - keyboard) * 0.88,
          backgroundColor: c.card,
          borderTopLeftRadius: 26,
          borderTopRightRadius: 26,
          transform: [{ translateY: progress.interpolate({ inputRange: [0, 1], outputRange: [winH * 0.6, 0] }) }],
        }}
      >
        <View style={{ alignSelf: 'center', width: 40, height: 4, borderRadius: 2, backgroundColor: c.border, marginTop: 10 }} />
        <ScrollView
          contentContainerStyle={{ padding: 20, paddingBottom: 20 + (keyboard ? 0 : insets.bottom), gap: 12 }}
          keyboardShouldPersistTaps="handled"
        >
          {children}
        </ScrollView>
        {/* Background bleed below the edge: covers spring overshoot and screen corners. */}
        <View style={{ position: 'absolute', left: 0, right: 0, top: '100%', height: 400, backgroundColor: c.card }} />
      </Animated.View>
    </Modal>
  );
}
