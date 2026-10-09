/** Shared UI primitives. Keep these small and theme-aware; screens compose them. */
import { useEffect, useState, type ReactNode } from 'react';
import {
  Alert,
  Animated,
  Pressable,
  ScrollView,
  StyleSheet,
  Switch,
  Text,
  TextInput,
  View,
  type StyleProp,
  type TextStyle,
  type ViewStyle,
} from 'react-native';
import { useSafeAreaInsets } from 'react-native-safe-area-context';

import { resolveLang, useT } from '../i18n';
import { animateLayout, dur, useMotion } from '../lib/motion';
import { useTheme } from '../lib/theme';
import { getState, useStore } from '../store/store';
import { Collapse } from './Collapse';
import { Icon, type IconName } from './Icon';

const AnimatedPressable = Animated.createAnimatedComponent(Pressable);

/**
 * Pressable with a springy scale-down on press. Falls back to an opacity change when
 * animations are disabled. Used by every tappable element for consistent feedback.
 */
export function Tap({
  children,
  onPress,
  onLongPress,
  style,
  disabled,
  scaleTo = 0.96,
  hitSlop,
  accessibilityLabel,
}: {
  children: ReactNode;
  onPress?: () => void;
  onLongPress?: () => void;
  style?: StyleProp<ViewStyle>;
  disabled?: boolean;
  scaleTo?: number;
  hitSlop?: number;
  accessibilityLabel?: string;
}) {
  const motion = useMotion();
  const scale = useState(() => new Animated.Value(1))[0];
  const [pressed, setPressed] = useState(false);
  const spring = (to: number) =>
    Animated.spring(scale, { toValue: to, useNativeDriver: true, speed: 40, bounciness: to === 1 ? 8 : 0 }).start();
  return (
    <AnimatedPressable
      onPress={onPress}
      onLongPress={onLongPress}
      disabled={disabled}
      hitSlop={hitSlop}
      accessibilityRole="button"
      accessibilityLabel={accessibilityLabel}
      onPressIn={() => (motion ? spring(scaleTo) : setPressed(true))}
      onPressOut={() => (motion ? spring(1) : setPressed(false))}
      style={[style, { transform: [{ scale }], opacity: disabled ? 0.4 : pressed ? 0.7 : 1 }]}
    >
      {children}
    </AnimatedPressable>
  );
}

export function Screen({
  children,
  scroll = true,
  bottomInset = 0,
}: {
  children: ReactNode;
  scroll?: boolean;
  /** Extra bottom padding, e.g. to clear a floating button. */
  bottomInset?: number;
}) {
  const c = useTheme();
  const insets = useSafeAreaInsets();
  const style = { flex: 1, backgroundColor: c.bg };
  const content = { padding: 16, paddingBottom: 32 + insets.bottom + bottomInset, gap: 12 };
  if (!scroll) return <View style={[style, content]}>{children}</View>;
  return (
    <ScrollView style={style} contentContainerStyle={content} keyboardShouldPersistTaps="handled">
      {children}
    </ScrollView>
  );
}

export function Card({ children, style }: { children: ReactNode; style?: StyleProp<ViewStyle> }) {
  const c = useTheme();
  return <View style={[styles.card, { backgroundColor: c.card, borderColor: c.border }, style]}>{children}</View>;
}

export function T({
  children,
  dim,
  size = 15,
  bold,
  color,
  style,
  center,
  numberOfLines,
}: {
  children: ReactNode;
  dim?: boolean;
  size?: number;
  bold?: boolean;
  color?: string;
  style?: StyleProp<TextStyle>;
  center?: boolean;
  numberOfLines?: number;
}) {
  const c = useTheme();
  return (
    <Text
      numberOfLines={numberOfLines}
      style={[
        {
          color: color ?? (dim ? c.textDim : c.text),
          fontSize: size,
          fontWeight: bold ? '700' : '400',
          textAlign: center ? 'center' : undefined,
          lineHeight: size * 1.35,
        },
        style,
      ]}
    >
      {children}
    </Text>
  );
}

export function H({ children, right, icon }: { children: ReactNode; right?: ReactNode; icon?: IconName }) {
  const c = useTheme();
  return (
    <View style={styles.rowBetween}>
      <Row style={{ flexWrap: 'nowrap', flex: 1 }}>
        {icon ? <Icon name={icon} size={18} color={c.accent} glow /> : null}
        <T size={18} bold style={{ flexShrink: 1 }}>{children}</T>
      </Row>
      {right}
    </View>
  );
}

export function Row({ children, gap = 8, style }: { children: ReactNode; gap?: number; style?: StyleProp<ViewStyle> }) {
  return <View style={[{ flexDirection: 'row', alignItems: 'center', gap, flexWrap: 'wrap' }, style]}>{children}</View>;
}

