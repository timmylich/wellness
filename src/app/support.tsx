/** "Support the author": Tribute links and crypto addresses. */
import { Stack } from 'expo-router';
import { View } from 'react-native';

import { DonationOptions } from '../components/DonationOptions';
import { Icon } from '../components/Icon';
import { Card, Screen, T } from '../components/ui';
import { useT } from '../i18n';
import { useTheme } from '../lib/theme';

export default function Support() {
  const c = useTheme();
  const L = useT();
  return (
    <Screen>
      <Stack.Screen options={{ title: L.donation.screenTitle }} />
      <Card style={{ alignItems: 'center', gap: 12, paddingVertical: 24 }}>
        <View style={{ width: 72, height: 72, borderRadius: 24, backgroundColor: c.accentSoft, alignItems: 'center', justifyContent: 'center' }}>
          <Icon name="donate" size={36} color={c.accent} />
        </View>
        <T bold size={19} center>{L.donation.screenTitle}</T>
        <T dim center>{L.donation.screenText}</T>
      </Card>
      <Card>
        <DonationOptions />
      </Card>
    </Screen>
  );
}
