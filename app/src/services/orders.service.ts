import { apiClient, toQueryString } from "@/lib/api-client";
import type { CheckoutInput, Order, OrderStatus, PayHereCheckoutPayload } from "@/types";

/** GET /stores/:storeId/orders */
export async function listOrdersByStore(storeId: string, status?: OrderStatus): Promise<Order[]> {
  const qs = toQueryString({ status });
  return apiClient.get<Order[]>(`/api/stores/${storeId}/orders${qs}`);
}

/** GET /orders/:id */
export async function getOrderById(id: string): Promise<Order | null> {
  return apiClient.getOrNull<Order>(`/api/orders/${id}`);
}

/** GET /orders/lookup?orderNumber=&phone= */
export async function findOrderByNumberAndPhone(
  orderNumber: string,
  phone: string,
): Promise<Order | null> {
  const qs = toQueryString({ orderNumber, phone });
  return apiClient.getOrNull<Order>(`/api/orders/lookup${qs}`);
}

/**
 * POST /orders (checkout) — fee calculation, stock decrement, and the mock
 * order-receipt "send" all happen server-side now (see
 * OrderService#createOrder in the backend), not here.
 */
export async function createOrder(input: CheckoutInput): Promise<Order> {
  return apiClient.post<Order>("/api/orders", input);
}

/** POST /orders/:id/payhere-checkout — hash generated server-side, never in the browser. */
export async function getPayHereCheckoutPayload(orderId: string): Promise<PayHereCheckoutPayload> {
  return apiClient.post<PayHereCheckoutPayload>(`/api/orders/${orderId}/payhere-checkout`);
}

/** GET /buyers/:buyerId/orders */
export async function listOrdersByBuyer(buyerId: string): Promise<Order[]> {
  return apiClient.get<Order[]>(`/api/buyers/${buyerId}/orders`);
}

/** PATCH /orders/:id/status */
export async function updateOrderStatus(
  id: string,
  status: OrderStatus,
  note?: string,
): Promise<Order> {
  return apiClient.patch<Order>(`/api/orders/${id}/status`, { status, note });
}

/** POST /orders/:id/receipt — buyer uploads proof of a bank transfer. */
export async function uploadReceipt(orderId: string, file: File): Promise<Order> {
  const formData = new FormData();
  formData.append("file", file);
  return apiClient.postForm<Order>(`/api/orders/${orderId}/receipt`, formData);
}

/** POST /orders/:id/verify-bank-transfer — seller accepts or rejects the uploaded receipt. */
export async function verifyBankTransfer(
  orderId: string,
  approved: boolean,
  note?: string,
): Promise<Order> {
  return apiClient.post<Order>(`/api/orders/${orderId}/verify-bank-transfer`, { approved, note });
}

/** POST /orders/:id/cancel — buyer cancels a bank-transfer order before a receipt is uploaded. */
export async function cancelOrder(orderId: string): Promise<Order> {
  return apiClient.post<Order>(`/api/orders/${orderId}/cancel`);
}
