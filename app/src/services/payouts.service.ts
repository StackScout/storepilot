import { apiClient } from "@/lib/api-client";
import type { Booking, FeeCollection, Order, Payout } from "@/types";

/** GET /stores/:storeId/payouts */
export async function listPayoutsByStore(storeId: string): Promise<Payout[]> {
  return apiClient.get<Payout[]>(`/api/stores/${storeId}/payouts`);
}

/**
 * Orders that have been delivered and paid, but aren't part of any payout
 * (scheduled or already paid) yet for this store — i.e. money the platform
 * is still holding on the seller's behalf.
 */
export async function getEligibleOrdersForPayout(storeId: string): Promise<Order[]> {
  return apiClient.get<Order[]>(`/api/stores/${storeId}/payouts/eligible-orders`);
}

/** Same idea as getEligibleOrdersForPayout, for bookings — a payout batch can include both, see PayoutService.createBatch. */
export async function getEligibleBookingsForPayout(storeId: string): Promise<Booking[]> {
  return apiClient.get<Booking[]>(`/api/stores/${storeId}/payouts/eligible-bookings`);
}

/** POST /stores/:storeId/payouts — bundle all currently-eligible orders into one scheduled payout. */
export async function createPayout(storeId: string): Promise<Payout> {
  return apiClient.post<Payout>(`/api/admin/stores/${storeId}/payouts`);
}

/** PATCH /payouts/:id/paid — admin confirms the bank transfer actually went out. */
export async function markPayoutPaid(payoutId: string, bankReference?: string): Promise<Payout> {
  return apiClient.patch<Payout>(`/api/admin/payouts/${payoutId}`, { bankReference });
}

/** GET /admin/payouts — every payout across every store. */
export async function adminListPayouts(): Promise<Payout[]> {
  return apiClient.get<Payout[]>("/api/admin/payouts");
}

// --- Fee collections — the reverse ledger from payouts, see types/payout.ts ---

/** GET /stores/:storeId/fee-collections */
export async function listFeeCollectionsByStore(storeId: string): Promise<FeeCollection[]> {
  return apiClient.get<FeeCollection[]>(`/api/stores/${storeId}/fee-collections`);
}

/**
 * COD/bank-transfer orders that are delivered and paid, but aren't part of
 * any fee collection (pending or already collected) yet — i.e. platform
 * fees the seller is holding that haven't been remitted.
 */
export async function getEligibleOrdersForFeeCollection(storeId: string): Promise<Order[]> {
  return apiClient.get<Order[]>(`/api/stores/${storeId}/fee-collections/eligible-orders`);
}

/** Same idea as getEligibleOrdersForFeeCollection, for bookings paid via "Pay at venue"/bank-transfer. */
export async function getEligibleBookingsForFeeCollection(storeId: string): Promise<Booking[]> {
  return apiClient.get<Booking[]>(`/api/stores/${storeId}/fee-collections/eligible-bookings`);
}

/** POST /stores/:storeId/fee-collections — bundle all currently-eligible orders into one pending fee collection. */
export async function createFeeCollection(storeId: string): Promise<FeeCollection> {
  return apiClient.post<FeeCollection>(`/api/admin/stores/${storeId}/fee-collections`);
}

/** PATCH /fee-collections/:id — admin confirms the seller actually paid the platform its fee. */
export async function markFeeCollectionCollected(feeCollectionId: string, reference?: string): Promise<FeeCollection> {
  return apiClient.patch<FeeCollection>(`/api/admin/fee-collections/${feeCollectionId}`, { reference });
}

/** GET /admin/fee-collections — every fee collection across every store. */
export async function adminListFeeCollections(): Promise<FeeCollection[]> {
  return apiClient.get<FeeCollection[]>("/api/admin/fee-collections");
}
