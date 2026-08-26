import { apiFetch } from '@/lib/api-client';
import type { SellerExportResponse } from '@/api/types';

export function exportSellerData(): Promise<SellerExportResponse> {
  return apiFetch<SellerExportResponse>('/api/me/seller/export');
}

/** Requires the seller's store (if any) to already be closed — the backend returns 409 otherwise. */
export function deleteSellerAccount(): Promise<void> {
  return apiFetch<void>('/api/me/seller/delete', { method: 'POST' });
}
