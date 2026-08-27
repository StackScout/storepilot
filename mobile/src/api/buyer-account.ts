import type { Buyer } from '@storepilot/shared-api';

import { ApiError, apiFetch } from '@/lib/api-client';

export async function getCurrentBuyer(): Promise<Buyer | null> {
  try {
    return await apiFetch<Buyer>('/api/me');
  } catch (e) {
    if (e instanceof ApiError && e.status === 404) return null;
    throw e;
  }
}

export function exportBuyerData(): Promise<unknown> {
  return apiFetch('/api/me/export');
}

export async function deleteBuyerAccount(): Promise<void> {
  await apiFetch<void>('/api/me/delete', { method: 'POST' });
}
