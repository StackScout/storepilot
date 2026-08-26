import { QueryClientProvider } from '@tanstack/react-query';
import * as Notifications from 'expo-notifications';
import { DarkTheme, DefaultTheme, type Href, router, ThemeProvider } from 'expo-router';
import * as SplashScreen from 'expo-splash-screen';
import { useEffect } from 'react';
import { AppState, useColorScheme } from 'react-native';

import { AnimatedSplashOverlay } from '@/components/animated-icon';
import AppTabs from '@/components/app-tabs';
import { LoginScreen } from '@/components/login-screen';
import { queryClient } from '@/lib/query-client';
import { syncPushToken } from '@/lib/push-notifications';
import { useAuthStore } from '@/store/auth-store';

SplashScreen.preventAutoHideAsync();

/** Maps a push notification's data payload (see e.g. BookingNotifier.sellerBookingCreated's `data` map) to the screen it should open. Cast to Href, not typed-routes-inferred: unlike an inline `router.push(\`/bookings/${id}\`)`, a template literal built inside a separate function and returned loses Expo Router's static Href narrowing. */
function routeForNotification(data: Record<string, unknown>): Href | null {
  const type = data.type;
  const id = data.id;
  if (typeof type !== 'string') return null;
  switch (type) {
    case 'order':
      return `/orders/${id}` as Href;
    case 'booking':
      return `/bookings/${id}` as Href;
    case 'product':
      return `/products/${id}` as Href;
    case 'conversation':
      return `/messages/${id}` as Href;
    case 'payout':
      return '/dashboard/settings/payouts' as Href;
    default:
      return null;
  }
}

export default function RootLayout() {
  const colorScheme = useColorScheme();
  const isHydrated = useAuthStore((s) => s.isHydrated);
  const isSignedIn = useAuthStore((s) => s.isSignedIn);
  const hydrate = useAuthStore((s) => s.hydrate);

  useEffect(() => {
    hydrate();
  }, [hydrate]);

  // Re-registers on every foreground (not just sign-in) — a token can go
  // stale (app reinstall, OS-level rotation) and this is the cheapest way
  // to keep it fresh without a dedicated background task.
  useEffect(() => {
    const subscription = AppState.addEventListener('change', (state) => {
      if (state === 'active' && useAuthStore.getState().isSignedIn) {
        void syncPushToken();
      }
    });
    return () => subscription.remove();
  }, []);

  useEffect(() => {
    const subscription = Notifications.addNotificationResponseReceivedListener((response) => {
      const data = response.notification.request.content.data as Record<string, unknown>;
      const route = routeForNotification(data);
      if (route) router.push(route);
    });
    return () => subscription.remove();
  }, []);

  return (
    <QueryClientProvider client={queryClient}>
      <ThemeProvider value={colorScheme === 'dark' ? DarkTheme : DefaultTheme}>
        <AnimatedSplashOverlay />
        {/* Hold the previous splash overlay up until auth state is known, so we never flash the login screen for an already-signed-in seller. */}
        {isHydrated ? isSignedIn ? <AppTabs /> : <LoginScreen /> : null}
      </ThemeProvider>
    </QueryClientProvider>
  );
}