export function Button({
  title,
  onPress,
  kind = 'primary',
  icon,
  style,
  disabled,
  big,
}: {
  title: string;
  onPress: () => void;
  kind?: 'primary' | 'secondary' | 'danger' | 'ghost';
  icon?: IconName;
  style?: StyleProp<ViewStyle>;
  disabled?: boolean;
  big?: boolean;
}) {
  const c = useTheme();
  const bg = { primary: c.accent, secondary: c.accentSoft, danger: c.danger, ghost: 'transparent' }[kind];
  const fg = kind === 'primary' || kind === 'danger' ? '#fff' : c.accent;
  return (
    <Tap onPress={onPress} disabled={disabled} style={[styles.button, big && { paddingVertical: 16 }, { backgroundColor: bg }, style]}>
      <Row style={{ flexWrap: 'nowrap', justifyContent: 'center' }}>
        {icon ? <Icon name={icon} size={big ? 20 : 18} color={fg} /> : null}
        <Text style={{ color: fg, fontWeight: '600', fontSize: big ? 17 : 15 }}>{title}</Text>
      </Row>
    </Tap>
  );
}

export function Chip({ label, active, onPress, icon }: { label: string; active?: boolean; onPress: () => void; icon?: IconName }) {
  const c = useTheme();
  return (
    <Tap
      onPress={onPress}
      style={[styles.chip, { borderColor: active ? c.accent : c.border, backgroundColor: active ? c.accentSoft : c.card }]}
    >
      <Row gap={6} style={{ flexWrap: 'nowrap' }}>
        {icon ? <Icon name={icon} size={16} color={c.accent} /> : null}
        <Text style={{ color: active ? c.accent : c.text, fontWeight: active ? '600' : '400' }}>{label}</Text>
      </Row>
    </Tap>
  );
}

/** Single-choice selector rendered as a row of chips. */
export function Segmented<V extends string | number>({
  value,
  options,
  onChange,
}: {
  value: V;
  options: { value: V; label: string }[];
  onChange: (v: V) => void;
}) {
  return (
    <Row>
      {options.map((o) => (
        <Chip key={String(o.value)} label={o.label} active={o.value === value} onPress={() => onChange(o.value)} />
      ))}
    </Row>
  );
}

export function Field({
  label,
  value,
  onChange,
  suffix,
  numeric = true,
  placeholder,
  hint,
}: {
  label?: string;
  value: string;
  onChange: (v: string) => void;
  suffix?: string;
  numeric?: boolean;
  placeholder?: string;
  hint?: string;
}) {
  const c = useTheme();
  const [focused, setFocused] = useState(false);
  return (
    // minWidth (not flexBasis) so the field wraps nicely in rows without growing tall in columns.
    <View style={{ gap: 4, flexGrow: 1, minWidth: 120 }}>
      {label ? <T dim size={13}>{label}</T> : null}
      <View style={[styles.input, { borderColor: focused ? c.accent : c.border, backgroundColor: c.cardAlt }]}>
        <TextInput
          value={value}
          onChangeText={(v) => onChange(numeric ? v.replace(',', '.').replace(/[^0-9.]/g, '') : v)}
          keyboardType={numeric ? 'decimal-pad' : 'default'}
          placeholder={placeholder}
          placeholderTextColor={c.textDim}
          onFocus={() => setFocused(true)}
          onBlur={() => setFocused(false)}
          style={{ flex: 1, color: c.text, fontSize: 16, paddingVertical: 10 }}
        />
        {suffix ? <T dim>{suffix}</T> : null}
      </View>
      {hint ? <T dim size={12}>{hint}</T> : null}
    </View>
  );
}

export function Toggle({ label, value, onChange, hint }: { label: string; value: boolean; onChange: (v: boolean) => void; hint?: string }) {
  const c = useTheme();
  return (
    <View style={styles.rowBetween}>
      <View style={{ flex: 1, paddingRight: 12 }}>
        <T>{label}</T>
        {hint ? <T dim size={12}>{hint}</T> : null}
      </View>
      <Switch
        value={value}
        onValueChange={(v) => {
          animateLayout();
          onChange(v);
        }}
        trackColor={{ true: c.accent }}
        thumbColor="#fff"
      />
    </View>
  );
}

/**
 * Collapsible explanation card. Hidden entirely when the user turns hints off.
 * `force` shows it regardless (used for critical safety info).
 */
