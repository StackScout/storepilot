import type { ShippingDetails } from "./order";

export interface Buyer {
  id: string;
  name: string;
  email: string;
  phone?: string;
  createdAt: string;
}

/** A saved entry in a buyer's address book — see the Address entity backing GET/POST/PATCH/DELETE /api/me/addresses. */
export interface Address {
  id: string;
  label?: string;
  shipping: ShippingDetails;
  isDefault: boolean;
  createdAt: string;
}

/** Same shape POST/PATCH /api/me/addresses accepts. isDefault defaults to false server-side except for a buyer's very first address, which always becomes default regardless of this flag. */
export interface AddressInput {
  label?: string;
  shipping: ShippingDetails;
  isDefault?: boolean;
}
