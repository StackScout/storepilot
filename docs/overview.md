# StorePilot — Product Overview

> Cross-references: [`frontend-architecture.md`](../app/docs/frontend-architecture.md) ·
> [`feature-index.md`](../app/docs/feature-index.md) · [`user-flows.md`](../app/docs/user-flows.md) ·
> [`roadmap.md`](roadmap.md) · [`gaps-and-assumptions.md`](gaps-and-assumptions.md)

## Purpose of the application

StorePilot is a **multi-vendor e-commerce marketplace** connecting small,
independent Sri Lankan businesses with local buyers. It lets a seller list
and sell products online without building their own storefront, and lets
buyers discover, browse, and buy from many independent local sellers through
one consistent shopping experience.

This is currently a **frontend-only demo/prototype**: there is no real
backend, database, payment gateway, or authentication system. All data is
seeded from static mock arrays and persisted only in the browser's
`localStorage` (see [`frontend-architecture.md`](../app/docs/frontend-architecture.md)
for the mechanics). Everything documented here describes the product as
*designed by the frontend*, which is the intended source of truth for
building the real backend.

## Business problem

Small Sri Lankan sellers (home businesses, craftspeople, small food
producers, boutique fashion labels, etc.) typically sell through informal
channels — Facebook/Instagram posts, WhatsApp — with no dedicated storefront,
no structured catalog, no order/inventory tracking, and no formal online
payment option beyond ad hoc bank transfers or cash. StorePilot's premise is to
give these sellers:

- A branded storefront page (`/stores/[slug]`) without needing their own
  website.
- A structured product catalog with categories, stock tracking, and pricing.
- A lightweight seller dashboard for orders, inventory, and payouts.
- A choice of payment methods, each independently toggleable per store:
  online payment via PayHere (sandbox-integrated), Cash on Delivery, and
  direct bank transfer with buyer-uploaded receipt + manual seller
  verification — since both COD and ad hoc bank transfers remain the norm
  for small sellers in this market, and PayHere's 3.5% fee isn't always
  worth it to them.
- A direct communication channel to buyers via WhatsApp (every store has a
  `whatsappNumber`; product/order pages surface a "Message on WhatsApp" /
  "Message seller" action).

For buyers, the problem being solved is discovery and trust: one place to
search across many small sellers by category, see ratings, and check out
with a single consistent flow, rather than messaging each seller
individually to place an order.

## Target users

