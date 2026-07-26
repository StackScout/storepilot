# Feature: Cart

> Index: [`feature-index.md`](../feature-index.md) · Architecture:
> [`frontend-architecture.md`](../frontend-architecture.md) · API:
> [`api-contracts.md`](../../../docs/api-contracts.md)

## Purpose

Let a buyer accumulate products before checkout. The cart is entirely
client-side (Zustand + `localStorage`) — there is no server-side cart
entity today.

## Business rules

- **A cart may only contain items from one store at a time.** `addItem`
  returns `false` (no mutation) if the cart already holds a different
  `storeId`. The caller must offer the buyer a choice to replace the cart
  (`replaceCartWithItem`) rather than merge it.
- Quantity per line item is clamped to `product.stockQuantity` at the
  moment of add/update — this is a **snapshot**, not revalidated against
  live stock later (e.g. if stock drops after the item was added, the cart
  won't automatically reduce the held quantity).
- **The cart reconciles against live product data whenever it loads**
  (`useCartReconciliation()`, `src/hooks/use-cart-reconciliation.ts`): every
  time the cart page, cart drawer, or checkout mounts (or the cart's set of
  product IDs changes), each held product is re-fetched by ID. A product
  that no longer exists (seller deleted it) is flagged
  `isUnavailable: true` on the `CartItem` rather than being silently
  dropped or shown with a stale snapshot — it stays visible, greyed out,
  with a "No longer available" badge, so the buyer can see what they lost.
  A product whose price changed has its `unitPriceLkr` (and
  `stockQuantity`) silently refreshed to the current value — no separate
  "price changed" notice, the buyer just sees the up-to-date number.
  Unavailable items are excluded from `cartItemCount`/`cartSubtotal` and
  from what checkout actually submits; checkout's "Place order" button is
  disabled while any remain (must be removed from `/cart` first).
- Removing the last item empties the cart (resets `storeId`/`storeName`/
  `storeSlug` to `null`), so a fresh add-from-another-store works
  immediately.
- Cart persists across page reloads and browser sessions via `localStorage`
  key `islandcart_cart` (no expiry).
- Cart is **not** tied to any buyer identity — clearing browser storage or
  switching devices loses it entirely; there is no server-side/cross-device
  cart.

## User stories

- As a buyer, I want to add a product to my cart and see the cart badge
  update immediately.
- As a buyer, I want to increase/decrease quantity or remove an item from
  the cart (both from the mini cart drawer and the full cart page).
- As a buyer, if I try to add a product from a different store than what's
  already in my cart, I want to be warned and choose whether to start over.
- As a buyer, I want my cart to persist if I reload the page or come back
  later.

## Pages

| Path | Component | Type | Notes |
|---|---|---|---|
| `/cart` | `src/app/(marketplace)/cart/page.tsx` | Client | Full cart view; empty-state if `isHydrated && items.length === 0` |

The cart is also viewable/editable from the **cart drawer** (`CartDrawer`),
mounted in `SiteHeader` and reachable from every marketplace page.

## Components

- `CartDrawer` (`components/marketplace/cart-drawer.tsx`) — header icon +
  badge (item count, "9+" past 9) opening a slide-out `Sheet` with line
  items, subtotal, and links to `/checkout` and `/cart`.
- `AddToCartControls` (`components/marketplace/add-to-cart-controls.tsx`) —
  product-page quantity stepper + add button + the cross-store conflict
  `Dialog` (documented fully in
  [`features/store-and-product-detail.md`](store-and-product-detail.md)).
- `QuantityStepper` (`components/marketplace/quantity-stepper.tsx`) —
  generic +/- stepper, `min`/`max` bounded, used by cart drawer, cart page,
  and add-to-cart controls.

## Hooks

- **`useCart()`** (`src/hooks/use-cart.ts`) — the sole entry point for cart
  state/mutations from components. Wraps `useCartStore` and adds:
  - `isHydrated` — `useSyncExternalStore`-based flag, `false` until the
    client has mounted. Before hydration, `cart` is forced to an empty
    shape so SSR output (always empty, since the server can't read
    `localStorage`) matches the client's first render.
  - `itemCount` (`cartItemCount`) and `subtotal` (`cartSubtotal`) — derived
    helpers exported from `cart-store.ts`, both now excluding
    `isUnavailable` items.
  - `syncItems` — the reconciliation write path (see below).
- **`useCartReconciliation()`** (`src/hooks/use-cart-reconciliation.ts`) —
  called (no arguments, no return value used) by `/cart`, `CartDrawer`, and
  checkout. Fetches `productsService.getProductById` for every distinct
  `productId` currently in the cart and calls `syncItems` with the results.
  Guards against refetching on every render via a `useRef`-held key (the
  sorted, joined product-ID list) — it only re-runs when that *set* of IDs
  actually changes, not on every quantity/price update `syncItems` itself
  causes.

## Context providers

None — Zustand doesn't require a Provider; the store is a module-level
singleton (`useCartStore`).

## State management

`src/store/cart-store.ts` — Zustand `create()` wrapped in
`persist(..., { name: "islandcart_cart", storage: createJSONStorage(() =>
localStorage) })`. Shape:

```ts
interface Cart { storeId: string | null; storeName: string | null; storeSlug: string | null; items: CartItem[] }
interface CartItem { productId; productName; productSlug; productImageUrl; unitPriceLkr; quantity; stockQuantity; isUnavailable?: boolean }
```

Actions: `addItem`, `replaceCartWithItem`, `updateQuantity` (removes the
item if the new quantity is `≤ 0`), `removeItem`, `clearCart`, `syncItems`
(reconciliation — see [Hooks](#hooks) above; takes
`{ productId, product: { priceLkr, stockQuantity } | null }[]`, `product:
null` meaning "no longer exists" ⇒ sets `isUnavailable: true` on that
item without removing it).

## Forms

None.

## Validation

None beyond the store-conflict check and stock-quantity clamping described
above — there is no schema validation on cart mutations (it's all local
state transitions, not submitted data).

## Navigation flow

```
Any product page ──(Add to cart)──► stays on page, toast confirms, drawer badge updates
                 ──(conflict: different store)──► Dialog: "Replace cart?" ──► replaceCartWithItem + router.refresh()
Header cart icon ──► CartDrawer (Sheet) ──(Checkout)──► /checkout
                                          ──(View cart)──► /cart
/cart ──(Proceed to checkout)──► /checkout
/cart ──(empty)──► EmptyState ──(Browse products)──► /search

/cart, CartDrawer, /checkout (on mount) ──► useCartReconciliation()
                                         ──► productsService.getProductById() per line
                                         ──► syncItems() ──► greys out deleted items,
                                             refreshes stale prices
```

## Expected backend APIs

**None required** for the cart itself if it remains a purely client-side,
device-local concept — there is no `Cart` entity in the current design (see
[`database-model.md`](../../../docs/database-model.md)). The cart's *contents* only
reach the backend at checkout time, as part of `POST /api/orders` (see
[`features/checkout.md`](checkout.md) and
[`api-contracts.md`](../../../docs/api-contracts.md)).

If buyer accounts are introduced (see [`roadmap.md`](../../../docs/roadmap.md)) and
cross-device cart persistence becomes a requirement, a `Cart`/`CartItem`
backend entity and `GET/PUT /api/cart` endpoints would need to be
introduced — not currently modeled anywhere.

### Request / response models

N/A — no cart API today.

## Error handling

None needed today (pure client state, no network calls). If a server-backed
cart is introduced, standard optimistic-update + rollback-on-error handling
would need to be added.

## Permissions

None — anyone (including with no session) can hold a cart.

## Edge cases

- Adding the same product twice increases quantity (clamped to stock) rather
  than creating a duplicate line item.
- Cart badge shows "9+" once `itemCount > 9` rather than the exact number.
- **Resolved**: a product deleted by its seller is no longer only caught at
  `createOrder` time — `useCartReconciliation()` now flags it
  `isUnavailable` as soon as the cart/checkout loads (see Business rules
  above), and checkout excludes it from what's actually submitted.
- **Still a real gap**: if `stockQuantity` for an item *drops but the
  product still exists* (e.g. another buyer bought the remaining stock),
  `syncItems` refreshes the displayed `stockQuantity` number but does
  **not** re-clamp the held `quantity` or warn the buyer that they're now
  requesting more than what's in stock — `createOrder` still doesn't
  re-validate requested quantity against current stock before
  decrementing (`decrementStock` just clamps to zero). See
  [`gaps-and-assumptions.md`](../../../docs/gaps-and-assumptions.md).
- Refreshing `/checkout` or `/cart` before hydration briefly shows an
  "empty" state even for a returning buyer with a full cart — expected and
  intentional (avoids a hydration mismatch), but worth knowing if a flicker
  is reported as a bug.

## Future improvements

- Server-validated stock check at add-to-cart and at checkout time (not
  just a stale client-side snapshot) — re-clamping `quantity` down when
  `stockQuantity` drops would close the remaining gap noted above.
- A visible "price changed" indicator, distinct from just silently showing
  the new number — revisit if buyers report confusion.
- Optional cross-device cart persistence tied to a buyer account (buyer
  accounts now exist — see [`features/buyer-accounts.md`](buyer-accounts.md)
  — but the cart itself is still 100% local; only the buyer's saved address
  and order history sync).
- Save-for-later / wishlist (related but distinct from cart).

## Technical notes

- The single-store-cart rule is enforced entirely client-side today; a real
  backend's `POST /api/orders` must **also** reject multi-store item lists
  server-side, since a modified/malicious client could bypass the Zustand
  store entirely and post arbitrary `items`.

## Dependencies

`zustand`, `zustand/middleware` (`persist`, `createJSONStorage`), `react`
(`useSyncExternalStore`, `useEffect`, `useRef`), `@/types` (`Cart`,
`CartItem`, `Product`), `@/services` (`productsService`, for
reconciliation).

## TODOs discovered

- No explicit `// TODO` comments in `cart-store.ts`, `use-cart.ts`, or
  `use-cart-reconciliation.ts`. The stock-revalidation gap above is
  inferred from the absence of a check, not a code comment.
