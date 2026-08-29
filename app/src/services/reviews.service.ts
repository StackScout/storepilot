import { apiClient, toQueryString } from "@/lib/api-client";
import type { PageResponse, Review, ReviewInput } from "@/types";

/** GET /products/:id/reviews — public. Paginated server-side. */
export async function listProductReviews(productId: string, page = 0, size = 20): Promise<PageResponse<Review>> {
  const qs = toQueryString({ page, size });
  return apiClient.get<PageResponse<Review>>(`/api/products/${productId}/reviews${qs}`);
}

/** POST /products/:id/reviews — requires a signed-in buyer with a delivered order containing this product. */
export async function createProductReview(productId: string, input: ReviewInput): Promise<Review> {
  return apiClient.post<Review>(`/api/products/${productId}/reviews`, input);
}

/** GET /stores/:id/reviews — public, store-level reviews only (not product reviews). Paginated server-side. */
export async function listStoreReviews(storeId: string, page = 0, size = 20): Promise<PageResponse<Review>> {
  const qs = toQueryString({ page, size });
  return apiClient.get<PageResponse<Review>>(`/api/stores/${storeId}/reviews${qs}`);
}

/** POST /stores/:id/reviews — requires a signed-in buyer with a delivered order or completed booking at this store. */
export async function createStoreReview(storeId: string, input: ReviewInput): Promise<Review> {
  return apiClient.post<Review>(`/api/stores/${storeId}/reviews`, input);
}
