/** Bottom-tab screen for the Health section. */
import { Screen } from '../../components/ui';
import { useT } from '../../i18n';
import { Health } from '../../sections/Health';

export default function HealthTab() {
  const L = useT();
  return (
    <Screen title={L.tabs.health}>
      <Health />
    </Screen>
  );
}
