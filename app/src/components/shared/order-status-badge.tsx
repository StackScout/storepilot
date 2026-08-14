import { StatusBadge, type StatusTone } from "@/components/shared/status-badge";
import type { OrderStatus } from "@/types";
import { ORDER_STATUS_LABELS } from "@/lib/constants";

const STATUS_TONES: Record<OrderStatus, StatusTone> = {
  pending: "warning",
  confirmed: "info",
  shipped: "highlight",
  delivered: "success",
  cancelled: "danger",
};

export function OrderStatusBadge({ status, className }: { status: OrderStatus; className?: string }) {
  return (
    <StatusBadge tone={STATUS_TONES[status]} className={className}>
      {ORDER_STATUS_LABELS[status]}
    </StatusBadge>
  );
}
