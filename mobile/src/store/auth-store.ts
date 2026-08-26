import { create } from 'zustand';

import { tokenStorage } from '@/lib/secure-storage';
import { clearPushToken, syncPushToken } from '@/lib/push-notifications';

type AuthState = {
  /** Undefined until hydrate() resolves — lets the root layout show a splash instead of flashing the login screen. */
  isHydrated: boolean;
  isSignedIn: boolean;
  role: string | null;
  email: string | null;
  name: string | null;
  hydrate: () => Promise<void>;
  signIn: (params: { accessToken: string; refreshToken: string; role: string | null; email: string | null; name: string | null }) => Promise<void>;
  updateAccessToken: (accessToken: string) => Promise<void>;
  updateProfile: (params: { role: string | null; email: string | null; name: string | null }) => void;
  signOut: () => Promise<void>;
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

  hydrate: async () => {
    const accessToken = await tokenStorage.getAccessToken();
    set({ isSignedIn: !!accessToken, isHydrated: true });
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
    set({ isSignedIn: false, role: null, email: null, name: null });
  },
}));
