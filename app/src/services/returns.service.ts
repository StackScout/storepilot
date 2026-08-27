import { apiClient, toQueryString } from "@/lib/api-client";
import type { PageResponse, ReturnRequest, ReturnRequestCreateInput } from "@/types";

/** POST /orders/:orderId/returns — buyer request, reachable unauthenticated (order ID is the credential, same model as receipt upload/cancel). */
export async function createReturnRequest(orderId: string, input: ReturnRequestCreateInput): Promise<ReturnRequest> {
  return apiClient.post<ReturnRequest>(`/api/orders/${orderId}/returns`, input);
}

/** GET /orders/:orderId/returns — used by both the buyer and seller order-detail pages. */
export async function listReturnsForOrder(orderId: string): Promise<ReturnRequest[]> {
  return apiClient.get<ReturnRequest[]>(`/api/orders/${orderId}/returns`);
}

/** POST /orders/:orderId/returns/:returnId/decision — seller approve/reject. */
export async function decideReturnRequest(
  orderId: string,
  returnId: string,
  approved: boolean,
  note?: string,
): Promise<ReturnRequest> {
  return apiClient.post<ReturnRequest>(`/api/orders/${orderId}/returns/${returnId}/decision`, { approved, note });
}

/** POST /orders/:orderId/returns/:returnId/mark-refunded — seller self-attests, COD/bank-transfer only (the platform is never a party to that money — see ReturnRequestService.markRefundedBySeller). */
export async function markReturnRefundedBySeller(
  orderId: string,
  returnId: string,
  refundReference?: string,
): Promise<ReturnRequest> {
  return apiClient.post<ReturnRequest>(`/api/orders/${orderId}/returns/${returnId}/mark-refunded`, { refundReference });
}

/** GET /stores/:storeId/returns — seller's own store. Paginated server-side. */
export async function listReturnsForStore(storeId: string, page = 0, size = 20): Promise<PageResponse<ReturnRequest>> {
  const qs = toQueryString({ page, size });
  return apiClient.get<PageResponse<ReturnRequest>>(`/api/stores/${storeId}/returns${qs}`);
}

/** GET /admin/returns — platform-wide, optionally filtered by status. Paginated server-side. */
export async function adminListReturns(status?: string, page = 0, size = 20): Promise<PageResponse<ReturnRequest>> {
  const qs = toQueryString({ status, page, size });
  return apiClient.get<PageResponse<ReturnRequest>>(`/api/admin/returns${qs}`);
}

/** PATCH /admin/returns/:id — admin confirms a PayHere refund (the platform's own merchant account is the one that has to send the money back — see ReturnRequestService.adminMarkRefunded). */
export async function adminMarkReturnRefunded(returnId: string, refundReference?: string): Promise<ReturnRequest> {
  return apiClient.patch<ReturnRequest>(`/api/admin/returns/${returnId}`, { refundReference });
}
