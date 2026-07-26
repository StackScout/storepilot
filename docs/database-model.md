# Database Model (Inferred)

> Cross-references: [`api-contracts.md`](api-contracts.md) ·
> [`gaps-and-assumptions.md`](gaps-and-assumptions.md) ·
> [`frontend-architecture.md`](../app/docs/frontend-architecture.md)

No SQL here by design — this is the domain model implied by
`src/types/*.ts` and the actual read/write behavior in
`src/services/*.service.ts`, annotated with what's **missing** for a real
multi-tenant backend. Field lists mirror the TypeScript types exactly;
"suggested indexes" and "constraints" are this document's own
recommendations, not present in the frontend.

## Entity summary

| Entity | Exists in frontend types today? | Notes |
|---|---|---|
| Store | ✅ `src/types/store.ts` | |
| StoreSettings | ✅ `src/types/store.ts` | 1:1 with Store, but currently split from it — see [Store ↔ StoreSettings](#store--storesettings-relationship) |
| Product | ✅ `src/types/product.ts` | |
| ProductImage | ✅ (embedded array on Product) | Type supports multiple, UI only manages one — see product-management doc |
| Order | ✅ `src/types/order.ts` | |
| OrderItem | ✅ (embedded array on Order) | Immutable snapshot, not a live FK to Product |
| OrderTimelineEntry | ✅ (embedded array on Order) | Append-only |
| Category | ⚠️ static config only (`src/mock/categories.ts`) | Not a queryable/dynamic entity today — see api-contracts.md#categories |
| **Seller / User** | ❌ **missing** | Store creation is now real (see `Store`/`StoreSettings` below), but there's still no persisted account/credential entity — see below |
| **Review** | ❌ **missing** | `rating`/`reviewCount` are static aggregate numbers with no backing review records |
| **Payout / Settlement** | ✅ `src/types/payout.ts` | Real ledger entity now — created/released only via `/admin`, see [`features/payouts.md`](../app/docs/features/payouts.md) |
| **Buyer** | ✅ `src/types/buyer.ts` | Real account entity now — name, email, phone, one saved `defaultShipping` address. No password (see [`gaps-and-assumptions.md`](gaps-and-assumptions.md)). See [`features/buyer-accounts.md`](../app/docs/features/buyer-accounts.md) |
| **Cart** | ❌ **missing (by design)** | Cart is 100% client-local (Zustand + localStorage); only its *contents* reach the backend, as `POST /api/orders` input |

---

## Store

Represents one seller's public storefront profile.

**Fields**
| Field | Type | Notes |
|---|---|---|
| `id` | string | PK |
| `slug` | string | Unique, used in public URLs (`/stores/[slug]`) |
| `name` | string | |
| `tagline` | string | |
| `description` | string | |
| `logoUrl` | string | |
| `bannerUrl` | string | |
| `category` | `StoreCategory` enum | fashion / food-beverage / beauty / handicrafts / electronics / home-living / jewelry / grocery |
| `address` | object | `{ city, district, province }` — embedded, not a separate Address entity |
| `whatsappNumber` | string | Used to build `wa.me` links; **not validated** as a real phone number anywhere |
| `rating` | number | Aggregate — **no backing Review records today**, see [Review](#review-missing-entity) |
| `reviewCount` | number | Same caveat |
| `productCount` | number | Displayed on `StoreCard`; not verified to match the actual live product count — likely meant to be a denormalized counter, kept in sync at write time in a real backend |
| `isVerified` | boolean | No verification workflow exists to set this — currently just a static mock flag |
| `joinedAt` | string (ISO date) | |
| `followerCount` | number | Displayed; **no "follow" action exists anywhere in the UI** — this is a display-only number with no way for a buyer to actually follow a store |
| `verificationStatus` | `"pending" \| "active" \| "rejected"` | **New.** Gates every public read (`listStores`, `getStoreBySlug` filter to `"active"` only). Set to `"pending"` at creation (`storesService.createStore`), flipped by admin via `setStoreVerificationStatus` — never by the seller. All 8 seed stores are seeded `"active"`. |

**Suggested indexes**: unique on `slug`; index on `category` (category
browse/filter); index on `verificationStatus` (admin's pending-applications
list, and every public list/detail query's filter); consider a search index
(full-text or trigram) on `name`/`tagline`/`address.city` for
`GET /api/stores?query=`.

**Business constraints**
- `slug` must be unique and URL-safe.
- Exactly one `Store` per seller in the current product design (no
  multi-store-per-seller UI exists) — confirm with product before assuming
  this must remain 1:1 forever.
- A `Store` is only publicly visible/orderable when `verificationStatus ===
  "active"`. Note the enforcement is **inconsistent by design's own
  admission today**: `listStores`/`getStoreBySlug` filter correctly, but
  `productsService` (search, featured, product-detail lookups) does **not**
  check the owning store's `verificationStatus` at all — a pending store's
  individual products could theoretically surface via a product-level query
  even though the store itself won't. Low practical risk (a brand-new store
  has zero products at signup time), but a real backend should filter
  products by owning-store status too, not just stores by their own status.

## StoreSettings

Private, seller-only configuration — separated from `Store` presumably so
public store reads never risk exposing bank details.

**Fields**
| Field | Type | Notes |
|---|---|---|
| `storeId` | string | FK → Store, effectively the PK (1:1) |
| `contactEmail` | string | |
| `contactPhone` | string | |
| `bankAccountName` | string | |
| `bankAccountNumber` | string | **Sensitive** — consider encryption at rest / masked display, not currently masked anywhere in the UI |
| `bankName` | string | |
| `transactionFeePercent` | number | **Now the actual rate used** by `orders.service.ts#createOrder` (falls back to the global `PLATFORM_FEE_PERCENT` constant if this row/field is absent) — previously computed but ignored, see [`features/payouts.md`](../app/docs/features/payouts.md) |
| `codEnabled` | boolean | Enforced at checkout — hides the "cod" option when false. At least one of `codEnabled`/`onlinePaymentEnabled`/`bankTransferEnabled` must always be true (validated client- and server-side, 409 otherwise). |
| `onlinePaymentEnabled` | boolean | Same, for the `"payhere"` option. |
| `bankTransferEnabled` | boolean | Same, for the `"bank-transfer"` option. **Opt-in, defaults `false`** (unlike the other two) — shows `bankName`/`bankAccountName`/`bankAccountNumber` to buyers at checkout, which the seller should consciously enable rather than have switch on silently just because those fields were already filled in for payouts. |
| `sellerType` | `"individual" \| "business"` | **New.** Collected at onboarding, reviewed by admin before approval. |
| `nicNumber` | string | **New, sensitive.** Always required. Displayed in full on `/admin` for review — not masked anywhere; a real backend should mask/restrict this. |
| `businessRegistrationNumber` | string, optional | **New.** Required (at the form level) only when `sellerType === "business"`; absent for individual sellers. |
| `rejectionReason` | string, optional | **New.** Set by admin when `Store.verificationStatus` is set to `"rejected"`; shown to the seller via the dashboard's `PendingVerificationBanner`. |

**Suggested indexes**: unique on `storeId` (1:1 enforcement).

**Business constraints**
- Every `Store` should have exactly one `StoreSettings` row. **Now
  guaranteed for stores created via `/onboarding`** (it always calls
  `updateStoreSettings` right after `createStore`) — but still **not**
  guaranteed for the 8 seed stores (`MOCK_STORE_SETTINGS` only has an entry
  for `store-01`; the other 7 have none). `updateStoreSettings` is now an
  **upsert** (creates a default-filled row if missing, instead of
  throwing — this was previously a real gap, now resolved), so calling it
  is always safe regardless of whether a row exists yet.

## Store ↔ StoreSettings relationship

1:1, but modeled as two separate reads/writes throughout the frontend
(`getStoreBySlug`/`getStoreById` vs. `getStoreSettings`/
`updateStoreSettings`). Preserve this split in a real backend — it maps
cleanly onto "public store profile" vs. "private seller-only config" and
supports different read-permission rules (public vs. owner-only) for each.

## Product

**Fields**
| Field | Type | Notes |
|---|---|---|
| `id` | string | PK |
| `storeId` | string | FK → Store |
| `storeName`, `storeSlug` | string | **Denormalized** from Store onto Product for cheap rendering — must be kept in sync if a store's name/slug ever changes (no such update path exists today, so this has never been exercised) |
| `name` | string | |
| `slug` | string | **Unique only within a store**, not globally — product lookups always key on `(storeSlug, productSlug)` or `(storeId, ...)`, never `slug` alone |
| `description` | string | |
| `images` | `ProductImage[]` | `{ id, url, alt }`; only the first image is ever rendered/edited anywhere in the current UI |
| `category` | `StoreCategory` enum | Same enum as Store's category — a product's category is independently chosen, not inherited from its store |
| `priceLkr` | number (integer LKR) | |
| `compareAtPriceLkr` | number, optional | "Was" price for showing a discount; only rendered as a strikethrough when strictly greater than `priceLkr` |
| `stockQuantity` | number (integer, ≥ 0) | |
| `status` | `"active" \| "draft" \| "out-of-stock"` | **Auto-forced to `"out-of-stock"` whenever `stockQuantity === 0`**, enforced in the service layer at create/update time, not a DB constraint today — recommend a DB trigger/check or enforce solely in the application layer, but pick one and be consistent |
| `sku` | string | **No uniqueness enforced** — two products in the same store can share a SKU today |
| `rating`, `reviewCount` | number | Same "no backing Review entity" caveat as Store; new products always start at `0`/`0` and nothing ever increments them |
| `createdAt`, `updatedAt` | string (ISO datetime) | |

**Suggested indexes**: `(storeId)`; unique composite `(storeId, slug)`;
`(category)`; `(status)`; `(createdAt)` for the `newest` sort; a search
index on `name`/`description` for `GET /api/products?query=`; consider
`(storeId, sku)` unique if SKU uniqueness-per-store becomes a real
requirement (not enforced today).

**Business constraints**
- `stockQuantity >= 0` always.
- `status = "out-of-stock"` whenever `stockQuantity = 0` (see above).
- A product belongs to exactly one store; never reassigned to a different
  store.
- Deleting a product must **not** cascade-delete or invalidate any
  `OrderItem` that references it — `OrderItem` is a snapshot, not a live
  FK (see below). This is already correctly modeled in the frontend's
  mental model and must be preserved.

## Order

**Fields**
| Field | Type | Notes |
|---|---|---|
| `id` | string | PK. **Currently used as a bare, guessable-format ID
  (`order-<timestamp>`) and exposed in public URLs with no auth check** —
  a real backend should use a non-enumerable ID (UUID) here, see
  [`api-contracts.md`](api-contracts.md#get-apiordersid) |
| `orderNumber` | string | Human-facing, format `SL-YYYYMMDD-####` — **must be enforced unique server-side**; current mock generates it with `Math.random()` and never checks for collisions |
| `storeId`, `storeName`, `storeSlug` | string | Denormalized from the (single) store this order belongs to — **an order can only belong to one store**, enforced client-side by the cart, must be re-enforced server-side |
| `items` | `OrderItem[]` | See below |
| `subtotalLkr` | number | Σ `unitPriceLkr × quantity` at order-creation time |
| `shippingFeeLkr` | number | Flat `350` today, not store/region/weight-dependent |
| `platformFeeLkr` | number | Computed at creation from a fee percentage — **see the rate-source inconsistency** in [`gaps-and-assumptions.md`](gaps-and-assumptions.md) |
| `totalLkr` | number | `subtotalLkr + shippingFeeLkr` — **fee is not included in what the buyer pays** |
| `status` | `OrderStatus` enum | `pending \| confirmed \| shipped \| delivered \| cancelled` — transitions constrained by a state machine that today lives **only in frontend UI code**, not the service/database layer (see order-management doc) |
| `paymentMethod` | `"payhere" \| "cod" \| "bank-transfer"` | |
| `paymentStatus` | `"unpaid" \| "paid" \| "refunded"` | `payhere` flips to `"paid"` via the PayHere notify webhook; `bank-transfer` flips via seller confirmation (`POST /api/orders/:id/verify-bank-transfer`); `cod` flips on delivery. All three start `"unpaid"`. |
| `receiptUrl` | string, optional | Set once the buyer uploads a bank-transfer proof-of-payment (`POST /api/orders/:id/receipt`) — backend-relative path under `/uploads/receipts/`. Only ever present for `paymentMethod === "bank-transfer"`. |
| `lastReminderSentAt` | datetime, optional | Backend-only — **not exposed in `OrderResponse`**. Set by `ReceiptReminderJob` each time it emails a bank-transfer buyer about a missing receipt; `null` means never reminded. See [`features/notification-emails.md`](../app/docs/features/notification-emails.md). |
| `shipping` | `ShippingDetails` | Embedded, not a reusable Address entity — see below |
| `timeline` | `OrderTimelineEntry[]` | Append-only audit log |
| `createdAt` | string (ISO datetime) | |
| `buyerEmail` | string | Where order-lifecycle emails are sent — collected at checkout for every order, guest or not (see [`features/notification-emails.md`](../app/docs/features/notification-emails.md)) |
| `buyerId` | string, optional | FK to `Buyer` — set only when the buyer was signed into a buyer account at checkout; absent on guest orders |

**Suggested indexes**: `(storeId)`; unique on `orderNumber`; `(status)`;
`(createdAt)`; a composite index or search strategy for the phone-based
lookup (`GET /api/orders/lookup`) — note the current matching rule (last 9
digits of phone) is unusual and doesn't map cleanly onto a simple equality
index; recommend normalizing phone numbers to a canonical format at write
time (e.g. E.164) to make this lookup indexable and precise instead of a
suffix scan.

**Business constraints**
- All `items` in an order must belong to the same `storeId` (single-store
  cart/order rule) — **must be validated server-side**, not just assumed
  from a trusting client.
- `status` transitions must follow the state machine documented in
  [`api-contracts.md`](api-contracts.md#patch-apiordersidstatus) —
  currently **not enforced by any service function**, only by which options
  the frontend UI happens to render.
- `paymentStatus` side effects: `delivered` + `cod` ⇒ `paid`; `cancelled` +
  previously `paid` ⇒ `refunded` (flag-only, no real refund transaction
  modeled — see [Payout / Settlement](#payout--settlement-missing-entity)).
- `OrderItem` fields must never be updated after creation — they are a
  point-in-time snapshot, intentionally decoupled from the live `Product`
  so historical orders remain accurate even if the product's price,
  name, or image changes later, or the product is deleted.

### OrderItem (embedded)

| Field | Type |
|---|---|
| `productId` | string |
| `productName` | string |
| `productImageUrl` | string |
| `unitPriceLkr` | number |
| `quantity` | number |

`productId` is a reference for traceability, but the other fields are
**snapshots** — do not join back to the live `Product` to render an order;
use the stored values.

### OrderTimelineEntry (embedded)

| Field | Type |
|---|---|
| `status` | `OrderStatus` |
| `label` | string (human-readable, e.g. "Order confirmed by seller") |
| `timestamp` | string (ISO datetime) |
| `note` | string, optional — **field exists but no current UI ever sets it** |

Append-only: a new entry is added on every status change; existing entries
are never edited or removed.

### ShippingDetails (embedded on Order, not its own entity)

| Field | Type |
|---|---|
| `fullName` | string |
| `phone` | string |
| `addressLine1` | string |
| `city` | string |
| `district` | string |
| `postalCode` | string |

Captured fresh at every checkout. A signed-in buyer's checkout form is
prefilled from `Buyer.defaultShipping`, and the address just used is saved
back to it after a successful order — but this remains a *snapshot copy* on
the `Order`, not a live reference; editing `Buyer.defaultShipping` later
never changes a past order's `shipping`. See
[Buyer](#buyer-now-implemented--see-srctypesbuyerts) below.

---

## Missing entities (to decide on)

These are referenced conceptually by the product (copy, UI affordances, or
displayed-but-static numbers) but have **no backing data model** in the
frontend today. Each needs an explicit product decision before a backend
team invents a shape for it.

### Seller / User *(missing entity)*
No persisted account record exists anywhere. The session cookie carries an
`email` string that is **never validated against, or stored in, any user
table** — literally any string typed into the `/login` form becomes the
session's `email` (still true). `/onboarding` now creates a real `Store` +
`StoreSettings`, which narrows the gap somewhat — but there is still no
`User`/`Seller` record tying an email to a password/credential or to the
store it created, which is exactly why `/login` can't look up which store
an email belongs to and always falls back to the hardcoded demo store. A
real backend needs a `User`/`Seller` entity: at minimum `id`, `email`
(unique), `passwordHash` (or equivalent for whichever auth method is
chosen), `storeId` (FK, if keeping the current 1 seller : 1 store
assumption), `createdAt`. See
[`features/seller-auth.md`](../app/docs/features/seller-auth.md) and
[`api-contracts.md`](api-contracts.md#auth) before implementing.

### Review *(missing entity)*
`Store.rating`/`reviewCount` and `Product.rating`/`reviewCount` are
display-only numbers with **no submission flow, no reviewer identity, no
review text** anywhere in the app. If reviews are an intended feature, a
`Review` entity is needed: `id`, `productId` or `storeId`, reviewer identity
(`Buyer.id` — now that buyer accounts exist, see below — plus a decision on
whether a guest can review at all), `rating` (1–5), `text?`, `createdAt`;
plus a recomputation strategy for the aggregate `rating`/`reviewCount`
fields (denormalized counters, updated on write, or computed at read time).

### Buyer *(now implemented — see `src/types/buyer.ts`)*
No longer missing, and guest checkout is preserved alongside it (signing in
is optional, not required). `Buyer` fields: `id`, `name`, `email`, `phone?`,
`defaultShipping?` (a `ShippingDetails` snapshot, saved automatically after
a signed-in checkout, offered as a prefill on the next one), `createdAt`.
`buyersService` (`getBuyerByEmail`, `getBuyerById`, `registerBuyer`,
`updateDefaultShipping`) is the read/write surface —
[`features/buyer-accounts.md`](../app/docs/features/buyer-accounts.md) has the full
contract. `Order.buyerId` links an order back to the buyer who placed it
while signed in (`listOrdersByBuyer`); guest orders simply have no
`buyerId`. Still missing: a password/credential (see
[`gaps-and-assumptions.md`](gaps-and-assumptions.md)) and a multi-address
address book (see below) — today there's exactly one saved address per
buyer, overwritten each checkout.

### Payout / Settlement *(now implemented — see `src/types/payout.ts`)*
No longer missing. `Payout` fields: `id`, `storeId`, `storeName`, `orders`
(a snapshot array of `{ orderId, orderNumber, subtotalLkr, platformFeeLkr,
netLkr }` per included order — not a bare `orderId` list, so a payout's
composition/amount stays accurate even if the underlying orders somehow
changed later), aggregate `subtotalLkr`/`platformFeeLkr`/`netLkr`, `status`
(`"scheduled" | "paid"` — no `"failed"` state modeled, since there's no
real bank integration to fail), `createdAt`, `paidAt?`, `bankReference?`.
Created and released only via `/admin` — see
[`features/payouts.md`](../app/docs/features/payouts.md) for the full read/write
contract (`payoutsService`) and eligibility rule.

### Address book *(missing entity)*
`Buyer` supports exactly **one** saved address (`defaultShipping`),
overwritten on every signed-in checkout — there's no multi-address list
(e.g. "Home" vs "Office") or an explicit "save this address" opt-in/out. A
real address book, if wanted, would be a `id`/`buyerId`/`label`/
`ShippingDetails` entity replacing the single embedded field.

---

## Category (static config, not a queryable entity)

`StoreCategory` is a fixed TypeScript union of 8 values, with static
labels/icons in `src/mock/categories.ts` (not fetched via any service
function). Whether this should become a real, backend-managed table (to
allow adding categories without a frontend deploy) is an open product
question — see [`api-contracts.md`](api-contracts.md#categories).

---

## Cart (explicitly not a backend entity today)

The cart is 100% client-side (Zustand + `localStorage`), scoped to one
browser/device, never persisted server-side — this is still true even now
that buyer accounts exist; a signed-in buyer's cart does not sync across
devices, only their *default shipping address* and *order history* do
(both live on the `Buyer`/`Order` backend records, not the cart itself).
Only the cart's **contents** reach the backend, as the `items` array in a
`POST /api/orders` request. If cross-device cart persistence becomes a
requirement, a `Cart`/`CartItem` entity would need to be introduced then —
not before.

`CartItem` also carries an `isUnavailable?: boolean` flag, set client-side
by `useCartReconciliation()` when a held product no longer exists (see
[Order](#order) above for the parallel `buyerEmail`/`buyerId` additions,
and [`features/cart.md`](../app/docs/features/cart.md) for the reconciliation
behavior). This flag has no backend equivalent to design for — it's purely
a derived, client-computed annotation over otherwise-real product data, not
new information a real backend needs to store.
