/** Day picker with − / + steps around a readable date. */
import { View } from 'react-native';

import { useT } from '../i18n';
import { addDays, humanDate } from '../lib/date';
import { useTheme } from '../lib/theme';
import { Icon } from './Icon';
import { Row, T, Tap } from './ui';

export function DateStep({ label, value, onChange }: { label: string; value: string; onChange: (d: string) => void }) {
  const c = useTheme();
  const L = useT();
  return (
    <View style={{ gap: 4 }}>
      <T dim size={13}>{label}</T>
      <Row style={{ flexWrap: 'nowrap' }}>
        <Tap onPress={() => onChange(addDays(value, -1))} style={{ padding: 10, borderRadius: 12, backgroundColor: c.accentSoft }}>
          <Icon name="minus" size={18} color={c.accent} />
        </Tap>
        <T bold center style={{ flex: 1 }}>{humanDate(value, L)}</T>
        <Tap onPress={() => onChange(addDays(value, 1))} style={{ padding: 10, borderRadius: 12, backgroundColor: c.accentSoft }}>
          <Icon name="plus" size={18} color={c.accent} />
        </Tap>
      </Row>
    </View>
  );
}
