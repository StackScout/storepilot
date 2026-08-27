import { apiFetch, apiFetchForm } from '@/lib/api-client';
import type { PageResponse, ProductResponse, ProductStatus } from '@/api/types';

/** No pagination UI on the seller dashboard yet — size=200 keeps today's "show everything" behavior. */
export async function listStoreProducts(storeId: string): Promise<ProductResponse[]> {
  return (await apiFetch<PageResponse<ProductResponse>>(`/api/stores/${storeId}/products?size=200`)).content;
}

export function getProduct(id: string): Promise<ProductResponse> {
  return apiFetch<ProductResponse>(`/api/products/${id}`);
}

export type ProductFormInput = {
  name: string;
  description: string;
  category: string;
  price: number;
  compareAtPrice: number | undefined;
  stockQuantity: number;
  trackStock: boolean;
  sku: string | undefined;
  status: ProductStatus;
};

/** For pre-filling the edit form, and for a quick partial edit (e.g. stock-only) that still has to resend the full DTO shape — see updateProduct's doc comment, PATCH here means "replace this product's fields," not a JSON-merge-patch. */
export function productToFormInput(product: ProductResponse): ProductFormInput {
  return {
    name: product.name,
    description: product.description,
    category: product.category,
    price: product.price,
    compareAtPrice: product.compareAtPrice,
    stockQuantity: product.stockQuantity,
    trackStock: product.trackStock,
    sku: product.sku,
    status: product.status === 'out-of-stock' ? 'active' : product.status,
  };
}

/**
 * Expo SDK 57's global fetch/FormData encoder only accepts real `Blob` parts for binary
 * data — it explicitly does not support React Native's classic {uri,name,type} file
 * reference (throws "Unsupported FormDataPart implementation"). So each local image uri
 * is read into a real Blob via fetch() before being appended. That Blob comes back with
 * no usable MIME type (backend then rejects with "Unsupported file type: null"), so
 * `.slice()` is used to force a proper image/jpeg content type onto it.
 */
async function buildProductForm(input: ProductFormInput, imageUris?: string[]): Promise<FormData> {
  const form = new FormData();
  form.append('data', new Blob([JSON.stringify(input)], { type: 'application/json' }));
  for (const uri of imageUris ?? []) {
    const rawBlob = await (await fetch(uri)).blob();
    const blob = rawBlob.slice(0, rawBlob.size, 'image/jpeg');
    form.append('images', blob, 'product.jpg');
  }
  return form;
}

/** New products require at least one image (server-enforced) — imageUris must be non-empty here. */
export async function createProduct(storeId: string, input: ProductFormInput, imageUris: string[]): Promise<ProductResponse> {
  return apiFetchForm<ProductResponse>(`/api/stores/${storeId}/products`, await buildProductForm(input, imageUris), 'POST');
}

/** imageUris is optional on edit — omit/empty leaves existing images untouched; providing any REPLACES the whole existing image set (the backend has no partial-append), see ProductService.update(). */
export async function updateProduct(id: string, input: ProductFormInput, imageUris?: string[]): Promise<ProductResponse> {
  return apiFetchForm<ProductResponse>(`/api/products/${id}`, await buildProductForm(input, imageUris), 'PATCH');
}
