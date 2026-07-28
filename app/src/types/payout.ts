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
  subtotal: number;
  platformFee: number;
  net: number;
}

export interface Payout {
  id: string;
  storeId: string;
  storeName: string;
  orders: PayoutOrderRef[];
  subtotal: number;
  platformFee: number;
  net: number;
  status: PayoutStatus;
  createdAt: string;
  paidAt?: string;
  bankReference?: string;
}

/**
 * The reverse ledger from Payout — COD and bank-transfer both pay the
 * seller directly, so for those the platform is owed its fee *by* the
 * seller, not the other way around. Same admin-batches-then-marks-settled
 * shape as Payout, just tracking what's owed. Stripe orders never appear in
 * either ledger — see the Stripe-settlements view instead, a read-only
 * reconciliation list, not a batchable ledger.
 */
export type FeeCollectionStatus = "pending" | "collected";

export interface FeeCollectionOrderRef {
  orderId: string;
  orderNumber: string;
  subtotal: number;
  platformFee: number;
}

export interface FeeCollection {
  id: string;
  storeId: string;
  storeName: string;
  orders: FeeCollectionOrderRef[];
  subtotal: number;
  platformFee: number;
  status: FeeCollectionStatus;
  createdAt: string;
  collectedAt?: string;
  reference?: string;
}
