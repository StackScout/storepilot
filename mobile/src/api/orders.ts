import { apiFetch, apiFetchForm } from '@/lib/api-client';
import type { OrderResponse, OrderStatus, PageResponse } from '@/api/types';

export function listStoreOrders(storeId: string, page = 0, size = 20, status?: OrderStatus): Promise<PageResponse<OrderResponse>> {
  const statusParam = status ? `&status=${status}` : '';
  return apiFetch<PageResponse<OrderResponse>>(`/api/stores/${storeId}/orders?page=${page}&size=${size}${statusParam}`);
}

export function getOrder(id: string): Promise<OrderResponse> {
  return apiFetch<OrderResponse>(`/api/orders/${id}`);
}

export type UpdateOrderStatusInput = {
  status: OrderStatus;
  note?: string;
  /** Required by the backend when status is "shipped". */
  trackingNumber?: string;
  courierServiceName?: string;
  /** Optional proof-of-handover photo, only meaningful alongside "shipped". */
  courierReceiptUri?: string;
};

/**
 * PATCH /api/orders/{id}/status is multipart (a "data" JSON part + an optional courierReceipt
 * file part) even when there's no file, matching every other status/decision endpoint that can
 * optionally take an attachment. Expo SDK 57's global fetch/FormData encoder only accepts real
 * `Blob` parts for binary data — it doesn't support React Native's classic {uri,name,type} file
 * reference — so the local file uri is read into a real Blob via fetch() before being appended.
 * That Blob comes back with no usable MIME type, so `.slice()` forces a proper one onto it.
 */
export async function updateOrderStatus(id: string, input: UpdateOrderStatusInput): Promise<OrderResponse> {
  const { courierReceiptUri, ...data } = input;
  const form = new FormData();
  form.append('data', new Blob([JSON.stringify(data)], { type: 'application/json' }));
  if (courierReceiptUri) {
    const rawBlob = await (await fetch(courierReceiptUri)).blob();
    const blob = rawBlob.slice(0, rawBlob.size, 'image/jpeg');
    form.append('courierReceipt', blob, 'courier-receipt.jpg');
  }
  return apiFetchForm<OrderResponse>(`/api/orders/${id}/status`, form, 'PATCH');
}

export function verifyBankTransfer(id: string, approved: boolean, note?: string): Promise<OrderResponse> {
  return apiFetch<OrderResponse>(`/api/orders/${id}/verify-bank-transfer`, {
    method: 'POST',
    body: { approved, note },
  });
}
