import type { Address, AddressInput } from '@storepilot/shared-api';

import { apiFetch } from '@/lib/api-client';

export function listAddresses(): Promise<Address[]> {
  return apiFetch<Address[]>('/api/me/addresses');
}

export function createAddress(input: AddressInput): Promise<Address> {
  return apiFetch<Address>('/api/me/addresses', { method: 'POST', body: input });
}

export function updateAddress(id: string, input: AddressInput): Promise<Address> {
  return apiFetch<Address>(`/api/me/addresses/${id}`, { method: 'PATCH', body: input });
}

export function setDefaultAddress(id: string): Promise<Address> {
  return apiFetch<Address>(`/api/me/addresses/${id}/default`, { method: 'POST' });
}

export async function deleteAddress(id: string): Promise<void> {
  await apiFetch<void>(`/api/me/addresses/${id}`, { method: 'DELETE' });
}
