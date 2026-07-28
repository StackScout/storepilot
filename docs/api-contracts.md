# API Contracts (Inferred)

> Cross-references: [`database-model.md`](database-model.md) ·
> [`frontend-architecture.md`](../app/docs/frontend-architecture.md) ·
> [`feature-index.md`](../app/docs/feature-index.md) ·
> [`gaps-and-assumptions.md`](gaps-and-assumptions.md)

**These endpoints do not exist yet.** They are inferred entirely from the
shape of `src/services/*.service.ts`, which currently implement this
contract against `localStorage` instead of a network call. URL paths below
are proposed (`/api/...`), following the shape of the `/** METHOD /path */`
comments already present in the source — adjust to your actual API
versioning/routing conventions, but keep the request/response *shapes*,
which are load-bearing (every field is consumed somewhere in the frontend).

Types referenced below (`Product`, `Store`, `Order`, etc.) are fully defined
in [`database-model.md`](database-model.md).

## Conventions used below

- **Auth** — `None` (public), `Seller session` (must be signed in), or
  `Seller session + ownership` (signed in **and** the session's `storeId`
  must match the resource's `storeId` — see [Authorization](#authorization)).
- Error responses are **proposed**, not implemented — the current mock
  layer only ever returns `null` (not found) or throws a bare `Error` with
  a plain string message (not found on update/delete, "Product not found"
  on order creation). No status codes, error codes, or structured error
  bodies exist today. Recommendations below are inferred from what the
  frontend *should* be able to distinguish, not what it currently does
  (today it mostly can't — see each feature doc's "Error handling"
  section).

## Authorization

**Critical, cross-cutting requirement not enforced anywhere in the current
mock layer**: every endpoint under "Seller session + ownership" must verify
that the authenticated seller's `storeId` (from their session) matches the
`storeId` of the resource being read/written (or the `storeId` owning the
`productId`/`orderId` in question). The current frontend never needs this
enforced because there is exactly one mock seller and one mock store — see
[`features/seller-auth.md`](../app/docs/features/seller-auth.md#permissions) and
[`gaps-and-assumptions.md`](gaps-and-assumptions.md) for the full
explanation. **Do not port the mock layer's lack of ownership checks into a
real backend.**

---

## Products

### `GET /api/products`
- **Purpose**: marketplace search/listing. Backs
  [marketplace browsing](../app/docs/features/marketplace-browsing.md).
- **Auth**: None.
- **Query params**: `category?: StoreCategory`, `query?: string`,
  `sort?: "newest"|"price-asc"|"price-desc"|"rating"` (default `newest`),
  `limit?: number`.
- **Response**: `200 Product[]`.
- **Validation**: `category` should be validated against the
  `StoreCategory` enum server-side (frontend currently sends unchecked
  values); `limit` should be bounded (e.g. max 100) — the frontend never
  currently sends an unbounded value, but nothing stops a client from doing
  so.
- **Errors**: none distinct today; recommend `400` for a malformed `sort`/
  `category`/`limit` rather than silently ignoring it (current mock
  behavior falls back to defaults silently).

### `GET /api/products/featured`
- **Purpose**: home page featured grid.
- **Auth**: None.
- **Query params**: `limit?: number` (default 8).
- **Response**: `200 Product[]` — currently just top-rated products,
  descending by `rating`; see
  [`features/marketplace-browsing.md`](../app/docs/features/marketplace-browsing.md)
  for the "no real curation" caveat.

### `GET /api/products/:id`
- **Purpose**: fetch a single product by internal ID (used by the seller's
  edit-product page).
- **Auth**: None required by current usage (edit page is behind the
  dashboard's session gate, but the service call itself has no ownership
  check) — **recommend**: `Seller session + ownership` if this ID-based
  lookup is only ever used seller-side; keep the slug-based lookup below
  for public product pages.
- **Response**: `200 Product` or `404`.

### `GET /api/stores/:storeSlug/products/:productSlug`
- **Purpose**: public product detail page.
- **Auth**: None.
- **Response**: `200 Product` or `404`. Must match **both** `storeSlug` and
  `productSlug` — product slugs are only unique within a store, not
  globally (see [`database-model.md`](database-model.md#product)).
- **Note**: current implementation does not filter out `status: "draft"`
  products from this lookup — see
  [`features/store-and-product-detail.md`](../app/docs/features/store-and-product-detail.md#permissions)
  and [`gaps-and-assumptions.md`](gaps-and-assumptions.md). Decide whether
  drafts should 404 here before launch.

### `GET /api/stores/:storeId/products`
- **Purpose**: storefront product grid (public) and seller product list
  (dashboard) — **same endpoint serves both today**.
- **Auth**: None (used publicly). If drafts must stay private to the
  owning seller, this endpoint needs to behave differently for an
  authenticated-owner request vs. a public request (e.g. include drafts
  only when the caller is the owning seller) — not currently distinguished
  anywhere.
- **Response**: `200 Product[]`, sorted by `updatedAt` descending.

### `POST /api/stores/:storeId/products`
- **Purpose**: create a product.
- **Auth**: Seller session + ownership (`storeId` must equal session's
  `storeId`).
- **Request body** (`ProductFormInput`):
  ```ts
  {
    name: string;              // min 3 chars
    description: string;       // min 10 chars
    category: StoreCategory;
    priceLkr: number;          // positive
    compareAtPriceLkr?: number; // positive, optional
    stockQuantity: number;     // integer >= 0
    sku: string;               // min 2 chars
    status: "active" | "draft" | "out-of-stock";
    imageUrl: string;          // non-empty; NOT validated as a URL today
  }
  ```
- **Response**: `201 Product` (full object, server-generated `id`, `slug`,
  `images[]` built from `imageUrl`, `rating: 0`, `reviewCount: 0`,
  `storeId`/`storeName`/`storeSlug` denormalized from the store,
  `createdAt`/`updatedAt`).
- **Validation rules**: mirror the zod schema above server-side (frontend
  validation must not be trusted). **Business rule**: if `stockQuantity ===
  0`, force `status = "out-of-stock"` regardless of the submitted `status`
  (current frontend behavior — see
  [`features/product-management.md`](../app/docs/features/product-management.md)).
- **Errors**: `400` invalid input, `401` no session, `403` `storeId`
  mismatch.

### `PATCH /api/products/:id`
- **Purpose**: update a product.
- **Auth**: Seller session + ownership (product's `storeId` must equal
  session's `storeId`).
- **Request body**: same shape as create (current frontend always resubmits
  the full `ProductFormInput`, not a partial patch).
- **Response**: `200 Product` (updated). Slug is **not** regenerated on
  name change (current behavior — see product-management doc).
- **Errors**: `400` invalid input, `401`, `403` ownership mismatch, `404`
  product not found.

### `DELETE /api/products/:id`
- **Purpose**: permanently delete a product.
- **Auth**: Seller session + ownership.
- **Response**: `204` (or `200 { success: true }`).
- **Business rule**: must **not** cascade-delete or invalidate historical
  `OrderItem` records — those are point-in-time snapshots, independent of
  the live product (current behavior, must be preserved).
- **Errors**: `401`, `403`, `404`.

### (Internal) stock decrement
- `decrementStock(items)` is invoked **server-side, as a side effect of
  order creation** — it is not, and should not become, its own public
  endpoint. Document it here only so backend implementers know this logic
  must live inside the `POST /api/orders` transaction, not as a separate
  client-callable call. **Must also enforce**: reject (or partially fail)
  the order if requested `quantity` exceeds current `stockQuantity` — the
  current mock does **not** do this (see
  [`features/checkout.md`](../app/docs/features/checkout.md#edge-cases)), it just
  clamps stock to zero, which allows overselling.

---

## Stores

### `GET /api/stores`
- **Purpose**: marketplace store search/listing, home page "popular
  stores".
- **Auth**: None.
- **Query params**: `category?: StoreCategory`, `query?: string`,
  `limit?: number`. No `sort` param exists today (see marketplace-browsing
  doc — "popular" is not actually a sort).
- **Response**: `200 Store[]`.

### `GET /api/stores/:slug`
- **Purpose**: public storefront page.
- **Auth**: None.
- **Response**: `200 Store` or `404`.

### `GET /api/stores/id/:id`
- **Purpose**: internal lookup by ID (used by the buyer order-confirmation
  page to fetch the store's WhatsApp number after loading an order by ID).
- **Auth**: None (current usage is from a public, unauthenticated page).
- **Response**: `200 Store` or `404`.
- **Consider**: eliminating this in favor of embedding `whatsappNumber`
  directly in the `Order` response, avoiding a second round trip — see
  [`features/order-tracking.md`](../app/docs/features/order-tracking.md#future-improvements).

### `GET /api/stores/:storeId/settings`
- **Purpose**: seller settings page, payouts page (bank account display).
- **Auth**: Seller session + ownership.
- **Response**: `200 StoreSettings` or `404` if none exist yet for this
  store (current mock still has this gap for 7 of 8 **seed** stores, whose
  rows predate `StoreSettings` — every store created via `/onboarding` gets
  one automatically; see
  [`features/store-settings.md`](../app/docs/features/store-settings.md#edge-cases)).

### `PATCH /api/stores/:storeId/settings`
- **Purpose**: update contact/bank/payment-method settings; also used
  internally by `/onboarding` to write the newly-created store's initial
  settings.
- **Auth**: Seller session + ownership.
- **Request body**:
  ```ts
  {
    contactEmail: string;       // valid email
    contactPhone: string;       // min 9 chars
    sellerType: "individual" | "business";
    nicNumber: string;          // required for verification
    businessRegistrationNumber?: string; // required if sellerType === "business"
    bankName: string;           // min 2 chars
    bankAccountName: string;    // min 2 chars
    bankAccountNumber: string;  // min 4 chars
    codEnabled: boolean;
    onlinePaymentEnabled: boolean;
    bankTransferEnabled: boolean; // opt-in, defaults false — shows
      // bankName/bankAccountName/bankAccountNumber to buyers at checkout
  }
  ```
- **Response**: `200 StoreSettings`.
- **Validation**: at least one of `codEnabled`/`onlinePaymentEnabled`/
  `bankTransferEnabled` must be `true` — `409 CONFLICT` otherwise, so a
  store can never end up with zero payment options at checkout.
- **Resolved**: this is now an **upsert** in the mock
  (`updateStoreSettings` creates a default-filled row if none exists) —
  real backend should preserve upsert semantics rather than 404ing on a
  first write.
- **Errors**: `400`, `401`, `403`.

---

## Orders

### `GET /api/stores/:storeId/orders`
- **Purpose**: seller order list (with optional status filter), dashboard
  overview, payouts page.
- **Auth**: Seller session + ownership.
- **Query params**: `status?: OrderStatus`.
- **Response**: `200 Order[]`, sorted by `createdAt` descending.
- **Note**: current frontend order-list page fetches this **without** the
  `status` param and filters client-side instead — the param should still
  be implemented server-side for scalability (see
  [`features/order-management.md`](../app/docs/features/order-management.md#edge-cases)),
  independent of whether the frontend is updated to use it.

### `GET /api/orders/:id`
- **Purpose**: buyer order-confirmation/tracking page **and** seller order
  detail page — same endpoint, two very different trust contexts.
- **Auth**: **None enforced today.** This is a deliberate-looking but
  under-specified design: a buyer with no account needs *some* way to view
  their own order right after checkout, and the current answer is
  "possession of the URL/ID is proof enough." For a real backend, at
  minimum: (a) use non-enumerable (e.g. UUID) order IDs so they can't be
  guessed/iterated, and (b) still enforce **ownership** for the
  seller-dashboard usage of this same endpoint (session's `storeId` must
  match `order.storeId`) even though the public usage has no session to
  check. See [Authorization](#authorization) and
  [`gaps-and-assumptions.md`](gaps-and-assumptions.md).
- **Response**: `200 Order` or `404`.

### `GET /api/orders/lookup`
- **Purpose**: buyer self-service order lookup without an account.
- **Auth**: None.
- **Query params**: `orderNumber: string`, `phone: string`.
- **Matching rule** (current behavior): `orderNumber` matched
  case-insensitively after trim; `phone` matched by stripping whitespace
  from both sides and checking the stored phone **ends with** the last 9
  digits of the input. This is a weak identity check (9 digits of a phone
  number is not secret) — flagged, not silently "fixed", since changing it
  is a product decision (e.g. requiring an OTP) not a pure bug fix. See
  [`features/order-tracking.md`](../app/docs/features/order-tracking.md#business-rules).
- **Response**: `200 Order` or `404`.

### `POST /api/orders`
- **Purpose**: checkout — create an order from cart contents.
- **Auth**: None (guest checkout remains fully supported). If the request
  carries a valid buyer session, `buyerId` should be trusted from the
  session server-side, not from the client-sent field — the mock trusts the
  client here since there's no real session validation to lean on yet.
- **Request body** (`CheckoutInput`):
  ```ts
  {
    storeId: string;
    items: { productId: string; quantity: number }[]; // must all resolve
      // to products belonging to `storeId` — single-store-per-order rule
      // enforced client-side today (Zustand cart), MUST be re-enforced
      // server-side (reject mixed-store item lists)
    shipping: {
      fullName: string;    // min 2
      phone: string;       // min 9, digits/+/whitespace only
      addressLine1: string; // min 5
      city: string;        // min 2
      district: string;    // one of SRI_LANKA_DISTRICTS
      postalCode: string;  // min 4
    };
    paymentMethod: "payhere" | "cod" | "bank-transfer";
    email: string;         // valid email — receipt destination, collected
                            // from every checkout, guest or signed-in
    buyerId?: string;      // set only when checking out signed in
  }
  ```
- **Response**: `201 Order` — server computes everything in the previous
  contract, plus:
  - `buyerEmail` = the request's `email`, stored verbatim on the order.
  - `buyerId` = the request's `buyerId`, if present.
  - Side effect: **sends an order-confirmation email** to `buyerEmail` via
    `OrderNotifier.orderConfirmed` (backend `notification` package — see
    [`features/notification-emails.md`](../app/docs/features/notification-emails.md)).
    Mocked today (`LoggingEmailService` logs instead of sending); swapping
    in a real provider (e.g. SES) is additive only, no call-site changes.
  - Side effect: if `buyerId` is present, **upserts
    `Buyer.defaultShipping`** to this order's `shipping` (see
    `PATCH /api/buyers/:id/default-shipping` below) — best-effort, should
    not fail order placement if it errors.
  - `orderNumber`: format `SL-YYYYMMDD-####` (4 random digits) — **must be
    unique**; current mock uses `Math.random()` with no collision check.
  - `subtotalLkr` = Σ `unitPriceLkr × quantity` (using **current** product
    price at order time, not any price the client might send).
  - `shippingFeeLkr` = flat `350` (`FLAT_SHIPPING_FEE_LKR`) — not currently
    configurable per store/region/weight.
  - `platformFeeLkr` = `round(subtotalLkr × platformFeePercent / 100)` —
    **decide the rate source** before implementing (global constant vs.
    per-store `transactionFeePercent` — currently inconsistent, see
    [`gaps-and-assumptions.md`](gaps-and-assumptions.md#platform-fee-percent-is-displayed-per-store-but-computed-globally)).
  - `totalLkr` = `subtotalLkr + shippingFeeLkr` (fee is **not** added to
    the buyer's total).
  - `status`: `"pending"`.
  - `paymentStatus`: `"paid"` if `paymentMethod === "payhere"` **and** a
    real gateway confirms payment (current mock sets this instantly with
    no gateway call — must not ship this shortcut to production, see
    [`features/checkout.md`](../app/docs/features/checkout.md#business-rules));
    `"unpaid"` if `"cod"`.
  - `timeline`: single entry, `{ status: "pending", label: "Order placed",
    timestamp: now }`.
  - Side effect: decrement stock for each item (see
    [Internal: stock decrement](#internal-stock-decrement) above) —
    **must** validate sufficient stock and reject/partially-fail otherwise;
    current mock does not.
- **Errors**: `400` invalid input or unknown `productId`, `409` insufficient
  stock (not implemented in mock — recommended for real backend), `422`
  mixed-store item list.

### `PATCH /api/orders/:id/status`
- **Purpose**: seller advances/cancels an order.
- **Auth**: Seller session + ownership (`order.storeId` must equal
  session's `storeId`).
- **Request body**: `{ status: OrderStatus; note?: string }` — `note` is
  accepted by the current mock service signature but **never sent by any
  UI today** (see
  [`features/order-management.md`](../app/docs/features/order-management.md#business-rules)).
- **Business rules to enforce server-side** (currently only enforced by
  which options the frontend's `Select` happens to render — **not** by the
  service layer itself):
  ```
  pending    → confirmed | cancelled
  confirmed  → shipped | cancelled
  shipped    → delivered
  delivered  → (terminal)
  cancelled  → (terminal)
  ```
  Plus: `status = "delivered"` on a `"cod"` order ⇒ `paymentStatus =
  "paid"`; `status = "cancelled"` when `paymentStatus === "paid"` ⇒
  `paymentStatus = "refunded"` (flag only — no real refund transaction is
  modeled).
- **Response**: `200 Order` (with the new status appended to `timeline`,
  including `note` if provided — prior timeline entries must never be
  mutated or removed).
- **Errors**: `400` invalid status value, `401`, `403` ownership mismatch
  or invalid transition, `404`.

### `POST /api/orders/:id/receipt`
- **Purpose**: buyer uploads proof of a bank transfer.
- **Auth**: None (matches `GET /api/orders/:id` — same "possession of the
  order ID is proof enough" model).
- **Request**: `multipart/form-data` with a `file` part — JPEG, PNG, WEBP or
  PDF, 5MB max.
- **Response**: `200 Order` with `receiptUrl` set (backend-relative path,
  e.g. `/uploads/receipts/{uuid}.png` — prefix with the API base URL to
  fetch it) and a new `timeline` entry, `{ label: "Payment receipt
  uploaded", note: "Awaiting seller verification" }`. Can be called again
  to replace the receipt as long as the order is still `unpaid`.
- **Errors**: `400` wrong file type/too large/empty, `404`, `409` order
  isn't a `"bank-transfer"` payment or is no longer `unpaid`.

### `POST /api/orders/:id/verify-bank-transfer`
- **Purpose**: seller accepts or rejects the buyer's uploaded receipt.
- **Auth**: Seller session + ownership (`order.storeId` must equal
  session's `storeId`).
- **Request body**: `{ approved: boolean; note?: string }`.
- **Response**: `200 Order`. On `approved: true`: `paymentStatus →
  "paid"`, `status: "pending" → "confirmed"`, timeline entry `"Payment
  confirmed by seller"`. On `approved: false`: no status/paymentStatus
  change — stays `pending`/`unpaid` so the buyer can upload a corrected
  receipt — timeline entry `"Payment receipt rejected"` with `note` as the
  reason.
- **Errors**: `401`, `403`, `404`, `409` order isn't a `"bank-transfer"`
  payment or is no longer `unpaid`.
- Side effect: **sends a "payment confirmed"/"receipt rejected" email** to
  `buyerEmail` via `OrderNotifier.bankTransferVerified` — see
  [`features/notification-emails.md`](../app/docs/features/notification-emails.md).

### `POST /api/orders/:id/cancel`
- **Purpose**: buyer self-cancels a bank-transfer order before a receipt is
  uploaded (e.g. they changed their mind, or the reminder email prompted
  them to give up rather than pay).
- **Auth**: None — same "possession of the order ID is proof enough" model
  as `GET /api/orders/:id` and `POST /api/orders/:id/receipt`.
- **Request**: no body.
- **Response**: `200 Order`. `status → "cancelled"`, `paymentStatus`
  unchanged (stays `"unpaid"` — nothing was ever paid), timeline entry
  `"Cancelled by buyer"`.
- **Errors**: `404`, `409` if the order isn't a `"bank-transfer"` payment,
  isn't `"pending"`/`"unpaid"`, or already has a `receiptUrl` (once a
  receipt exists the seller must act on it via verify/reject — a buyer
  can't cancel out from under a pending seller review; this is enforced
  server-side, not just hidden in the UI).
- **Not general-purpose**: COD/PayHere orders have no buyer-initiated
  cancel path today.

---

## Auth

**Entirely mocked today — no real credential system exists.** See
[`features/seller-auth.md`](../app/docs/features/seller-auth.md) for the full picture
before implementing any of these for real; endpoints below are the
*intended replacements*, not descriptions of current behavior.

### `POST /api/auth/login`
- **Purpose**: real seller sign-in (replaces "any email signs in").
- **Auth**: None (this *is* the auth entry point).
- **Request body**: `{ email: string; password: string }` — TODO: product
  to confirm password vs. OTP vs. magic-link.
- **Response**: `200`, sets a signed/encrypted session cookie
  (`storepilot_session` or equivalent) containing at minimum `{ sellerId,
  storeId, role: "seller" }`. Body should not need to repeat the session
  payload if using cookie-based auth, but may return a minimal `{ seller:
  {...} }` for client-side display.
- **Errors**: `401` invalid credentials, `400` malformed input.

### `POST /api/auth/register`
- **Purpose**: real seller + store registration. The mock already does the
  *store-creation* half of this for real — `/onboarding` calls
  `storesService.createStore()` (client-side; a `Store` row is genuinely
  persisted, `verificationStatus: "pending"`) then
  `storesService.updateStoreSettings()`, then establishes a session. What's
  still missing is the **seller/user** half: no password, no `User`/`Seller`
  record, so nothing lets `/login` later map an email back to the store it
  created.
- **Auth**: None.
- **Request body** (current onboarding form fields; add `password` for a
  real credential system):
  ```ts
  {
    email: string; password: string; // password: not collected by mock today
    storeName: string; category: StoreCategory; tagline: string;
    description: string; city: string; district: string;
    whatsappNumber: string; contactEmail: string; contactPhone: string;
    sellerType: "individual" | "business";
    nicNumber: string; businessRegistrationNumber?: string; // required if business
    bankName: string; bankAccountName: string; bankAccountNumber: string;
  }
  ```
- **Response**: `201` — must create **both** a new seller/user record and a
  new, distinct `Store` (generated unique `slug`, `verificationStatus:
  "pending"`) plus its `StoreSettings` row, then establish a session. A
  real backend should also reject/queue duplicate applications from the
  same NIC/business-registration number — not enforced by the mock.
- **Errors**: `400` invalid input, `409` email (or NIC/business-reg number)
  already registered.

### `POST /api/auth/logout`
- **Purpose**: sign out.
- **Auth**: Seller session (or none — logging out an already-logged-out
  session should be a no-op success, not an error).
- **Response**: `200`, clears the session cookie.

### `GET /api/auth/session` *(proposed, not currently needed)*
- **Purpose**: client-side "who am I" check, only needed if a future
  feature requires client components to know the signed-in seller without
  a server-side prop drill (today, `getSession()` is always called
  server-side and passed down — e.g. `dashboard/layout.tsx` → sidebar).
- **Auth**: Seller session.
- **Response**: `200 { sellerId, storeId, email } ` or `401`.
- **Now built for buyers**: `GET /api/account/session` (`src/app/api/account/session/route.ts`)
  is exactly this pattern, but for the buyer session, used by the site
  header's account link. It's a **route handler**, not read server-side in
  a shared layout — see [Why a route handler, not a layout read](#why-a-route-handler-not-a-layout-read) below.

---

## Buyer Accounts

Unlike seller `/login` above, this is **not aspirational** — it's a real,
working mock: a genuine `Buyer` row is created and looked up, guest
checkout keeps working unchanged, and signing in is optional. The only gap
from a real system is the credential (see
[`gaps-and-assumptions.md`](gaps-and-assumptions.md)). See
[`features/buyer-accounts.md`](../app/docs/features/buyer-accounts.md).

### `POST /api/buyers` (register)
- **Purpose**: create a buyer account and sign in.
- **Auth**: None.
- **Request body**: `{ name: string; email: string; phone?: string }` —
  add `password` for a real credential system.
- **Response**: `201 Buyer`, session established.
- **Errors**: `400` invalid input, `409` email already registered (current
  mock message: *"An account with this email already exists. Try signing
  in instead."*).

### `GET /api/buyers/by-email?email=`
- **Purpose**: sign-in lookup — unlike seller `/login`, this is a **real**
  email→account lookup, not a no-op.
- **Auth**: None (this *is* part of the sign-in flow).
- **Response**: `200 Buyer` or `200 null` (mock never 404s here; the
  client decides what "not found" means for the UI).

### `GET /api/buyers/:id`
- **Purpose**: fetch full buyer profile (used by the account page, and by
  checkout to prefill from `defaultShipping`).
- **Auth**: Buyer session + ownership (`id` must equal session's
  `buyerId`) — **not enforced in the mock**, same gap as everywhere else in
  this document.
- **Response**: `200 Buyer` or `404`.

### `PATCH /api/buyers/:id/default-shipping`
- **Purpose**: save/overwrite the buyer's one saved address. Called
  automatically after every signed-in checkout (best-effort, doesn't block
  order placement on failure) — not a user-facing form today.
- **Auth**: Buyer session + ownership.
- **Request body**: `ShippingDetails` (see [`database-model.md`](database-model.md#shippingdetails-embedded-on-order-not-its-own-entity)).
- **Response**: `200 Buyer`.

### `GET /api/buyers/:buyerId/orders`
- **Purpose**: order history on the account page.
- **Auth**: Buyer session + ownership.
- **Response**: `200 Order[]`, sorted by `createdAt` descending — orders
  where `Order.buyerId === buyerId`. Guest orders (no `buyerId`) never
  appear here, even if placed with the same email.

### `POST /api/auth/logout` *(buyer variant)*
- Same shape as the seller logout above, but for the buyer session
  (`signOutBuyer` in the mock) — redirects to `/` instead of `/login`
  since there's no buyer-only area that requires being signed out of.

#### Why a route handler, not a layout read
The obvious implementation would read the session cookie in
`(marketplace)/layout.tsx` (a Server Component, wraps every marketplace
page) and pass `buyerName` down as a prop — this was tried and reverted.
Next.js forces **any** route that reads a dynamic API like the session
cookie into per-request server rendering, and a shared layout's dynamic-ness
cascades to every page beneath it. That would have silently turned the
home page, `/search`, and every store/product page from statically
generated (good for SEO, per
[`frontend-architecture.md`](../app/docs/frontend-architecture.md)) into always-dynamic,
just to know whether to show "Sign in" or a name in the header. The route
handler keeps that cost scoped to the one client-side fetch
(`useBuyerAccountLink`, `src/hooks/use-buyer-account-link.ts`) that
actually needs it.

---

## Payouts

Backs [`features/payouts.md`](../app/docs/features/payouts.md). Read-only for the
seller — payout runs are only ever created/released via the admin
endpoints below, never requested by the seller.

### `GET /api/stores/:storeId/payouts`
- **Purpose**: seller payouts page (available/scheduled/paid summary +
  history table).
- **Auth**: Seller session + ownership.
- **Response**: `200 Payout[]`, sorted by `createdAt` descending.

### `GET /api/stores/:storeId/payouts/eligible-orders`
- **Purpose**: compute the "available for payout" figure shown on the
  seller payouts page and the admin payout-runs panel.
- **Auth**: Seller session + ownership (also used, unauthenticated per
  current mock, by the admin tool below — **must** gain a real admin-role
  check once one exists).
- **Response**: `200 Order[]` — orders where `status === "delivered"` and
  `paymentStatus === "paid"` and not already included in an existing
  `Payout` for this store.

---

## Admin (mock only — see `gaps-and-assumptions.md`)

**None of this exists as a real, authenticated surface.** `/admin` today is
reachable by anyone with the URL — see
[`gaps-and-assumptions.md`](gaps-and-assumptions.md#admin-has-no-authentication-or-authorization-at-all).
Endpoints below describe the mock's current behavior so a real admin role
can be retrofitted with auth, not as an already-secure design to copy
as-is.

### `GET /api/admin/stores?status=pending`
- **Purpose**: list store applications awaiting review.
- **Auth (proposed)**: Admin role — **not enforced today**.
- **Response**: `200 Store[]`, filterable by `verificationStatus`.

### `PATCH /api/admin/stores/:storeId/verification`
- **Purpose**: approve or reject a store application.
- **Auth (proposed)**: Admin role — **not enforced today**.
- **Request body**: `{ status: "active" | "rejected"; rejectionReason?: string }`
  (`rejectionReason` required by the current UI when rejecting, stored on
  the store's `StoreSettings`).
- **Response**: `200 Store` (with `verificationStatus`/`isVerified`
  updated). Approving makes the store immediately visible on public
  listings (search, home, storefront); rejecting does not delete it.
- **Errors**: `400`, `401`/`403` once real admin auth exists, `404`.

### `POST /api/admin/stores/:storeId/payouts`
- **Purpose**: bundle all of a store's payout-eligible orders into one new
  `"scheduled"` `Payout`.
- **Auth (proposed)**: Admin role — **not enforced today**.
- **Response**: `201 Payout`.
- **Errors**: `400` if there are zero eligible orders (current mock
  throws rather than creating an empty payout).

### `GET /api/admin/payouts`
- **Purpose**: admin's "all payouts" table across every store.
- **Auth (proposed)**: Admin role.
- **Response**: `200 Payout[]`.

### `PATCH /api/admin/payouts/:payoutId`
- **Purpose**: mark a scheduled payout as paid, once the bank transfer has
  actually been sent.
- **Auth (proposed)**: Admin role.
- **Request body**: `{ status: "paid"; bankReference?: string }`.
- **Response**: `200 Payout` (`paidAt` set to now).
- **Errors**: `400`, `404`.

---

## Categories

`StoreCategory` is currently a **fixed, hardcoded 8-value union** with
static labels/icons in `src/mock/categories.ts` — not fetched from any
service function. **Recommend deciding explicitly**: keep categories as
frontend-bundled static config (simplest, matches current design, but means
adding a category requires a frontend deploy), or introduce a real
`GET /api/categories` endpoint if categories should become
backend-managed/dynamic. Not building this endpoint is a valid choice, not
an oversight — flagged here only so it's a deliberate decision.

---

## Error response convention (recommended)

Not implemented in the mock layer (which only returns `null` or throws
bare `Error`s), but recommended for the real API so the frontend can
eventually replace its generic toasts with specific messaging:

```ts
{
  error: {
    code: string;      // e.g. "VALIDATION_ERROR", "NOT_FOUND", "FORBIDDEN_OWNERSHIP", "INSUFFICIENT_STOCK"
    message: string;   // human-readable
    fields?: Record<string, string>; // field-level validation messages, keyed to match the zod schemas already in the frontend
  }
}
```

This shape is chosen so `fields` keys can map 1:1 onto the react-hook-form
field names already used in each form (see each feature doc's "Validation"
section) — the frontend does not do this today, but should when a real
API lands.
