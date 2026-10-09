/** "‹ Today ›" day switcher used by per-day sections. */
import { useT } from '../i18n';
import { addDays, humanDate, todayKey } from '../lib/date';
import { useTheme } from '../lib/theme';
import { Icon } from './Icon';
import { Row, T, Tap } from './ui';

export function DateNav({ date, onChange }: { date: string; onChange: (d: string) => void }) {
  const c = useTheme();
  const L = useT();
  const isToday = date >= todayKey();
  return (
    <Row style={{ justifyContent: 'space-between', flexWrap: 'nowrap' }}>
      <Tap onPress={() => onChange(addDays(date, -1))} hitSlop={12} style={{ padding: 6 }}>
        <Icon name="left" color={c.accent} glow />
      </Tap>
      <Tap onPress={() => onChange(todayKey())}>
        <T bold size={16}>{humanDate(date, L)}</T>
      </Tap>
      <Tap onPress={() => onChange(addDays(date, 1))} disabled={isToday} hitSlop={12} style={{ padding: 6 }}>
        <Icon name="right" color={c.accent} glow={!isToday} />
      </Tap>
    </Row>
  );
}
