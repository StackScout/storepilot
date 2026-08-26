import { apiFetch } from '@/lib/api-client';
import type { MfaSetupResponse, MfaStatusResponse } from '@/api/types';

export function getMfaStatus(): Promise<MfaStatusResponse> {
  return apiFetch<MfaStatusResponse>('/api/auth/mfa/status');
}

/** Nothing is persisted server-side until verifyMfaSetup succeeds — safe to call again if the user backs out. */
export function startMfaSetup(): Promise<MfaSetupResponse> {
  return apiFetch<MfaSetupResponse>('/api/auth/mfa/setup', { method: 'POST' });
}

/** This is what actually turns MFA on — setup alone doesn't. */
export function verifyMfaSetup(code: string): Promise<void> {
  return apiFetch<void>('/api/auth/mfa/verify', { method: 'POST', body: { code } });
}

export function disableMfa(): Promise<void> {
  return apiFetch<void>('/api/auth/mfa/disable', { method: 'POST' });
}
