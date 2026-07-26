/**
 * A basic settlement ledger: a batch of eligible orders bundled into one
 * payout run for a store. Distinct from deriving totals from Orders at read
 * time (see docs/database-model.md's "Payout / Settlement" entity). Created
 * and released only by the (mock) admin side, never by the seller — payouts
 * represent the platform actually moving escrowed funds, so the seller
 * dashboard only ever displays this ledger, it never writes to it.
 */
export type PayoutStatus = "scheduled" | "paid";

export interface PayoutOrderRef {
  orderId: string;
  orderNumber: string;
  subtotalLkr: number;
  platformFeeLkr: number;
  netLkr: number;
}

export interface Payout {
  id: string;
  storeId: string;
  storeName: string;
  orders: PayoutOrderRef[];
  subtotalLkr: number;
  platformFeeLkr: number;
  netLkr: number;
  status: PayoutStatus;
  createdAt: string;
  paidAt?: string;
  bankReference?: string;
}
