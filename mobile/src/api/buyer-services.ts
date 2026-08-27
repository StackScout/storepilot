import type { BookableService, DayAvailability } from '@storepilot/shared-api';

import { ApiError, apiFetch, resolveAssetUrl, toQueryString } from '@/lib/api-client';

/** Port of the web app's public-browsing parts of bookable-services.service.ts + availability.service.ts. */

function normalizeService(service: BookableService): BookableService {
  return { ...service, images: service.images.map((img) => ({ ...img, url: resolveAssetUrl(img.url) })) };
}

export async function listServicesByStore(storeId: string): Promise<BookableService[]> {
  return (await apiFetch<BookableService[]>(`/api/stores/${storeId}/bookable-services`, { skipAuth: true })).map(normalizeService);
}

export async function getServiceById(id: string): Promise<BookableService | null> {
  try {
    const service = await apiFetch<BookableService>(`/api/bookable-services/${id}`, { skipAuth: true });
    return normalizeService(service);
  } catch (e) {
    if (e instanceof ApiError && e.status === 404) return null;
    throw e;
  }
}

/** GET /stores/:storeId/bookable-services/:serviceId/availability?from=&to= — computed on read; omitted from/to defaults to today through the next 30 days. */
export function getSlots(storeId: string, serviceId: string, from?: string, to?: string): Promise<DayAvailability[]> {
  const qs = toQueryString({ from, to });
  return apiFetch<DayAvailability[]>(`/api/stores/${storeId}/bookable-services/${serviceId}/availability${qs}`, { skipAuth: true });
}
