/** About: license, privacy, data sources, medical disclaimer. */
import Constants from 'expo-constants';
import { Stack } from 'expo-router';
import { Linking, View } from 'react-native';

import { Logo } from '../components/Logo';
import { Button, Card, H, Screen, T } from '../components/ui';
import { useT } from '../i18n';
import { useTheme } from '../lib/theme';

const REPO_URL = 'https://github.com/timmylich/wellness';

export default function About() {
  const c = useTheme();
  const L = useT();
  const A = L.about;
  return (
    <Screen>
      <Stack.Screen options={{ title: L.more.about }} />
      <View style={{ alignItems: 'center', gap: 8, marginVertical: 12 }}>
        <Logo size={80} color={c.accent} />
        <T size={24} bold>Wellness</T>
        <T dim>{A.version(Constants.expoConfig?.version ?? '—')}</T>
      </View>
      <Card>
        <H icon="code">{A.openSource}</H>
        <T>{A.openSourceText}</T>
        <Button title={A.github} kind="secondary" onPress={() => Linking.openURL(REPO_URL)} />
      </Card>
      <Card>
        <H icon="info">{A.privacy}</H>
        <T>{A.privacyText}</T>
      </Card>
      <Card>
        <H>{A.sources}</H>
        {A.sourcesList.map((s) => <T key={s} size={14}>• {s}</T>)}
      </Card>
      <Card>
        <H>{A.important}</H>
        <T size={14}>{A.importantText}</T>
      </Card>
    </Screen>
  );
}
