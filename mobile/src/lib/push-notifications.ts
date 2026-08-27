import Constants from 'expo-constants';
import * as Device from 'expo-device';
import { Platform } from 'react-native';

import { apiFetch } from './api-client';

/**
 * Backend counterpart: backend/.../notification/PushToken.kt + PushTokenController
 * (POST/DELETE /api/me/seller/push-tokens). See ExpoPushNotificationService
 * for how these tokens get used — this app never talks to Apple/Google
 * directly, only to Expo's push relay via the backend.
 *
 * IMPORTANT: this project has no EAS projectId configured yet (no eas.json,
 * no app.json extra.eas.projectId) — getExpoPushTokenAsync needs one to
 * return a real token. Until `eas init`/`eas build:configure` sets that up,
 * registerForPushNotificationsAsync logs a warning and returns null rather
 * than throwing, so the rest of the app (sign-in, etc.) is never blocked by
 * this being unconfigured. Also: remote push notifications are unavailable
 * in Expo Go on Android since SDK 53 — a development build is required to
 * actually receive one there (iOS Expo Go isn't restricted the same way,
 * but a dev build is still the reliable way to test this end to end).
 */

/**
 * Remote push notifications are unavailable in Expo Go on Android since
 * SDK 53 — merely *importing* expo-notifications throws there outright
 * (confirmed live: expo-notifications' own module-init code checks for
 * Expo Go on Android and throws, which crashed every route in the app
 * since this file is imported unconditionally via auth-store.ts). So the
 * module itself must not be `import`ed statically — it's `require`d
 * lazily below, only when not running inside Expo Go. A real development
 * build doesn't have this restriction at all.
 */
export const isExpoGo = Constants.appOwnership === 'expo';

type NotificationsModule = typeof import('expo-notifications');
export const NotificationsApi: NotificationsModule | null = isExpoGo ? null : (require('expo-notifications') as NotificationsModule);

if (NotificationsApi) {
  NotificationsApi.setNotificationHandler({
    handleNotification: async () => ({
      shouldShowBanner: true,
      shouldShowList: true,
      shouldPlaySound: true,
      shouldSetBadge: false,
    }),
  });
}

const ANDROID_CHANNEL_ID = 'default';

async function ensureAndroidChannel() {
  if (Platform.OS !== 'android' || !NotificationsApi) return;
  await NotificationsApi.setNotificationChannelAsync(ANDROID_CHANNEL_ID, {
    name: 'StorePilot',
    importance: NotificationsApi.AndroidImportance.HIGH,
  });
}

/** Requests permission and returns a real Expo push token, or null if unavailable (Expo Go on Android, simulator, permission denied, or no EAS projectId configured — see this file's doc comment). */
export async function registerForPushNotificationsAsync(): Promise<string | null> {
  if (!NotificationsApi) {
    console.warn('[push] Remote push notifications are unavailable in Expo Go — skipping (works fine in a real development build).');
    return null;
  }

  await ensureAndroidChannel();

  if (!Device.isDevice) {
    console.warn('[push] Push notifications require a physical device — skipping on simulator/emulator.');
    return null;
  }

  const existing = await NotificationsApi.getPermissionsAsync();
  let status = existing.status;
  if (status !== 'granted') {
    status = (await NotificationsApi.requestPermissionsAsync()).status;
  }
  if (status !== 'granted') {
    console.warn('[push] Notification permission not granted.');
    return null;
  }

  const projectId = Constants.expoConfig?.extra?.eas?.projectId ?? Constants.easConfig?.projectId;
  if (!projectId) {
    console.warn('[push] No EAS projectId configured (run `eas init`) — can\'t register for push notifications yet.');
    return null;
  }

  try {
    const { data: token } = await NotificationsApi.getExpoPushTokenAsync({ projectId });
    return token;
  } catch (e) {
    console.warn('[push] Failed to get an Expo push token', e);
    return null;
  }
}

/** Called on sign-in and on every app-foreground while signed in — a no-op (not an error) wherever registerForPushNotificationsAsync returns null. */
export async function syncPushToken(): Promise<void> {
  const token = await registerForPushNotificationsAsync();
  if (!token) return;
  try {
    await apiFetch('/api/me/seller/push-tokens', {
      method: 'POST',
      body: { token, platform: Platform.OS },
    });
  } catch (e) {
    console.warn('[push] Failed to register push token with the backend', e);
  }
}

/** Called on sign-out, before tokens are cleared (the call needs a still-valid session to authenticate). Best-effort — a failure here must never block sign-out. */
export async function clearPushToken(): Promise<void> {
  const token = await registerForPushNotificationsAsync();
  if (!token) return;
  try {
    await apiFetch('/api/me/seller/push-tokens', {
      method: 'DELETE',
      body: { token },
    });
  } catch (e) {
    console.warn('[push] Failed to unregister push token from the backend', e);
  }
}
