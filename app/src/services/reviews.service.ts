import { apiClient } from "@/lib/api-client";
import type { Review, ReviewInput } from "@/types";

/** GET /products/:id/reviews — public. */
export async function listProductReviews(productId: string): Promise<Review[]> {
  return apiClient.get<Review[]>(`/api/products/${productId}/reviews`);
}

/** POST /products/:id/reviews — requires a signed-in buyer with a delivered order containing this product. */
export async function createProductReview(productId: string, input: ReviewInput): Promise<Review> {
  return apiClient.post<Review>(`/api/products/${productId}/reviews`, input);
}

/** GET /stores/:id/reviews — public, store-level reviews only (not product reviews). */
export async function listStoreReviews(storeId: string): Promise<Review[]> {
  return apiClient.get<Review[]>(`/api/stores/${storeId}/reviews`);
}

/** POST /stores/:id/reviews — requires a signed-in buyer with a delivered order or completed booking at this store. */
export async function createStoreReview(storeId: string, input: ReviewInput): Promise<Review> {
  return apiClient.post<Review>(`/api/stores/${storeId}/reviews`, input);
}
