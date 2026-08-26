import { apiFetch } from '@/lib/api-client';
import type { AuthSessionResponse } from '@/api/types';

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
