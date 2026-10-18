/** Root navigator: loads persisted data and applies the theme. Onboarding gate lives in (tabs)/_layout. */
// Defines the background step task at module scope, before Android may invoke it.
import '../lib/backgroundSteps';

import { Stack } from 'expo-router';
import * as SplashScreen from 'expo-splash-screen';
import { StatusBar } from 'expo-status-bar';
import { useEffect } from 'react';
import { SafeAreaProvider } from 'react-native-safe-area-context';

import { DialogHost } from '../components/Dialog';
import { DonationPrompt } from '../components/DonationPrompt';
import { useT } from '../i18n';
import { useMotion } from '../lib/motion';
import { useTheme } from '../lib/theme';
import { maybeCheckForUpdate } from '../lib/updates';
import { useStepTracking } from '../lib/useStepCounter';
import { hydrate, useHydrated, useStore } from '../store/store';

// Keep the native splash (logo) until stored data is loaded, instead of flashing a placeholder.
SplashScreen.preventAutoHideAsync().catch(() => {});

export default function RootLayout() {
  const c = useTheme();
  const L = useT();
  const motion = useMotion();
  const ready = useHydrated();
  const onboarded = useStore((s) => s.meta.onboarded);
  const stepsEnabled = useStore((s) => s.settings.sections.activity);
  const stepsSource = useStore((s) => s.settings.stepsSource);

  useEffect(() => {
    hydrate();
  }, []);
  useEffect(() => {
    if (ready) SplashScreen.hide();
  }, [ready]);
  useStepTracking(ready && onboarded && stepsEnabled, stepsSource);
  useEffect(() => {
    if (ready) maybeCheckForUpdate((v) => ({ title: L.settings.updateTitle, body: L.settings.updateBody(v) }));
    // Once per launch; the check itself is rate-limited to once a day.
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [ready]);

  if (!ready) return null;

  return (
    <SafeAreaProvider>
      <StatusBar style={c.dark ? 'light' : 'dark'} />
      <Stack
        screenOptions={{
          headerStyle: { backgroundColor: c.card },
          headerTintColor: c.text,
          headerShadowVisible: false,
          contentStyle: { backgroundColor: c.bg },
          // The platform default transition is native and follows the system animation scale.
          animation: motion ? 'default' : 'none',
        }}
      >
        <Stack.Screen name="(tabs)" options={{ headerShown: false }} />
        <Stack.Screen name="onboarding" options={{ headerShown: false }} />
      </Stack>
      <DonationPrompt />
      <DialogHost okText={L.common.done} />
    </SafeAreaProvider>
  );
}
