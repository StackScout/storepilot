import type { PageResponse, Review, ReviewInput } from '@storepilot/shared-api';

import { apiFetch } from '@/lib/api-client';

/** No pagination UI yet — size=200 keeps today's "show everything" behavior. */
export async function listProductReviews(productId: string): Promise<Review[]> {
  return (await apiFetch<PageResponse<Review>>(`/api/products/${productId}/reviews?size=200`, { skipAuth: true })).content;
}

export function createProductReview(productId: string, input: ReviewInput): Promise<Review> {
  return apiFetch<Review>(`/api/products/${productId}/reviews`, { method: 'POST', body: input });
}

/** No pagination UI yet — size=200 keeps today's "show everything" behavior. */
export async function listStoreReviews(storeId: string): Promise<Review[]> {
  return (await apiFetch<PageResponse<Review>>(`/api/stores/${storeId}/reviews?size=200`, { skipAuth: true })).content;
}

export function createStoreReview(storeId: string, input: ReviewInput): Promise<Review> {
  return apiFetch<Review>(`/api/stores/${storeId}/reviews`, { method: 'POST', body: input });
}
