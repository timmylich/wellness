/**
 * Support request shown on usage milestones (see lib/donation.ts), or on demand from
 * Settings → Support. Offers a GitHub star, financial support (opens /support) and Telegram.
 */
import { router } from 'expo-router';
import { useMemo, useSyncExternalStore } from 'react';
import { Linking, Modal, ScrollView, View } from 'react-native';

import { REPO_URL, TELEGRAM_URL } from '../data/donate';
import { useT } from '../i18n';
import { getDonationPreview, pendingMilestone, setDonationPreview, subscribeDonationPreview, weightLost } from '../lib/donation';
import { useMotion } from '../lib/motion';
import { useTheme } from '../lib/theme';
import { markDonationShown } from '../store/actions';
import { getState, useStore } from '../store/store';
import { Icon } from './Icon';
import { Button, T } from './ui';

export function DonationPrompt() {
  const c = useTheme();
  const L = useT();
  const motion = useMotion();
  const onboarded = useStore((s) => s.meta.onboarded);
  const days = useStore((s) => s.meta.usageDays.length);
  const shown = useStore((s) => s.meta.donationShown);
  const preview = useSyncExternalStore(subscribeDonationPreview, getDonationPreview);
  const milestone = onboarded ? pendingMilestone(days, shown) : null;
  const open = milestone != null || preview;
  // Computed once per opening; the store snapshot is enough for a rough figure.
  const lost = useMemo(() => (open ? weightLost(getState()) : null), [open]);

  if (!open) return null;
  const close = () => {
    if (preview) setDonationPreview(false);
    else if (milestone != null) markDonationShown(milestone);
  };
  const go = (fn: () => void) => {
    close();
    fn();
  };

  return (
    <Modal transparent animationType={motion ? 'fade' : 'none'} visible onRequestClose={close} statusBarTranslucent>
      <View style={{ flex: 1, backgroundColor: '#0008', justifyContent: 'center', padding: 24 }}>
        <View style={{ backgroundColor: c.card, borderRadius: 24, maxHeight: '90%', overflow: 'hidden' }}>
          <ScrollView contentContainerStyle={{ padding: 24, gap: 12 }}>
            <View style={{ alignSelf: 'center', width: 72, height: 72, borderRadius: 24, backgroundColor: c.accentSoft, alignItems: 'center', justifyContent: 'center' }}>
              <Icon name="donate" size={36} color={c.accent} />
            </View>
            <T size={20} bold center>{milestone != null && !preview ? L.donation.title(milestone) : L.donation.previewTitle}</T>
            {lost != null ? <T center color={c.ok} bold>{L.donation.lost(lost)}</T> : null}
            <T center dim>{L.donation.text}</T>
            <Button title={L.donation.star} icon="star" kind="secondary" onPress={() => go(() => Linking.openURL(REPO_URL).catch(() => {}))} />
            <Button title={L.donation.money} subtitle={L.donation.moneyHint} icon="donate" onPress={() => go(() => router.push('/support'))} />
            <Button title={L.donation.telegram} icon="send" kind="secondary" onPress={() => go(() => Linking.openURL(TELEGRAM_URL).catch(() => {}))} />
            <Button title={L.donation.later} kind="ghost" onPress={close} />
          </ScrollView>
        </View>
      </View>
    </Modal>
  );
}
