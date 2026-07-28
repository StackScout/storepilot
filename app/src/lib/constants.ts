/**
 * Country-specific platform content (name, tagline, currency, fees, state
 * options, ...) is no longer baked into this file or NEXT_PUBLIC_* build
 * args — it's read from the backend's platform_settings/states tables at
 * runtime via GET /api/platform-config and GET /api/states (see
 * lib/platform-config.ts and hooks/use-platform-config.ts). That's what
 * lets a second country's deployment (e.g. Australia alongside a future
 * Sri Lanka one) be configured by editing DB rows, not rebuilding this
 * image with different env vars.
 */
export const ORDER_STATUS_LABELS: Record<string, string> = {
  pending: "Pending",
  confirmed: "Confirmed",
  shipped: "Shipped",
  delivered: "Delivered",
  cancelled: "Cancelled",
};
