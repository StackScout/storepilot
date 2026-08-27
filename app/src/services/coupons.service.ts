import { apiClient, toQueryString } from "@/lib/api-client";
import type { Coupon, CouponInput, CouponPreviewResponse, PageResponse } from "@/types";

/** GET /stores/:storeId/coupons — seller-scoped, this store's own coupons. Paginated server-side; defaults to a generous page size since a seller usually runs a handful of active coupons. */
export async function listStoreCoupons(storeId: string, page = 0, size = 50): Promise<PageResponse<Coupon>> {
  const qs = toQueryString({ page, size });
  return apiClient.get<PageResponse<Coupon>>(`/api/stores/${storeId}/coupons${qs}`);
}

/** POST /stores/:storeId/coupons */
export async function createStoreCoupon(storeId: string, input: CouponInput): Promise<Coupon> {
  return apiClient.post<Coupon>(`/api/stores/${storeId}/coupons`, input);
}

/** PATCH /coupons/:id — seller-scoped; the backend refuses this against a coupon that isn't the caller's own store's. */
export async function updateStoreCoupon(id: string, input: CouponInput): Promise<Coupon> {
  return apiClient.patch<Coupon>(`/api/coupons/${id}`, input);
}

/** DELETE /coupons/:id */
export async function deleteStoreCoupon(id: string): Promise<void> {
  await apiClient.delete<void>(`/api/coupons/${id}`);
}

/** GET /admin/coupons — admin-scoped, platform-wide coupons. Paginated server-side. */
export async function listPlatformCoupons(page = 0, size = 50): Promise<PageResponse<Coupon>> {
  const qs = toQueryString({ page, size });
  return apiClient.get<PageResponse<Coupon>>(`/api/admin/coupons${qs}`);
}

/** POST /admin/coupons */
export async function createPlatformCoupon(input: CouponInput): Promise<Coupon> {
  return apiClient.post<Coupon>("/api/admin/coupons", input);
}

/** PATCH /admin/coupons/:id */
export async function updatePlatformCoupon(id: string, input: CouponInput): Promise<Coupon> {
  return apiClient.patch<Coupon>(`/api/admin/coupons/${id}`, input);
}

/** DELETE /admin/coupons/:id */
export async function deletePlatformCoupon(id: string): Promise<void> {
  await apiClient.delete<void>(`/api/admin/coupons/${id}`);
}

/** POST /coupons/preview — public, side-effect-free dry run shown before checkout actually submits. */
export async function previewCoupon(
  code: string,
  storeId: string,
  kind: "order" | "booking",
  amount: number,
): Promise<CouponPreviewResponse> {
  return apiClient.post<CouponPreviewResponse>("/api/coupons/preview", { code, storeId, kind, amount });
}
