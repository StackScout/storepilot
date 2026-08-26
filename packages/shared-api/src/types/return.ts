/**
 * A buyer's post-delivery return/refund request against one order — see
 * backend's ReturnRequest.kt. Order.status itself is never touched by this
 * feature; only Order.paymentStatus flips "paid" -> "refunded", once money
 * has actually moved (or, for Stripe, immediately on approval).
 */
export type ReturnReasonCategory =
  | "defective"
  | "wrong-item"
  | "not-as-described"
  | "changed-mind"
  | "other";

/**
 * requested -> approved|rejected (seller decision). approved then either
 * jumps straight to refunded (Stripe — synchronous) or moves to
 * refund-pending (PayHere/COD/bank-transfer — no live refund API for any of
 * these, a human still has to move the money; who confirms it is split by
 * payment-method custody — see ReturnRequestCard). rejected is the only
 * status a new request may be submitted after.
 */
export type ReturnRequestStatus = "requested" | "approved" | "rejected" | "refund-pending" | "refunded";

export interface ReturnRequest {
  id: string;
  orderId: string;
  orderNumber: string;
  storeId: string;
  storeName: string;
  paymentMethod: string;
  reasonCategory: ReturnReasonCategory;
  reasonNote?: string;
  status: ReturnRequestStatus;
  sellerDecisionNote?: string;
  refundReference?: string;
  /**
   * Set only if the order was already included in a Payout/FeeCollection
   * batch at the time the seller approved the return — a visible flag for
   * manual admin reconciliation, since there's no automatic clawback for an
   * already-settled batch. Never set for Stripe orders.
   */
  settlementReconciliationNote?: string;
  createdAt: string;
  decidedAt?: string;
  refundedAt?: string;
}

export interface ReturnRequestCreateInput {
  reasonCategory: ReturnReasonCategory;
  reasonNote?: string;
}
