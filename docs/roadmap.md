# Roadmap

> Cross-references: [`gaps-and-assumptions.md`](gaps-and-assumptions.md) ·
> [`api-contracts.md`](api-contracts.md) · [`database-model.md`](database-model.md)
> · [`overview.md`](overview.md)

This is a snapshot of what's incomplete **as of the current codebase**
(Spring Boot/Postgres backend + Next.js frontend), organized by priority.
It reflects gaps discovered by reading the code, not a committed product
plan — treat "Must have" as "blocks a real launch," not as scheduled work.

## Must have (blocks any real/production launch)

This section is kept current against the actual backend
(`backend/src/main/kotlin/com/storepilot/backend/`), not the original
`localStorage`-mock frontend the earlier version of this doc described.
Everything below marked **Resolved** was verified against the current
source, not assumed.

- ~~**A real backend and database.**~~ **Resolved** — Spring Boot +
  Postgres, package-by-feature, Flyway-migrated schema. See
  [`frontend-architecture.md`](../app/docs/frontend-architecture.md) for
  what this replaced.
- ~~**Real seller authentication.**~~ **Resolved** — Cognito
  `AdminInitiateAuth` password check in `AuthController.login()`; no more
  "any email signs in as the mock seller."
- ~~**Real seller/store registration.**~~ **Resolved** — a real `Seller`
  JPA entity (keyed by Cognito `sub`) now exists; `GET /api/me/store`
  routes a returning seller to the store they actually own, not a
  hardcoded demo store.
- ~~**Real authentication/authorization for `/admin`.**~~ **Resolved** —
  `/api/admin/**` requires `ROLE_ADMIN` server-side
  (`SecurityConfig.kt`), and `proxy.ts` gates the frontend `/admin/*`
  routes on a verified JWT with the `admin` Cognito group. Admins are
  bootstrapped out-of-band via `infra/scripts/create-admin.sh`, never
  self-registered or promoted from a buyer/seller account.
- ~~**A real credential for buyer accounts.**~~ **Resolved** — buyer
  `/account/login` now does the same Cognito password check as sellers,
  not just an email lookup.
- ~~**Authorization/ownership checks on every seller-scoped resource.**~~
  **Resolved** — `ProductService.requireOwnership()`,
  `StoreService.requireOwnedStore()`, `OrderService.requireSellerOwnsOrder()`
  all verify the signed-in seller's own `Store`/`Seller` id before any
  read/write, consistently across services.
- **Server-side re-validation of all form input.** Mostly done — auth,
  product, and order DTOs use jakarta validation
  (`@NotBlank`/`@Email`/`@Positive`/etc.), and `StoreDtos.kt`'s
  `StoreSettingsInput`/`StoreProfileInput` now validate email/phone/URL/fee
  fields too, with `@Valid` wired up on both `StoreController` PATCH
  endpoints. Remaining gap: none identified as of this pass, but re-check
  any new DTO added later — this list only reflects what's been audited.
