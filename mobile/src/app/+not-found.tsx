import { Redirect } from 'expo-router';

import { useAuthStore } from '@/store/auth-store';

/**
 * Expo Router's built-in fallback for any unmatched path — including the
 * bare/initial URL Expo Go opens the project with, which has no file route
 * of its own (the root Stack only declares the `(app)` and `stores` groups,
 * neither of which has an index at the bare path). Redirects to the same
 * first screen AppLayout ((app)/_layout.tsx) would otherwise land on,
 * mirroring its exact seller-vs-everyone-else decision so this doesn't
 * drift out of sync with it.
 */
export default function NotFoundScreen() {
  const isHydrated = useAuthStore((s) => s.isHydrated);
  const isSignedIn = useAuthStore((s) => s.isSignedIn);
  const role = useAuthStore((s) => s.role);

  if (!isHydrated) return null;
  return <Redirect href={isSignedIn && role === 'seller' ? '/dashboard' : '/home'} />;
}
