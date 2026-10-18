/**
 * Bottom sheet with a dimmed backdrop. Uses its own spring animation (Modal's built-in slide is
 * slow and ignores the system animation scale) and extends its background below the screen
 * edge so nothing shows through under rounded corners or the navigation bar.
 *
 * - Drag the grabber (or the content when scrolled to the top) down to close.
 * - The last content stays rendered while the sheet animates out, so callers can clear their
 *   state in onClose without the sheet going blank first.
 * - Sits on top of the keyboard and scrolls the focused field into view.
 */
import { useEffect, useRef, useState, type ReactNode } from 'react';
import {
  Animated,
  Keyboard,
  Modal,
  PanResponder,
  Pressable,
  ScrollView,
  TextInput,
  useWindowDimensions,
  View,
  type NativeScrollEvent,
  type NativeSyntheticEvent,
} from 'react-native';
import { useSafeAreaInsets } from 'react-native-safe-area-context';

import { dur, useMotion } from '../lib/motion';
import { useTheme } from '../lib/theme';

// Set while a finger is on a control that scrolls vertically itself (e.g. a Wheel).
let dragBlocked = false;
export function blockSheetDrag(on: boolean) {
  dragBlocked = on;
}

/** Slide out immediately (the visible=false effect then continues from the current value). */
function startClosing(progress: Animated.Value) {
  Animated.timing(progress, { toValue: 0, duration: dur(180), useNativeDriver: true }).start();
}

/** Distance or speed of a downward drag that closes the sheet. */
const DISMISS_DY = 110;
const DISMISS_VY = 1.1;

export function Sheet({ visible, onClose, children }: { visible: boolean; onClose: () => void; children: ReactNode }) {
  const c = useTheme();
  const motion = useMotion();
  const insets = useSafeAreaInsets();
  const { height: winH } = useWindowDimensions();
  const [mounted, setMounted] = useState(visible);
  const [kept, setKept] = useState<ReactNode>(children);
  const [progress] = useState(() => new Animated.Value(0));
  const [keyboard, setKeyboard] = useState(0);
  const scroll = useRef<ScrollView>(null);
  const content = useRef<View>(null);
  const [drag] = useState(() => new Animated.Value(0));
  const [dismissed, setDismissed] = useState(0);
  const [{ grab, body, onScroll }] = useState(() => {
    // Content scroll position, captured in this closure so the responders can read it.
    let scrollY = 0;
    const release = (_: unknown, g: { dy: number; vy: number }) => {
      if (g.dy > DISMISS_DY || g.vy > DISMISS_VY) {
        // Start sliding out right away; the caller's re-render may take a few frames.
        startClosing(progress);
        setDismissed((n) => n + 1);
      }
      else Animated.spring(drag, { toValue: 0, useNativeDriver: true, speed: 24, bounciness: 4 }).start();
    };
    const reset = () => Animated.spring(drag, { toValue: 0, useNativeDriver: true }).start();
    const move = (_: unknown, g: { dy: number }) => drag.setValue(Math.max(0, g.dy));
    // The grabber strip always drags.
    const grabber = PanResponder.create({
      onStartShouldSetPanResponder: () => true,
      onMoveShouldSetPanResponder: () => true,
      onPanResponderTerminationRequest: () => false,
      onPanResponderMove: move,
      onPanResponderRelease: release,
      onPanResponderTerminate: reset,
    });
    // The content drags only when it is scrolled to the top and the gesture is downward.
    const wantsDrag = (dy: number, dx: number) => !dragBlocked && scrollY <= 0 && dy > 10 && Math.abs(dy) > Math.abs(dx) * 1.5;
    const content = PanResponder.create({
      onMoveShouldSetPanResponderCapture: (_, g) => wantsDrag(g.dy, g.dx),
      onMoveShouldSetPanResponder: (_, g) => wantsDrag(g.dy, g.dx),
      onPanResponderTerminationRequest: () => false,
      onPanResponderMove: move,
      onPanResponderRelease: release,
      onPanResponderTerminate: reset,
    });
    return {
      grab: grabber,
      body: content,
      onScroll: (e: NativeSyntheticEvent<NativeScrollEvent>) => {
        scrollY = e.nativeEvent.contentOffset.y;
      },
    };
  });
  useEffect(() => {
    if (dismissed) onClose();
    // Only a new swipe-dismiss should close; onClose identity changes every render.
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [dismissed]);

  // Mount as soon as it becomes visible; unmount after the closing animation.
  if (visible && !mounted) setMounted(true);
  // Remember the content while visible so it can animate out unchanged.
  if (visible && children !== kept) setKept(children);
  const shown = motion ? mounted || visible : visible;

  useEffect(() => {
    if (visible) drag.setValue(0);
    if (!motion) {
      progress.setValue(visible ? 1 : 0);
      return;
    }
    const anim = visible
      ? Animated.spring(progress, { toValue: 1, useNativeDriver: true, speed: 22, bounciness: 3 })
      : Animated.timing(progress, { toValue: 0, duration: dur(180), useNativeDriver: true });
    anim.start(({ finished }) => finished && !visible && setMounted(false));
  }, [visible, motion, progress, drag]);

  useEffect(() => {
    const show = Keyboard.addListener('keyboardDidShow', (e) => {
      setKeyboard(e.endCoordinates.height);
      // Bring the focused field into view once the sheet has moved above the keyboard.
      setTimeout(() => {
        const input = TextInput.State.currentlyFocusedInput();
        if (!input || !content.current) return;
        input.measureLayout(content.current, (_x, y) => scroll.current?.scrollTo({ y: Math.max(0, y - 80), animated: true }), () => {});
      }, 60);
    });
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
        <Pressable
          style={{ flex: 1 }}
          onPress={() => {
            startClosing(progress);
            onClose();
          }}
        />
      </Animated.View>
      <Animated.View
        {...body.panHandlers}
        style={{
          position: 'absolute',
          left: 0,
          right: 0,
          bottom: keyboard,
          maxHeight: (winH - keyboard) * 0.9,
          backgroundColor: c.card,
          borderTopLeftRadius: 26,
          borderTopRightRadius: 26,
          transform: [{ translateY: Animated.add(progress.interpolate({ inputRange: [0, 1], outputRange: [winH * 0.6, 0] }), drag) }],
        }}
      >
        {/* Grabber: a tall strip so it is easy to catch. */}
        <View {...grab.panHandlers} style={{ paddingTop: 10, paddingBottom: 12 }}>
          <View style={{ alignSelf: 'center', width: 44, height: 5, borderRadius: 3, backgroundColor: c.border }} />
        </View>
        <ScrollView
          ref={scroll}
          scrollEventThrottle={32}
          onScroll={onScroll}
          contentContainerStyle={{ paddingHorizontal: 20, paddingBottom: 20 + (keyboard ? 0 : insets.bottom) }}
          keyboardShouldPersistTaps="handled"
        >
          <View ref={content} style={{ gap: 12 }}>
            {visible ? children : kept}
          </View>
        </ScrollView>
        {/* Background bleed below the edge: covers spring overshoot and screen corners. */}
        <View style={{ position: 'absolute', left: 0, right: 0, top: '100%', height: 400, backgroundColor: c.card }} />
      </Animated.View>
    </Modal>
  );
}
