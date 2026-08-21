import { apiClient } from "@/lib/api-client";

/**
 * GET /api/me/seller/export — everything StorePilot holds about the
 * signed-in seller (profile, store, settings, products, orders, bookings,
 * payouts, fee collections, reviews, coupons), as a single JSON bundle.
 * Not typed further here since it's downloaded/displayed as raw JSON, not
 * rendered field-by-field.
 */
export async function exportSellerData(): Promise<unknown> {
  return apiClient.get<unknown>("/api/me/seller/export");
}

/**
 * POST /api/me/seller/delete — permanent. Only reachable once the seller's
 * store is already closed (see stores.service.ts's closeStore) — the
 * backend rejects this with a 409 otherwise. Cancels the Pro subscription,
 * deletes the Stripe Customer, deauthorizes the Stripe Connect account,
 * anonymizes the Seller/StoreSettings rows, and deletes the Cognito
 * identity — the session is unrecoverable after this call succeeds, so the
 * caller must sign the seller out immediately afterward.
 */
export async function deleteSellerAccount(): Promise<void> {
  await apiClient.post<void>("/api/me/seller/delete");
}