export function Hint({ title, children, force }: { title: string; children: ReactNode; force?: boolean }) {
  const c = useTheme();
  const enabled = useStore((s) => s.settings.showHints);
  const [open, setOpen] = useState(false);
  if (!enabled && !force) return null;
  return (
    <Tap scaleTo={0.98} onPress={() => setOpen(!open)} style={[styles.hint, { backgroundColor: c.accentSoft }]}>
      <Row style={{ flexWrap: 'nowrap' }}>
        <Icon name="info" size={18} color={c.accent} />
        <T color={c.accent} bold style={{ flex: 1 }}>{title}</T>
        <Chevron open={open} color={c.accent} />
      </Row>
      <Collapse open={open}>
        <View style={{ paddingTop: 8, gap: 6 }}>{typeof children === 'string' ? <T size={14}>{children}</T> : children}</View>
      </Collapse>
    </Tap>
  );
}

/** Down-chevron that rotates to "up" when open. */
export function Chevron({ open, color, size = 18 }: { open: boolean; color: string; size?: number }) {
  const motion = useMotion();
  const [rot] = useState(() => new Animated.Value(open ? 1 : 0));
  useEffect(() => {
    if (motion) Animated.timing(rot, { toValue: open ? 1 : 0, duration: dur(200), useNativeDriver: true }).start();
    else rot.setValue(open ? 1 : 0);
  }, [open, motion, rot]);
  return (
    <Animated.View style={{ transform: [{ rotate: rot.interpolate({ inputRange: [0, 1], outputRange: ['0deg', '180deg'] }) }] }}>
      <Icon name="down" size={size} color={color} />
    </Animated.View>
  );
}

export function Stat({ label, value, unit, color }: { label: string; value: string | number; unit?: string; color?: string }) {
  return (
    <View style={{ flexGrow: 1, flexBasis: 90 }}>
      <T dim size={12}>{label}</T>
      <T size={20} bold color={color}>
        {value}
        {unit ? <T dim size={13}> {unit}</T> : null}
      </T>
    </View>
  );
}

export function Empty({ text }: { text: string }) {
  return <T dim center style={{ paddingVertical: 12 }}>{text}</T>;
}

/** List row; long-press offers deletion. */
export function ListItem({
  title,
  subtitle,
  right,
  left,
  onPress,
  onDelete,
}: {
  title: string;
  subtitle?: string;
  right?: ReactNode;
  left?: ReactNode;
  onPress?: () => void;
  onDelete?: () => void;
}) {
  const c = useTheme();
  const L = useT();
  const confirmDelete = () =>
    onDelete &&
    Alert.alert(L.common.deleteEntry, title, [
      { text: L.common.cancel, style: 'cancel' },
      {
        text: L.common.delete,
        style: 'destructive',
        onPress: () => {
          animateLayout();
          onDelete();
        },
      },
    ]);
  return (
    <Tap scaleTo={0.98} onPress={onPress} onLongPress={confirmDelete} style={[styles.listItem, { borderColor: c.border }]}>
      {left}
      <View style={{ flex: 1 }}>
        <T>{title}</T>
        {subtitle ? <T dim size={12}>{subtitle}</T> : null}
      </View>
      {right}
    </Tap>
  );
}

export function Divider() {
  const c = useTheme();
  return <View style={{ height: StyleSheet.hairlineWidth, backgroundColor: c.border, marginVertical: 4 }} />;
}

/** Parse a user-entered number; returns undefined for empty/invalid input. */
export function parseNum(s: string): number | undefined {
  const n = parseFloat(s.replace(',', '.'));
  return Number.isFinite(n) ? n : undefined;
}

/** Locale-aware number formatting for the current UI language. */
export function fmt(n: number, digits = 0): string {
  const locale = resolveLang(getState().settings.language) === 'ru' ? 'ru-RU' : 'en-US';
  return n.toLocaleString(locale, { maximumFractionDigits: digits, minimumFractionDigits: 0 });
}

const styles = StyleSheet.create({
  card: { borderRadius: 18, padding: 16, gap: 10, borderWidth: StyleSheet.hairlineWidth },
  rowBetween: { flexDirection: 'row', alignItems: 'center', justifyContent: 'space-between' },
  button: { borderRadius: 14, paddingVertical: 12, paddingHorizontal: 16, alignItems: 'center', justifyContent: 'center' },
  chip: { borderRadius: 20, paddingVertical: 7, paddingHorizontal: 14, borderWidth: 1 },
  input: { flexDirection: 'row', alignItems: 'center', borderWidth: 1, borderRadius: 12, paddingHorizontal: 12, gap: 6 },
  hint: { borderRadius: 14, padding: 12 },
  listItem: { flexDirection: 'row', alignItems: 'center', paddingVertical: 10, borderBottomWidth: StyleSheet.hairlineWidth, gap: 10 },
});
