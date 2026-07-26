import { apiClient } from "@/lib/api-client";
import type { Order, Payout } from "@/types";

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