| User | Description |
|---|---|
| **Buyer / shopper** | Anyone browsing the public marketplace. No account or login is required — checkout is guest-only, identified solely by name/phone/address entered at checkout. |
| **Seller / merchant** | A small business owner who lists products through the seller dashboard. In the current build there is exactly **one** mock seller account (see [User roles](#user-roles)) — the product is designed for many sellers, but the demo only wires up one. |
| **Platform operator** | StorePilot itself, which earns the platform fee on every sale and reviews new sellers before they go live. A minimal, **unauthenticated** internal tool exists at `/admin` (store approval, payout runs) — see [`features/seller-auth.md`](../app/docs/features/seller-auth.md#admin-not-a-real-role) — but there is no real admin login/role. |

## User roles

The frontend currently recognizes two authenticated roles (`SessionPayload`
is a `{ role: "seller"; ... } | { role: "buyer"; ... }` union, see
`src/lib/session.ts`), plus an unprotected internal tool:

- **`seller`** — grants access to `/dashboard/*`. Onboarding
  (`/onboarding`) now creates a **real, distinct** `Store` + `StoreSettings`
  row per submission and signs the new session into *that* store. `/login`,
  however, still has no credential check or email→store lookup — *any*
  email there signs in as the same hardcoded demo store
  (`CURRENT_SELLER_STORE_ID = "store-01"`, Ceylon Spice Co.). See
  [`features/seller-auth.md`](../app/docs/features/seller-auth.md).
- **`buyer`** — optional, grants access to `/account/*`. Unlike `/login`
  above, `/account/login` does a **real** email lookup against a
  persisted `Buyer` record — the gap is a missing password/OTP, not a
  fake lookup. Guest checkout is unaffected and remains the default; an
  account only adds a saved address and cross-visit order history. See
  [`features/buyer-accounts.md`](../app/docs/features/buyer-accounts.md).
- **Platform admin (`/admin`) is not a real role** — the route has no
  session check at all (unlike `/dashboard`, it isn't in `proxy.ts`'s
  matcher). It exists to demonstrate the seller-verification and
  payout-release workflow, not as production access control. See
  [`features/seller-auth.md`](../app/docs/features/seller-auth.md#admin-not-a-real-role).

## High-level workflow

```
Seller side:                          Buyer side:
  Sign in / onboard  ─────┐             Browse home / search / category
        │                 │                       │
        ▼                 │                       ▼
  Manage products          │             View store → view product
        │                 │                       │
        ▼                 │                       ▼
  Orders come in ◄─────────┴──────────  Add to cart → checkout (guest)
        │                                          │
        ▼                                          ▼
  Update order status                    Track order / view confirmation
        │                                          │
        ▼                                          ▼
  View payouts                            Message seller via WhatsApp
```

Both sides meet at the **Order**: a buyer's checkout creates an `Order`
scoped to one store; that same order then appears in the seller's dashboard
for fulfillment. See [`user-flows.md`](../app/docs/user-flows.md) for step-by-step
journeys and [`features/order-management.md`](../app/docs/features/order-management.md) /
[`features/checkout.md`](../app/docs/features/checkout.md) for the mechanics.

## Core features

| Feature | Summary |
|---|---|
| Marketplace browsing & search | Home page, category filters, keyword search, sort — see [`features/marketplace-browsing.md`](../app/docs/features/marketplace-browsing.md) |
| Store & product detail | Public storefront and product pages — see [`features/store-and-product-detail.md`](../app/docs/features/store-and-product-detail.md) |
| Cart | Single-seller-per-order cart, persisted client-side — see [`features/cart.md`](../app/docs/features/cart.md) |
| Checkout | Guest checkout, COD or PayHere, shipping form — see [`features/checkout.md`](../app/docs/features/checkout.md) |
| Order tracking | Post-checkout confirmation + order-number/phone lookup — see [`features/order-tracking.md`](../app/docs/features/order-tracking.md) |
| Seller auth & onboarding | Mock sign-in; onboarding now really creates a `Store` in `pending` verification status, collecting NIC/business-registration/bank details for review — see [`features/seller-auth.md`](../app/docs/features/seller-auth.md) |
| Seller dashboard overview | Revenue/order/stock stat cards, pending-verification banner — see [`features/seller-dashboard-overview.md`](../app/docs/features/seller-dashboard-overview.md) |
| Product management | Seller product CRUD — see [`features/product-management.md`](../app/docs/features/product-management.md) |
| Order management | Seller order list, detail, status workflow — see [`features/order-management.md`](../app/docs/features/order-management.md) |
| Payouts | Real settlement ledger (`Payout` entity): available / scheduled / paid, read-only for the seller — see [`features/payouts.md`](../app/docs/features/payouts.md) |
| Store settings | Contact/bank/payment-method configuration — see [`features/store-settings.md`](../app/docs/features/store-settings.md) |
| Platform admin (mock) | Unauthenticated `/admin` tool: approve/reject pending stores, create payout batches, mark payouts paid — see [`features/seller-auth.md`](../app/docs/features/seller-auth.md#admin-not-a-real-role) |

Full index with related pages/components: [`feature-index.md`](../app/docs/feature-index.md).

## Monetization model

StorePilot takes a **percentage-based platform (transaction) fee** on each
sale, deducted from the seller's payout — **not** charged as an additional
line item to the buyer.

- Rate: `PLATFORM_FEE_PERCENT = 3.5` (%), defined in `src/lib/constants.ts`,
  displayed to sellers throughout onboarding, the dashboard overview, order
  detail, and payouts pages as "3.5%".
- Buyer-facing total: `subtotalLkr + shippingFeeLkr` (flat `350` LKR
  shipping, `FLAT_SHIPPING_FEE_LKR`). The platform fee is **not** added to
  what the buyer pays.
- Seller-facing payout: `subtotalLkr − platformFeeLkr`. Shipping fee is
  described as "buyer paid" and is not shown as part of the seller's
  earnings or deduction in the dashboard order detail view — its ultimate
  disposition (does the seller keep it, does a courier get paid from it?) is
  **not modeled** — see [`gaps-and-assumptions.md`](gaps-and-assumptions.md).
- No listing fees, subscription fees, or ad placements exist in the frontend
  — onboarding explicitly states "free to join, we only charge a small fee
  per sale — no monthly costs."
- `StoreSettings.transactionFeePercent` **is now the source of truth**:
  `createOrder` reads the order's store's settings and uses
  `transactionFeePercent`, falling back to the global `PLATFORM_FEE_PERCENT`
  constant only if the store has no settings row yet. (Previously the order
  service ignored the per-store field entirely — see
  [`features/payouts.md`](../app/docs/features/payouts.md) for what's still unresolved:
  the dashboard overview and payouts page use different "which orders count
  as earnings" filters.)
