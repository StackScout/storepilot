# Feature: Payouts

> Index: [`feature-index.md`](../feature-index.md) · Architecture:
> [`frontend-architecture.md`](../frontend-architecture.md) · API:
> [`api-contracts.md`](../../../docs/api-contracts.md)

## Purpose

Give a seller a read-only view of what they've earned, what the platform
has scheduled for release, and what's already been paid out — backed by a
real ledger entity (`Payout`), not just numbers derived from `Order` at read
time. Payout **creation and release are platform (admin) actions**, not
something the seller can trigger — this mirrors how the platform actually
holds buyer funds (see the payments discussion in
[`overview.md`](../../../docs/overview.md)): the seller can't self-release their own
escrowed money.

## Business rules

- **`Payout` is a real entity now** (`src/types/payout.ts`,
  `src/services/payouts.service.ts`), not derived-at-read-time. Fields:
  `id`, `storeId`, `storeName`, `orders` (snapshot of each included order's
  `orderId`/`orderNumber`/`subtotalLkr`/`platformFeeLkr`/`netLkr`),
  aggregate `subtotalLkr`/`platformFeeLkr`/`netLkr`, `status`
  (`"scheduled" | "paid"`), `createdAt`, `paidAt?`, `bankReference?`.
- **Eligibility** (`getEligibleOrdersForPayout(storeId)`): an order counts
  once it's `status: "delivered"` **and** `paymentStatus: "paid"`, and
  isn't already referenced by any existing payout (scheduled or paid) for
  that store. This is the platform's "money we're still holding" view.
- **Only `/admin` can create or release a payout**:
  `payoutsService.createPayout(storeId)` bundles *all* currently-eligible
  orders into one new `"scheduled"` payout; `markPayoutPaid(payoutId,
  bankReference?)` flips it to `"paid"` and stamps `paidAt`. Neither is
  callable from the seller-facing dashboard UI.
- The seller dashboard (`/dashboard/payouts`) shows three numbers derived
  live from the ledger + eligible-orders query: **Available** (eligible,
  not yet in any payout), **Scheduled** (sum of `netLkr` across this
  store's `"scheduled"` payouts), **Paid out** (sum across `"paid"`
  payouts) — plus the full payout history table.
- `platformFeeLkr` on each order is now computed from that order's store's
  `StoreSettings.transactionFeePercent` (falling back to the global
  `PLATFORM_FEE_PERCENT` constant if the store has no settings row) — see
  `orders.service.ts#createOrder`. This resolves the previous
  inconsistency where the displayed per-store rate was never actually used.
- **Still unresolved**: the [dashboard overview](seller-dashboard-overview.md)'s
  revenue/fee stat cards count **all** non-cancelled orders regardless of
  `paymentStatus`, while this page's "Available" figure only counts
  `delivered` + `paid` orders not yet batched. The two pages still disagree
  on "how much have I earned" for a store with unpaid COD orders in
  progress — this is a genuine, still-open product decision (pick one
  definition of "earnings", or clearly label the difference), not
  something this feature fixed.
- Bank account display (`bankName`, `bankAccountName`, `bankAccountNumber`)
  still comes from `StoreSettings`, editable on
  [Store Settings](store-settings.md).

## User stories

- As a seller, I want to see how much is available, scheduled, and already
  paid out, without being able to trigger a payout myself.
- As a seller, I want a history of every payout batch and its status.
- As a platform operator, I want to see which stores have money owed to
  them, bundle it into a payout batch, and record when I've actually sent
  the bank transfer.

## Pages

| Path | Component | Type | Notes |
|---|---|---|---|
| `/dashboard/payouts` | `src/app/dashboard/payouts/page.tsx` | Client | Read-only. Three `useQuery` calls: payouts, eligible orders, store settings |
| `/admin` (Payout runs section) | `src/app/admin/page.tsx` | Client | Create payout batches (per eligible store), mark scheduled payouts as paid |

## Components

`StatCard`, `Badge` (status pill), `TableRowSkeleton`, `EmptyState` (shared —
see [`ui-components.md`](../ui-components.md)). `/admin` additionally uses
`Dialog` (mark-as-paid bank reference prompt).

## Hooks

