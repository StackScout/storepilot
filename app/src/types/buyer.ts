import type { ShippingDetails } from "./order";

export interface Buyer {
  id: string;
  name: string;
  email: string;
  phone?: string;
  /** Snapshot of the last-used checkout address, offered as a prefill. */
  defaultShipping?: ShippingDetails;
  createdAt: string;
}
