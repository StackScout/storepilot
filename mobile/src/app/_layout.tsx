import { QueryClientProvider } from '@tanstack/react-query';
import { DarkTheme, DefaultTheme, type Href, router, Stack, ThemeProvider } from 'expo-router';
import * as SplashScreen from 'expo-splash-screen';
import { useEffect } from 'react';
import { AppState, useColorScheme } from 'react-native';

import { AnimatedSplashOverlay } from '@/components/animated-icon';
import { BiometricLockScreen } from '@/components/biometric-lock-screen';
import { queryClient } from '@/lib/query-client';
import { NotificationsApi, syncPushToken } from '@/lib/push-notifications';
import { useAuthStore } from '@/store/auth-store';

SplashScreen.preventAutoHideAsync();

/**
 * Maps a push notification's data payload (see e.g. BookingNotifier.sellerBookingCreated's
 * `data` map) to the screen it should open. Cast to Href, not
 * typed-routes-inferred: unlike an inline `router.push(\`/bookings/${id}\`)`,
 * a template literal built inside a separate function and returned loses
 * Expo Router's static Href narrowing.
 *
 * Buyer and seller notifications share the same (type, id) vocabulary (see
 * BuyerNotificationType/SellerNotificationType's doc comments) but resolve
 * to different routes — only one role is ever signed in at a time, so the
 * currently signed-in role (not the payload) decides which tree a tap opens.
 */
function routeForNotification(data: Record<string, unknown>): Href | null {
  const type = data.type;
  const id = data.id;
  if (typeof type !== 'string') return null;
  const isBuyer = useAuthStore.getState().role === 'buyer';
  switch (type) {
    case 'order':
      return (isBuyer ? `/account/orders/${id}` : `/orders/${id}`) as Href;
    case 'booking':
      return (isBuyer ? `/account/bookings/${id}` : `/bookings/${id}`) as Href;
    case 'product':
      return `/products/${id}` as Href;
    case 'conversation':
      return (isBuyer ? `/account/messages/${id}` : `/messages/${id}`) as Href;
    case 'payout':
      return '/dashboard/settings/payouts' as Href;
    default:
      return null;
  }
}

export default function RootLayout() {
  const colorScheme = useColorScheme();
  const hydrate = useAuthStore((s) => s.hydrate);
  const isHydrated = useAuthStore((s) => s.isHydrated);
  const isLocked = useAuthStore((s) => s.isLocked);

  useEffect(() => {
    hydrate();
  }, [hydrate]);

  // Re-registers on every foreground (not just sign-in) — a token can go
  // stale (app reinstall, OS-level rotation) and this is the cheapest way
  // to keep it fresh without a dedicated background task. Backgrounding
  // re-locks immediately (no grace period) whenever the biometric
  // preference is on — lock() itself is a no-op otherwise.
  useEffect(() => {
    const subscription = AppState.addEventListener('change', (state) => {
      if (state === 'active' && useAuthStore.getState().isSignedIn) {
        void syncPushToken(useAuthStore.getState().role);
      } else if (state === 'background') {
        useAuthStore.getState().lock();
      }
    });
    return () => subscription.remove();
  }, []);

  useEffect(() => {
    // Same Expo-Go-on-Android restriction as push-notifications.ts — this
    // listener is meaningless there anyway (no remote pushes to tap), and
    // expo-notifications isn't even loaded (NotificationsApi is null) in
    // that environment.
    if (!NotificationsApi) return;
    const subscription = NotificationsApi.addNotificationResponseReceivedListener((response) => {
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
        <Stack screenOptions={{ headerShown: false }}>
          <Stack.Screen name="(app)" />
        </Stack>
        {/* Drawn as an overlay (like AnimatedSplashOverlay above), never swapped in for the Stack — unmounting the whole navigator on every lock/unlock caused "state update on a component that hasn't mounted yet" from its in-flight async init, and would also reset in-app navigation state on every background/foreground cycle. */}
        {isHydrated && isLocked ? <BiometricLockScreen /> : null}
      </ThemeProvider>
    </QueryClientProvider>
  );
}
