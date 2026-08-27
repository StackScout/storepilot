import * as Linking from 'expo-linking';
import * as WebBrowser from 'expo-web-browser';

import { apiFetch } from '@/lib/api-client';
import type { AuthSessionResponse } from '@/api/types';

const BASE_URL = process.env.EXPO_PUBLIC_API_BASE_URL;

/**
 * Opens Cognito's Google Hosted-UI flow in an in-app auth session (not a
 * plain browser tab) — openAuthSessionAsync captures the final redirect to
 * our own app.json `scheme` itself, so this never needs a separate
 * deep-link listener. The backend's googleCallback (see AuthController.kt)
 * recognizes platform=mobile and redirects here with tokens as query
 * params instead of setting cookies, since mobile has no cookie jar.
 */
export async function signInWithGoogle(intent: 'buyer' | 'seller'): Promise<AuthSessionResponse | null> {
  const redirectUrl = Linking.createURL('auth-callback');
  const authUrl = `${BASE_URL}/api/auth/google/start?intent=${intent}&platform=mobile`;
  const result = await WebBrowser.openAuthSessionAsync(authUrl, redirectUrl);
  if (result.type !== 'success' || !result.url) return null;

  const { queryParams } = Linking.parse(result.url);
  const error = queryParams?.error;
  if (error) throw new Error(typeof error === 'string' ? error : 'Google sign-in failed');

  const accessToken = queryParams?.accessToken;
  const refreshToken = queryParams?.refreshToken;
  if (typeof accessToken !== 'string' || typeof refreshToken !== 'string') return null;

  return {
    signedIn: true,
    accessToken,
    refreshToken,
    role: typeof queryParams?.role === 'string' ? (queryParams.role as AuthSessionResponse['role']) : undefined,
  };
}

export interface RegisterResult {
  email: string;
  name: string;
}

/** POST /api/auth/register — creates a real Cognito account but does NOT sign in (email-unverified until verifyEmail()). */
export function register(name: string, email: string, password: string, accountType: 'buyer' | 'seller'): Promise<RegisterResult> {
  return apiFetch<RegisterResult>('/api/auth/register', { method: 'POST', body: { name, email, password, accountType }, skipAuth: true });
}

/** Doesn't sign in by itself — callers pair this with login() using the password they still hold in memory. */
export async function verifyEmail(email: string, code: string): Promise<void> {
  await apiFetch<void>('/api/auth/verify-email', { method: 'POST', body: { email, code }, skipAuth: true });
}

export async function resendVerificationCode(email: string): Promise<void> {
  await apiFetch<void>('/api/auth/resend-verification-code', { method: 'POST', body: { email }, skipAuth: true });
}

export function login(email: string, password: string): Promise<AuthSessionResponse> {
  return apiFetch<AuthSessionResponse>('/api/auth/login', {
    method: 'POST',
    body: { email, password },
    skipAuth: true,
  });
}

export function mfaChallenge(email: string, session: string, code: string): Promise<AuthSessionResponse> {
  return apiFetch<AuthSessionResponse>('/api/auth/mfa/challenge', {
    method: 'POST',
    body: { email, session, code },
    skipAuth: true,
  });
}

export function getSession(): Promise<AuthSessionResponse> {
  return apiFetch<AuthSessionResponse>('/api/auth/session');
}

export function logout(): Promise<void> {
  return apiFetch<void>('/api/auth/logout', { method: 'POST' });
}
