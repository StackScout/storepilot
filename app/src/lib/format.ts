import type { PaymentMethod, ReturnReasonCategory, ReturnRequestStatus } from "@/types";

const PAYMENT_METHOD_LABELS: Record<PaymentMethod, string> = {
  cod: "Cash on Delivery",
  payhere: "PayHere (online)",
  "bank-transfer": "Bank transfer",
  stripe: "Stripe (online)",
};

export function paymentMethodLabel(method: PaymentMethod): string {
  return PAYMENT_METHOD_LABELS[method];
}

/** Same wire values as paymentMethodLabel, but "cod" reads as "Pay at venue" in a booking context — see docs/features/bookings.md. */
export function bookingPaymentMethodLabel(method: PaymentMethod): string {
  return method === "cod" ? "Pay at venue" : PAYMENT_METHOD_LABELS[method];
}

const RETURN_REASON_LABELS: Record<ReturnReasonCategory, string> = {
  defective: "Item arrived defective",
  "wrong-item": "Received the wrong item",
  "not-as-described": "Not as described",
  "changed-mind": "Changed my mind",
  other: "Other",
};

export function returnReasonLabel(reason: ReturnReasonCategory): string {
  return RETURN_REASON_LABELS[reason];
}

const RETURN_STATUS_LABELS: Record<ReturnRequestStatus, string> = {
  requested: "Requested",
  approved: "Approved",
  rejected: "Rejected",
  "refund-pending": "Refund pending",
  refunded: "Refunded",
};

export function returnStatusLabel(status: ReturnRequestStatus): string {
  return RETURN_STATUS_LABELS[status];
}

export function formatDate(iso: string): string {
  return new Date(iso).toLocaleDateString("en-LK", {
    day: "numeric",
    month: "short",
    year: "numeric",
  });
}

export function formatDateTime(iso: string): string {
  return new Date(iso).toLocaleString("en-LK", {
    day: "numeric",
    month: "short",
    year: "numeric",
    hour: "numeric",
    minute: "2-digit",
  });
}

export function timeAgo(iso: string): string {
  const diffMs = Date.now() - new Date(iso).getTime();
  const minutes = Math.floor(diffMs / 60000);
  if (minutes < 1) return "just now";
  if (minutes < 60) return `${minutes}m ago`;
  const hours = Math.floor(minutes / 60);
  if (hours < 24) return `${hours}h ago`;
  const days = Math.floor(hours / 24);
  if (days < 30) return `${days}d ago`;
  return formatDate(iso);
}
