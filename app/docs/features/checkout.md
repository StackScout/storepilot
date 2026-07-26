# Feature: Checkout

> Index: [`feature-index.md`](../feature-index.md) · Architecture:
> [`frontend-architecture.md`](../frontend-architecture.md) · API:
> [`api-contracts.md`](../../../docs/api-contracts.md)

## Purpose

Convert a cart into an `Order`: collect shipping details and a payment
method from an anonymous buyer, submit the order, decrement stock, and hand
off to the order confirmation page.

## Business rules

- **Guest checkout by default, buyer accounts optional.** Nobody is
  required to sign in — checkout works exactly as before for a guest. If
  the buyer *is* signed in (see
  [`features/buyer-accounts.md`](buyer-accounts.md)), the form prefills
  from `Buyer.defaultShipping` (once it loads) and the resulting `Order`
  is tagged with `buyerId`.
- **Email is now collected on every checkout**, guest or signed-in — it's
  where the order receipt is sent (see below), not just a delivery detail.
- Order total shown to the buyer = `subtotal + flat shipping fee (350 LKR)`.
  The platform fee is **not** added to the buyer's total — it's deducted
  from the seller's payout only (see [`overview.md`](../../../docs/overview.md#monetization-model)).
- Payment method is `"cod"` (default selection), `"payhere"`, or
  `"bank-transfer"` — only the methods enabled in the store's
  `StoreSettings` are shown (see
  [`store-settings.md`](store-settings.md#business-rules)); if the current
  selection becomes unavailable it's corrected to whichever method the
  store does offer.
  - `cod` → order created with `paymentStatus: "unpaid"`, flips to `"paid"`
    on delivery.
  - `payhere` → order created with `paymentStatus: "unpaid"`; the checkout
    form immediately submits a hidden HTML form to PayHere's Checkout API
    (`submitPayHereCheckout`, `src/lib/payhere.ts`), navigating the buyer's
    browser away to PayHere's own gateway page — **not** the payhere.js
    onsite popup SDK, which was tried first but dropped: its `startPayment`
    readiness (an async domain-validation step) proved unreliable even with
    an approved merchant domain. PayHere redirects the buyer back to
    `returnUrl`/`cancelUrl` (both `/orders/{id}`) once they finish or
    cancel; the order flips to `"paid"` asynchronously via PayHere's
    server-to-server notify webhook, independent of that redirect — see
    [`payhere-checkout` docs](../../../docs/api-contracts.md#post-apiordersidpayhere-checkout).
  - `bank-transfer` → order created with `paymentStatus: "unpaid"`; the
    checkout page shows the seller's bank details inline, and the buyer
    uploads a receipt from the order confirmation page
    (`POST /api/orders/:id/receipt`). The order stays `"unpaid"` until the
    seller manually confirms or rejects it from their dashboard
    (`POST /api/orders/:id/verify-bank-transfer`) — never auto-confirmed.
    While a receipt is missing, `orders/[orderId]/page.tsx` deliberately
    does **not** show the normal green "Order placed!" success banner —
    that would misleadingly imply the order is done. Instead it shows an
    amber "Payment pending" state (`Clock` icon) and highlights the bank
    transfer card itself ("Action required" badge) so the buyer doesn't
    mistake an unpaid order for a completed one. The buyer can also cancel
    from this state (`POST /api/orders/:id/cancel` — see below); both the
    pending styling and the cancel option disappear once a receipt is
    uploaded or the order is cancelled.
  - **Buyer-initiated cancel** (`POST /api/orders/:id/cancel`,
    `OrderService.cancelBankTransferOrder`): unauthenticated, same
    "order ID is proof enough" model as the other order endpoints.
    Deliberately narrow — only a `bank-transfer` order that's still
    `pending`/`unpaid` with **no receipt uploaded yet** can be
    self-cancelled this way (guarded server-side, not just hidden in the
    UI); once a receipt exists the seller is expected to act on it via
    verify/reject, not have it pulled out from under them. COD/PayHere
    orders have no buyer-initiated cancel path.
- **Order-lifecycle emails go through a backend notification abstraction**
  (`backend/.../notification/`, see
  [`notification-emails.md`](notification-emails.md) for the full design) —
  `EmailService` is a transport-only interface with one mock
  (`LoggingEmailService`, logs instead of sending), and `OrderNotifier`
  owns the copy for each event: order confirmed (buyer), receipt uploaded
  (seller), bank-transfer verified/rejected (buyer), and a periodic
  reminder for bank-transfer orders still missing a receipt (buyer — see
  `ReceiptReminderJob`). Swapping in a real provider (AWS SES etc.) later
  only means adding a second `EmailService` bean; nothing in `OrderService`
  or the reminder job changes. The order confirmation page shows "A
  receipt has been sent to `<email>`" regardless of which `EmailService` is
  active.
- **If signed in, the shipping address just used is saved back to
  `Buyer.defaultShipping`** after order creation (best-effort — a failure
  here is swallowed, it must never block order placement).
- **The cart must be free of unavailable items to submit.** See
  [`features/cart.md`](cart.md) — `useCartReconciliation()` runs on this
  page too, and the "Place order" button is disabled while any cart item
  is flagged `isUnavailable`.
- On success: stock is decremented for each purchased item
  (`decrementStock`), the cart is cleared, and the buyer is redirected to
  `/orders/[order.id]`.
- If the cart is empty when this page loads (or becomes empty, e.g. via a
  second browser tab), the buyer is redirected to `/cart` — **except** in
  the instant right after a successful order placement, where the cart is
  *intentionally* cleared but the redirect must not fire (guarded by
  `orderPlacedRef`, a `useRef` boolean set synchronously in `onSuccess`
  before the effect re-runs).

## User stories

- As a buyer, I want to enter my delivery details and choose a payment
  method to place my order.
- As a buyer, I want to see an order summary (items, subtotal, shipping,
  total) before I confirm.
- As a buyer, I want to be redirected somewhere sensible if I reach checkout
  with an empty cart.

## Pages

| Path | Component | Type | Notes |
|---|---|---|---|
| `/checkout` | `src/app/(marketplace)/checkout/page.tsx` + `checkout-form.tsx` | Server shell + Client form | `page.tsx` is now a thin **Server Component** that reads the session and passes a `buyerSession` prop to the client `CheckoutForm`; all the form/mutation logic that used to live directly in `page.tsx` moved to `checkout-form.tsx` unchanged otherwise |

## Components

No dedicated sub-components beyond the `page.tsx`/`checkout-form.tsx` split
above — the form, order summary, and payment method radio group are all
inline in `checkout-form.tsx`. Uses shared `PriceDisplay` and shadcn
primitives (`RadioGroup`, `Select`, `Input`, `Label`).

## Hooks

- `useCart()` — reads `cart`, `subtotal`, `isHydrated`; calls `clearCart()`
  on success.
- `useCartReconciliation()` — see [`features/cart.md`](cart.md); keeps the
  order summary and submit-eligibility in sync with live product data.
- `useQuery(["buyer", buyerId], buyersService.getBuyerById)` — only enabled
  when `buyerSession` is present; feeds the prefill `useEffect` below.
- `useMutation` (TanStack Query) — wraps `ordersService.createOrder`, plus
  a best-effort `buyersService.updateDefaultShipping` call on success when
  signed in.
- `useForm` (react-hook-form) with `zodResolver`.
- `useRouter` (Next.js) — `router.push` on success, `router.replace` for the
  empty-cart redirect.

## Context providers

Relies on the root `QueryClientProvider` (for `useMutation`) — no
feature-specific provider.

## State management

- Cart: Zustand, via `useCart()` (read-only here except for `clearCart()`).
- Form: react-hook-form's internal state (`register`, `watch`, `setValue`,
  `formState.errors`).
- Mutation lifecycle: TanStack Query (`mutation.isPending` disables the
  submit button and swaps in a spinner).
- `orderPlacedRef` (`useRef<boolean>`) — guards the empty-cart redirect
  effect from firing after a deliberate post-order `clearCart()`. This is a
  `useRef`, not `useState`, specifically so setting it doesn't trigger a
  re-render/re-run of the effect on its own — the comment in the source
  calls out that it must be set *synchronously, before* `clearCart()`, to
  avoid a race with the effect.
- When signed in, `district`'s `Select` is now **controlled**
  (`value={district}` bound to `watch("district")`) rather than
  uncontrolled — required so a `reset()` call from the buyer-prefill effect
  actually shows up in the UI, not just in react-hook-form's internal
  state. Guests never notice the difference.

## Forms

react-hook-form, `zodResolver(checkoutSchema)`. Fields: `fullName`,
`email`, `phone`, `addressLine1`, `city`, `district` (via `Select`, set
with `setValue` + `shouldValidate: true`), `postalCode`, `paymentMethod`
(via `RadioGroup`). `defaultValues.email` seeds from `buyerSession?.email`
when signed in; a `useEffect` keyed on the fetched `Buyer` calls `reset()`
with the rest of the fields once `defaultShipping` loads (same "fetch →
`reset()`" pattern as [`store-settings.md`](store-settings.md)).

## Validation

```ts
const checkoutSchema = z.object({
  fullName: z.string().min(2, "Enter the recipient's full name"),
  email: z.string().email("Enter a valid email"),
  phone: z.string().min(9, "Enter a valid Sri Lankan phone number")
              .regex(/^[0-9+\s]+$/, "Digits only"),
  addressLine1: z.string().min(5, "Enter the delivery address"),
  city: z.string().min(2, "Enter a city/town"),
  district: z.string().min(1, "Select a district"),
  postalCode: z.string().min(4, "Enter a postal code"),
  paymentMethod: z.enum(["payhere", "cod", "bank-transfer"]),
});
```

All validation is **client-side only** today. A real backend must re-validate
`POST /api/orders` input independently (never trust the client) — see
[`api-contracts.md`](../../../docs/api-contracts.md).

## Navigation flow

```
/cart ──(Proceed to checkout)──► /checkout
/checkout (empty cart, hydrated) ──► redirect /cart
/checkout (signed in) ──► useQuery(buyer) ──► reset() form with defaultShipping
/checkout ──(Place order, success)──► clearCart() ──► sendOrderReceiptEmail() [inside createOrder]
                                    ──► updateDefaultShipping() [best-effort, if signed in]
                                    ──► /orders/[order.id]
/checkout ──(Place order, error)──► toast.error, stays on page
/checkout (cart has unavailable items) ──► submit disabled, banner links back to /cart
```

## Expected backend APIs

- `POST /api/orders`

### Request model

```ts
// CheckoutInput
{
  storeId: string;
  items: { productId: string; quantity: number }[];
  shipping: {
    fullName: string; phone: string; addressLine1: string;
    city: string; district: string; postalCode: string;
  };
  paymentMethod: "payhere" | "cod";
  email: string;
  buyerId?: string; // present only when checking out signed in
}
```

### Response model

```ts
Order // full object, see database-model.md#order — includes generated
      // id, orderNumber, computed subtotalLkr/platformFeeLkr/shippingFeeLkr/
      // totalLkr, initial status "pending", initial timeline entry,
      // paymentStatus derived from paymentMethod, buyerEmail, buyerId?
```

## Error handling

- Frontend: `onError` shows a generic toast — "Something went wrong placing
  your order. Please try again." No field-level or reason-specific error
  handling exists (e.g. "this item is now out of stock" is not
  distinguished from a network failure).
- Backend implementers should return distinguishable error codes (see
  [`api-contracts.md`](../../../docs/api-contracts.md#error-conventions)) so the
  frontend can eventually show more specific messages — currently it can't,
  because the mock `createOrder` only ever throws a single generic
  `Error("Product {id} not found")` shape, and the page doesn't inspect the
  error at all.

## Permissions

Public — no authentication required to check out. `page.tsx` reads the
session only to pass along `buyerSession` for the prefill/tagging
behavior above; there's no ownership check anywhere (any `storeId`/`items`
combination is still accepted from any caller, signed in or not).

## Edge cases

- **No re-validation of requested quantity against live stock at
  checkout** — `createOrder` only checks that each `productId` still
  exists (throws if not); it does not check `quantity <= product.stockQuantity`.
  Combined with the cart's stale stock snapshot (see
  [`features/cart.md`](cart.md)), this means an order can be placed for
  more units than are actually in stock, and `decrementStock` will simply
  clamp to zero rather than reject the order. **This must be fixed
  server-side** — flagged in [`gaps-and-assumptions.md`](../../../docs/gaps-and-assumptions.md).
- ~~No email is collected~~ **Resolved** — email is now a required field on
  every checkout; see Business rules above.
- District is a free-standing `Select` from a fixed list
  (`SRI_LANKA_DISTRICTS`); city is a free-text field with no relationship to
  district (no cascading city-per-district list).
- `compareAtPriceLkr` (discounts) are not re-shown at checkout, only the
  live `unitPriceLkr` — acceptable since `CartItem.unitPriceLkr` is already
  the effective price captured at add-to-cart time (and, since
  reconciliation, kept current — see [`features/cart.md`](cart.md)).
- If `updateDefaultShipping` fails after a successful `createOrder` (e.g. a
  transient localStorage error), the order still completes normally — the
  save is wrapped in `.catch(() => {})` specifically so it can never turn a
  successful checkout into an error toast.

## Future improvements

- Real PayHere integration (redirect/embed + webhook confirmation) instead
  of instant client-side `paymentStatus: "paid"`.
- Server-side stock re-validation at order creation (reserve/lock stock).
- ~~Buyer email capture for receipts/notifications~~ **Resolved** — see
  Business rules above. Still open: a **real** email provider (see
  [`gaps-and-assumptions.md`](../../../docs/gaps-and-assumptions.md)).
- ~~Address book for returning buyers (requires buyer accounts)~~
  **Partially resolved** — buyer accounts exist and save one address (see
  [`features/buyer-accounts.md`](buyer-accounts.md)); a real multi-address
  book is still open.
- Order total shown before navigating away from cart to reduce
  drop-off (currently only shown after entering the checkout page).

## Technical notes

- `orderPlacedRef` pattern is a good template for "mutation just cleared
  state that would otherwise trigger a guard effect" races elsewhere.
- The `page.tsx`/`checkout-form.tsx` split exists solely to let `page.tsx`
  be an `async` Server Component (to call `getSession()`) while keeping
  the actual form as a client component — the same "read session
  server-side, pass down as a prop" convention used everywhere else in
  this app (see [`frontend-architecture.md`](../frontend-architecture.md)),
  rather than introducing a client-side session Context.

## Dependencies

`react-hook-form`, `@hookform/resolvers/zod`, `zod`,
`@tanstack/react-query`, `sonner`, `next/navigation` (`useRouter`),
`@/services` (`ordersService`, `buyersService`), `@/hooks/use-cart`,
`@/hooks/use-cart-reconciliation`, `@/lib/session`, `@/lib/constants`
(`SRI_LANKA_DISTRICTS`, `FLAT_SHIPPING_FEE_LKR`).

## TODOs discovered

- No explicit `// TODO` comments in `checkout/page.tsx` or
  `checkout-form.tsx`. The stock re-validation gap is inferred from the
  code's actual behavior, not a comment.
