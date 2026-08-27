import type { PageResponse, Product, StoreCategory, WishlistStatus } from '@storepilot/shared-api';

import { ApiError, apiFetch, resolveAssetUrl, toQueryString } from '@/lib/api-client';

/** Direct port of the web app's products.service.ts public-browsing functions — same endpoints, same normalization. Seller-scoped product management stays in api/products.ts. */

function normalizeProduct(product: Product): Product {
  return { ...product, images: product.images.map((img) => ({ ...img, url: resolveAssetUrl(img.url) })) };
}

export interface ProductQueryParams {
  category?: StoreCategory;
  query?: string;
  sort?: 'newest' | 'price-asc' | 'price-desc' | 'rating';
  minPrice?: number;
  maxPrice?: number;
  page?: number;
  size?: number;
}

const DEFAULT_PAGE_SIZE = 24;

export async function listProducts(params: ProductQueryParams = {}): Promise<PageResponse<Product>> {
  const qs = toQueryString({
    category: params.category,
    query: params.query,
    sort: params.sort,
    minPrice: params.minPrice,
    maxPrice: params.maxPrice,
    page: params.page ?? 0,
    size: params.size ?? DEFAULT_PAGE_SIZE,
  });
  const result = await apiFetch<PageResponse<Product>>(`/api/products${qs}`, { skipAuth: true });
  return { ...result, content: result.content.map(normalizeProduct) };
}

export async function getFeaturedProducts(limit = 8): Promise<Product[]> {
  return (await listProducts({ sort: 'rating', page: 0, size: limit })).content;
}

export async function getProductById(id: string): Promise<Product | null> {
  try {
    const product = await apiFetch<Product>(`/api/products/${id}`, { skipAuth: true });
    return normalizeProduct(product);
  } catch (e) {
    if (e instanceof ApiError && e.status === 404) return null;
    throw e;
  }
}

export async function listProductsByStore(storeId: string): Promise<Product[]> {
  return (await apiFetch<Product[]>(`/api/stores/${storeId}/products`, { skipAuth: true })).map(normalizeProduct);
}

/** GET /products/:id/wishlist — public; reports false for a signed-out visitor. */
export function getWishlistStatus(productId: string): Promise<WishlistStatus> {
  return apiFetch<WishlistStatus>(`/api/products/${productId}/wishlist`, { skipAuth: true });
}

/** POST /products/:id/wishlist — requires a signed-in buyer. Idempotent. */
export function addToWishlist(productId: string): Promise<WishlistStatus> {
  return apiFetch<WishlistStatus>(`/api/products/${productId}/wishlist`, { method: 'POST' });
}

/** DELETE /products/:id/wishlist — requires a signed-in buyer. Idempotent. */
export async function removeFromWishlist(productId: string): Promise<void> {
  await apiFetch<void>(`/api/products/${productId}/wishlist`, { method: 'DELETE' });
}

/** GET /me/wishlist — the signed-in buyer's saved products. */
export async function listMyWishlist(): Promise<Product[]> {
  return (await apiFetch<Product[]>('/api/me/wishlist')).map(normalizeProduct);
}
