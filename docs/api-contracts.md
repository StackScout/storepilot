# API Contracts

> Cross-references: [`database-model.md`](database-model.md) ·
> [`overview.md`](overview.md) · [`feature-index.md`](../app/docs/feature-index.md) ·
> [`gaps-and-assumptions.md`](gaps-and-assumptions.md) · [`roadmap.md`](roadmap.md)

Real, implemented REST endpoints served by the Spring Boot backend
(`backend/src/main/kotlin/com/storepilot/backend/*/`). Source of truth for
every entry below: the `@RestController` classes (grepped for
`@GetMapping`/`@PostMapping`/`@PatchMapping`/`@DeleteMapping`), each
endpoint's request/response DTOs in the matching `*Dtos.kt`, and
`common/security/SecurityConfig.kt` for the actual enforced auth
requirement — not assumed from the controller alone, since a route can be
`permitAll` at the filter-chain level and still be ownership-gated inside
the service.

The frontend's real HTTP client is `app/src/lib/api-client.ts`, called by
the namespaced functions in `app/src/services/*.service.ts` (one file per
feature area, barrel-exported via `app/src/services/index.ts`) — these call
exactly the paths documented below, over `fetch` with `credentials:
"include"` for the httpOnly Cognito auth cookies. This document doesn't
restate each frontend function name; see the service files directly if you
need the call site.

## Conventions

- **Auth** column values: `None` (public/`permitAll`), `Authenticated`
  (any valid Cognito JWT, any role), `SELLER`/`BUYER`/`ADMIN` (role
  required via `hasRole(...)`), or `Owner (SELLER)` (role **plus** a
  service-layer ownership check — the signed-in seller's own `Seller.id`
  must match the resource's `store.seller.id`, verified by helper methods
  like `requireOwnedStore`/`requireSellerOwnsOrder`, not just the role
  gate).