- ~~**Server-side stock validation at checkout.**~~ **Resolved** —
  `OrderService.createOrder()` rejects checkout with `409 CONFLICT` if a
  line item's quantity exceeds `stockQuantity`, for any product (and
  store) that has stock management enabled; skipped entirely when either
  is opted out, matching the existing `trackStock`/`stockManagementEnabled`
  toggles. `ProductService.decrementStock()`'s clamp-to-zero is now just a
  defense against a same-product race between two concurrent checkouts,
  not the primary guard. See
  [`features/checkout.md`](../app/docs/features/checkout.md#edge-cases).
- ~~**A real payment gateway integration for PayHere.**~~ **Resolved** —
  `PayHereController.notify()` verifies the MD5 signature and flips
  `paymentStatus` asynchronously via webhook; orders start `UNPAID`, not
  instantly "paid." (Stripe Connect is also fully implemented, beyond
  what this doc originally scoped.)
- ~~**A real order-status state machine enforced server-side.**~~
  **Resolved** — `OrderService.updateStatus()` now checks the target
  status against `ALLOWED_STATUS_TRANSITIONS` (mirroring the frontend's
  `OrderStatusSelect`) and rejects an illegal transition (e.g.
  `pending → delivered`) with `409 CONFLICT`, instead of relying solely on
  which options a dropdown happens to render.
- ~~**Signed/encrypted sessions.**~~ **Resolved** — real Cognito JWTs
  (httpOnly cookies), verified server-side via the OAuth2 resource server
  and edge-verified in `proxy.ts` via `jwtVerify` against Cognito's JWKS.
- **Unique, unguessable order IDs** — order IDs are now UUIDs (no longer
  guessable), but `GET /api/orders/{id}` is still fully unauthenticated by
  design ("possession of the order ID is the credential," per
  `OrderService.kt`'s doc comment) — a deliberate tradeoff, not an
  oversight, but still worth a product decision if buyer PII exposure via
  a leaked/shared order ID becomes a concern.

## Should have (expected of a serious v1, not launch-blocking)

Audited against the current backend/frontend the same way as "Must have"
above — several items below were previously marked open on the strength of
an out-of-date description (e.g. "a URL-paste field" for product images)
and are now confirmed resolved; others are newly fixed as of this pass.

- **MFA for seller and admin accounts** (buyers lower priority). Still not
  implemented — no TOTP/challenge-response code anywhere in
  `AuthController.kt`. Cognito supports this natively (TOTP/authenticator
  app preferred over SMS — no SNS cost, not phishable), but it's a real
  feature, not a config toggle: the current login flow does a direct
  `ADMIN_USER_PASSWORD_AUTH` call that returns tokens immediately; with MFA
  enabled on the User Pool, Cognito instead returns a challenge that needs a
  second `AdminRespondToAuthChallenge` call, plus a new
  enroll/verify-TOTP endpoint and QR-code UI in account settings, plus an
  MFA-code prompt in the login UI. Applies to whichever actor types (buyer/
  seller/admin) share this login path.
- ~~**Buyer accounts... real credential**~~ **Resolved** — buyer
  `/account/login` now does the same Cognito password check as sellers.
  **Still open: a multi-address book** — `Buyer.defaultShipping` is still
  a single embedded field, no `Address` entity/list/CRUD endpoints. See
  [`features/buyer-accounts.md`](../app/docs/features/buyer-accounts.md).
- ~~**Real image upload/storage for products**~~ **Resolved** — this bullet
  described the old mock's URL-paste field, which no longer exists.
  `ProductController`/`ProductService.storeImages()` accept real
  `multipart/form-data` uploads via `FileStorageService`, and the frontend
  product form uses an `<ImageUploader />`, not a text input.
- **Review/rating submission system** — still not implemented. No `Review`
  entity/controller/service anywhere in the backend; `rating`/
  `reviewCount` are still static fields on `Product`/`Store` with no write
  path, only ever set by seed data.
- ~~**Fix the platform-fee source-of-truth inconsistency**~~ **Resolved** —
  `platformFee` now reads the order's store's
  `StoreSettings.transactionFeePercent`. See
  [`gaps-and-assumptions.md`](gaps-and-assumptions.md).
- ~~**Wire `codEnabled`/`onlinePaymentEnabled` into checkout.**~~
  **Resolved** — `checkout-form.tsx` now conditionally renders each
  payment option (COD/online/bank-transfer/Stripe) based on the store's
  actual settings, with an auto-fallback and a disabled-submit guard if
  none are enabled.
- ~~**Reconcile "earnings" definitions**~~ **Resolved** — the dashboard
  overview's "Revenue" stat now sums only `paymentStatus === "paid"`
  orders (same definition the payouts page's "available" figure already
  used), instead of every non-cancelled order regardless of payment
  status. See [`features/payouts.md`](../app/docs/features/payouts.md).
- ~~**Fix the "Active products" stat card**~~ **Resolved** — now filters
  to `status === "active"` before counting, instead of counting every
  product regardless of status.
- ~~**Hide draft products from public storefront/product queries**~~
  **Resolved** — public search (`ProductSpecifications.notDraft()`), the
  public per-store product listing, and direct-by-id lookup
  (`GET /api/products/{id}`) now all exclude/404 a draft product for
  anyone but its owning seller (`ProductService.isOwnedByCurrentSeller`).
  The seller's own product list and edit page are unaffected — they still
  see every status. Verified locally: an anonymous request for a seeded
  draft product 404s from search, the store's public listing, and direct
  ID lookup; an active product is unaffected.
- ~~**Auto-create a default `StoreSettings` row**~~ **Resolved** —
  `updateStoreSettings` is now an upsert (creates a default-filled row if
  missing). 7 of 8 *seed* stores still have no row (untouched mock data,
  not a code gap), but every store created via `/onboarding` gets one.
- ~~**Email capture at checkout**~~ **Implemented** — email is a required
  checkout field. ~~Still open: a real email provider~~ **Resolved** —
  `SesEmailService` is `@Profile("aws")` and `docker-compose.prod.yml`
  sets `SPRING_PROFILES_ACTIVE: aws`, so SES (not just the logging stub)
  is genuinely active in production.
- **Duplicate-SKU validation** within a store — deliberately still not
  implemented (see `Product.kt`'s doc comment); unchanged from before.
- ~~**What happens to a cart when its held product is deleted, or its
  price changes?**~~ **Implemented** — `useCartReconciliation()` now
  re-syncs the cart against live product data on every load. See
  [`features/cart.md`](../app/docs/features/cart.md).

## Nice to have

- **Dark mode toggle.** `next-themes` is installed and dark CSS variables
  exist, but nothing turns dark mode on (no `ThemeProvider`, no UI toggle).
- **Multi-image product galleries** — `Product.images` is already typed as
  an array, but every UI surface only ever shows/edits the first image.
- **A working "follow store" action** — `followerCount` is displayed
  everywhere but there is no way for a buyer to actually follow a store.
- **Real-time order status push** (WebSocket/notification) instead of
  requiring the buyer/seller to reload a page to see a status change.
- **`OrderTimelineEntry.note` usage** — the field and service parameter
  already exist; no UI lets a seller attach a note to a status change.
- **Localization** (Sinhala/Tamil), given the Sri Lanka-specific market.
- **A trend/period comparison** on `StatCard` (`trend`/`trendDirection`
  props exist, unused everywhere).
- **Store follow/unfollow, wishlists, saved searches.**

## Technical debt

- **`localStorage`-as-database coupling.** Deliberate for the demo, but the
  server/`localStorage` split forces most of the app into client
  components — worth revisiting the render strategy once a real database
  removes that constraint (see
  [`frontend-architecture.md`](../app/docs/frontend-architecture.md)).
- **No automated tests found anywhere in the repository** (no `*.test.*`,
  `*.spec.*`, or test runner config observed).
- **No typed API client / no shared query-key constants.** Every page
  hand-writes its own `useQuery`/`useMutation` calls and query keys inline
  (e.g. `["products", "store", storeId]` is duplicated verbatim across at
  least three files) — a shared hooks layer (`useProducts()`,
  `useStoreOrders()`, etc.) would reduce drift risk. See
  [`frontend-architecture.md#hooks`](../app/docs/frontend-architecture.md#hooks).
- **Inconsistent data-fetching patterns for equivalent needs**: the buyer
  order-confirmation page uses raw `useEffect` + `useState` where the
  (functionally identical) seller order-detail page uses `useQuery` for the
  same `getOrderById` call. See
  [`features/order-tracking.md`](../app/docs/features/order-tracking.md#technical-notes).
- **No pagination anywhere.** Every list service accepts an optional
  `limit` that just slices an in-memory array; there's no cursor/offset
  concept, and several pages (dashboard order list, in particular) fetch
  the entire collection and filter client-side.
- **Unused exported components** (`ProductCardSkeleton`,
  `ProductGridSkeleton`, `StoreCardSkeleton` in
  `shared/loading-skeletons.tsx`) — not wired into any `loading.tsx` or
  Suspense boundary today.
- **No error boundaries** (`error.tsx`) on any route — a thrown service
  exception currently surfaces as Next.js's default unstyled error page.

## Future scalability

- **Real search** (full-text/trigram database index, or an external search
  service like Algolia/Elasticsearch/Meilisearch) — current substring
  matching over an in-memory array does not scale past demo-sized catalogs.
- **Pagination/cursoring** on every list endpoint before catalog or order
  volume grows meaningfully.
- ~~**A dedicated payout/settlement ledger**~~ **Implemented** — see
  [`database-model.md`](database-model.md#payout--settlement-now-implemented--see-srctypespayoutts)
  and [`features/payouts.md`](../app/docs/features/payouts.md). Remaining scalability
  work: real bank-transfer integration, scheduled/automated payout runs
  instead of an admin manually clicking "Create payout batch".
- **CDN-backed image storage** once real uploads replace URL-pasting.
- **Background jobs** for payout settlement runs, order-status reminder
  emails/SMS, and stock-alert notifications (the low-stock banner today
  only appears when a seller happens to open the dashboard).
- **Multi-store-per-seller support**, if the product ever needs sellers to
  run more than one storefront (not implied by the current 1:1 design, but
  worth flagging as a scaling question before the `Seller`/`Store`
  relationship is hard-coded 1:1 in a real schema).
- **Admin/back-office role and tooling** — a *mock* version now exists
  (`/admin`: store approval, payout runs — see
  [`features/seller-auth.md#admin-not-a-real-role`](../app/docs/features/seller-auth.md#admin-not-a-real-role)),
  but it has no auth and covers only two of many likely back-office needs
  (disputes and category curation are still entirely unaddressed).
