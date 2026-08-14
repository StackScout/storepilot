import { StatusBadge, type StatusTone } from "@/components/shared/status-badge";
import type { BookingStatus } from "@/types";
import { BOOKING_STATUS_LABELS } from "@/lib/constants";

const STATUS_TONES: Record<BookingStatus, StatusTone> = {
  pending: "warning",
  confirmed: "info",
  completed: "success",
  cancelled: "neutral",
  "no-show": "danger",
};

export function BookingStatusBadge({ status, className }: { status: BookingStatus; className?: string }) {
  return (
    <StatusBadge tone={STATUS_TONES[status]} className={className}>
      {BOOKING_STATUS_LABELS[status]}
    </StatusBadge>
  );
}
