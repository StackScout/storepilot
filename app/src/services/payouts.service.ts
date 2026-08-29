import { apiClient, toQueryString } from "@/lib/api-client";
import type { Booking, FeeCollection, Order, PageResponse, Payout } from "@/types";

/** GET /stores/:storeId/payouts — paginated server-side. */
export async function listPayoutsByStore(storeId: string, page = 0, size = 20): Promise<PageResponse<Payout>> {
  const qs = toQueryString({ page, size });
  return apiClient.get<PageResponse<Payout>>(`/api/stores/${storeId}/payouts${qs}`);
}

/**
 * Orders that have been delivered and paid, but aren't part of any payout
 * (scheduled or already paid) yet for this store — i.e. money the platform
 * is still holding on the seller's behalf. "Shallow" paginated server-side
 * (see backend PayoutService's doc comment) — correct results, computed
 * in-memory rather than a LIMIT/OFFSET query.
 */
export async function getEligibleOrdersForPayout(storeId: string, page = 0, size = 20): Promise<PageResponse<Order>> {
  const qs = toQueryString({ page, size });
  return apiClient.get<PageResponse<Order>>(`/api/stores/${storeId}/payouts/eligible-orders${qs}`);
}

/** Same idea as getEligibleOrdersForPayout, for bookings — a payout batch can include both, see PayoutService.createBatch. */
export async function getEligibleBookingsForPayout(storeId: string, page = 0, size = 20): Promise<PageResponse<Booking>> {
  const qs = toQueryString({ page, size });
  return apiClient.get<PageResponse<Booking>>(`/api/stores/${storeId}/payouts/eligible-bookings${qs}`);
}

/**
 * Admin-only equivalent of getEligibleOrdersForPayout/getEligibleBookingsForPayout —
 * the seller-facing endpoints reject a non-owning caller (including admin),
 * so the accounting dashboard's "which stores are due a payout" scan across
 * every active store must go through these instead.
 */
export async function adminGetEligibleOrdersForPayout(storeId: string, page = 0, size = 20): Promise<PageResponse<Order>> {
  const qs = toQueryString({ page, size });
  return apiClient.get<PageResponse<Order>>(`/api/admin/stores/${storeId}/payouts/eligible-orders${qs}`);
}

export async function adminGetEligibleBookingsForPayout(storeId: string, page = 0, size = 20): Promise<PageResponse<Booking>> {
  const qs = toQueryString({ page, size });
  return apiClient.get<PageResponse<Booking>>(`/api/admin/stores/${storeId}/payouts/eligible-bookings${qs}`);
}

/** POST /stores/:storeId/payouts — bundle all currently-eligible orders into one scheduled payout. */
export async function createPayout(storeId: string): Promise<Payout> {
  return apiClient.post<Payout>(`/api/admin/stores/${storeId}/payouts`);
}

/** PATCH /payouts/:id/paid — admin confirms the bank transfer actually went out. */
export async function markPayoutPaid(payoutId: string, bankReference?: string): Promise<Payout> {
  return apiClient.patch<Payout>(`/api/admin/payouts/${payoutId}`, { bankReference });
}

/** GET /admin/payouts — every payout across every store. Paginated server-side. */
export async function adminListPayouts(page = 0, size = 20): Promise<PageResponse<Payout>> {
  const qs = toQueryString({ page, size });
  return apiClient.get<PageResponse<Payout>>(`/api/admin/payouts${qs}`);
}

// --- Fee collections — the reverse ledger from payouts, see types/payout.ts ---

/** GET /stores/:storeId/fee-collections — paginated server-side. */
export async function listFeeCollectionsByStore(storeId: string, page = 0, size = 20): Promise<PageResponse<FeeCollection>> {
  const qs = toQueryString({ page, size });
  return apiClient.get<PageResponse<FeeCollection>>(`/api/stores/${storeId}/fee-collections${qs}`);
}

/**
 * COD/bank-transfer orders that are delivered and paid, but aren't part of
 * any fee collection (pending or already collected) yet — i.e. platform
 * fees the seller is holding that haven't been remitted. "Shallow"
 * paginated server-side, same reasoning as getEligibleOrdersForPayout.
 */
export async function getEligibleOrdersForFeeCollection(storeId: string, page = 0, size = 20): Promise<PageResponse<Order>> {
  const qs = toQueryString({ page, size });
  return apiClient.get<PageResponse<Order>>(`/api/stores/${storeId}/fee-collections/eligible-orders${qs}`);
}

/** Same idea as getEligibleOrdersForFeeCollection, for bookings paid via "Pay at venue"/bank-transfer. */
export async function getEligibleBookingsForFeeCollection(storeId: string, page = 0, size = 20): Promise<PageResponse<Booking>> {
  const qs = toQueryString({ page, size });
  return apiClient.get<PageResponse<Booking>>(`/api/stores/${storeId}/fee-collections/eligible-bookings${qs}`);
}

/** Admin-only equivalent of getEligibleOrdersForFeeCollection/getEligibleBookingsForFeeCollection — see adminGetEligibleOrdersForPayout's doc comment. */
export async function adminGetEligibleOrdersForFeeCollection(storeId: string, page = 0, size = 20): Promise<PageResponse<Order>> {
  const qs = toQueryString({ page, size });
  return apiClient.get<PageResponse<Order>>(`/api/admin/stores/${storeId}/fee-collections/eligible-orders${qs}`);
}

export async function adminGetEligibleBookingsForFeeCollection(storeId: string, page = 0, size = 20): Promise<PageResponse<Booking>> {
  const qs = toQueryString({ page, size });
  return apiClient.get<PageResponse<Booking>>(`/api/admin/stores/${storeId}/fee-collections/eligible-bookings${qs}`);
}

/** POST /stores/:storeId/fee-collections — bundle all currently-eligible orders into one pending fee collection. */
export async function createFeeCollection(storeId: string): Promise<FeeCollection> {
  return apiClient.post<FeeCollection>(`/api/admin/stores/${storeId}/fee-collections`);
}

/** PATCH /fee-collections/:id — admin confirms the seller actually paid the platform its fee. */
export async function markFeeCollectionCollected(feeCollectionId: string, reference?: string): Promise<FeeCollection> {
  return apiClient.patch<FeeCollection>(`/api/admin/fee-collections/${feeCollectionId}`, { reference });
}

/** GET /admin/fee-collections — every fee collection across every store. Paginated server-side. */
export async function adminListFeeCollections(page = 0, size = 20): Promise<PageResponse<FeeCollection>> {
  const qs = toQueryString({ page, size });
  return apiClient.get<PageResponse<FeeCollection>>(`/api/admin/fee-collections${qs}`);
}
