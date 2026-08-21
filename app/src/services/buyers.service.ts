import { apiClient } from "@/lib/api-client";
import type { Buyer } from "@/types";

/**
 * GET /api/me — the signed-in buyer's own profile, derived from the auth
 * cookie server-side. Registration/login now go through auth.service.ts
 * (real Cognito accounts) instead of a plain buyer-profile POST — there is
 * no more "look up or create a buyer by email" here. Saved addresses:
 * see addresses.service.ts.
 */
export async function getCurrentBuyer(): Promise<Buyer | null> {
  return apiClient.getOrNull<Buyer>("/api/me");
}

/**
 * GET /api/me/export — everything StorePilot holds about the signed-in
 * buyer, as a single JSON bundle (data-subject access request). Shape is
 * assembled server-side from the buyer's own existing data — not typed
 * further here since it's downloaded/displayed as raw JSON, not rendered
 * field-by-field.
 */
export async function exportBuyerData(): Promise<unknown> {
  return apiClient.get<unknown>("/api/me/export");
}

/**
 * POST /api/me/delete — permanent. Order/booking history is anonymized in
 * place (kept for tax/accounting retention); everything else is genuinely
 * deleted, including the Cognito identity — the session is unrecoverable
 * after this call succeeds, so the caller must sign the buyer out
 * immediately afterward.
 */
export async function deleteBuyerAccount(): Promise<void> {
  await apiClient.post<void>("/api/me/delete");
}
