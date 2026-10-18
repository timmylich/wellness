/**
 * App-styled replacement for Android's system Alert. Call `showDialog()` from anywhere; the
 * single `<DialogHost />` in the root layout renders the queue one dialog at a time.
 */
import { useEffect, useState, useSyncExternalStore } from 'react';
import { Animated, Modal, Pressable, View } from 'react-native';

import { dur, useMotion } from '../lib/motion';
import { useTheme } from '../lib/theme';
import { Icon, type IconName } from './Icon';
import { Button, T } from './ui';

export interface DialogButton {
  text: string;
  kind?: 'primary' | 'secondary' | 'danger' | 'ghost';
  onPress?: () => void;
}

export interface DialogOptions {
  title: string;
  message?: string;
  icon?: IconName;
  /** Defaults to a single "OK"-style button that just closes. */
  buttons?: DialogButton[];
}

let queue: DialogOptions[] = [];
const listeners = new Set<() => void>();
const emit = () => listeners.forEach((l) => l());

export function showDialog(opts: DialogOptions) {
  queue = [...queue, opts];
  emit();
}

/** Destructive confirmation: Cancel + a red action button. */
export function confirmDialog(opts: { title: string; message?: string; cancel: string; ok: string; onOk: () => void; icon?: IconName }) {
  showDialog({
    title: opts.title,
    message: opts.message,
    icon: opts.icon ?? 'trash',
    buttons: [
      { text: opts.cancel, kind: 'secondary' },
      { text: opts.ok, kind: 'danger', onPress: opts.onOk },
    ],
  });
}

function closeCurrent() {
  queue = queue.slice(1);
  emit();
}

const subscribe = (l: () => void) => {
  listeners.add(l);
  return () => listeners.delete(l);
};

export function DialogHost({ okText }: { okText: string }) {
  const c = useTheme();
  const motion = useMotion();
  const current = useSyncExternalStore(subscribe, () => queue[0] ?? null);
  const [progress] = useState(() => new Animated.Value(0));

  useEffect(() => {
    if (!current) return;
    progress.setValue(motion ? 0 : 1);
    if (motion) Animated.spring(progress, { toValue: 1, useNativeDriver: true, speed: 22, bounciness: 5 }).start();
  }, [current, motion, progress]);

  const close = (after?: () => void) => {
    const done = () => {
      closeCurrent();
      after?.();
    };
    if (!motion) return done();
    Animated.timing(progress, { toValue: 0, duration: dur(120), useNativeDriver: true }).start(done);
  };

  if (!current) return null;
  const buttons = current.buttons?.length ? current.buttons : [{ text: okText, kind: 'primary' as const }];
  return (
    <Modal transparent visible animationType="none" statusBarTranslucent navigationBarTranslucent onRequestClose={() => close()}>
      <Animated.View style={{ flex: 1, backgroundColor: 'rgba(0,0,0,0.45)', opacity: progress, justifyContent: 'center', padding: 28 }}>
        <Pressable style={{ position: 'absolute', inset: 0 }} onPress={() => close()} />
        <Animated.View
          style={{
            backgroundColor: c.card,
            borderRadius: 24,
            padding: 20,
            gap: 12,
            borderWidth: 1,
            borderColor: c.border,
            transform: [{ scale: progress.interpolate({ inputRange: [0, 1], outputRange: [0.92, 1] }) }],
          }}
        >
          {current.icon ? (
            <View style={{ width: 48, height: 48, borderRadius: 16, backgroundColor: c.accentSoft, alignItems: 'center', justifyContent: 'center', alignSelf: 'center' }}>
              <Icon name={current.icon} size={24} color={c.accent} />
            </View>
          ) : null}
          <T size={18} bold center>{current.title}</T>
          {current.message ? <T dim center>{current.message}</T> : null}
          <View style={{ flexDirection: 'row', gap: 10, marginTop: 4 }}>
            {buttons.map((b, i) => (
              <Button key={i} title={b.text} kind={b.kind ?? 'secondary'} style={{ flex: 1 }} onPress={() => close(b.onPress)} />
            ))}
          </View>
        </Animated.View>
      </Animated.View>
    </Modal>
  );
}
