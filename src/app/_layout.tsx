/** Root navigator: loads persisted data and applies the theme. Onboarding gate lives in (tabs)/_layout. */
// Defines the background step task at module scope, before Android may invoke it.
import '../lib/backgroundSteps';

import { Stack } from 'expo-router';
import * as SplashScreen from 'expo-splash-screen';
import { StatusBar } from 'expo-status-bar';
import { useEffect } from 'react';
import { SafeAreaProvider } from 'react-native-safe-area-context';

import { DonationPrompt } from '../components/DonationPrompt';
import { useMotion } from '../lib/motion';
import { firebaseProvider } from '../lib/firebaseMessaging';
import { initNotifications, setNotificationProvider } from '../lib/notifications';
import { useTheme } from '../lib/theme';
import { useStepTracking } from '../lib/useStepCounter';
import { hydrate, useHydrated, useStore } from '../store/store';

// Push notifications go through Firebase Cloud Messaging (opt-in, see Settings → Notifications).
setNotificationProvider(firebaseProvider);

// Keep the native splash (logo) until stored data is loaded, instead of flashing a placeholder.
SplashScreen.preventAutoHideAsync().catch(() => {});

export default function RootLayout() {
  const c = useTheme();
  const motion = useMotion();
  const ready = useHydrated();
  const onboarded = useStore((s) => s.meta.onboarded);
  const stepsEnabled = useStore((s) => s.settings.sections.activity);
  const stepsSource = useStore((s) => s.settings.stepsSource);
  const notificationsOn = useStore((s) => s.settings.notifications.enabled);

  useEffect(() => {
    hydrate();
  }, []);
  useEffect(() => {
    if (ready) SplashScreen.hide();
  }, [ready]);
  useEffect(() => (ready ? initNotifications() : undefined), [ready, notificationsOn]);
  useStepTracking(ready && onboarded && stepsEnabled, stepsSource);

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
    </SafeAreaProvider>
  );
}
