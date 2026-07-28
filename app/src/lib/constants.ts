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

/**
 * sessionStorage key recording the order a buyer was just redirected to a
 * payment gateway (PayHere/Stripe) for. Set right before the redirect,
 * consumed on `/orders/[orderId]` to clear the cart only once that specific
 * order comes back paid — not before, so a declined/cancelled gateway
 * payment doesn't leave the buyer with an empty cart and a stuck unpaid
 * order (see checkout-form.tsx and orders/[orderId]/page.tsx).
 */
export const PENDING_GATEWAY_ORDER_KEY = "islandcart_pending_gateway_order";
