import { apiFetch } from '@/lib/api-client';
import type { PageResponse, PayoutResponse } from '@/api/types';

/** No pagination UI on the seller dashboard yet — size=200 keeps today's "show everything" behavior. */
export async function listStorePayouts(storeId: string): Promise<PayoutResponse[]> {
  return (await apiFetch<PageResponse<PayoutResponse>>(`/api/stores/${storeId}/payouts?size=200`)).content;
}
