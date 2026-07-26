import { apiClient } from "@/lib/api-client";
import type { Buyer, BuyerRegistrationInput, ShippingDetails } from "@/types";

/** GET /buyers/by-email — a real lookup, unlike seller /login's mock (backend returns 200 + null body if not found). */
export async function getBuyerByEmail(email: string): Promise<Buyer | null> {
  return apiClient.get<Buyer | null>(`/api/buyers/by-email?email=${encodeURIComponent(email)}`);
}

/** GET /buyers/:id */
export async function getBuyerById(id: string): Promise<Buyer | null> {
  return apiClient.getOrNull<Buyer>(`/api/buyers/${id}`);
}

/** POST /buyers (register) */
export async function registerBuyer(input: BuyerRegistrationInput): Promise<Buyer> {
  return apiClient.post<Buyer>("/api/buyers", input);
}

/** PATCH /buyers/:id/default-shipping */
export async function updateDefaultShipping(buyerId: string, shipping: ShippingDetails): Promise<Buyer> {
  return apiClient.patch<Buyer>(`/api/buyers/${buyerId}/default-shipping`, shipping);
}
