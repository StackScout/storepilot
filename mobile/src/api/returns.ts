import { apiFetch } from '@/lib/api-client';
import type { PageResponse, ReturnRequestResponse } from '@/api/types';

/** No pagination UI on the seller dashboard yet — size=200 keeps today's "show everything" behavior. */
export async function listStoreReturns(storeId: string): Promise<ReturnRequestResponse[]> {
  return (await apiFetch<PageResponse<ReturnRequestResponse>>(`/api/stores/${storeId}/returns?size=200`)).content;
}

export function decideReturn(orderId: string, returnId: string, approved: boolean, note?: string): Promise<ReturnRequestResponse> {
  return apiFetch<ReturnRequestResponse>(`/api/orders/${orderId}/returns/${returnId}/decision`, {
    method: 'POST',
    body: { approved, note },
  });
}

/** Seller-callable only for COD/bank-transfer orders — PayHere refunds must go through the admin endpoint (out of scope here). */
export function markReturnRefunded(orderId: string, returnId: string, refundReference?: string): Promise<ReturnRequestResponse> {
  return apiFetch<ReturnRequestResponse>(`/api/orders/${orderId}/returns/${returnId}/mark-refunded`, {
    method: 'POST',
    body: { refundReference },
  });
}
