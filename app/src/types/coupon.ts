export type DiscountType = "percent" | "fixed";

/** Mirrors backend Coupon. `storeId` undefined means platform-wide (admin-managed); set means store-specific (seller-managed). */
export interface Coupon {
  id: string;
  code: string;
  storeId?: string;
  discountType: DiscountType;
  /** Percent (1-100) when discountType is "percent"; cents when "fixed". */
  discountValue: number;
  appliesToOrders: boolean;
  appliesToBookings: boolean;
  /** Undefined means unlimited uses. */
  maxUses?: number;
  usedCount: number;
  /** Cents — the order subtotal or booking service price must be at least this for the coupon to apply. */
  minSubtotal: number;
  expiresAt?: string;
  active: boolean;
  createdAt: string;
}

export interface CouponInput {
  code: string;
  discountType: DiscountType;
  discountValue: number;
  appliesToOrders: boolean;
  appliesToBookings: boolean;
  maxUses?: number;
  minSubtotal: number;
  expiresAt?: string;
  active: boolean;
}

export interface CouponPreviewResponse {
  valid: boolean;
  discountAmount: number;
  message?: string;
}
