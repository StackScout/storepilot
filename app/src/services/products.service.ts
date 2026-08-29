import { apiClient, resolveAssetUrl, toQueryString } from "@/lib/api-client";
import type { PageResponse, Product, ProductFormInput, StoreCategory, WishlistStatus } from "@/types";

/** Image URLs coming back from the backend may be relative (local FileStorageService) or already absolute (S3 presigned, or picsum.photos seed data) — normalize once here. */
function normalizeProduct(product: Product): Product {
  return { ...product, images: product.images.map((img) => ({ ...img, url: resolveAssetUrl(img.url) })) };
}

export interface ProductQueryParams {
  category?: StoreCategory;
  query?: string;
  sort?: "newest" | "price-asc" | "price-desc" | "rating";
  minPrice?: number;
  maxPrice?: number;
  page?: number;
  size?: number;
}

const DEFAULT_PAGE_SIZE = 24;

/**
 * GET /products — filtering, sorting, and pagination all happen server-side
 * (see backend ProductService#search); this never fetches more than one
 * page's worth of rows.
 */
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
  const result = await apiClient.get<PageResponse<Product>>(`/api/products${qs}`);
  return { ...result, content: result.content.map(normalizeProduct) };
}

/** GET /products/featured — top-rated products; no dedicated backend endpoint, just page 0 of a rating-sorted search. */
export async function getFeaturedProducts(limit = 8): Promise<Product[]> {
  return (await listProducts({ sort: "rating", page: 0, size: limit })).content;
}

/** GET /stores/:storeId/products — paginated server-side; defaults to a generous page size since most callers want "the whole catalog." */
export async function listProductsByStore(storeId: string, page = 0, size = 100): Promise<PageResponse<Product>> {
  const qs = toQueryString({ page, size });
  const result = await apiClient.get<PageResponse<Product>>(`/api/stores/${storeId}/products${qs}`);
  return { ...result, content: result.content.map(normalizeProduct) };
}

/**
 * GET /stores/:storeSlug/products/:productSlug — no dedicated backend
 * endpoint either; composed from the store-by-slug and products-by-store
 * endpoints, same two lookups the mock effectively needed anyway. Walks
 * every page of the store's catalog (rare beyond page 0 — a store's
 * product count only exceeds one page at the max page size for a genuinely
 * large catalog) since the product being looked up could be on any page.
 */
export async function getProductBySlug(
  storeSlug: string,
  productSlug: string,
): Promise<Product | null> {
  const store = await apiClient.getOrNull<{ id: string }>(`/api/stores/${storeSlug}`);
  if (!store) return null;
  let page = 0;
  while (true) {
    const result = await listProductsByStore(store.id, page);
    const match = result.content.find((p) => p.slug === productSlug);
    if (match) return match;
    if (page + 1 >= result.totalPages) return null;
    page += 1;
  }
}

/** GET /products/:id */
export async function getProductById(id: string): Promise<Product | null> {
  const product = await apiClient.getOrNull<Product>(`/api/products/${id}`);
  return product ? normalizeProduct(product) : null;
}

function buildProductFormData(input: ProductFormInput, images: File[]): FormData {
  const formData = new FormData();
  formData.append("data", new Blob([JSON.stringify(input)], { type: "application/json" }));
  images.forEach((file) => formData.append("images", file));
  return formData;
}

/**
 * POST /stores/:storeId/products — `storeName`/`storeSlug` are accepted for
 * call-site compatibility but unused: the backend derives both from the
 * store relation instead of trusting client-denormalized values. `images`
 * must contain at least one file — the backend rejects an empty list.
 */
export async function createProduct(
  storeId: string,
  _storeName: string,
  _storeSlug: string,
  input: ProductFormInput,
  images: File[],
): Promise<Product> {
  const product = await apiClient.postForm<Product>(`/api/stores/${storeId}/products`, buildProductFormData(input, images));
  return normalizeProduct(product);
}

/**
 * PATCH /products/:id — `images` empty means "keep the product's existing
 * images"; non-empty replaces the whole set (see backend ProductService.update).
 */
export async function updateProduct(
  id: string,
  input: ProductFormInput,
  images: File[] = [],
): Promise<Product> {
  const product = await apiClient.patchForm<Product>(`/api/products/${id}`, buildProductFormData(input, images));
  return normalizeProduct(product);
}

/** DELETE /products/:id */
export async function deleteProduct(id: string): Promise<void> {
  await apiClient.delete<void>(`/api/products/${id}`);
}

/** GET /products/:id/wishlist — public; reports false for a signed-out visitor. */
export async function getWishlistStatus(productId: string): Promise<WishlistStatus> {
  return apiClient.get<WishlistStatus>(`/api/products/${productId}/wishlist`);
}

/** POST /products/:id/wishlist — requires a signed-in buyer. Idempotent. */
export async function addToWishlist(productId: string): Promise<WishlistStatus> {
  return apiClient.post<WishlistStatus>(`/api/products/${productId}/wishlist`);
}

/** DELETE /products/:id/wishlist — requires a signed-in buyer. Idempotent. */
export async function removeFromWishlist(productId: string): Promise<void> {
  await apiClient.delete<void>(`/api/products/${productId}/wishlist`);
}

/** GET /me/wishlist — the signed-in buyer's saved products. Paginated server-side; defaults to a generous page size since a wishlist is usually shown in full. */
export async function listMyWishlist(page = 0, size = 100): Promise<PageResponse<Product>> {
  const qs = toQueryString({ page, size });
  const result = await apiClient.get<PageResponse<Product>>(`/api/me/wishlist${qs}`);
  return { ...result, content: result.content.map(normalizeProduct) };
}
