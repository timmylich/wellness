/** Donation channels: Tribute (Telegram / web) and crypto addresses (tap to copy). */
import * as Clipboard from 'expo-clipboard';
import * as Haptics from 'expo-haptics';
import { useState } from 'react';
import { Linking, View } from 'react-native';

import { TRIBUTE_TELEGRAM_URL, TRIBUTE_WEB_URL, WALLETS } from '../data/donate';
import { useT } from '../i18n';
import { alpha, useTheme } from '../lib/theme';
import { Icon } from './Icon';
import { Button, T, Tap } from './ui';

export function DonationOptions() {
  const c = useTheme();
  const L = useT();
  const [copied, setCopied] = useState<string | null>(null);

  const copy = async (id: string, address: string) => {
    await Clipboard.setStringAsync(address);
    Haptics.notificationAsync(Haptics.NotificationFeedbackType.Success).catch(() => {});
    setCopied(id);
  };

  return (
    <View style={{ gap: 10 }}>
      <T bold>{L.donation.tribute}</T>
      <T dim size={12}>{L.donation.tributeHint}</T>
      <Button title={L.donation.tributeTelegram} icon="send" onPress={() => Linking.openURL(TRIBUTE_TELEGRAM_URL).catch(() => {})} />
      <Button title={L.donation.tributeWeb} icon="globe" kind="secondary" onPress={() => Linking.openURL(TRIBUTE_WEB_URL).catch(() => {})} />
      <T bold style={{ marginTop: 6 }}>{L.donation.crypto}</T>
      {WALLETS.map((w) => (
        <Tap
          key={w.id}
          scaleTo={0.98}
          onPress={() => copy(w.id, w.address)}
          accessibilityLabel={`${L.donation.wallets[w.id]}: ${L.donation.copy}`}
          style={{ borderRadius: 14, padding: 12, gap: 4, backgroundColor: c.cardAlt }}
        >
          <View style={{ flexDirection: 'row', alignItems: 'center', justifyContent: 'space-between' }}>
            <T bold>{L.donation.wallets[w.id]}</T>
            <Icon name={copied === w.id ? 'check' : 'copy'} size={18} color={c.accent} />
          </View>
          <T dim size={12}>{w.networks}</T>
          <T size={12} style={{ fontFamily: 'monospace', color: copied === w.id ? c.accent : c.text, backgroundColor: alpha(c.accent, 0.06), padding: 4, borderRadius: 6 }}>
            {copied === w.id ? L.donation.copied : w.address}
          </T>
        </Tap>
      ))}
      <T dim size={11}>{L.donation.evmNote}</T>
    </View>
  );
}