- **Money** fields are always integer cents (see
  [`database-model.md`](database-model.md#conventions-used-throughout)).
- **Pagination**: endpoints that return a `PageResponse<T>` accept
  `page`/`size` query params (0-indexed, `size` capped at 100
  server-side regardless of what's requested) and respond with
  `{ content, page, size, totalElements, totalPages }`.
- **Error shape** (`ApiError.kt`, applied globally by
  `GlobalExceptionHandler`):
  ```ts
  {
    error: {
      code: string;      // "VALIDATION_ERROR" | "NOT_FOUND" | "CONFLICT" |
                          // "FORBIDDEN_OWNERSHIP" | "UNAUTHENTICATED" |
                          // "EMAIL_NOT_VERIFIED" | "INTERNAL_ERROR"
      message: string;
      fields?: Record<string, string>; // only on VALIDATION_ERROR from a failed @Valid body
    }
  }
  ```
  Mapped 1:1 from typed exceptions: `NotFoundException` → 404,
  `ConflictException` → 409, `ForbiddenException` → 403,
  `UnauthenticatedException` → 401, `EmailNotVerifiedException` → 403 (kept
  distinguishable from a plain wrong-password 401 so the frontend can route
  to email verification instead of a generic error), any other
  `IllegalArgumentException`/`require()` failure → 400, anything unhandled
  → 500 (logged server-side, generic message returned).

---

## Auth

Real Cognito-backed authentication — `AdminInitiateAuth`/`AdminCreateUser`
Cognito Admin* APIs for direct email/password, plus a separate Hosted-UI
OAuth flow for Google sign-in. Buyer and seller are **mutually exclusive
identities** — one email can hold the `buyer` Cognito group or eventually
the `seller` group (granted only by onboarding, `POST /api/stores`), never
both; `register()`/`create()` both actively refuse to cross-grant. See
`common/security/AuthController.kt`'s doc comment for the full flow
rationale.

### `POST /api/auth/register`
- **Auth**: None.
- **Body**: `{ name, email, password (min 8 chars), accountType: "buyer" | "seller" }`.
- Creates a Cognito user with `email_verified=false` via `AdminCreateUser` +
  `AdminSetUserPassword(permanent=true)` (Cognito's own confirmation-code
  flow is never used). A `"buyer"` registration is granted the `buyer`
  group immediately; a `"seller"` registration gets **no group at all**
  until onboarding. Does **not** sign the caller in — emails a 6-digit
  app-owned verification code (`EmailVerificationService`) instead.
- **Response**: `201 { email, name }`.
- **Errors**: `409` if the email already exists.

### `POST /api/auth/verify-email`
- **Auth**: None. **Body**: `{ email, code }`.
- Confirms the code, flips the Cognito user's `email_verified` to `true`.
  Still doesn't sign the caller in — the frontend re-uses the
  already-typed password to call `login()` itself right after this
  succeeds. **Response**: `204`.

### `POST /api/auth/resend-verification-code`
- **Auth**: None. **Body**: `{ email }`. Always `204`, even for an unknown
  email — deliberately doesn't reveal whether an account exists.

### `POST /api/auth/login`
- **Auth**: None. **Body**: `{ email, password }`.
- Refuses to authenticate an account whose `email_verified` is still
  `false` (`403 EMAIL_NOT_VERIFIED`), checked **before** any session
  cookie is set. On success, sets httpOnly `access_token`/`refresh_token`
  cookies (`SameSite=Lax`) and returns
  `{ signedIn: true, role, email, name }`. An admin login is additionally
  recorded in `audit_logs` (`ADMIN_LOGIN`).
- **Errors**: `401` wrong credentials or unknown email (same message
  either way — doesn't distinguish which).

### `POST /api/auth/refresh`
- **Auth**: valid `refresh_token` cookie. Exchanges it for a fresh access
  token (tries the email/password Cognito client first, falls back to the
  OAuth token endpoint for a Google-originated session). **Response**:
  `200 { signedIn: true }`. The frontend's `api-client.ts` calls this
  automatically and retries once on any `401`, so most 401s are invisible
  to the rest of the app.

### `GET /api/auth/google/start?intent=buyer|seller` / `GET /api/auth/google/callback`
- **Auth**: None. Redirect-based Hosted-UI OAuth handoff — the frontend
  only ever links to `start` (defaulting `intent` to `buyer`); `intent`
  round-trips through Cognito unchanged as the OAuth2 `state` param, back
  to `callback`, which exchanges the auth code and then branches on
  `(intent, existing Cognito group)`:
  - groupless + `buyer` intent: JIT-assigns the `buyer` group (as before),
    sets cookies, redirects to `/account`.
  - groupless + `seller` intent: assigns **no** group — lands on
    `/onboarding` exactly like a freshly-verified password-registered
    seller. Onboarding (`POST /api/stores`) remains the only thing that
    ever grants `seller`.
  - existing group matches intent: ordinary returning sign-in, redirects
    to `/account` (buyer) or `/dashboard` (seller).
  - existing group doesn't match intent (e.g. an existing buyer using the
    seller button): rejected — no cookies set, redirected back to the
    intent-appropriate login page with
    `?error=google_wrong_account_type&existingRole=<role>`.

### `POST /api/auth/logout`
- **Auth**: Authenticated or none (no-op success either way).
  Best-effort `AdminUserGlobalSignOut` against Cognito, then always clears
  both cookies. **Response**: `200 { signedIn: false }`.

### `GET /api/auth/session`
- **Auth**: None (returns `{ signedIn: false }` for a guest rather than
  erroring). Lets the frontend learn its own auth state without the tokens
  themselves being JS-readable. **Response**:
  `{ signedIn, role?, email?, name? }`.

---

## Stores

### `GET /api/stores`
- **Auth**: None. **Query**: `category?`, `query?` (substring match on
  name/tagline/city), `page?`, `size?`. Filters to
  `verification_status = 'active'` only. **Response**: `200 PageResponse<Store>`,
  sorted by `rating` descending.

### `GET /api/stores/id/{id}`
- **Auth**: None. Internal lookup by UUID, **not** verification-status
  gated. **Response**: `200 Store` or `404`.

### `GET /api/stores/{slug}`
- **Auth**: None. Public storefront lookup — `active` stores only.
  **Response**: `200 Store` or `404`.

### `GET /api/me/store`
- **Auth**: SELLER. The authenticated seller's own store, resolved
  server-side from their Cognito identity — never a client-trusted
  storeId. **Response**: `200 Store` or `404` if they haven't onboarded
  yet.

### `GET /api/stores/{storeId}/settings`
- **Auth**: Owner (SELLER). Full settings row: bank details, contact info,
  verification identity fields/documents, Stripe Connect status.
  **Response**: `200 StoreSettings` or `404` if none exists yet.

### `GET /api/stores/{storeId}/public-settings`
- **Auth**: None. Buyer-safe subset for checkout/order pages —
  `{ storeId, bankAccountName, bankAccountNumber, bankName, codEnabled,
  onlinePaymentEnabled, bankTransferEnabled, pickupEnabled, stripeEnabled,
  stripeChargesEnabled }`. Deliberately excludes contact info, NIC/ABN/
  business-registration numbers, verification documents, and the Stripe
  account id.

### `PATCH /api/stores/{storeId}/settings`
- **Auth**: Owner (SELLER). Upsert (creates the row on first call —
  onboarding's second step — updates it thereafter). Every field in the
  body is optional; a field left `null`/absent is untouched, matching a
  `Partial<...>` PATCH semantics.
- **Body** (`StoreSettingsInput`): `contactEmail?`, `contactPhone?`,
  `bankAccountName?`, `bankAccountNumber?`, `bankName?`,
  `transactionFeePercent?` (0–100), `codEnabled?`, `onlinePaymentEnabled?`,
  `bankTransferEnabled?`, `sellerType?`, `driverLicenceNumber?`, `abn?`,
  `nicNumber?`, `businessRegistrationNumber?`, `rejectionReason?`,
  `stockManagementEnabled?`, `pickupEnabled?`, `stripeEnabled?`.
- **Business rules**:
  - **`409`** if the store is `verification_status = 'active'` and the
    request touches any identity-verification field (`sellerType`,
    `driverLicenceNumber`, `abn`, `nicNumber`, `businessRegistrationNumber`)
    — those go through
    [verification change requests](#store-verification-change-requests)
    instead once a store is approved. Never blocks a `pending`/`rejected`
    store's initial submission or resubmission.
  - `codEnabled`/`bankTransferEnabled` are **silently forced `false`**
    (not rejected) whenever the owning seller isn't on the Pro plan,
    regardless of what was requested — see [`overview.md`](overview.md).
  - `409` if the resulting `codEnabled`/`onlinePaymentEnabled`/
    `bankTransferEnabled` would all be `false` — a store must always offer
    at least one payment method.
  - Validates the country-specific identity fields (`requireCountryVerificationFields`
    — driver's licence + ABN for an AU deployment, NIC + business
    registration for LK) whenever they're being set.
  - A real change to `bankName`/`bankAccountName`/`bankAccountNumber`
    triggers an admin notification (email + `admin_notifications` row) —
    only fired on an actual diff, not every save.
  - Any successful save (other than the admin-rejection path reusing this
    same method to stash `rejectionReason`) is recorded to `audit_logs` as
    `STORE_SETTINGS_UPDATED`, attributed to the seller.
- **Response**: `200 StoreSettings`.

### `PATCH /api/stores/{storeId}/profile`
- **Auth**: Owner (SELLER). Public social links only — separate from
  `/settings` since those are private data.
- **Body**: `{ facebookUrl?, instagramUrl?, tiktokUrl? }` (each validated
  as a URL if present). A field left `null` is untouched; an empty string
  clears it. **Response**: `200 Store`.

### `POST /api/stores/{storeId}/{driver-licence-document|abn-document|nic-document|business-reg-document}`
- **Auth**: Owner (SELLER). `multipart/form-data`, field `file` (image or
  PDF, 5MB max). Same post-approval freeze as the settings PATCH above —
  `409` if the store is already `active`. **Response**: `200 StoreSettings`
  with the corresponding `*DocumentUrl` updated.

### `POST /api/stores/{storeId}/logo` / `POST /api/stores/{storeId}/banner`
- **Auth**: Owner (SELLER). `multipart/form-data`, field `file` (image,
  5MB max). **Not** subject to the post-approval freeze — a store's logo/
  banner can be changed anytime. **Response**: `200 Store`.

### `POST /api/stores`
- **Auth**: Authenticated (any Cognito identity — this call is what grants
  `ROLE_SELLER` in the first place, so it can't itself require that role).
- **Body** (`StoreApplicationInput`): `name`, `category`, `tagline`,
  `description`, `city`, `state`, `whatsappNumber` — all required,
  non-blank.
- Onboarding, step one. `409` if the caller already holds the `buyer`
  group (buyer/seller are mutually exclusive). First-time onboarding also
  creates the `Seller` row and grants the Cognito `seller` group in the
  same transaction. Creates the `Store` with `verification_status =
  'pending'`, auto-generated unique `slug`. Does **not** create
  `StoreSettings` — the frontend's onboarding flow calls
  `PATCH /api/stores/{storeId}/settings` as a second step.
- **Response**: `201 Store`.

---

## Store verification change requests

The post-approval identity-change workflow — see
[`overview.md`](overview.md) and
[`database-model.md#store_verification_change_requests-v12`](database-model.md#store_verification_change_requests-v12).

### `GET /api/stores/{storeId}/verification-change-requests/current`
- **Auth**: Owner (SELLER). The seller's own currently-`PENDING` request,
  if any. **Response**: `200 StoreVerificationChangeRequest` or `404`.

### `POST /api/stores/{storeId}/verification-change-requests`
- **Auth**: Owner (SELLER). `multipart/form-data`: a `data` JSON part
  (`VerificationChangeRequestInput` — `sellerType?`, `driverLicenceNumber?`,
  `abn?`, `nicNumber?`, `businessRegistrationNumber?`, all optional) plus
  up to 4 optional file parts (`driverLicenceDocument`, `abnDocument`,
  `nicDocument`, `businessRegDocument`).
- **Business rules**: `409` if the store isn't `active` yet (use direct
  settings edits instead); `409` if a `PENDING` request already exists for
  this store; `400` if the body includes neither a text field nor a
  document; proposed values are merged against the store's *current*
  `store_settings` before country-field validation runs, so a submission
  that only changes the ABN is still validated against the seller's
  existing `sellerType`. Records `STORE_VERIFICATION_CHANGE_REQUESTED` to
  `audit_logs` and notifies admins.
- **Response**: `200 StoreVerificationChangeRequest` (includes both the
  proposed values and the store's current live values side by side, for an
  old-vs-new diff view).

### `GET /api/admin/verification-change-requests`
- **Auth**: ADMIN. **Query**: `status?` (`pending`/`approved`/`rejected`).
  **Response**: `200 StoreVerificationChangeRequest[]`, newest first.

### `POST /api/admin/verification-change-requests/{id}/approve`
- **Auth**: ADMIN. Applies every proposed field/document onto the real
  `store_settings` row (only the ones the request actually set), re-runs
  country-field validation defensively, marks the request `APPROVED`
  with `reviewedAt`/`reviewedByEmail`. Records
  `STORE_VERIFICATION_CHANGE_APPROVED`. **Response**: `200 StoreSettings`
  (the updated real settings, not the request).
- **Errors**: `409` if the request isn't still `PENDING`.

### `POST /api/admin/verification-change-requests/{id}/reject`
- **Auth**: ADMIN. **Body**: `{ rejectionReason }` (required — `400` if
  blank). Marks the request `REJECTED`; nothing on `store_settings`
  changes. Records `STORE_VERIFICATION_CHANGE_REJECTED`. **Response**:
  `200 StoreVerificationChangeRequest`.

---

## Products

### `GET /api/products`
- **Auth**: None. **Query**: `category?`, `query?`, `minPrice?`,
  `maxPrice?`, `sort?` (`"newest"` default | `"price-asc"` |
  `"price-desc"` | `"rating"`), `page?`, `size?`.
- Filtering, sorting, and pagination all happen in the SQL query
  (`Specification` + `Pageable`) — never "fetch everything, filter in
  memory." Excludes drafts and products belonging to a non-`active` store.
- **Response**: `200 PageResponse<Product>`.

### `GET /api/products/{id}`
- **Auth**: None required at the route level, but ownership-gated inside:
  a `draft` product 404s (not 403s) for anyone but its owning seller, so a
  stranger can't distinguish "doesn't exist" from "exists but is a draft."
  **Response**: `200 Product` or `404`.

### `GET /api/stores/{storeId}/products`
- **Auth**: None at the route level. Serves **both** the public storefront
  grid and the seller's own dashboard product list from the same endpoint —
  the response set depends on whether the caller is resolved as the
  owning seller (sees every status, including drafts) or not (drafts
  excluded), not on any query param a public caller could set themselves.
  **Response**: `200 Product[]`, sorted by `updatedAt` descending.
- Note: there is no dedicated `GET /stores/:storeSlug/products/:productSlug`
  endpoint — the frontend composes a product-detail-by-slug lookup from
  this endpoint plus `GET /api/stores/{slug}`.

### `POST /api/stores/{storeId}/products`
- **Auth**: Owner (SELLER). `multipart/form-data`: a `data` JSON part
  (`ProductFormInput`) plus an `images` file-list part (**at least one
  required** — `400` on an empty list).
- **Body** (`ProductFormInput`): `name`, `description`, `category`,
  `price` (cents, ≥ 1), `compareAtPrice?`, `stockQuantity` (≥ 0),
  `trackStock` (default true), `sku?`, `status`.
- **Business rules**: `category` must equal the owning store's own
  `category` (`409` otherwise — a product is no longer independently
  categorizable). `trackStock` is forced `false` if the store's
  `stockManagementEnabled` is `false`. `status` is force-set to
  `"out-of-stock"` whenever effective `trackStock` is true and
  `stockQuantity = 0`, regardless of the submitted value.
- **Response**: `201 Product`.

### `PATCH /api/products/{id}`
- **Auth**: Owner (SELLER). Same `multipart/form-data` shape as create;
  the frontend always resubmits the full form, not a partial diff. An
  **empty** `images` part means "keep the product's existing images
  unchanged"; a **non-empty** one replaces the whole set. Slug is **not**
  regenerated on a name change (URL stays stable). Same category-lock and
  stock-status rules as create. **Response**: `200 Product`.

### `DELETE /api/products/{id}`
- **Auth**: Owner (SELLER). Hard delete — does **not** cascade to or
  invalidate any historical `OrderItem` (those are immutable snapshots,
  decoupled from the live product by design). **Response**: `204`.

---

## Orders

### `GET /api/stores/{storeId}/orders`
- **Auth**: Owner (SELLER). **Query**: `status?`, `page?`, `size?`.
  **Response**: `200 PageResponse<Order>`, sorted by `createdAt`
  descending.

### `GET /api/me/orders`
- **Auth**: BUYER. The signed-in buyer's own order history — `buyerId`
  always comes from the resolved session, never a path/query param (this
  replaced an earlier by-buyerId-parameter design that was a plain IDOR).
  **Response**: `200 Order[]`, sorted by `createdAt` descending. Guest
  orders (no linked buyer) never appear here, even for the same email.

### `GET /api/orders/lookup`
- **Auth**: None. **Query**: `orderNumber`, `phone`. Matches
  `orderNumber` case-insensitively after trim; `phone` by comparing the
  stored phone's **last 9 digits** against the input's last 9 digits after
  stripping whitespace — a deliberately weak, still-unresolved identity
  check (9 digits isn't secret), flagged rather than silently changed
  since replacing it (e.g. with an OTP) is a product decision. **Response**:
  `200 Order` or `404`.

### `GET /api/orders/{id}`
- **Auth**: None — **by design**. Serves both the buyer's post-checkout
  confirmation/tracking page (no account, no session to check) and the
  seller's own order-detail page from the same endpoint. Order IDs are
  UUIDs (non-enumerable), and per `OrderService.kt`'s doc comment,
  "possession of the order ID is the credential" is a deliberate tradeoff
  here, not an oversight — see
  [`roadmap.md`](roadmap.md) for the open question of whether this needs
  revisiting for buyer-PII exposure via a leaked/shared link. **Response**:
  `200 Order` or `404`.

### `POST /api/orders`
- **Auth**: None (guest checkout fully supported) — if a valid buyer
  session is present, the order is linked to it automatically.
- **Body** (`CheckoutInput`):
  ```ts
  {
    storeId: string;
    items: { productId: string; quantity: number }[]; // non-empty
    shipping: {
      fullName: string; phone: string;      // always required
      addressLine1?, city?, state?, postalCode?: string; // required only when deliveryMethod = "shipping"
    };
    paymentMethod: "payhere" | "cod" | "bank-transfer" | "stripe";
    deliveryMethod: "shipping" | "pickup";
    email: string; // receipt destination, required for every checkout
  }
  ```
  No `buyerId` field — the order's buyer link (if any) is derived
  server-side from the authenticated session only, never trusted from the
  client.
- **Business rules**:
  - `404` if any `productId` doesn't resolve.
  - `409` if any line item's `quantity` exceeds the product's
    `stockQuantity`, for a product (and store) with stock tracking on —
    skipped entirely for products/stores that opted out.
  - `409` if `deliveryMethod = "pickup"` but the store doesn't have
    `pickupEnabled`. Pickup orders get `shippingFee = 0` and no address
    fields stored.
  - `409` if `paymentMethod` is `cod` or `bank-transfer` and the seller
    isn't on the Pro plan (defense in depth — the settings toggle should
    already prevent offering it).
  - `409` if `paymentMethod = "payhere"` and the deployment's country isn't
    `LK`, or `"stripe"` and it isn't `AU` — each gateway is currently
    single-country.
  - `platformFee` is computed from the order's store's
    `transactionFeePercent` (falling back to the platform-wide default only
    if the store has no settings row yet).
  - Side effects: decrements stock for every trackable line item;
    sends an order-confirmation email
    (see [`features/notification-emails.md`](../app/docs/features/notification-emails.md)).
  - `paymentStatus` starts `"unpaid"` for every method — COD flips to
    `"paid"` on delivery, PayHere/Stripe flip asynchronously via their
    respective webhooks/notify callbacks once the buyer actually pays,
    bank-transfer flips once the seller verifies the uploaded receipt.
- **Response**: `201 Order`.

### `PATCH /api/orders/{id}/status`
- **Auth**: Owner (SELLER). `multipart/form-data`: a `data` JSON part
  (`OrderStatusUpdateInput`) plus an optional `courierReceipt` file part
  (only meaningful when transitioning to `shipped`).
- **Body**: `{ status, note?, trackingNumber?, courierServiceName? }`.
- **Business rules**: transition must be legal per the server-side state
  machine (`pending → confirmed|cancelled`, `confirmed → shipped|cancelled`,
  `shipped → delivered`, `delivered`/`cancelled` terminal; each status also
  allows a self-transition) — `409` otherwise, enforced in the service
  layer, not just by which options a dropdown renders. `trackingNumber`/
  `courierServiceName` are **required** specifically when the target status
  is `shipped` (`400` if missing). `delivered` on a `cod` order flips
  `paymentStatus` to `paid`. `cancelled` on an already-`paid` order flips
  `paymentStatus` to `refunded` — for a Stripe order this **actually
  issues a refund** via the Stripe API first (including the platform's own
  application fee) and rolls back the whole request if that call fails,
  rather than claiming a refund happened when no money moved; every other
  payment method's cancel is a status flag only. **Response**: `200 Order`
  with a new append-only `timeline` entry.

### `POST /api/orders/{id}/receipt`
- **Auth**: None (same "order ID is the credential" model). `multipart/
  form-data`, field `file` (JPEG/PNG/WEBP/PDF, 5MB max). `409` if the
  order isn't `bank-transfer` or is no longer `unpaid`. **Response**:
  `200 Order` with `receiptUrl` set and a new timeline entry. Can be called
  again to replace the receipt as long as the order is still `unpaid`.

### `POST /api/orders/{id}/verify-bank-transfer`
- **Auth**: Owner (SELLER). **Body**: `{ approved: boolean, note?: string }`.
  `409` if the order isn't `bank-transfer` or is no longer `unpaid`.
  On `approved: true`: `paymentStatus → paid`, `status: pending → confirmed`.
  On `approved: false`: **clears `receiptUrl`** (returns the order to "no
  receipt on file", re-enabling both re-upload and the buyer-cancel path
  below), status/paymentStatus unchanged. Either way sends a
  confirmation/rejection email to the buyer. **Response**: `200 Order`.

### `POST /api/orders/{id}/cancel`
- **Auth**: None (same credential model). Narrow, buyer-self-service:
  only a `bank-transfer` order still `pending`/`unpaid` with **no receipt
  currently on file** can be cancelled this way — `409` otherwise
  (including if a receipt was uploaded and is awaiting seller review; once
  uploaded, the seller is expected to act on it, not have it pulled out
  from under them). A rejected receipt doesn't count as "on file" (verify
  clears it), so cancel becomes available again after a rejection. COD/
  PayHere/Stripe orders have no buyer-initiated cancel path at all.
  **Response**: `200 Order`, `status → cancelled`.

### `GET /api/stores/{storeId}/stripe-settlements` / `GET /api/admin/stripe-settlements`
- **Auth**: Owner (SELLER) / ADMIN respectively. Read-only reconciliation
  view — every `paid` Stripe order for a store (or platform-wide for the
  admin variant). **Not** a ledger to release or collect from; Connect
  already settled the money at charge time. **Response**: `200 Order[]`.

---

## Payments (PayHere and Stripe checkout)

### `POST /api/orders/{id}/payhere-checkout`
- **Auth**: None. Builds the hidden-form payload PayHere's Checkout API
  expects, with the payment hash computed **server-side** so
  `merchant_secret` never reaches the browser. **Response**: `200
  PayHereCheckoutResponse` (`actionUrl`, `merchantId`, `orderId`, `items`,
  `amount`, `currency`, `hash`, `notifyUrl`, `returnUrl`, `cancelUrl`, plus
  buyer name/email/phone/address fields PayHere requires on the form).
  Only meaningful for an `LK` deployment.

### `POST /api/payments/payhere/notify`
- **Auth**: None (PayHere calls this directly; verified by MD5 signature
  inside the handler, not by session). `application/x-www-form-urlencoded`.
  Must be reachable on a public URL — PayHere never calls `localhost`.
  Flips `paymentStatus` to `paid` once verified. **Response**: `200`.

### `POST /api/stores/{storeId}/stripe-connect/onboard`
- **Auth**: Owner (SELLER). Creates the seller's Stripe Connect
  **Standard** account (`country="AU"`, `409` on any other deployment
  country) if one doesn't exist yet, and an Account Link. **Response**:
  `200 { onboardingUrl }` — redirect the seller's browser here.

### `POST /api/stores/{storeId}/stripe-connect/refresh`
- **Auth**: Owner (SELLER). Pulls the connected account's live
  `charges_enabled`/`payouts_enabled` status directly from Stripe and syncs
  it onto `store_settings` — a manual fallback for when the
  `account.updated` webhook is misconfigured, dropped, or hasn't arrived
  yet; safe to call anytime, a no-op if nothing changed. **Response**: `204`.

### `POST /api/orders/{id}/stripe-checkout`
- **Auth**: None (guest-reachable, same credential model as PayHere's
  equivalent). `409` if the order isn't `stripe`, no longer `unpaid`, or
  the store's connected account isn't `stripeChargesEnabled` yet. Creates
  a Stripe Checkout Session **directly on the seller's connected account**
  (a direct charge, not platform-custody-then-transfer) with
  `application_fee_amount` set to the order's `platformFee` — Stripe
  deducts the platform's cut automatically at charge time. **Response**:
  `200 { checkoutUrl }` — redirect the buyer's browser here.

### `POST /api/payments/stripe/webhook`
- **Auth**: None (raw body signature-verified inside the handler against
  Stripe's `Stripe-Signature` header — must be configured in the Stripe
  Dashboard to listen to **connected-account** events, not just the
  platform account's own, or every seller's sale-related events would
  never arrive). Handles `checkout.session.completed` (marks the order
  paid, advances `pending → confirmed`), `checkout.session.expired`/
  `async_payment_failed` (timeline note only, order stays unpaid so a
  fresh session can be created), and `account.updated` (syncs
  `stripeChargesEnabled`/`stripePayoutsEnabled`). Idempotent — a redelivered
  `checkout.session.completed` for an already-paid order is a no-op.
  **Response**: `200`.

---

## Payouts

Read-only for the seller — created and released only via the admin
endpoints. See
[`database-model.md#payout`](database-model.md#payout) for the eligibility
rule (PayHere orders only) and
[`features/payouts.md`](../app/docs/features/payouts.md).

### `GET /api/stores/{storeId}/payouts`
- **Auth**: Owner (SELLER). **Response**: `200 Payout[]`, newest first.

### `GET /api/stores/{storeId}/payouts/eligible-orders`
- **Auth**: Owner (SELLER). PayHere orders `delivered` + `paid` + not
  already in a payout batch. **Response**: `200 Order[]`.

### `POST /api/admin/stores/{storeId}/payouts`
- **Auth**: ADMIN. Bundles every currently-eligible order into one new
  `scheduled` payout. **Response**: `201 Payout`. **Errors**: `409` if
  there are zero eligible orders.

### `GET /api/admin/payouts`
- **Auth**: ADMIN. Every payout, every store. **Response**: `200 Payout[]`.

### `PATCH /api/admin/payouts/{payoutId}`
- **Auth**: ADMIN. **Body**: `{ bankReference?: string }`. Marks
  `status: paid`, sets `paidAt`, records `PAYOUT_MARKED_PAID` to the audit
  log. **Response**: `200 Payout`.

---

## Fee collections

The reverse-direction ledger — COD/bank-transfer orders, where the seller
owes the platform its fee. Mirrors Payouts exactly; see
[`database-model.md#fee_collections-v4`](database-model.md#fee_collections-v4).

- `GET /api/stores/{storeId}/fee-collections` — Owner (SELLER).
- `GET /api/stores/{storeId}/fee-collections/eligible-orders` — Owner
  (SELLER). COD/bank-transfer orders `delivered` + `paid` + not yet
  collected.
- `POST /api/admin/stores/{storeId}/fee-collections` — ADMIN. `201`,
  `409` on zero eligible orders.
- `GET /api/admin/fee-collections` — ADMIN, all stores.
- `PATCH /api/admin/fee-collections/{feeCollectionId}` — ADMIN. **Body**:
  `{ reference?: string }`. Marks `status: collected`, records
  `FEE_COLLECTION_MARKED_COLLECTED`.

---

## Buyer account

### `GET /api/me`
- **Auth**: BUYER. The authenticated buyer's own profile (JIT-provisioned
  on first call if needed). **Response**: `200 Buyer`.

### `PATCH /api/me/default-shipping`
- **Auth**: BUYER. **Body**: full `ShippingDetails` (all fields required —
  this is a real saved-address write, unlike the conditionally-optional
  shape checkout itself accepts). Called automatically after every
  signed-in checkout, but also directly callable. **Response**: `200 Buyer`.

There is no `GET /api/buyers/{id}`/by-email lookup anymore — the buyer's
own identity always comes from their session (`/api/me`), never a
client-supplied id, closing what was previously an IDOR-shaped gap.

---

## Billing (seller Pro plan)

A real Stripe **Subscription** on the platform's own Stripe account (the
platform charging the seller) — entirely separate from Stripe Connect
above (the seller receiving buyer payments). See
[`overview.md`](overview.md) for what Pro unlocks.

### `GET /api/me/seller/plan`
- **Auth**: SELLER. **Response**: `200 { plan, currentPeriodEnd,
  cancelAtPeriodEnd, monthlyPriceCents, currencyCode }` — price/currency
  always read live from `platform_settings`, never hardcoded frontend-side.

### `POST /api/me/seller/billing/checkout`
- **Auth**: SELLER. `409` if already Pro. Creates (or reuses) a Stripe
  Customer for the seller, then a Checkout Session in `subscription` mode.
  **Response**: `200 { checkoutUrl }` — redirect the seller's browser here.

### `POST /api/me/seller/billing/cancel`
- **Auth**: SELLER. Sets `cancel_at_period_end` on the Stripe
  subscription — the seller keeps Pro access through the period already
  paid for, not an immediate downgrade. **Response**: `200 SellerPlan`.

### `POST /api/me/seller/billing/refresh`
- **Auth**: SELLER. Re-syncs plan state directly from Stripe — fallback
  for a misconfigured/dropped webhook, same pattern as the Stripe Connect
  refresh endpoint. **Response**: `200 SellerPlan`.

### `POST /api/billing/stripe/webhook`
- **Auth**: None (signature-verified inside the handler — this Dashboard
  endpoint must listen to the platform account's **own** events, not
  connected-account events, the opposite configuration from the order-
  payment webhook above). Handles `checkout.session.completed` (mode
  `subscription`), `customer.subscription.updated`/`.deleted` — syncs
  `plan`/`stripeSubscriptionId`/`planCurrentPeriodEnd`/
  `planCancelAtPeriodEnd` from the Stripe Subscription object.
  **Response**: `200`.

---

## Admin — store review

### `GET /api/admin/stores`
- **Auth**: ADMIN. **Query**: `status?`. **Response**: `200 Store[]`,
  newest first.

### `GET /api/admin/stores/{storeId}/settings`
- **Auth**: ADMIN. Same shape as the seller-facing settings read, not
  ownership-gated (an admin can review any store). **Response**: `200
  StoreSettings` or `404`.

### `PATCH /api/admin/stores/{storeId}/verification`
- **Auth**: ADMIN. **Body**: `{ status: "active" | "rejected",
  rejectionReason? }`. Sets `verification_status`/`isVerified`; a
  `rejectionReason` is stashed onto `store_settings` (overwritten on each
  rejection — the durable multi-rejection history lives in `audit_logs`
  instead, as `STORE_APPROVED`/`STORE_REJECTED`). Approving makes the
  store immediately visible on every public listing. **Response**: `200 Store`.

## Admin — management, notifications, audit log, accounting

### `POST /api/admin/admins`
- **Auth**: ADMIN. **Body**: `{ name, email, password }`. Creates a
  Cognito user directly in the `admin` group — this is the only path into
  that group besides the out-of-band bootstrap script
  (`infra/scripts/create-admin.sh`); never self-service. **Response**:
  `200 { email, name }` — no local `Admin` row exists yet (JIT-created on
  the invitee's first login).

### `GET /api/admin/admins`
- **Auth**: ADMIN. Sourced live from Cognito (`ListUsersInGroup`), not the
  local `admins` table, so an invited-but-never-logged-in admin still
  shows up. **Response**: `200 { email, name, invitedAt }[]`.

### `GET /api/admin/notifications` / `GET /api/admin/notifications/summary`
- **Auth**: ADMIN. **Response**: `200 AdminNotification[]` / `200
  { unreadCount }`.

### `PATCH /api/admin/notifications/{id}/read` / `PATCH /api/admin/notifications/read-all`
- **Auth**: ADMIN. Not per-admin-scoped — any admin can mark any
  notification read.

### `GET /api/admin/audit-log`
- **Auth**: ADMIN. **Query**: `action?`, `targetType?`, `page?`, `size?`.
  **Response**: `200 PageResponse<AuditLog>` — every recorded admin and
  seller-initiated audit event (see
  [`database-model.md#audit_logs-v9`](database-model.md#audit_logs-v9)).

### `GET /api/admin/accounting/summary`
- **Auth**: ADMIN. Platform-wide money-in-flight snapshot: `{
  payoutsScheduledTotal, payoutsPaidTotal, feeCollectionsPendingTotal,
  feeCollectionsCollectedTotal, stripeSettledTotal,
  stripePlatformFeeTotal }` — all cents.

---

## Platform config, states, and ABN lookup

### `GET /api/platform-config`
- **Auth**: None. The live `platform_settings` row — name, tagline,
  country/currency, fee percent, shipping fee, Pro price, payment-method
  defaults, support contact. The frontend fetches this instead of baking
  country content into `NEXT_PUBLIC_*` build-time env vars, so a
  deployment's platform-level copy/pricing can change without a rebuild.

### `GET /api/states`
- **Auth**: None. This deployment's state/province/district options
  (`states` table), ordered by `sort_order` then name.

### `GET /api/abn-lookup/{abn}`
- **Auth**: None. Checksum-validates the ABN and, if a live ABR API GUID
  is configured (`AbrProperties`), looks it up against the Australian
  Business Register. **Response**: `200 { status: "found" |
  "invalid-format" | "not-found" | "not-configured" | "error",
  entityName?, abnStatus?, entityTypeName?, gstRegistered? }` — used by
  both the onboarding form (self-check, before a session/store exists) and
  `/admin` (review).

---

## Categories

`StoreCategory` remains a fixed, code-level 8-value enum (mirrored
identically in the frontend), not a database-backed or API-exposed list —
see [`database-model.md`](database-model.md#entities-that-still-dont-exist).
Adding a category still requires a deploy on both sides. Not building a
`GET /api/categories` endpoint is a standing, deliberate choice, not an
oversight — revisit only if categories need to change without a deploy.
