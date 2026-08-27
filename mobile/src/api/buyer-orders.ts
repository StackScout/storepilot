import type { Order } from '@storepilot/shared-api';

import { ApiError, apiFetch, apiFetchForm, resolveAssetUrl } from '@/lib/api-client';

function normalizeOrder(order: Order): Order {
  return {
    ...order,
    items: order.items.map((item) => ({ ...item, productImageUrl: resolveAssetUrl(item.productImageUrl) })),
    courierReceiptUrl: order.courierReceiptUrl ? resolveAssetUrl(order.courierReceiptUrl) : order.courierReceiptUrl,
  };
}

export async function getOrderById(id: string): Promise<Order | null> {
  try {
    return normalizeOrder(await apiFetch<Order>(`/api/orders/${id}`, { skipAuth: true }));
  } catch (e) {
    if (e instanceof ApiError && e.status === 404) return null;
    throw e;
  }
}

/** GET /api/me/orders — the signed-in buyer's own order history. */
export async function listMyOrders(): Promise<Order[]> {
  return (await apiFetch<Order[]>('/api/me/orders')).map(normalizeOrder);
}

export async function requestOrderLookupCode(orderNumber: string, phone: string): Promise<void> {
  await apiFetch<void>('/api/orders/lookup/request-code', { method: 'POST', body: { orderNumber, phone }, skipAuth: true });
}

export async function verifyOrderLookupCode(orderNumber: string, phone: string, code: string): Promise<Order> {
  return normalizeOrder(await apiFetch<Order>('/api/orders/lookup/verify', { method: 'POST', body: { orderNumber, phone, code }, skipAuth: true }));
}

/** See api/products.ts's buildProductForm doc comment — same Blob-from-uri workaround for Expo SDK 57's FormData encoder. */
export async function uploadReceipt(orderId: string, imageUri: string): Promise<Order> {
  const rawBlob = await (await fetch(imageUri)).blob();
  const blob = rawBlob.slice(0, rawBlob.size, 'image/jpeg');
  const form = new FormData();
  form.append('file', blob, 'receipt.jpg');
  return normalizeOrder(await apiFetchForm<Order>(`/api/orders/${orderId}/receipt`, form, 'POST'));
}

export async function cancelOrder(orderId: string): Promise<Order> {
  return normalizeOrder(await apiFetch<Order>(`/api/orders/${orderId}/cancel`, { method: 'POST', skipAuth: true }));
}
