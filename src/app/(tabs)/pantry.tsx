/** Bottom-tab screen for the Pantry section. */
import { Screen } from '../../components/ui';
import { useT } from '../../i18n';
import { Pantry } from '../../sections/Pantry';

export default function PantryTab() {
  const L = useT();
  return (
    <Screen title={L.tabs.pantry}>
      <Pantry />
    </Screen>
  );
}
