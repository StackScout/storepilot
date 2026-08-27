import type { Review, ReviewInput } from '@storepilot/shared-api';

import { apiFetch } from '@/lib/api-client';

export function listProductReviews(productId: string): Promise<Review[]> {
  return apiFetch<Review[]>(`/api/products/${productId}/reviews`, { skipAuth: true });
}

export function createProductReview(productId: string, input: ReviewInput): Promise<Review> {
  return apiFetch<Review>(`/api/products/${productId}/reviews`, { method: 'POST', body: input });
}

export function listStoreReviews(storeId: string): Promise<Review[]> {
  return apiFetch<Review[]>(`/api/stores/${storeId}/reviews`, { skipAuth: true });
}

export function createStoreReview(storeId: string, input: ReviewInput): Promise<Review> {
  return apiFetch<Review>(`/api/stores/${storeId}/reviews`, { method: 'POST', body: input });
}
