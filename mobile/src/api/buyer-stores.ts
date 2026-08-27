import type { FollowStatus, PageResponse, Store, StoreCategory, StorePublicSettings } from '@storepilot/shared-api';

import { ApiError, apiFetch, resolveAssetUrl, toQueryString } from '@/lib/api-client';

/** Port of the web app's stores.service.ts public-browsing functions. Seller-scoped store management stays in api/stores.ts. */

function normalizeStore(store: Store): Store {
  return {
    ...store,
    logoUrl: store.logoUrl ? resolveAssetUrl(store.logoUrl) : store.logoUrl,
    bannerUrl: store.bannerUrl ? resolveAssetUrl(store.bannerUrl) : store.bannerUrl,
  };
}

export interface StoreQueryParams {
  category?: StoreCategory;
  query?: string;
  page?: number;
  size?: number;
}

const DEFAULT_PAGE_SIZE = 24;

export async function listStores(params: StoreQueryParams = {}): Promise<PageResponse<Store>> {
  const qs = toQueryString({ category: params.category, query: params.query, page: params.page ?? 0, size: params.size ?? DEFAULT_PAGE_SIZE });
  const page = await apiFetch<PageResponse<Store>>(`/api/stores${qs}`, { skipAuth: true });
  return { ...page, content: page.content.map(normalizeStore) };
}

export async function getStoreBySlug(slug: string): Promise<Store | null> {
  try {
    const store = await apiFetch<Store>(`/api/stores/${slug}`, { skipAuth: true });
    return normalizeStore(store);
  } catch (e) {
    if (e instanceof ApiError && e.status === 404) return null;
    throw e;
  }
}

/** GET /stores/:id/public-settings — no auth required; buyer-safe subset for checkout/order pages. */
export async function getPublicStoreSettings(storeId: string): Promise<StorePublicSettings | null> {
  try {
    return await apiFetch<StorePublicSettings>(`/api/stores/${storeId}/public-settings`, { skipAuth: true });
  } catch (e) {
    if (e instanceof ApiError && e.status === 404) return null;
    throw e;
  }
}

/** GET /stores/:id/follow — public; reports false for a signed-out visitor. */
export function getFollowStatus(storeId: string): Promise<FollowStatus> {
  return apiFetch<FollowStatus>(`/api/stores/${storeId}/follow`, { skipAuth: true });
}

/** POST /stores/:id/follow — requires a signed-in buyer. Idempotent. */
export function followStore(storeId: string): Promise<FollowStatus> {
  return apiFetch<FollowStatus>(`/api/stores/${storeId}/follow`, { method: 'POST' });
}

/** DELETE /stores/:id/follow — requires a signed-in buyer. Idempotent. */
export async function unfollowStore(storeId: string): Promise<void> {
  await apiFetch<void>(`/api/stores/${storeId}/follow`, { method: 'DELETE' });
}
