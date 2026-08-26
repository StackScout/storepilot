/** Money is always integer cents on the wire (see backend's Order.total etc. doc comments) — never hand-format. */
export function formatMoney(cents: number): string {
  return (cents / 100).toLocaleString(undefined, { minimumFractionDigits: 2, maximumFractionDigits: 2 });
}

export function formatDate(iso: string): string {
  return new Date(iso).toLocaleDateString(undefined, { year: 'numeric', month: 'short', day: 'numeric' });
}

export function formatDateTime(iso: string): string {
  return new Date(iso).toLocaleString(undefined, {
    year: 'numeric',
    month: 'short',
    day: 'numeric',
    hour: '2-digit',
    minute: '2-digit',
  });
}

const ORDER_STATUS_LABELS: Record<string, string> = {
  pending: 'Pending',
  confirmed: 'Confirmed',
  shipped: 'Shipped',
  delivered: 'Delivered',
  cancelled: 'Cancelled',
};

export function orderStatusLabel(status: string): string {
  return ORDER_STATUS_LABELS[status] ?? status;
}

const ORDER_STATUS_COLORS: Record<string, string> = {
  pending: '#B98900',
  confirmed: '#208AEF',
  shipped: '#7A3FE0',
  delivered: '#1E9E5A',
  cancelled: '#D64545',
};

export function orderStatusColor(status: string): string {
  return ORDER_STATUS_COLORS[status] ?? '#60646C';
}

const BOOKING_STATUS_LABELS: Record<string, string> = {
  pending: 'Pending',
  confirmed: 'Confirmed',
  completed: 'Completed',
  cancelled: 'Cancelled',
  'no-show': 'No-show',
};

export function bookingStatusLabel(status: string): string {
  return BOOKING_STATUS_LABELS[status] ?? status;
}

const BOOKING_STATUS_COLORS: Record<string, string> = {
  pending: '#B98900',
  confirmed: '#208AEF',
  completed: '#1E9E5A',
  cancelled: '#D64545',
  'no-show': '#7A3FE0',
};

export function bookingStatusColor(status: string): string {
  return BOOKING_STATUS_COLORS[status] ?? '#60646C';
}

const RETURN_STATUS_LABELS: Record<string, string> = {
  requested: 'Requested',
  approved: 'Approved',
  rejected: 'Rejected',
  'refund-pending': 'Refund pending',
  refunded: 'Refunded',
};

export function returnStatusLabel(status: string): string {
  return RETURN_STATUS_LABELS[status] ?? status;
}

const RETURN_STATUS_COLORS: Record<string, string> = {
  requested: '#B98900',
  approved: '#208AEF',
  rejected: '#D64545',
  'refund-pending': '#7A3FE0',
  refunded: '#1E9E5A',
};

export function returnStatusColor(status: string): string {
  return RETURN_STATUS_COLORS[status] ?? '#60646C';
}

const RETURN_REASON_LABELS: Record<string, string> = {
  defective: 'Defective',
  'wrong-item': 'Wrong item',
  'not-as-described': 'Not as described',
  'changed-mind': 'Changed mind',
  other: 'Other',
};

export function returnReasonLabel(reason: string): string {
  return RETURN_REASON_LABELS[reason] ?? reason;
}

export function formatDuration(minutes: number): string {
  if (minutes < 60) return `${minutes} min`;
  const hours = Math.floor(minutes / 60);
  const rest = minutes % 60;
  return rest === 0 ? `${hours}h` : `${hours}h ${rest}m`;
}
