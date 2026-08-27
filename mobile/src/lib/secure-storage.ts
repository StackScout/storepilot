import * as SecureStore from 'expo-secure-store';
import { Platform } from 'react-native';

const ACCESS_TOKEN_KEY = 'storepilot_access_token';
const REFRESH_TOKEN_KEY = 'storepilot_refresh_token';
const BIOMETRIC_LOCK_KEY = 'storepilot_biometric_lock_enabled';

// expo-secure-store has no real Keychain/Keystore equivalent on web (its web
// shim throws — SecureStore.getValueWithKeyAsync is not a function). This
// app never ships to web; the web target only exists as a fast local
// preview surface during development, so localStorage here is a dev-only
// convenience, not a production security decision.
const webStorage = {
  getItemAsync: async (key: string) => (Platform.OS === 'web' ? window.localStorage.getItem(key) : null),
  setItemAsync: async (key: string, value: string) => {
    if (Platform.OS === 'web') window.localStorage.setItem(key, value);
  },
  deleteItemAsync: async (key: string) => {
    if (Platform.OS === 'web') window.localStorage.removeItem(key);
  },
};

const backend = Platform.OS === 'web' ? webStorage : SecureStore;

/**
 * iOS Keychain / Android Keystore-backed token storage — the mobile
 * equivalent of the web app's httpOnly cookies (see CookieBearerTokenResolver
 * on the backend). Never store tokens in AsyncStorage/plain state.
 */
export const tokenStorage = {
  async getAccessToken(): Promise<string | null> {
    return backend.getItemAsync(ACCESS_TOKEN_KEY);
  },
  async getRefreshToken(): Promise<string | null> {
    return backend.getItemAsync(REFRESH_TOKEN_KEY);
  },
  async setTokens(accessToken: string, refreshToken: string): Promise<void> {
    await backend.setItemAsync(ACCESS_TOKEN_KEY, accessToken);
    await backend.setItemAsync(REFRESH_TOKEN_KEY, refreshToken);
  },
  async setAccessToken(accessToken: string): Promise<void> {
    await backend.setItemAsync(ACCESS_TOKEN_KEY, accessToken);
  },
  async clear(): Promise<void> {
    await backend.deleteItemAsync(ACCESS_TOKEN_KEY);
    await backend.deleteItemAsync(REFRESH_TOKEN_KEY);
  },
};

/** A device-level preference, not tied to any one account — deliberately survives sign-out so re-signing in on the same device keeps the lock enabled. */
export const biometricPreference = {
  async isEnabled(): Promise<boolean> {
    return (await backend.getItemAsync(BIOMETRIC_LOCK_KEY)) === 'true';
  },
  async setEnabled(enabled: boolean): Promise<void> {
    await backend.setItemAsync(BIOMETRIC_LOCK_KEY, enabled ? 'true' : 'false');
  },
};
