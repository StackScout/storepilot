import { apiClient, resolveAssetUrl, toQueryString } from "@/lib/api-client";
import type { BookableService, BookableServiceFormInput, PageResponse } from "@/types";

/** Image URLs coming back from the backend may be relative or already absolute — normalize once here, same as products.service.ts's normalizeProduct. */
function normalizeService(service: BookableService): BookableService {
  return { ...service, images: service.images.map((img) => ({ ...img, url: resolveAssetUrl(img.url) })) };
}

/** GET /bookable-services/:id */
export async function getServiceById(id: string): Promise<BookableService | null> {
  const service = await apiClient.getOrNull<BookableService>(`/api/bookable-services/${id}`);
  return service ? normalizeService(service) : null;
}

/** GET /stores/:storeId/bookable-services — paginated server-side; defaults to a generous page size since a store's service menu is usually small. */
export async function listServicesByStore(storeId: string, page = 0, size = 100): Promise<PageResponse<BookableService>> {
  const qs = toQueryString({ page, size });
  const result = await apiClient.get<PageResponse<BookableService>>(`/api/stores/${storeId}/bookable-services${qs}`);
  return { ...result, content: result.content.map(normalizeService) };
}

/**
 * No dedicated backend endpoint for slug lookup — composed from the
 * store-by-slug and services-by-store endpoints, same pattern as
 * products.service.ts's getProductBySlug. Walks every page of the store's
 * service menu, same reasoning as getProductBySlug.
 */
export async function getServiceBySlug(storeSlug: string, serviceSlug: string): Promise<BookableService | null> {
  const store = await apiClient.getOrNull<{ id: string }>(`/api/stores/${storeSlug}`);
  if (!store) return null;
  let page = 0;
  while (true) {
    const result = await listServicesByStore(store.id, page);
    const match = result.content.find((s) => s.slug === serviceSlug);
    if (match) return match;
    if (page + 1 >= result.totalPages) return null;
    page += 1;
  }
}

function buildServiceFormData(input: BookableServiceFormInput, images: File[]): FormData {
  const formData = new FormData();
  formData.append("data", new Blob([JSON.stringify(input)], { type: "application/json" }));
  images.forEach((file) => formData.append("images", file));
  return formData;
}

/** POST /stores/:storeId/bookable-services */
export async function createService(
  storeId: string,
  input: BookableServiceFormInput,
  images: File[],
): Promise<BookableService> {
  const service = await apiClient.postForm<BookableService>(`/api/stores/${storeId}/bookable-services`, buildServiceFormData(input, images));
  return normalizeService(service);
}

/** PATCH /bookable-services/:id — images empty means "keep the service's existing images", same convention as updateProduct. */
export async function updateService(
  id: string,
  input: BookableServiceFormInput,
  images: File[] = [],
): Promise<BookableService> {
  const service = await apiClient.patchForm<BookableService>(`/api/bookable-services/${id}`, buildServiceFormData(input, images));
  return normalizeService(service);
}

/** DELETE /bookable-services/:id — refused by the backend while any non-terminal booking still references it. */
export async function deleteService(id: string): Promise<void> {
  await apiClient.delete<void>(`/api/bookable-services/${id}`);
}
