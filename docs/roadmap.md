# Roadmap

> Cross-references: [`gaps-and-assumptions.md`](gaps-and-assumptions.md) ·
> [`api-contracts.md`](api-contracts.md) · [`database-model.md`](database-model.md)
> · [`overview.md`](overview.md)

This is a snapshot of what's incomplete **as of the current frontend
codebase**, organized by priority. It reflects gaps discovered by reading
the code, not a committed product plan — treat "Must have" as "blocks a
real launch," not as scheduled work.

## Must have (blocks any real/production launch)

- **A real backend and database.** Everything today is `localStorage`
  pretending to be a server (see
  [`frontend-architecture.md`](../app/docs/frontend-architecture.md)). No data survives
  a cleared browser or is shared across devices/users.
- **Real seller authentication.** Any email signs in as the one mock
  seller; there is no password/credential check and no user table at all.
  See [`features/seller-auth.md`](../app/docs/features/seller-auth.md).
- ~~**Real seller/store registration.**~~ **Partially resolved** —
  `/onboarding` now creates a real `Store` + `StoreSettings` row in
  `"pending"` verification status. Still missing: a `User`/`Seller`
  entity, so `/login` still can't route a returning email back to the
  store it created. See
  [`api-contracts.md#post-apiauthregister`](api-contracts.md#post-apiauthregister).
- **Real authentication/authorization for `/admin`.** The new mock admin
  tool (store approval, payout release) has **zero** auth today — see
  [`gaps-and-assumptions.md`](gaps-and-assumptions.md#admin-has-no-authentication-or-authorization-at-all).
- **A real credential for buyer accounts.** `/account/login` does a real
  email lookup (unlike seller `/login`) but still has no password/OTP —
  knowing an email is sufficient to sign in as that buyer. See
  [`features/buyer-accounts.md`](../app/docs/features/buyer-accounts.md).
- **Authorization/ownership checks on every seller-scoped resource**
  (products, orders, store settings). Nothing today verifies a signed-in
  seller's `storeId` matches the resource being read or written — invisible
  with one seller, a critical security hole with more than one. See
  [`api-contracts.md#authorization`](api-contracts.md#authorization).
- **Server-side re-validation of all form input.** Every zod schema in the
  frontend is client-side only; a real API must not trust it.
- **Server-side stock validation at checkout.** Orders can currently be
  placed for more units than are in stock; the mock just clamps to zero
  instead of rejecting. See [`features/checkout.md`](../app/docs/features/checkout.md#edge-cases).
- **A real payment gateway integration for PayHere**, including
  webhook-driven `paymentStatus` updates. The mock marks `payhere` orders
  "paid" instantly with no gateway interaction at all.
- **A real order-status state machine enforced server-side**, not just by
  which options a dropdown happens to render.
- **Signed/encrypted sessions**, replacing the current unsigned base64
  cookie.
- **Unique, unguessable order IDs** (the public order-confirmation/tracking
  endpoint has no auth — see
  [`api-contracts.md#get-apiordersid`](api-contracts.md#get-apiordersid)).

## Should have (expected of a serious v1, not launch-blocking)

- ~~**Buyer accounts**~~ **Implemented** — optional register/sign-in, one
  saved address, order history, guest checkout still fully supported. See
  [`features/buyer-accounts.md`](../app/docs/features/buyer-accounts.md). Still open:
  a real credential (no password today) and a multi-address book.
- **Real image upload/storage** for products (currently a URL-paste field
  — explicitly marked as a placeholder in source).
- **Review/rating submission system** — `rating`/`reviewCount` are static
  display numbers today with no backing `Review` entity or submission UI.
- ~~**Fix the platform-fee source-of-truth inconsistency**~~ **Resolved** —
  `platformFeeLkr` now reads the order's store's
  `StoreSettings.transactionFeePercent`. See
  [`gaps-and-assumptions.md`](gaps-and-assumptions.md).
- **Wire `codEnabled`/`onlinePaymentEnabled` into checkout.** Sellers can
  toggle these in settings today, but the checkout page always offers both
  payment methods regardless.
- **Reconcile "earnings" definitions** between the dashboard overview
  (all non-cancelled orders) and the payouts page (only paid orders) — see
  [`features/payouts.md`](../app/docs/features/payouts.md).
- **Fix the "Active products" stat card** — currently counts all
  statuses (including drafts/out-of-stock), not just active ones.
- **Hide draft products from public storefront/product queries** —
  currently visible to anyone with the URL.
- ~~**Auto-create a default `StoreSettings` row**~~ **Resolved** —
  `updateStoreSettings` is now an upsert (creates a default-filled row if
  missing). 7 of 8 *seed* stores still have no row (untouched mock data,
  not a code gap), but every store created via `/onboarding` gets one.
- ~~**Email capture at checkout**~~ **Implemented** — email is now a
  required checkout field, and a mock receipt is "sent" (logged, not
  actually delivered) on order creation. Still open: a real email provider
  — see [`gaps-and-assumptions.md`](gaps-and-assumptions.md).
- **Duplicate-SKU validation** within a store.
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
