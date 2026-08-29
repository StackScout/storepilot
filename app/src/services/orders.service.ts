import { apiClient, resolveAssetUrl, toQueryString } from "@/lib/api-client";
import type { CheckoutInput, Order, OrderStatus, PageResponse, PayHereCheckoutPayload } from "@/types";

/** courierReceiptUrl and each item's productImageUrl may be relative (local FileStorageService) or already absolute (S3 presigned) — normalize once here. receiptUrl is intentionally left untouched: render sites already call toApiUrl() on it directly. */
export function normalizeOrder(order: Order): Order {
  return {
    ...order,
    items: order.items.map((item) => ({ ...item, productImageUrl: resolveAssetUrl(item.productImageUrl) })),
    courierReceiptUrl: order.courierReceiptUrl ? resolveAssetUrl(order.courierReceiptUrl) : order.courierReceiptUrl,
  };
}

/** GET /stores/:storeId/orders — paginated (page is 0-indexed, size defaults to 20 backend-side). */
export async function listOrdersByStore(
  storeId: string,
  status?: OrderStatus,
  page = 0,
  size = 20,
): Promise<PageResponse<Order>> {
  const qs = toQueryString({ status, page, size });
  const result = await apiClient.get<PageResponse<Order>>(`/api/stores/${storeId}/orders${qs}`);
  return { ...result, content: result.content.map(normalizeOrder) };
}

/** GET /orders/:id */
export async function getOrderById(id: string): Promise<Order | null> {
  const order = await apiClient.getOrNull<Order>(`/api/orders/${id}`);
  return order ? normalizeOrder(order) : null;
}

/** POST /orders/lookup/request-code — first step of guest lookup, emails a one-time code. Always resolves, regardless of whether orderNumber/phone matched anything. */
export async function requestOrderLookupCode(orderNumber: string, phone: string): Promise<void> {
  await apiClient.post<void>("/api/orders/lookup/request-code", { orderNumber, phone });
}

/** POST /orders/lookup/verify — second step of guest lookup. Throws ApiRequestError (404 number/phone mismatch, 400 bad/expired code) on failure — callers should catch and show the error's message. */
export async function verifyOrderLookupCode(orderNumber: string, phone: string, code: string): Promise<Order> {
  return normalizeOrder(await apiClient.post<Order>("/api/orders/lookup/verify", { orderNumber, phone, code }));
}

/**
 * POST /orders (checkout) — fee calculation, stock decrement, and the mock
 * order-receipt "send" all happen server-side now (see
 * OrderService#createOrder in the backend), not here.
 */
export async function createOrder(input: CheckoutInput): Promise<Order> {
  return normalizeOrder(await apiClient.post<Order>("/api/orders", input));
}

/** POST /orders/:id/payhere-checkout — hash generated server-side, never in the browser. */
export async function getPayHereCheckoutPayload(orderId: string): Promise<PayHereCheckoutPayload> {
  return apiClient.post<PayHereCheckoutPayload>(`/api/orders/${orderId}/payhere-checkout`);
}

/** POST /orders/:id/stripe-checkout — returns a ready-to-redirect Stripe Checkout URL, no client-side Stripe.js needed. */
export async function getStripeCheckoutUrl(orderId: string): Promise<{ checkoutUrl: string }> {
  return apiClient.post<{ checkoutUrl: string }>(`/api/orders/${orderId}/stripe-checkout`);
}

/** GET /api/me/orders — the signed-in buyer's own order history, derived from the auth cookie. Paginated server-side. */
export async function listMyOrders(page = 0, size = 20): Promise<PageResponse<Order>> {
  const qs = toQueryString({ page, size });
  const result = await apiClient.get<PageResponse<Order>>(`/api/me/orders${qs}`);
  return { ...result, content: result.content.map(normalizeOrder) };
}

/**
 * GET /stores/:storeId/stripe-settlements — read-only reconciliation view of
 * paid Stripe orders: what went through Stripe, what Stripe auto-paid the
 * seller, what the platform automatically took. Never a batchable ledger
 * like payouts/fee-collections — Connect already moved the money. Paginated
 * server-side.
 */
export async function listStripeSettlementsByStore(storeId: string, page = 0, size = 20): Promise<PageResponse<Order>> {
  const qs = toQueryString({ page, size });
  const result = await apiClient.get<PageResponse<Order>>(`/api/stores/${storeId}/stripe-settlements${qs}`);
  return { ...result, content: result.content.map(normalizeOrder) };
}

/** GET /admin/stripe-settlements — same view, platform-wide. Paginated server-side. */
export async function adminListStripeSettlements(page = 0, size = 20): Promise<PageResponse<Order>> {
  const qs = toQueryString({ page, size });
  const result = await apiClient.get<PageResponse<Order>>(`/api/admin/stripe-settlements${qs}`);
  return { ...result, content: result.content.map(normalizeOrder) };
}

/**
 * PATCH /orders/:id/status — trackingNumber/courierServiceName are required
 * by the backend when status is "shipped"; courierReceipt is always optional.
 */
export async function updateOrderStatus(
  id: string,
  status: OrderStatus,
  options?: { note?: string; trackingNumber?: string; courierServiceName?: string; courierReceipt?: File },
): Promise<Order> {
  const formData = new FormData();
  formData.append(
    "data",
    new Blob(
      [
        JSON.stringify({
          status,
          note: options?.note,
          trackingNumber: options?.trackingNumber,
          courierServiceName: options?.courierServiceName,
        }),
      ],
      { type: "application/json" },
    ),
  );
  if (options?.courierReceipt) formData.append("courierReceipt", options.courierReceipt);
  return normalizeOrder(await apiClient.patchForm<Order>(`/api/orders/${id}/status`, formData));
}

/** POST /orders/:id/receipt — buyer uploads proof of a bank transfer. */
export async function uploadReceipt(orderId: string, file: File): Promise<Order> {
  const formData = new FormData();
  formData.append("file", file);
  return normalizeOrder(await apiClient.postForm<Order>(`/api/orders/${orderId}/receipt`, formData));
}

/** POST /orders/:id/verify-bank-transfer — seller accepts or rejects the uploaded receipt. */
export async function verifyBankTransfer(
  orderId: string,
  approved: boolean,
  note?: string,
): Promise<Order> {
  return normalizeOrder(
    await apiClient.post<Order>(`/api/orders/${orderId}/verify-bank-transfer`, { approved, note }),
  );
}

/** POST /orders/:id/cancel — buyer cancels a bank-transfer order before a receipt is uploaded. */
export async function cancelOrder(orderId: string): Promise<Order> {
  return normalizeOrder(await apiClient.post<Order>(`/api/orders/${orderId}/cancel`));
}