Seller page: `useQuery` ×3 — `["payouts", storeId]` →
`payoutsService.listPayoutsByStore`; `["payout-eligible-orders", storeId]` →
`payoutsService.getEligibleOrdersForPayout`; `["store-settings", storeId]` →
`storesService.getStoreSettings`. `storeId` comes from `useSellerStoreId()`
(see [`seller-auth.md`](seller-auth.md#seller-store-context-new)), not a
hardcoded constant.

Admin page: `useQuery` for eligible stores + all payouts, `useMutation` ×2
(`createPayout`, `markPayoutPaid`).

## Context providers

Root `QueryClientProvider` + `SellerStoreProvider` (dashboard layout).

## State management

All derived inline from the three queries on each render (no memoization;
fine at current scale, same as before).

## Forms

None on the seller page (read-only). `/admin`'s "mark as paid" dialog has a
single optional bank-reference text input; "reject application" (store
verification, not payouts) has a required reason textarea.

## Validation

None beyond the admin dialogs' basic required/optional field handling.

## Navigation flow

```
/dashboard (sidebar) ──► /dashboard/payouts   (read-only)

/admin ──► Payout runs section ──(Create payout batch)──► new "scheduled" Payout
                               ──(Mark as paid)──► Payout.status = "paid"
```

## Expected backend APIs

- `GET /api/stores/:storeId/payouts` — list a store's payout ledger.
- `GET /api/stores/:storeId/payouts/eligible-orders` — orders not yet
  batched (or compute this server-side as part of the payouts list
  response — an implementation detail).
- `POST /api/stores/:storeId/payouts` — **admin-only**, create a batch from
  currently-eligible orders.
- `PATCH /api/payouts/:id/paid` — **admin-only**, mark released.
- `GET /api/stores/:storeId/settings` (reused, see
  [`api-contracts.md`](../../../docs/api-contracts.md)).

### Request / response models

See [`database-model.md`](../../../docs/database-model.md) for the `Payout` shape —
now implemented, not just proposed.

## Error handling

- Seller page: failed queries silently fall back to empty arrays /
  `undefined` settings, same permissive pattern as before.
- Admin mutations: every one has an `onError` → `toast.error(...)`.
  `createPayout` throws (mock `Error`, not a structured error) if called
  with zero eligible orders — the admin UI prevents this by only showing
  "Create payout batch" for stores that already have eligible orders.

## Permissions

Seller page requires a seller session (dashboard-wide gate) — no ownership
check beyond that, same caveat as every other seller-scoped page (see
[`features/seller-auth.md`](seller-auth.md#permissions)). `/admin` has
**no permission check of any kind** — see
[`seller-auth.md`](seller-auth.md#admin-not-a-real-role).

## Edge cases

- No payouts yet → `EmptyState` ("No payouts yet"), stat cards show `Rs. 0`.
- A store with **no `StoreSettings` record** — no longer a real gap for
  *newly onboarded* stores, since onboarding always creates one; still true
  for old seed stores that predate this feature (7 of 8), same caveat as
  documented in [`store-settings.md`](store-settings.md).
- Seed data: `src/mock/payouts.ts` includes one historical `"paid"` payout
  for `store-01` covering `order-1004`, so the ledger doesn't look empty on
  first visit.

## Future improvements

- Reconcile the dashboard-overview vs. payouts-page "earnings" definition
  (see Business rules above) — still open.
- Payout scheduling (a recurring "run every Monday" job) instead of an
  admin manually clicking "Create payout batch".
- Real bank transfer integration instead of a free-text `bankReference`
  field.
- Partial/split payouts, or excluding specific orders from a batch (today
  it's all-eligible-or-nothing per store).
- Link each row in the payout history to the underlying orders.

## Technical notes

- This is the reference implementation of the `Payout`/`Settlement` entity
  that [`database-model.md`](../../../docs/database-model.md) previously listed as
  "missing" — see that doc for the full field-by-field writeup.
- `payoutsService` imports from both `orders.service.ts`
  (`listOrdersByStore`) and `stores.service.ts` (`getStoreById`) — the only
  service module in the app that composes two others, since a payout is
  inherently a cross-cutting view over orders + store identity.

## Dependencies

`@tanstack/react-query`, `lucide-react`, `@/services`
(`payoutsService`, `storesService`, `ordersService` transitively),
`@/lib/currency`, `@/lib/format`, `@/hooks/use-seller-store`.

## TODOs discovered

- No explicit `// TODO` comments. `payouts.service.ts` documents the
  "admin-only, never seller-triggered" design decision inline on
  `createPayout`/`markPayoutPaid`'s JSDoc.
