/**
 * Keeps inputs above the on-screen keyboard. With edge-to-edge layouts Android no longer
 * resizes the window, so scroll containers add the keyboard height as bottom padding and
 * scroll the focused field into view.
 */
import { useEffect, useRef, useState } from 'react';
import { Keyboard, TextInput, type ScrollView, type View } from 'react-native';

/** Space kept above the focused field when scrolling it into view. */
const FIELD_MARGIN = 96;

export function useKeyboardAvoid() {
  const [keyboard, setKeyboard] = useState(0);
  const scrollRef = useRef<ScrollView>(null);
  const contentRef = useRef<View>(null);
  useEffect(() => {
    const show = Keyboard.addListener('keyboardDidShow', (e) => {
      setKeyboard(e.endCoordinates.height);
      setTimeout(() => {
        const input = TextInput.State.currentlyFocusedInput();
        if (!input || !contentRef.current) return;
        input.measureLayout(contentRef.current, (_x, y) => scrollRef.current?.scrollTo({ y: Math.max(0, y - FIELD_MARGIN), animated: true }), () => {});
      }, 60);
    });
    const hide = Keyboard.addListener('keyboardDidHide', () => setKeyboard(0));
    return () => {
      show.remove();
      hide.remove();
    };
  }, []);
  return { keyboard, scrollRef, contentRef };
}
