import { apiFetch } from '@/lib/api-client';
import type { CouponInput, CouponResponse } from '@/api/types';

export function listStoreCoupons(storeId: string): Promise<CouponResponse[]> {
  return apiFetch<CouponResponse[]>(`/api/stores/${storeId}/coupons`);
}

export function createCoupon(storeId: string, input: CouponInput): Promise<CouponResponse> {
  return apiFetch<CouponResponse>(`/api/stores/${storeId}/coupons`, { method: 'POST', body: input });
}

/** Store is derived server-side from the existing coupon — not passed here. */
export function updateCoupon(id: string, input: CouponInput): Promise<CouponResponse> {
  return apiFetch<CouponResponse>(`/api/coupons/${id}`, { method: 'PATCH', body: input });
}

export function deleteCoupon(id: string): Promise<void> {
  return apiFetch<void>(`/api/coupons/${id}`, { method: 'DELETE' });
}
