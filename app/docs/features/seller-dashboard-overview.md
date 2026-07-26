# Feature: Seller Dashboard Overview

> Index: [`feature-index.md`](../feature-index.md) · Architecture:
> [`frontend-architecture.md`](../frontend-architecture.md) · API:
> [`api-contracts.md`](../../../docs/api-contracts.md)

## Purpose

The seller's dashboard landing page: at-a-glance revenue/orders/stock stat
cards, a low-stock warning, and a recent-orders table.

## Business rules

- **Revenue** = sum of `subtotalLkr` across all non-cancelled orders for the
  store (shipping and platform fee excluded).
- **Platform fees** stat = sum of `platformFeeLkr` across all non-cancelled
  orders (informational; not the seller's payout — see
  [`features/payouts.md`](payouts.md) for the actual payout figure, which
  additionally filters to `paymentStatus === "paid"`). **Note the
  inconsistency**: this card sums fees over *all non-cancelled* orders
  (including unpaid COD orders not yet delivered), while the Payouts page
  only sums over *paid* orders — the two pages will show different
  totals for the same store, and neither page cross-links to explain why.
  See [`gaps-and-assumptions.md`](../../../docs/gaps-and-assumptions.md).
- **Pending orders** = count of orders with `status === "pending"`.
- **Active products** = `products.length` from `listProductsByStore` — this
  count includes **all** statuses (active, draft, out-of-stock), not just
  `"active"` ones, despite the stat card label "Active products". Likely
  mislabeled — see [`gaps-and-assumptions.md`](../../../docs/gaps-and-assumptions.md).
- **Low stock alert** — shown when any product has `status !==
  "out-of-stock"` and `stockQuantity <= 5`; lists affected product names in
  one banner (no per-product action, just a link to the products page).

## User stories

- As a seller, I want a quick summary of my store's performance when I open
  the dashboard.
- As a seller, I want to be warned if products are running low on stock.
- As a seller, I want to see my most recent orders without navigating away.

## Pages

| Path | Component | Type | Notes |
|---|---|---|---|
| `/dashboard` | `src/app/dashboard/page.tsx` | Client | Two parallel `useQuery` calls (orders, products), all math derived client-side from the results |

## Components

`StatCard` (`components/dashboard/stat-card.tsx`), `OrderStatusBadge`,
`TableRowSkeleton`, `EmptyState` (shared — see
[`ui-components.md`](../ui-components.md)).

## Hooks

`storeId` comes from `useSellerStoreId()` (see
[`seller-auth.md`](seller-auth.md#seller-store-context-new)), not a
hardcoded constant. Two `useQuery` calls: `["orders", storeId]` →
`ordersService.listOrdersByStore`, `["products", "store", storeId]` →
`productsService.listProductsByStore`. No custom hook wraps these — query
keys/functions are written inline (see
[`frontend-architecture.md`](../frontend-architecture.md#hooks) for the
broader pattern/risk this represents).

## Context providers

Root `QueryClientProvider` only.

## State management

Purely derived from the two queries — no local `useState`. All stat-card
math (`revenue`, `platformFees`, `pendingCount`, `lowStockProducts`) is
recomputed on every render from `ordersQuery.data`/`productsQuery.data`
(not memoized — fine at current data volumes, worth a `useMemo` if the
order/product lists grow large).

## Forms

None.

## Validation

None (read-only page).

## Navigation flow

```
/dashboard (default landing after sign-in) ──(View all, orders)──► /dashboard/orders
                                            ──(Manage stock)──────► /dashboard/products
                                            ──(order row link)────► /dashboard/orders/[orderId]
```

## Expected backend APIs

- `GET /api/stores/:storeId/orders`
- `GET /api/stores/:storeId/products`

Both already required by other features (
[`features/order-management.md`](order-management.md),
[`features/product-management.md`](product-management.md)) — this page
introduces no new endpoint, just aggregates existing data client-side.
**Consider** whether a real backend should instead expose a dedicated
`GET /api/stores/:storeId/dashboard-summary` endpoint to avoid shipping full
order/product lists to the client just to sum a few fields — see
[Future improvements](#future-improvements).

### Request / response models

Reuses `Order[]` / `Product[]` — see
[`database-model.md`](../../../docs/database-model.md).

## Error handling

No error state UI — only `isLoading` is checked (renders
`TableRowSkeleton`s). A failed query would leave `orders`/`products`
defaulting to `[]` (via `?? []`), silently rendering as if the store simply
has no data. This is misleading and should be fixed once a real backend can
actually fail (network errors, 5xxs) — see
[`gaps-and-assumptions.md`](../../../docs/gaps-and-assumptions.md).

## Permissions

Requires an authenticated `seller` session (enforced by `proxy.ts` at the
`/dashboard` route level). The page now correctly scopes to the
signed-in seller's own `storeId` (via `useSellerStoreId()`), but like the
rest of the dashboard, the **service layer itself** still does not verify
`storeId` against the session — see
[`features/seller-auth.md`](seller-auth.md#permissions).

## Edge cases

- Zero orders → stat cards show `Rs. 0` / `0`, recent-orders table shows
  `EmptyState`, no crash.
- All products well-stocked → low-stock banner simply doesn't render (no
  "all good" affirmative state).

## Future improvements

- Fix the "Active products" label/logic mismatch (either filter to
  `status === "active"` or rename the label).
- Reconcile the platform-fee figure with the Payouts page (same filter
  criteria, or clearly label what each represents).
- Dedicated summary endpoint instead of full list downloads once data
  volume grows.
- Date-range filtering (currently always "all time").
- Real trend indicators — `StatCard` supports a `trend`/`trendDirection`
  prop but this page never passes them (no period-over-period comparison
  exists anywhere yet).

## Technical notes

- `products.length` conflates all three `ProductStatus` values under an
  "Active products" label — flagged above; don't assume the label is
  accurate when building a replacement API.

## Dependencies

`@tanstack/react-query`, `lucide-react`, `@/services`, `@/lib/currency`,
`@/lib/format`, `@/hooks/use-seller-store`.

## TODOs discovered

- No explicit `// TODO` comments. The "Active products" mislabel and the
  platform-fee inconsistency with the Payouts page are both inferred from
  reading the two pages side by side, not from any code comment.
