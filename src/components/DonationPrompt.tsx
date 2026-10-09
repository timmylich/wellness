/**
 * One-time donation request shown on usage milestones (see lib/donation.ts).
 * Placeholder for now: no payment integration, just a friendly message.
 */
import { Modal, View } from 'react-native';

import { useT } from '../i18n';
import { pendingMilestone } from '../lib/donation';
import { useMotion } from '../lib/motion';
import { useTheme } from '../lib/theme';
import { markDonationShown } from '../store/actions';
import { useStore } from '../store/store';
import { Icon } from './Icon';
import { Button, T } from './ui';

export function DonationPrompt() {
  const c = useTheme();
  const L = useT();
  const motion = useMotion();
  const enabled = useStore((s) => s.settings.donationPrompts);
  const onboarded = useStore((s) => s.meta.onboarded);
  const days = useStore((s) => s.meta.usageDays.length);
  const shown = useStore((s) => s.meta.donationShown);
  const milestone = enabled && onboarded ? pendingMilestone(days, shown) : null;

  if (milestone == null) return null;
  const close = () => markDonationShown(milestone);

  return (
    <Modal transparent animationType={motion ? 'fade' : 'none'} visible onRequestClose={close}>
      <View style={{ flex: 1, backgroundColor: '#0008', justifyContent: 'center', padding: 24 }}>
        <View style={{ backgroundColor: c.card, borderRadius: 24, padding: 24, gap: 14, alignItems: 'stretch' }}>
          <View style={{ alignItems: 'center' }}>
            <Icon name="donate" size={44} color={c.accent} glow />
          </View>
          <T size={20} bold center>{L.donation.title(milestone)}</T>
          <T center dim>{L.donation.text}</T>
          <Button title={L.donation.support} onPress={close} />
          <Button title={L.donation.later} kind="ghost" onPress={close} />
          <T dim size={12} center>{L.donation.note}</T>
        </View>
      </View>
    </Modal>
  );
}
