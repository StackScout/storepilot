import { apiClient } from "@/lib/api-client";
import type { Buyer, ShippingDetails } from "@/types";

/**
 * GET /api/me — the signed-in buyer's own profile, derived from the auth
 * cookie server-side. Registration/login now go through auth.service.ts
 * (real Cognito accounts) instead of a plain buyer-profile POST — there is
 * no more "look up or create a buyer by email" here.
 */
export async function getCurrentBuyer(): Promise<Buyer | null> {
  return apiClient.getOrNull<Buyer>("/api/me");
}

/** PATCH /api/me/default-shipping */
export async function updateDefaultShipping(shipping: ShippingDetails): Promise<Buyer> {
  return apiClient.patch<Buyer>("/api/me/default-shipping", shipping);
}
