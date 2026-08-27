import { apiFetch, apiFetchForm } from '@/lib/api-client';
import type { BookableServiceResponse, PageResponse } from '@/api/types';

/** No pagination UI on the seller dashboard yet — size=200 keeps today's "show everything" behavior. */
export async function listStoreServices(storeId: string): Promise<BookableServiceResponse[]> {
  return (await apiFetch<PageResponse<BookableServiceResponse>>(`/api/stores/${storeId}/bookable-services?size=200`)).content;
}

export function getService(id: string): Promise<BookableServiceResponse> {
  return apiFetch<BookableServiceResponse>(`/api/bookable-services/${id}`);
}

export type BookableServiceFormInput = {
  name: string;
  description: string;
  category: string;
  price: number;
  durationMinutes: number;
  bufferMinutes: number;
  status: 'active' | 'draft';
};

export function serviceToFormInput(service: BookableServiceResponse): BookableServiceFormInput {
  return {
    name: service.name,
    description: service.description,
    category: service.category,
    price: service.price,
    durationMinutes: service.durationMinutes,
    bufferMinutes: service.bufferMinutes,
    status: service.status,
  };
}

/** See buildProductForm's comment in products.ts — Expo's fetch only accepts real Blob parts for binary data. */
async function buildServiceForm(input: BookableServiceFormInput, imageUris?: string[]): Promise<FormData> {
  const form = new FormData();
  form.append('data', new Blob([JSON.stringify(input)], { type: 'application/json' }));
  for (const uri of imageUris ?? []) {
    const rawBlob = await (await fetch(uri)).blob();
    const blob = rawBlob.slice(0, rawBlob.size, 'image/jpeg');
    form.append('images', blob, 'service.jpg');
  }
  return form;
}

export async function createService(storeId: string, input: BookableServiceFormInput, imageUris: string[]): Promise<BookableServiceResponse> {
  return apiFetchForm<BookableServiceResponse>(`/api/stores/${storeId}/bookable-services`, await buildServiceForm(input, imageUris), 'POST');
}

export async function updateService(id: string, input: BookableServiceFormInput, imageUris?: string[]): Promise<BookableServiceResponse> {
  return apiFetchForm<BookableServiceResponse>(`/api/bookable-services/${id}`, await buildServiceForm(input, imageUris), 'PATCH');
}

export function deleteService(id: string): Promise<void> {
  return apiFetch<void>(`/api/bookable-services/${id}`, { method: 'DELETE' });
}
