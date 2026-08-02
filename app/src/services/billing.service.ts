import { apiClient } from "@/lib/api-client";
import type { SellerPlanInfo } from "@/types";

/** GET /me/seller/plan */
export async function getMyPlan(): Promise<SellerPlanInfo> {
  return apiClient.get<SellerPlanInfo>("/api/me/seller/plan");
}

/** POST /me/seller/billing/checkout — returns a Stripe Checkout URL (subscription mode); redirect the browser to it. */
export async function startProCheckout(): Promise<{ checkoutUrl: string }> {
  return apiClient.post<{ checkoutUrl: string }>("/api/me/seller/billing/checkout");
}

/** POST /me/seller/billing/cancel — keeps Pro access through the period already paid for. */
export async function cancelPro(): Promise<SellerPlanInfo> {
  return apiClient.post<SellerPlanInfo>("/api/me/seller/billing/cancel");
}

/** POST /me/seller/billing/refresh — fallback for when the billing webhook is misconfigured or drops an event, same pattern as Stripe Connect's refresh. */
export async function refreshPlanStatus(): Promise<SellerPlanInfo> {
  return apiClient.post<SellerPlanInfo>("/api/me/seller/billing/refresh");
}
