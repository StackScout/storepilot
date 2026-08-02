/** Matches backend SellerPlan — "free" or "pro". Pro unlocks Cash on Delivery + Bank transfer as payment options (more features expected to gate on this later). */
export type SellerPlanTier = "free" | "pro";

/** Matches backend SellerPlanResponse. */
export interface SellerPlanInfo {
  plan: SellerPlanTier;
  currentPeriodEnd?: string;
  cancelAtPeriodEnd: boolean;
  /** Cents — see backend SellerPlan.kt. */
  monthlyPriceCents: number;
  currencyCode: string;
}
