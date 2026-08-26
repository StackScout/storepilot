import { apiFetch } from '@/lib/api-client';
import type { PayoutResponse } from '@/api/types';

export function listStorePayouts(storeId: string): Promise<PayoutResponse[]> {
  return apiFetch<PayoutResponse[]>(`/api/stores/${storeId}/payouts`);
}
