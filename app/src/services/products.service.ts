import { apiClient, toQueryString } from "@/lib/api-client";
import type { Product, ProductFormInput, StoreCategory } from "@/types";

export interface ProductQueryParams {
  category?: StoreCategory;
  query?: string;
  sort?: "newest" | "price-asc" | "price-desc" | "rating";
  limit?: number;
}

function sortProducts(products: Product[], sort: ProductQueryParams["sort"]): Product[] {
  switch (sort) {
    case "price-asc":
      return [...products].sort((a, b) => a.priceLkr - b.priceLkr);
    case "price-desc":
      return [...products].sort((a, b) => b.priceLkr - a.priceLkr);
    case "rating":
      return [...products].sort((a, b) => b.rating - a.rating);
    case "newest":
    default:
      return [...products].sort(
        (a, b) => new Date(b.createdAt).getTime() - new Date(a.createdAt).getTime(),
      );
  }
}

/**
 * GET /products — sort/limit applied client-side after fetching; the
 * backend's search endpoint only filters by category/query.
 */
export async function listProducts(params: ProductQueryParams = {}): Promise<Product[]> {
  const qs = toQueryString({ category: params.category, query: params.query });
  const results = await apiClient.get<Product[]>(`/api/products${qs}`);
  const sorted = sortProducts(results, params.sort);
  return params.limit ? sorted.slice(0, params.limit) : sorted;
}

/** GET /products/featured — top-rated products; no dedicated backend endpoint, sorted client-side. */
export async function getFeaturedProducts(limit = 8): Promise<Product[]> {
  const results = await apiClient.get<Product[]>("/api/products");
  return [...results].sort((a, b) => b.rating - a.rating).slice(0, limit);
}

/**
 * GET /stores/:storeSlug/products/:productSlug — no dedicated backend
 * endpoint either; composed from the store-by-slug and products-by-store
 * endpoints, same two lookups the mock effectively needed anyway.
 */
export async function getProductBySlug(
  storeSlug: string,
  productSlug: string,
): Promise<Product | null> {
  const store = await apiClient.getOrNull<{ id: string }>(`/api/stores/${storeSlug}`);
  if (!store) return null;
  const products = await apiClient.get<Product[]>(`/api/stores/${store.id}/products`);
  return products.find((p) => p.slug === productSlug) ?? null;
}

/** GET /products/:id */
export async function getProductById(id: string): Promise<Product | null> {
  return apiClient.getOrNull<Product>(`/api/products/${id}`);
}

/** GET /stores/:storeId/products */
export async function listProductsByStore(storeId: string): Promise<Product[]> {
  return apiClient.get<Product[]>(`/api/stores/${storeId}/products`);
}

/**
 * POST /stores/:storeId/products — `storeName`/`storeSlug` are accepted for
 * call-site compatibility but unused: the backend derives both from the
 * store relation instead of trusting client-denormalized values.
 */
export async function createProduct(
  storeId: string,
  _storeName: string,
  _storeSlug: string,
  input: ProductFormInput,
): Promise<Product> {
  return apiClient.post<Product>(`/api/stores/${storeId}/products`, input);
}

/** PATCH /products/:id */
export async function updateProduct(id: string, input: ProductFormInput): Promise<Product> {
  return apiClient.patch<Product>(`/api/products/${id}`, input);
}

/** DELETE /products/:id */
export async function deleteProduct(id: string): Promise<void> {
  await apiClient.delete<void>(`/api/products/${id}`);
}
