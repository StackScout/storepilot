import { create } from 'zustand';

import { biometricPreference, tokenStorage } from '@/lib/secure-storage';
import { clearPushToken, syncPushToken } from '@/lib/push-notifications';

type AuthState = {
  /** Undefined until hydrate() resolves — lets the root layout show a splash instead of flashing the login screen. */
  isHydrated: boolean;
  isSignedIn: boolean;
  role: string | null;
  email: string | null;
  name: string | null;
  /** Device-level preference: prompt for Face ID/fingerprint before trusting a stored session. */
  biometricLockEnabled: boolean;
  /** True while a signed-in session exists but hasn't cleared the biometric prompt yet (cold start with a stored token, or returning from the background) — the root layout shows a lock screen instead of the app while this is true. */
  isLocked: boolean;
  hydrate: () => Promise<void>;
  signIn: (params: { accessToken: string; refreshToken: string; role: string | null; email: string | null; name: string | null }) => Promise<void>;
  updateAccessToken: (accessToken: string) => Promise<void>;
  updateProfile: (params: { role: string | null; email: string | null; name: string | null }) => void;
  signOut: () => Promise<void>;
  setBiometricLockEnabled: (enabled: boolean) => Promise<void>;
  lock: () => void;
  unlock: () => void;
};

/**
 * Only holds session state actually needed for UI/routing decisions
 * (signed in? which role?) — the tokens themselves live in expo-secure-store,
 * never in this in-memory/React-state store, so they can't leak via a
 * debugger inspecting store state or a crash report.
 */
export const useAuthStore = create<AuthState>((set) => ({
  isHydrated: false,
  isSignedIn: false,
  role: null,
  email: null,
  name: null,
  biometricLockEnabled: false,
  isLocked: false,

  hydrate: async () => {
    const [accessToken, biometricLockEnabled] = await Promise.all([tokenStorage.getAccessToken(), biometricPreference.isEnabled()]);
    const isSignedIn = !!accessToken;
    // Locks immediately on cold start when a session already exists — a fresh signIn() below never sets this, since the user just authenticated a moment ago.
    set({ isSignedIn, isHydrated: true, biometricLockEnabled, isLocked: isSignedIn && biometricLockEnabled });
  },

  signIn: async ({ accessToken, refreshToken, role, email, name }) => {
    await tokenStorage.setTokens(accessToken, refreshToken);
    set({ isSignedIn: true, role, email, name });
    // Best-effort, fire-and-forget — a slow/failed push registration must never delay sign-in.
    void syncPushToken();
  },

  updateAccessToken: async (accessToken: string) => {
    await tokenStorage.setAccessToken(accessToken);
  },

  updateProfile: ({ role, email, name }) => set({ role, email, name }),

  signOut: async () => {
    // Must run before tokenStorage.clear() — unregistering needs the
    // still-valid session to authenticate the DELETE call.
    await clearPushToken();
    await tokenStorage.clear();
    set({ isSignedIn: false, role: null, email: null, name: null, isLocked: false });
  },

  setBiometricLockEnabled: async (enabled: boolean) => {
    await biometricPreference.setEnabled(enabled);
    set({ biometricLockEnabled: enabled });
  },

  // Only takes effect when there's an active session and the preference is on — locking a signed-out or preference-off app would show a lock screen with nothing to unlock into.
  lock: () => set((s) => (s.isSignedIn && s.biometricLockEnabled ? { isLocked: true } : {})),
  unlock: () => set({ isLocked: false }),
}));
