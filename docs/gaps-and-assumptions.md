# Gaps, Inconsistencies & Undocumented Assumptions

> Cross-references: every other file in `docs/`. This document exists
> because the task of documenting this codebase surfaced findings that
> don't belong inside any single feature doc — either because they're
> cross-cutting, or because they require a **product decision**, not just
> a documentation note. Read this alongside
> [`roadmap.md`](roadmap.md) (which prioritizes the same findings into
> must/should/nice/tech-debt) before starting backend work.

Each finding below states: what was found, why it matters, and where it's
discussed in more depth.

## Security / authorization gaps

### No ownership checks anywhere in the service layer
`updateProduct`, `deleteProduct`, `getProductById`, `getOrderById`,
`updateOrderStatus`, `getStoreSettings`, `updateStoreSettings` all operate
on a bare `id`/`storeId` parameter with **no verification** that it belongs
to the currently signed-in seller. This is invisible today because the
mock data has exactly one seller and the frontend always passes
`CURRENT_SELLER_STORE_ID`. **This must be added in any real backend** — see
[`api-contracts.md#authorization`](api-contracts.md#authorization) and
[`features/seller-auth.md#permissions`](../app/docs/features/seller-auth.md#permissions).

### Order confirmation/tracking has no authentication at all
`GET /api/orders/:id` is used by both an anonymous buyer (right after
checkout) and, unchanged, by the authenticated seller dashboard. The order
contains full buyer PII (name, phone, address). Anyone who obtains an order
ID can view it. Mitigations to decide on: unguessable IDs (UUID, not
sequential/timestamp-based), and/or requiring the seller-side read to
additionally check ownership even though the buyer-side read has no
session to check. See
[`api-contracts.md#get-apiordersid`](api-contracts.md#get-apiordersid).

### `/admin` has no authentication or authorization at all — RESOLVED
This described the pre-Cognito, `localStorage`-mock era of this codebase.
Since the real Spring Boot/Cognito backend landed, `/admin/**` is gated
both client-side (`proxy.ts` checks the `admin` Cognito group, redirecting
to `/admin/login` otherwise) and server-side (`SecurityConfig.kt`'s
`.requestMatchers("/api/admin/**").hasRole("ADMIN")`). Admin accounts are
never self-registered — the first one is bootstrapped out-of-band via
`infra/scripts/create-admin.sh`, and existing admins can invite further
ones in-app (`POST /api/admin/admins`, see `AdminManagementService.kt`).
Every store-verification decision, admin invite, and payout/fee-collection
settlement is now recorded in a durable `audit_logs` table (see
`AuditLogService.kt`), viewable at `/admin/audit-log`.

### `mockDb` has no schema-migration story
`src/lib/mock-db.ts` seeds a `localStorage` key once, the first time it's
read, and never re-seeds or migrates it afterward. When a seed object's
shape changes during development (e.g. `Store` gaining
`verificationStatus` while building the payout/verification feature), a
browser with an **older** cached collection silently keeps
`undefined`/missing fields for anything new, until that key is manually
cleared. Encountered directly while testing this feature (a stale cached
`Store` without `verificationStatus` was incorrectly filtered out of every
public listing). Not a bug in any single feature — a structural limitation
of the mock persistence layer worth fixing (e.g. a version stamp +
reseed-on-mismatch) if its lifetime extends much further.

### Product visibility doesn't check the owning store's verification status
`listStores`/`getStoreBySlug` correctly filter to `verificationStatus ===
"active"`, but `productsService` (search, featured, product-detail lookups)
has no equivalent check against the owning store. Low practical impact
today (a newly-`"pending"` store has zero products at signup time), but a
real backend should filter products by owning-store status too. See
[`database-model.md#product`](database-model.md#product).

### ~~Buyer accounts have no password — email alone is the credential~~ RESOLVED
This described the pre-Cognito, `localStorage`-mock era. Since the real
Spring Boot/Cognito backend landed, `AuthController.register()` requires a
real `password` for `accountType == "buyer"` (provisioned via Cognito's
`AdminSetUserPassword(permanent=true)`), and `login()` authenticates via
`adminInitiateAuth` with `USERNAME`/`PASSWORD` — the same mechanism seller
accounts use. `BuyerController` now only exposes a read-only `GET /api/me`;
there is no email-only sign-in path left anywhere.

### ~~Session cookie is unsigned and unencrypted~~ RESOLVED
This described the pre-Cognito, `localStorage`-mock era
(`src/lib/session.ts`, deleted in the same commit that landed real
Google/Cognito login). The real backend sets `storepilot_access_token`/
`storepilot_refresh_token` as `httpOnly` + `secure` + `SameSite=Lax`
cookies (`AuthController.kt`'s `setAuthCookies`), carrying a genuine
Cognito-issued, cryptographically signed JWT — not an opaque/forgeable
value. `CookieBearerTokenResolver` feeds this straight into Spring
Security's standard JWT signature validation. One mechanism, shared
identically by buyer, seller, and admin auth — no weaker path exists for
any of the three.

### ~~Order lookup uses a weak "credential" (last 9 phone digits)~~ RESOLVED
Replaced with a real two-step, emailed one-time-code flow
(`GuestLookupOtpService`): a 6-digit code, SHA-256 hashed at rest, 10-minute
TTL, 5-attempt lockout, required before `OrderService`/`BookingService`
reveal anything (`POST /api/orders/lookup/request-code` then
`POST /api/orders/lookup/verify` — same shape for bookings). See
[`features/order-tracking.md`](../app/docs/features/order-tracking.md#business-rules).

### ~~No data subject access/export/delete mechanism~~ RESOLVED
Neither buyers nor sellers had a way to see, export, or delete what
StorePilot holds about them — a real gap under Australian Privacy Act
APP 12 (access)/APP 13 (correction) and Sri Lanka's PDPA, which
additionally grants an explicit erasure right. Both sides now have this,
self-service and instant (no admin review):

- **Buyers**: `GET /api/me/export` (a full JSON bundle of profile,
  addresses, orders, bookings, reviews, conversations, saved searches,
  wishlist, follows) and `POST /api/me/delete` (`BuyerAccountService`).
- **Sellers**: `GET /api/me/seller/export` and `POST /api/me/seller/delete`
  (`SellerAccountService`) — gated behind a new, separate
  `POST /api/stores/{storeId}/close` step (`StoreService.closeStore`),
  since a seller can't be deleted while a store still has orders/bookings
  in flight or fees/payouts owed either direction.

**Agreed policy**: "delete" never destroys financial/order/booking
history — those rows are anonymized in place (personal fields redacted,
the transaction itself kept for tax/accounting retention) — while
everything with no independent retention need (addresses, saved searches,
wishlist, follows, seller verification documents) is genuinely deleted,
including the Cognito identity itself. Full design:
[`api-contracts.md`](api-contracts.md#buyer-account) and
[`api-contracts.md`](api-contracts.md#seller-account).

### ~~No stock re-validation at checkout~~ RESOLVED
`OrderService.createOrder` now rejects checkout with a 409 if any line
item's quantity exceeds `stockQuantity`, for products (and stores) that
have stock management enabled — skipped entirely when either the product
or its store has opted out. `ProductService.decrementStock`'s clamp-to-zero
now only matters as a defense against a same-product race between two
concurrent checkouts, not as the primary overselling guard. See
[`features/checkout.md#edge-cases`](../app/docs/features/checkout.md#edge-cases).

### Return refunds for COD/bank-transfer are seller self-attested, with no platform verification
`ReturnRequestService.markRefundedBySeller` lets the seller mark a
return `refunded` on their own word — a `refundReference` is recorded but
never checked against anything. A dishonest seller could mark a return
refunded without actually paying the buyer back, and the platform has no
way to detect it. This is a deliberate design choice, not an oversight:
it mirrors the exact trust model `verifyBankTransfer` already accepts on
the *inbound* side (a seller could verify a bank-transfer receipt they
never actually checked) — so it's consistent with this codebase's
existing risk posture for COD/bank-transfer money, not a new hole. Flag
before launch if stronger guarantees (e.g. requiring the buyer to confirm
receipt) are needed. See
[`api-contracts.md#returns--refunds`](api-contracts.md#returns--refunds).

### No line-item-removal/clawback mechanism for Payout/FeeCollection batches
If a return is approved for an order that's already inside a *settled*
(`paid`/`collected`) `payouts`/`fee_collections` batch, real money has
already changed hands between the platform and the seller for that
order — and there is no code path anywhere to remove a line item from an
existing batch, scheduled or paid. `ReturnRequestService` surfaces this
as a `settlementReconciliationNote` visible to admins (see
[`database-model.md#return_requests`](database-model.md#return_requests)),
but reconciling the overlap (e.g. adjusting the seller's next payout by
hand) is entirely manual. Worth a real batch-adjustment feature if
returns on already-settled orders turn out to be common in practice.

### No buyer ABN/business-name capture for tax invoices ≥ A$1,000
The ATO requires a compliant tax invoice for a sale of A$1,000 or more to
additionally show the *buyer's* identity or ABN, not just the seller's.
Checkout never asks for this, and `Order`/`OrderResponse` have no field for
it — deliberately deferred (see [`api-contracts.md`](api-contracts.md)'s
tax-invoice fields, `sellerAbn`/`gstAmount`, which cover every sale under
that threshold). Low practical impact today given this catalog's price
points, but worth adding — likely an optional "buying for a business?"
field at checkout — if B2B-sized orders become common.

## Data-model / business-logic inconsistencies

### ~~Platform fee percent is displayed per-store but computed globally~~ RESOLVED
`orders.service.ts#createOrder` now reads the order's store's
`StoreSettings.transactionFeePercent` and uses it to compute
`platformFeeLkr`, falling back to the global `PLATFORM_FEE_PERCENT`
constant only when the store has no settings row. There still isn't a
settings-form field to *edit* `transactionFeePercent` (it's set once, at
onboarding, from a fixed default — see `updateStoreSettings`'s upsert
defaults) — that remains a real gap if per-store fee negotiation is ever a
product requirement, but the core inconsistency (computed vs. displayed
using different values) is fixed. See
[`overview.md#monetization-model`](overview.md#monetization-model),
[`features/payouts.md`](../app/docs/features/payouts.md).

### "Active products" stat counts every status
The dashboard overview's "Active products" stat card is `products.length`
with no status filter — it includes drafts and out-of-stock items. See
[`features/seller-dashboard-overview.md`](../app/docs/features/seller-dashboard-overview.md#business-rules).

### Two different definitions of "earnings" across two pages
Dashboard overview sums fees/revenue over all **non-cancelled** orders
(regardless of payment status); Payouts sums only over **paid**,
non-cancelled orders. A store with pending/unpaid COD orders will see
different numbers on the two pages with no explanation of why. See
[`features/payouts.md`](../app/docs/features/payouts.md#business-rules).

### Draft products are publicly visible
`listProductsByStore` (used for both the public storefront grid and the
seller's own product list) applies no `status` filter — a `"draft"`
product is fully visible on the public storefront today. Likely
unintended, but not code-commented as a bug. See
[`features/store-and-product-detail.md#permissions`](../app/docs/features/store-and-product-detail.md#permissions).

### Shipping fee's ultimate disposition is unmodeled
Buyers pay a flat 350 LKR shipping fee; the seller order-detail page labels
it "(buyer paid)" and excludes it from the seller's payout math — but
nothing in the system models who actually receives it (seller? courier
partner? platform?). Needs an explicit business decision before real money
moves.

## Missing entities (see `database-model.md` for full detail)

- **Seller/User** — no persisted account record; session `email` is never
  validated against or stored in anything (still true for `/login`; see
  below — `/onboarding` at least creates a real `Store` now).
- **Review** — `rating`/`reviewCount` are static numbers with no
  submission flow or backing records.
- ~~**Payout/Settlement**~~ — **implemented.** `src/types/payout.ts` +
  `payouts.service.ts` now provide a real ledger, created/released only via
  `/admin`. See [`features/payouts.md`](../app/docs/features/payouts.md).
- ~~**Buyer/Customer account**~~ — **implemented.** `src/types/buyer.ts` +
  `buyers.service.ts` provide a real `Buyer` record (name, email, phone,
  one saved `defaultShipping` address), a mock session (`role: "buyer"`),
  and `/account/register`, `/account/login`, `/account`. Guest checkout
  still works and remains the default — signing in is optional. Order
  history is scoped by the new `Order.buyerId` field, only set when the
  buyer was signed in at checkout. See
  [`features/buyer-accounts.md`](../app/docs/features/buyer-accounts.md). Same caveat
  as seller auth: no password, just an email lookup — see
  [Security / authorization gaps](#security--authorization-gaps) below.

## Undocumented / unclear assumptions worth confirming with product

- ~~**Onboarding is cosmetic.**~~ **Resolved** — `/onboarding` now creates a
  real, distinct `Store` + `StoreSettings` row (in `"pending"` verification
  status) and signs the new session into it, instead of discarding the form
  and reusing the demo seller. See
  [`features/seller-auth.md`](../app/docs/features/seller-auth.md). `/login` is
  unchanged and still has no email→store lookup — see the next point.
- **One seller, one store, per *session* — but multiple stores can now
  exist.** `/onboarding` can create any number of distinct stores (each
  browser/localStorage accumulates them). The remaining gap: `/login` still
  has no way to know which store a given email belongs to (no `User`
  entity — see [Missing entities](#missing-entities-see-database-modelmd-for-full-detail)
  above), so signing in via `/login` always lands on the hardcoded demo
  store regardless of how many stores exist. A seller who onboards, signs
  out, then tries to sign back in via `/login` will **not** return to their
  own store today.
- **`next-themes` is installed but completely unwired.** No
  `ThemeProvider`, no toggle control, yet dark-mode CSS variables exist in
  `globals.css`. Either finish the feature or remove the dependency —
  currently it's neither.
- **Categories are hardcoded, not backend data.** `StoreCategory` is a
  fixed 8-value TypeScript union with static labels/icons. Confirm whether
  categories should ever be added/removed without a frontend deploy — if
  so, they need to become a real backend-managed list.
- **No email is collected from buyers.** Checkout only asks for name,
  phone, and address — there's no channel for an email receipt or
  notification. Confirm this is intentional (SMS/WhatsApp-first market) and
  not an oversight.
- **`OrderTimelineEntry.note` is fully supported by the type and service
  signature but never surfaced in any UI.** Either it's a half-built
  feature or intentionally deferred — worth a product call before building
  a "reason for status change" UI from scratch vs. just wiring up what
  already exists.

## Unclear workflows

- **What happens when a seller's product edit invalidates in-flight
  orders?** (e.g. seller drops the price after an order was placed at the
  old price.) Current design already protects against this correctly
  (`OrderItem` snapshots price/name at purchase time) — but this protection
  is implicit in the data model, not documented anywhere in-source. Now
  documented in [`database-model.md#orderitem-embedded`](database-model.md#orderitem-embedded)
  — preserve this behavior in any reimplementation.
- ~~**What happens to a cart when its held product is deleted by the
  seller?**~~ **Resolved.** `useCartReconciliation()`
  (`src/hooks/use-cart-reconciliation.ts`) re-fetches every cart line's
  product on load (cart page, cart drawer, checkout) and flags a
  now-missing product `isUnavailable: true` on the `CartItem` rather than
  silently dropping it — the buyer sees it greyed out with a "No longer
  available" badge and must remove it themselves. Unavailable items are
  excluded from `cartSubtotal`/`cartItemCount`, and checkout's submit
  button is disabled while any remain. See
  [`features/cart.md`](../app/docs/features/cart.md).
- ~~**What happens if a seller edits a product's price while it's sitting in
  a buyer's cart?**~~ **Resolved** for the *displayed* price — the same
  `useCartReconciliation()` sync also refreshes `unitPriceLkr`/
  `stockQuantity` from the current product record, so the cart/checkout UI
  no longer shows a stale price. `createOrder` already re-read the current
  price server-side for the final charge; the two now agree before the
  buyer ever submits, instead of only agreeing after the fact. Buyers are
  not shown an explicit "price changed" notice distinct from just seeing
  the updated number — a lighter-weight choice than a dedicated banner,
  revisit if that turns out to be confusing in practice.

## How to use this document

- When implementing a backend endpoint from [`api-contracts.md`](api-contracts.md),
  check here first for any "must re-enforce" or "must decide" note relevant
  to that endpoint.
- When a future Claude Code session is asked to "just wire up the real
  API," this document is the list of behavioral changes that are **not**
  optional 1:1 ports of current mock behavior — they're bugs/gaps that
  should not be carried into production.
- If a finding here turns out to be intentional (confirmed with product),
  update this document to say so explicitly, with the reasoning, rather
  than deleting the entry — future readers should be able to tell "known
  gap, not yet decided" apart from "confirmed intentional, here's why."
