import { apiClient } from "@/lib/api-client";
import type { Address, AddressInput } from "@/types";

/** GET /api/me/addresses — the signed-in buyer's saved address book, default first. */
export async function listAddresses(): Promise<Address[]> {
  return apiClient.get<Address[]>("/api/me/addresses");
}

/** POST /api/me/addresses — a buyer's very first saved address always becomes their default, regardless of input.isDefault. */
export async function createAddress(input: AddressInput): Promise<Address> {
  return apiClient.post<Address>("/api/me/addresses", input);
}

/** PATCH /api/me/addresses/{id} */
export async function updateAddress(id: string, input: AddressInput): Promise<Address> {
  return apiClient.patch<Address>(`/api/me/addresses/${id}`, input);
}

/** POST /api/me/addresses/{id}/default */
export async function setDefaultAddress(id: string): Promise<Address> {
  return apiClient.post<Address>(`/api/me/addresses/${id}/default`);
}

/** DELETE /api/me/addresses/{id} — if the deleted address was the default, the next-oldest remaining one (if any) is promoted automatically. */
export async function deleteAddress(id: string): Promise<void> {
  await apiClient.delete<void>(`/api/me/addresses/${id}`);
}
