import { ApiError, apiFetch } from '@/lib/api-client';
import type { StoreApplicationInput, StoreResponse, StoreStatsResponse } from '@/api/types';

/** GET /api/me/store returns a bare 404 (no JSON body) when the seller hasn't onboarded a store yet — not a StoreResponse-shaped null. */
export async function getMyStore(): Promise<StoreResponse | null> {
  try {
    return await apiFetch<StoreResponse>('/api/me/store');
  } catch (e) {
    if (e instanceof ApiError && e.status === 404) return null;
    throw e;
  }
}

export function getStoreStats(storeId: string): Promise<StoreStatsResponse> {
  return apiFetch<StoreStatsResponse>(`/api/stores/${storeId}/stats`);
}

/** POST /api/stores — the onboarding step that actually creates the store and grants the caller's account the "seller" Cognito group (see StoreService.create's doc comment). */
export function createStore(input: StoreApplicationInput): Promise<StoreResponse> {
  return apiFetch<StoreResponse>('/api/stores', { method: 'POST', body: input });
}

/** Idempotent — returns the current StoreResponse unchanged if already closed. Blocked by 409 while orders/bookings/fees/payouts are in flight. */
export function closeStore(storeId: string): Promise<StoreResponse> {
  return apiFetch<StoreResponse>(`/api/stores/${storeId}/close`, { method: 'POST' });
}
