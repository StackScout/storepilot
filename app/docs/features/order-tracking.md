# Feature: Order Tracking (Buyer-Facing)

> Index: [`feature-index.md`](../feature-index.md) · Architecture:
> [`frontend-architecture.md`](../frontend-architecture.md) · API:
> [`api-contracts.md`](../../../docs/api-contracts.md)

## Purpose

Let a buyer view an order's status right after checkout, and let a
*returning* buyer (no account) look up an existing order later using its
order number and phone number.

## Business rules

- Order confirmation page (`/orders/[orderId]`) is reachable by **anyone
  who has the order's internal `id`** — there is no authentication or
  ownership check. This is intentional for the immediate post-checkout
  redirect (the buyer just created the order and has no account to prove
  identity with), but it also means the URL alone is the only protection —
  see [Permissions](#permissions) and
  [`gaps-and-assumptions.md`](../../../docs/gaps-and-assumptions.md).
- The lookup form (`/track-order`) is the *only* re-entry point for a
  **guest** buyer without the direct order URL. It matches on **exact
  order number** (case-insensitive) **and** the last 9 digits of the phone
  number — see [Validation](#validation) for the exact matching rule. This
  is a weak "credential" (9 digits of a phone number is not secret),
  documented as a known limitation, not a bug to silently fix without a
  product decision. A buyer who was **signed in** at checkout has a second,
  stronger re-entry point: `/account`'s order history (see
  [`features/buyer-accounts.md`](buyer-accounts.md)), scoped by `buyerId`
  rather than a guessable phone suffix.
- The order confirmation page shows the full timeline
  (`OrderTimelineEntry[]`), current status, items, totals, delivery
  address, payment method, the email the receipt was sent to
  (`order.buyerEmail`, see [`features/checkout.md`](checkout.md)), and a
  "Message seller" WhatsApp link built from the store's `whatsappNumber`.
- **Bank-transfer orders get a distinct header state while unpaid**: the
  page does not show the normal green "Order placed!" success banner for
  `paymentMethod === "bank-transfer" && paymentStatus === "unpaid" &&
  status !== "cancelled"` — that would misleadingly read as "done" when
  the buyer still needs to pay. Instead it shows an amber "Payment
  pending" state and highlights the bank-transfer card with an "Action
  required" badge. A `CancelOrderButton` (confirm-dialog) appears
  alongside the upload form while no receipt has been uploaded yet — see
  [`features/checkout.md`](checkout.md#business-rules) for the cancel
  endpoint's rules. Both the pending styling and the cancel option go away
  once a receipt is uploaded or the order is cancelled.

## User stories

- As a buyer, right after placing an order, I want to see a confirmation
  with my order number and status.
- As a buyer, I want to check on an order later without needing an account,
  using my order number and phone.
- As a buyer, I want a way to contact the seller directly if I have a
  question about my order.

## Pages

| Path | Component | Type | Notes |
|---|---|---|---|
| `/orders/[orderId]` | `src/app/(marketplace)/orders/[orderId]/page.tsx` | Client | Fetches via `useEffect` (not `useQuery`) — see [Technical notes](#technical-notes) |
| `/track-order` | `src/app/(marketplace)/track-order/page.tsx` | Client | Local `useState` form, no react-hook-form |

## Components

`EmptyState` (order-not-found / no-results states), `OrderStatusBadge`,
`PriceDisplay` (shared components — see
[`ui-components.md`](../ui-components.md)), `CancelOrderButton`
(`src/components/marketplace/cancel-order-button.tsx` — bank-transfer only,
see Business rules above).

## Hooks

Neither page uses a custom hook or TanStack Query — both use plain
`useState`/`useEffect` (order detail) or `useState` + a manual async handler
(track-order). This is an inconsistency with the rest of the app (which
uses `useQuery` for equivalent client-side data fetching elsewhere, e.g. the
dashboard's identical `getOrderById` call in
`dashboard/orders/[orderId]/page.tsx` **does** use `useQuery`) — see
[`gaps-and-assumptions.md`](../../../docs/gaps-and-assumptions.md).

## Context providers

Root `QueryClientProvider` is present but unused by this feature (see
above).

## State management

- `/orders/[orderId]`: `useState<Order | null | undefined>` (three-state:
  `undefined` = loading, `null` = not found, `Order` = loaded) +
  `useState<Store | null>` for the store (needed for the WhatsApp link). A
  `cancelled` flag inside the effect guards against setting state after
  unmount/param change.
- `/track-order`: `orderNumber`, `phone`, `isLoading`, `error` — all plain
  `useState`.

## Forms

`/track-order` is a manually-wired `<form onSubmit>` with controlled
inputs — **not** react-hook-form/zod, unlike every other form in the app.
No client-side format validation beyond HTML `required`.

## Validation

- `/track-order` lookup logic (`findOrderByNumberAndPhone`, in
  `orders.service.ts`): order number compared case-insensitively after
  `trim()`; phone compared by stripping whitespace from both the input and
  the stored `shipping.phone`, then checking that the stored phone **ends
  with** the last 9 digits of the input. This tolerates different
  country-code prefixes (`+94771234567` vs `0771234567`) but is a fairly
  loose match.
- No format validation (e.g. order number pattern, phone digit count) is
  enforced before calling the lookup — an obviously-malformed input just
  returns "not found."

## Navigation flow

```
/checkout (success) ──► /orders/[order.id]  (direct, no auth)
/track-order ──(found)──► router.push(/orders/[order.id])
/track-order ──(not found)──► inline error message, stays on page
/orders/[orderId] ──(Message seller)──► external wa.me link (new tab)
/orders/[orderId] ──(Continue shopping)──► /search
```

## Expected backend APIs

- `GET /api/orders/:id`
- `GET /api/orders/lookup?orderNumber=&phone=`
- `GET /api/stores/:id` (fetched after the order loads, to build the
  WhatsApp link — could be avoided if the order response embedded the
  store's `whatsappNumber` directly; see
  [Future improvements](#future-improvements))

See [`api-contracts.md`](../../../docs/api-contracts.md) for full shapes.

### Request models

```ts
// GET /api/orders/:id — path param only
// GET /api/orders/lookup
{ orderNumber: string; phone: string }
```

### Response models

```ts
Order | null   // null/404 → "Order not found" empty state
```

## Error handling

- Order detail page: any lookup failure (including a genuine network/server
  error) is indistinguishable from "not found" today — both result in
  `order === null` and the same "Order not found" empty state, whose copy
  ("this order may have been created in a different browser session")
  actively assumes the mock-localStorage failure mode rather than a generic
  backend error. **Must be revisited** once a real backend/database exists,
  since that copy would be actively misleading.
- Track-order: generic inline error text for any non-match; no distinction
  between "no such order," "phone doesn't match," or a request failure.

## Permissions

Public, unauthenticated, on both pages. See
[`gaps-and-assumptions.md`](../../../docs/gaps-and-assumptions.md) for the
authorization implications of `GET /api/orders/:id` being fully open (order
contains buyer PII: name, phone, full address).

## Edge cases

- Order detail page: `orderId` changing (e.g. via back/forward navigation
  between two order URLs) correctly re-triggers the fetch and resets state,
  guarded by the effect's cleanup (`cancelled = true`).
- Track-order: leading/trailing whitespace in either field is trimmed
  before comparison; embedded whitespace in the phone number is stripped
  entirely (e.g. `"077 123 4567"` and `"0771234567"` are treated the same).
- If an order's `store` has since been deleted (not currently possible —
  there's no store-delete feature), `getStoreById` would return `null` and
  the WhatsApp button would render with `href="#"` — a dead link, not
  hidden.

## Future improvements

- Require a proper identity check (e.g. one-time code sent to the phone) if
  order lookup security needs to be strengthened beyond "know the last 9
  phone digits."
- Embed the store's `whatsappNumber` (and any other display-only store
  fields the order page needs) directly in the `Order` API response to
  avoid a second round-trip.
- Convert both pages to `useQuery` for consistency with the rest of the
  codebase and to get built-in loading/error/retry semantics for free.
- Push-based status updates (buyer notified when status changes) instead of
  requiring a page revisit — partially addressed for bank-transfer orders
  by the receipt-reminder emails and payment-confirmed/rejected emails, see
  [`features/notification-emails.md`](notification-emails.md); other
  status changes (shipped, delivered) still require a page revisit.

## Technical notes

- The order detail page's `useEffect`-based fetch (vs. `useQuery` used
  everywhere else equivalent) appears to be an inconsistency rather than a
  deliberate choice — worth normalizing if this page is touched for other
  reasons.

## Dependencies

`react` (`use`, `useEffect`, `useState`), `next/navigation` (`useRouter` on
track-order), `@/services` (`ordersService`, `storesService`).

## TODOs discovered

- No explicit `// TODO` comments in either file. The "different browser
  session" empty-state copy is a strong signal the mock-data limitation
  was known and intentionally surfaced to the user, but there's no comment
  flagging it needs to change for a real backend — noted here.
