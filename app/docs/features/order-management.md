# Feature: Order Management (Seller)

> Index: [`feature-index.md`](../feature-index.md) · Architecture:
> [`frontend-architecture.md`](../frontend-architecture.md) · API:
> [`api-contracts.md`](../../../docs/api-contracts.md)

## Purpose

Let a seller view incoming orders, filter by status, inspect order detail
(items, customer, delivery address, payment), and advance an order through
its fulfillment lifecycle.

## Business rules

- **Order status state machine** (`NEXT_STATUS_OPTIONS` in
  `order-status-select.tsx`) — the *only* place this rule is enforced:

  ```
  pending    → pending | confirmed | cancelled
  confirmed  → confirmed | shipped | cancelled
  shipped    → shipped | delivered
  delivered  → delivered            (terminal — Select is disabled)
  cancelled  → cancelled            (terminal — Select is disabled)
  ```

  This is enforced by only rendering the allowed next options in the
  `Select` — **the service layer (`updateOrderStatus`) does not itself
  validate the transition**, it will happily set any `OrderStatus` value
  passed to it. A real backend must re-implement this state machine
  server-side; it must not rely on the frontend only offering valid
  choices.
- Changing status to `"delivered"` on a `"cod"` order also sets
  `paymentStatus: "paid"` (cash was collected on delivery).
- Changing status to `"cancelled"` when `paymentStatus === "paid"` sets
  `paymentStatus: "refunded"` — this is a **status flag change only**; no
  actual refund transaction, gateway call, or ledger entry is created.
- Every status change appends a new `OrderTimelineEntry` (never mutates or
  removes prior entries) — the timeline is meant to be an append-only
  audit log of the order's history.
- `OrderTimelineEntry.note` exists in the type/service signature
  (`updateOrderStatus(id, status, note?)`) but **no UI anywhere ever
  supplies a note** — `OrderStatusSelect` calls `mutation.mutate(value)`
  with no third argument. This capability is unused, not missing from the
  type.
- The seller's payout for an order = `subtotalLkr − platformFeeLkr`,
  displayed on the order detail page; shipping fee is labeled "(buyer
  paid)" and excluded from this calculation. What actually happens to the
  shipping fee (kept by seller? paid to a courier? kept by the platform?)
  is **not modeled** — see [`gaps-and-assumptions.md`](../../../docs/gaps-and-assumptions.md).

## User stories

- As a seller, I want to see all orders for my store, filterable by status.
- As a seller, I want to view full order detail: items, customer contact,
  delivery address, payment method/status.
- As a seller, I want to advance an order's status (confirm, ship, deliver)
  or cancel it, following a sensible allowed-transitions flow.
- As a seller, I want to see my net payout for each order.

## Pages

| Path | Component | Type | Notes |
|---|---|---|---|
| `/dashboard/orders` | `src/app/dashboard/orders/page.tsx` | Client | Status filter chips (`all`/pending/confirmed/shipped/delivered/cancelled), client-side filtering of the fetched list |
| `/dashboard/orders/[orderId]` | `src/app/dashboard/orders/[orderId]/page.tsx` | Client | Full detail + `OrderStatusSelect` |

## Components

`OrderStatusSelect` (`components/dashboard/order-status-select.tsx`),
`OrderStatusBadge`, `TableRowSkeleton`, `EmptyState`, `PriceDisplay` (shared
— see [`ui-components.md`](../ui-components.md)).

## Hooks

`useQuery` (list: `["orders", storeId]`, `storeId` from
`useSellerStoreId()`; detail: `["order", orderId]`), `useMutation`
(`OrderStatusSelect`'s status update), `useQueryClient` (invalidates
`["orders"]` and `["order", order.id]` on success).

## Context providers

Root `QueryClientProvider` only.

## State management

- List page: `filter: OrderStatus | "all"` (`useState`), filtering happens
  **client-side** over the already-fetched full order list — the `status`
  param the service function accepts (`listOrdersByStore(storeId,
  status?)`) is never actually passed by this page; it always fetches all
  orders and filters in the browser instead.
- Detail page: no local state beyond the query/mutation.

## Forms

None — status change is a `Select`, not a submitted form.

## Validation

None client-side beyond the state-machine option list described above.

## Navigation flow

```
/dashboard/orders ──(filter chip)──► same page, list re-filters client-side
/dashboard/orders ──(order number link)──► /dashboard/orders/[orderId]
/dashboard/orders/[orderId] ──(Back to orders)──► /dashboard/orders
/dashboard/orders/[orderId] ──(status change)──► same page, toast confirms, badge/select update
```

## Expected backend APIs

- `GET /api/stores/:storeId/orders?status=`
- `GET /api/orders/:id`
- `PATCH /api/orders/:id/status`

See [`api-contracts.md`](../../../docs/api-contracts.md) for full shapes.

### Request models

```ts
// GET /api/stores/:storeId/orders
{ status?: OrderStatus }
// PATCH /api/orders/:id/status
{ status: OrderStatus; note?: string }
```

### Response models

```ts
Order[] // list
Order   // detail / after status update
```

## Error handling

Status-update mutation has a generic `onError` toast ("Couldn't update
order status. Try again."). No handling exists for "you tried an invalid
transition" as a distinct error, because the frontend UI structurally
prevents offering invalid transitions — but a real backend **must** still
validate and reject invalid transitions defensively (a direct API call
could bypass the UI), and the frontend has no code path today to display
that rejection meaningfully beyond the generic toast.

## Permissions

Requires seller session (dashboard-wide gate). **No ownership check** on
`getOrderById`/`updateOrderStatus` — any signed-in seller session (today,
there's only one) can view/mutate any order by ID, since the service layer
never compares the order's `storeId` to the session's `storeId`. This is a
must-fix for a real multi-tenant backend — see
[`features/seller-auth.md`](seller-auth.md#permissions) and
[`api-contracts.md`](../../../docs/api-contracts.md#authorization).

## Edge cases

- List page fetches the **entire** order history for the store on every
  visit and filters client-side — fine at demo data volumes, will not scale
  once a store has thousands of orders; a real backend integration should
  pass `status` (and pagination) as real query parameters instead of
  filtering client-side.
- `delivered` and `cancelled` orders render a disabled `Select` (single
  option, matching current status) rather than being hidden/read-only text
  — visually still looks like a dropdown even though it can't be changed.
- Cancelling a `pending` (never-paid, since only `payhere` orders are
  `paid` at creation) COD order does **not** change `paymentStatus` (stays
  `"unpaid"`) — the `paid → refunded` rule only fires if the order was
  already marked paid.

## Future improvements

- Move status filtering and pagination server-side.
- Support the `note` field from the UI (e.g. "shipped via XYZ courier,
  tracking #...") since the data model already supports it.
- Enforce the status state machine server-side (defense in depth).
- Model shipping-fee disposition explicitly (seller keeps it / platform
  keeps it / paid out to a courier partner).
- Real refund processing tied to `cancelled` + `paid → refunded`.

## Technical notes

- The status state machine lives *only* in
  `components/dashboard/order-status-select.tsx` as a plain object literal
  — if a backend team needs the canonical transition rules, this is the
  single source to port, not `orders.service.ts` (which has no transition
  awareness at all).

## Dependencies

`@tanstack/react-query`, `sonner`, `@/services` (`ordersService`),
`@/lib/constants` (`ORDER_STATUS_LABELS`), `@/hooks/use-seller-store`.

## TODOs discovered

- No explicit `// TODO` comments. The unused `note` parameter and the
  client-side-only status filtering are both inferred from reading the
  code, not code comments.
