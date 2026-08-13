# Database Model

> Cross-references: [`api-contracts.md`](api-contracts.md) ·
> [`overview.md`](overview.md) · [`gaps-and-assumptions.md`](gaps-and-assumptions.md) ·
> [`roadmap.md`](roadmap.md)

Real Postgres schema, managed by Flyway migrations under
`backend/src/main/resources/db/migration/` (`V1__init_schema.sql` through
`V12__store_verification_change_requests.sql`, read in full for this
document) and mapped by JPA entity classes under
`backend/src/main/kotlin/com/storepilot/backend/*/`. `spring.jpa.hibernate.ddl-auto`
is `validate`, not `update` — the migrations are the actual source of truth;
entity classes must be kept in sync with them by hand, not the other way
around.

`V1` is itself a **squashed rewrite** of what were originally 11 separate
migration files, collapsed into one baseline once the schema had settled
and no production data existed yet to migrate forward (see `V1`'s own doc
comment). `V2`–`V12` are real, additive migrations layered on top of that
baseline — this document describes the schema as it stands after all
twelve.

## Conventions used throughout

- **Every table** gets `id uuid primary key` (`gen_random_uuid()`-style
  default via `GenerationType.UUID`, not a DB sequence), plus
  `created_at`/`updated_at timestamptz not null` — set by JPA auditing
  (`BaseEntity.kt`, `@CreatedDate`/`@LastModifiedDate`). A frontend type's
  `joinedAt`/`createdAt`/etc. always maps to this `created_at` column;
  there's never a separate column for it.
- **All money is an integer count of the currency's smallest unit**
  (cents for AUD, the current default deployment currency — see
  `overview.md`), never a decimal or whole-unit value. This was a
  deliberate migration (`V3__money_as_cents.sql`, a one-time `× 100`
  rewrite of every existing money column) away from an earlier
  whole-dollar-integer design. `transaction_fee_percent` and
  `platform_fee_percent` are the only money-*adjacent* fields stored as
  `numeric(5,2)` (a percentage, not a currency amount).
- **Enums are stored as their wire-value string**, not a Postgres enum
  type or an ordinal — every Kotlin enum in this codebase implements
  `WireValue` and has a JPA `@Converter(autoApply = true)` that persists
  `.wireValue` (e.g. `"out-of-stock"`, not `2` or `OUT_OF_STOCK`). This is
  also exactly the string the frontend's TypeScript union types expect, so
  DTO mapping is a pure pass-through.
- **Two immutable-snapshot patterns repeat across the schema**: `OrderItem`/
  `PayoutOrderRef`/`FeeCollectionOrderRef` all store a plain `uuid` column
  (`product_id`, `order_id`) that is **not a foreign key** — deliberately,
  so deleting the referenced row (a product, or an order swept into a later
  ledger entry) never breaks a historical record that already copied the
  data it needs.
- **File/document references are never resolved URLs at rest.** Every
  `*_url` column that comes from an upload (`logo_url`, product image
  `url`, `receipt_url`, every `*_document_url`) stores a `FileStorageService`
  reference — a local path or an S3 object key, depending on deployment
  profile — resolved to a fetchable URL fresh at *read* time (S3 mode signs
  a presigned URL with its own expiry). Never assume a stored value is
  directly fetchable.
- **`extension pg_trgm`** is enabled (`V1`) and backs `gin` trigram indexes
  used for substring/`ILIKE`-style search on `stores.name`/`tagline`/`city`
  and `products.name`/`description` — this is real database-side search,
  not an in-memory filter.

---

## common / platform (cross-cutting tables)

### `platform_settings`

The single live, DB-backed row of platform configuration. Seeded once by
`DataSeeder` from `PlatformProperties`' env-var-bound defaults **only if
the table is empty**; from then on, this row — not the Kotlin config class —
is what the running app reads (`PlatformConfigService`), so a deployment
can change its fee percent, country, or currency by updating this row
directly, no rebuild/redeploy required. Exposed publicly via
`GET /api/platform-config`.

| Column | Type | Notes |
|---|---|---|
| `name`, `tagline` | varchar | Platform branding |
| `country_name`, `country_code` | varchar | `country_code` is ISO 3166-1 alpha-2 (e.g. `"AU"`, `"LK"`) — selects this deployment's `states` rows and prefixes generated order numbers |
| `currency_code`, `currency_symbol`, `currency_locale` | varchar | ISO 4217 code, display symbol, `Intl`-style locale string |
| `platform_fee_percent` | numeric(5,2) | Default fee charged when a store has no `store_settings.transaction_fee_percent` override |
| `flat_shipping_fee` | integer | Cents |
| `pro_monthly_price_cents` | integer | Added in `V10`; live price for the seller Pro-plan subscription — see [seller](#seller) below |
| `default_cod_enabled`, `default_online_payment_enabled`, `default_bank_transfer_enabled` | boolean | Applied to a newly-onboarded store's `store_settings` row when the seller doesn't explicitly set them |
| `support_email`, `company_location` | varchar | Display copy |

**Why one row, no country column**: each country is its own separate
database/deployment (see `PlatformProperties`' doc comment — infra is
per-country, never shared), so there's never a need to disambiguate rows by
country here.

### `states`

This deployment's administrative-division options for address forms (a
generic "state/province" concept — see `StoreAddress`/`ShippingDetails`
below), seeded directly by migration, not hardcoded in Kotlin or
TypeScript. `V1` seeds Australia's 8 states/territories (the current
default deployment target); a Sri Lanka deployment would seed its own
districts in its own copy of this migration instead. Exposed via
`GET /api/states`.

| Column | Type | Notes |
|---|---|---|
| `name` | varchar | e.g. `"New South Wales"` |
| `sort_order` | integer | Display order |

No country column here either, same reasoning as `platform_settings`.

### `email_verification_codes` (`V7`)

One row per email awaiting verification for a real email/password
registration (see `EmailVerificationService`, `AuthController.register()`/
`verifyEmail()`). Re-registering or resending a code overwrites the row in
place rather than accumulating history.

| Column | Type | Notes |
|---|---|---|
| `email` | varchar, unique | One pending code per email, not per Cognito user id — the Cognito user already exists (unverified) by the time this row is created |
| `code_hash` | varchar(64) | Hashed, never the raw code |
| `expires_at` | timestamptz | |
| `attempts` | int, default 0 | Rate-limits guessing |

---

## seller

### `sellers`

The account behind a `Store` — created explicitly during onboarding
(`POST /api/stores`), never JIT-provisioned the way `buyers`/`admins` rows
are, since onboarding already collects real business data a JIT row
couldn't fabricate.

| Column | Type | Notes |
|---|---|---|
| `cognito_sub` | varchar, unique, not null | Links to the Cognito identity (JWT `sub` claim) |
| `email` | varchar, unique, not null | Profile-data cache only — see below |
| `name` | varchar, not null | |
| `plan` | varchar, default `'free'` (`V10`) | `"free" \| "pro"` — see [`SellerPlan`](#sellerplan-and-billing) below |
| `stripe_customer_id`, `stripe_subscription_id` | varchar, nullable (`V10`) | The seller's own Stripe Customer/Subscription on the **platform's** Stripe account (billing them for Pro) — distinct from `store_settings.stripe_account_id` (their connected account, for accepting buyer payments) |
| `plan_current_period_end` | timestamptz, nullable (`V10`) | |
| `plan_cancel_at_period_end` | boolean, default false (`V10`) | True once cancelled but still inside a paid period |

**Unique partial index**: `idx_sellers_stripe_subscription_id` on
`stripe_subscription_id` **where not null** (`V10`) — enforces uniqueness
without rejecting the many rows where it's still null.

**Why `cognito_sub`/`email`/`name` exist here at all**: this row is a
**profile-data cache only**. `ROLE_SELLER` authorization always comes from
the JWT's `cognito:groups` claim (checked by `SecurityConfig`'s
`hasRole("SELLER")` matchers), never from whether this row exists — see
`CurrentActor.kt`'s doc comment. The access token the API actually
validates carries no profile attributes (only `sub`/groups), so this cache
avoids a live Cognito API call on every request that needs a seller's
name/email.

#### `SellerPlan` and billing

Free vs. Pro is a real, Stripe-billed subscription — see
`SellerBillingService.kt`. **COD and bank-transfer checkout are gated
behind Pro** (`OrderService.createOrder`, `StoreService.upsertSettings`):
a free-plan seller can only accept online payment (PayHere/Stripe,
whichever is live for the deployment's country). This is enforced at both
the settings-save layer (silently forced off, not rejected) and again at
order-creation time (rejected with `409`) as defense in depth against a
stale client. See [`api-contracts.md#billing`](api-contracts.md#billing).

---

## buyer

### `buyers`

| Column | Type | Notes |
|---|---|---|
| `name` | varchar, not null | |
| `email` | varchar, unique, not null | |
| `phone` | varchar, nullable | |
| `cognito_sub` | varchar, unique, nullable | **Null for a guest-checkout buyer** who never created an account; set once they do. Nullable+unique lets many guest rows coexist while still preventing two accounts from claiming the same Cognito identity |
| `shipping_full_name`, `shipping_phone`, `shipping_address_line1`, `shipping_city`, `shipping_state`, `shipping_postal_code` | varchar, all nullable | Embedded `ShippingDetails` — the buyer's one saved default address (`defaultShipping`), overwritten after every signed-in checkout |

Same profile-cache-only pattern as `sellers`/`admins`: `ROLE_BUYER` comes
from the JWT, never from this row's existence. Unlike `sellers`, **this row
is JIT-provisioned** on first authenticated request from a buyer-role JWT
(`CurrentActor.buyerOrNull()`) — safe because "buyer" is a group anyone can
self-register into. A guest-checkout row created earlier under the same
email gets linked (its `cognito_sub` filled in) rather than duplicated, the
first time that person creates a real account.

No password column — Cognito owns credentials entirely; this table only
ever caches profile/shipping data.

---

## admin

### `admins`

| Column | Type | Notes |
|---|---|---|
| `cognito_sub` | varchar, unique, not null | |
| `email` | varchar, unique, not null | |
| `name` | varchar, not null | |

JIT-provisioned on first authenticated request from a JWT whose
`cognito:groups` includes `admin` — safe only because there is **no public
path** into that Cognito group: the first admin is bootstrapped
out-of-band (`infra/scripts/create-admin.sh`), and every admin after that
is invited by an existing one (`POST /api/admin/admins`). Same
profile-cache-only caveat as `sellers`/`buyers` — removing someone from the
Cognito group revokes access immediately regardless of whether this row
still exists.

### `audit_logs` (`V9`)

Write-once activity log — every row is a permanent record, never updated
after insert. Covers **both** admin-initiated actions (store
approve/reject, admin invited, payout/fee-collection marked settled,
verification-change-request approve/reject) **and** seller-initiated
changes to their own store (settings updated, verification change
requested) — see `AuditAction.kt` for the full enumerated list.

| Column | Type | Notes |
|---|---|---|
| `actor_email` | varchar, not null | Always populated, even when `actor_id` can't be |
| `actor_id` | uuid, **nullable, no FK constraint** | See below |
| `action` | varchar, not null | One of `AuditAction`'s wire values |
| `target_type`, `target_id` | varchar, nullable | e.g. `"store"` / the store's UUID |
| `description` | text, not null | Pre-rendered human-readable summary — **not** reconstructed from the other fields at read time, so the log stays meaningful even after the target row is renamed or deleted |

**Why `actor_id` has no FK constraint**: it's a polymorphic reference — the
same column holds either an `Admin.id` or a `Seller.id`, whichever actually
performed the action (which one it is isn't stored explicitly, but is
always inferable from `action`, e.g. `STORE_APPROVED` is always an admin).
A single FK can't point at two different tables, and it's also
legitimately `null` for `ADMIN_LOGIN` rows recorded during the login
request itself, before that admin's session/JWT — and thus their resolved
`Admin` row — exists yet (see `AuditLogService.recordAdminLogin`'s doc
comment). `actor_email` is what's actually relied on to identify the actor
in the UI; `actor_id` is best-effort cross-referencing only.

**Indexes**: `action`, `target_type`, `created_at desc` (for the admin
audit-log page's default recent-first view).

### `admin_notifications`

An admin-facing activity feed, **not per-admin-account** — any admin can
read or dismiss any row, matching how `ROLE_ADMIN` itself isn't
per-admin-scoped anywhere else in this app. Currently fired for exactly two
events: a seller changing their payout bank details, and a seller
submitting a verification change request — both are seller actions an
admin has no other way to observe (see `AdminNotificationService.kt`).

| Column | Type | Notes |
|---|---|---|
| `type` | varchar, not null | `"bank-details-changed" \| "verification-change-requested"` |
| `message` | text, not null | Pre-rendered, same reasoning as `audit_logs.description` |
| `store_id` | uuid, nullable | Not a FK — informational only |
| `read` | boolean, default false | |

**Index**: `read` (unread-count queries).

---

## store

### `stores`

The public storefront profile.

| Column | Type | Notes |
|---|---|---|
| `seller_id` | uuid, FK → `sellers`, not null | |
| `slug` | varchar, unique, not null | URL-safe, auto-generated at onboarding (`uniqueSlug`, suffixes `-2`, `-3`, ... on collision) |
| `name`, `tagline`, `description` | varchar/text, not null | |
| `logo_url`, `banner_url` | varchar, **nullable** (`V11`) | Null until the seller uploads one — the frontend renders a generated initials avatar / color block in the meantime, not a stock placeholder. Originally not-null; relaxed in `V11__nullable_store_images.sql` once generated avatars shipped |
| `category` | varchar, not null | `StoreCategory` — fixed 8-value enum (fashion, food-beverage, beauty, handicrafts, electronics, home-living, jewelry, grocery), still not a DB-managed table (see [`api-contracts.md#categories`](api-contracts.md#categories)) |
| `city`, `state` | varchar, not null | Embedded `StoreAddress` — see below |
| `whatsapp_number` | varchar, not null | Not validated as a real phone number anywhere |
| `rating`, `review_count` | double / integer, default 0 | No backing `Review` entity exists — see [`gaps-and-assumptions.md`](gaps-and-assumptions.md) and [`roadmap.md`](roadmap.md) |
| `product_count`, `follower_count` | integer, default 0 | Denormalized display counters; no "follow" action exists in the product to actually increment `follower_count` |
| `is_verified` | boolean, default false | Mirrors `verification_status == 'active'`, kept as a separate boolean for cheap frontend display |
| `verification_status` | varchar, default `'pending'` | `"pending" \| "active" \| "rejected"` — gates every public read; set only by the admin approval workflow, never directly by the seller |
| `facebook_url`, `instagram_url`, `tiktok_url` | varchar, nullable | Public social links, seller-editable via `PATCH /api/stores/{storeId}/profile` — live here (public data) rather than `store_settings` (private data) |

**Why address fields live directly on `stores`, not a joined table**: one
embedded `StoreAddress` (`city`, `state`) per store, no separate `Address`
entity — a store has exactly one address, so a join buys nothing. `state`
is deliberately one generic "state/province" field (not a district+province
pair) so the same shape works for any country's address model — Sri
Lanka's district and Australia's state both fit here; valid values come
from the `states` reference table for whichever country this deployment
serves.

**Indexes**: `verification_status`; `gin` trigram indexes on
`lower(name)`, `lower(tagline)`, `lower(city)` (marketplace search).

### `store_settings`

Private, seller-only configuration — split from `stores` specifically so a
public store read never risks exposing bank details or identity documents.

| Column | Type | Notes |
|---|---|---|
| `store_id` | uuid, PK, FK → `stores`, `on delete cascade` | Shares `stores`' primary key (`@MapsId` — see below) |
| `contact_email`, `contact_phone` | varchar, not null | |
| `bank_account_name`, `bank_account_number`, `bank_name` | varchar, not null | **Sensitive** — not masked/encrypted anywhere today, displayed in full to the owning seller and to admins |
| `transaction_fee_percent` | numeric(5,2), not null | Per-store override of `platform_settings.platform_fee_percent`; used directly by `OrderService.createOrder` at checkout time |
| `cod_enabled`, `online_payment_enabled`, `bank_transfer_enabled` | boolean, defaults `true`/`true`/`false` | Checkout-time payment method toggles. At least one must always be `true` (`409` otherwise — `requireAtLeastOnePaymentMethod`). **`cod_enabled`/`bank_transfer_enabled` are silently forced `false` for a non-Pro seller** regardless of what's stored, both when settings are saved and again at order-creation time |
| `seller_type` | varchar, not null | `"individual" \| "business"` |
| `driver_licence_number` | varchar, nullable | Australia-deployment identity field |
| `abn` | varchar, nullable | Australian Business Number — required (with checksum validation) when `seller_type = 'business'` on an AU deployment |
| `nic_number` | varchar, nullable (`V2`) | Sri Lanka National Identity Card number — required on an LK deployment |
| `business_registration_number` | varchar, nullable (`V2`) | Sri Lanka business-registration equivalent of `abn` |
| `driver_licence_document_url`, `abn_document_url`, `nic_document_url`, `business_reg_document_url` | varchar, nullable | Uploaded proof documents — `FileStorageService` references, not resolved URLs |
| `rejection_reason` | text, nullable | Set by an admin when the store is rejected; shown to the seller |
| `stock_management_enabled` | boolean, default true | Store-wide switch — when false, no product in this store tracks stock regardless of its own `track_stock`, and the new-product page hides the stock UI |
| `pickup_enabled` | boolean, default false (`V6`) | Opt-in, off by default — not every seller has a pickup location |
| `stripe_account_id` | varchar, nullable (`V5`) | The seller's own Stripe Connect **Standard** account id (`acct_...`) |
| `stripe_charges_enabled`, `stripe_payouts_enabled` | boolean, default false (`V5`) | Mirror the connected account's real status, synced only via the `account.updated` webhook or an explicit refresh — never inferred from "an account id exists" |
| `stripe_enabled` | boolean, default false (`V5`) | The seller's own on/off preference for offering Stripe at checkout, independent of onboarding status — lets them pause it without disconnecting |

**Why `@MapsId` instead of a separate `id` + unique FK**: `StoreSettings`
shares `Store`'s primary key exactly (1:1, `store_id` is simultaneously
this table's PK and its FK to `stores`) — this is the cleanest way to
express "exactly one settings row per store, deletable together" in JPA,
and matches `on delete cascade` at the DB level.

**Why country-specific verification fields are all nullable, side by
side**: which pair (`driver_licence_number`/`abn` vs. `nic_number`/
`business_registration_number`) is required is decided per-deployment by
`platform_settings.country_code` (see `StoreVerificationValidation.kt`'s
`requireCountryVerificationFields`), never both at once for a given store —
a given deployment only ever populates the pair matching its own country,
leaving the other `null`.

**Business rule — post-approval edits are frozen**: once
`stores.verification_status = 'active'`, the seller-identity fields above
(`seller_type`, `driver_licence_number`, `abn`, `nic_number`,
`business_registration_number`, and their four document URLs) can no
longer be edited directly through `PATCH /api/stores/{storeId}/settings`
or the document-upload endpoints — those calls now `409`. Instead the
seller must go through `store_verification_change_requests` (below), which
requires admin re-approval before anything here actually changes. This
never blocks a `pending`/`rejected` store's initial submission or
resubmission — only edits after approval.

### `store_verification_change_requests` (`V12`)

A seller's proposed change to their already-approved store's
identity-verification fields — nothing here is live data; it only takes
effect once an admin approves it and `StoreVerificationChangeRequestService`
copies the fields onto the real `store_settings` row.

| Column | Type | Notes |
|---|---|---|
| `store_id` | uuid, FK → `stores`, not null | |
| `status` | varchar, not null | `"pending" \| "approved" \| "rejected"` |
| `seller_type`, `driver_licence_number`, `abn`, `nic_number`, `business_registration_number` | varchar, all nullable | A submission only needs to include what's actually changing — merged against the store's *current* `store_settings` values before validation, not required to resend every field |
| `driver_licence_document_url`, `abn_document_url`, `nic_document_url`, `business_reg_document_url` | varchar, nullable | Same partial-submission convention |
| `rejection_reason` | text, nullable | Set by the reviewing admin |
| `reviewed_at`, `reviewed_by_email` | timestamptz / varchar, nullable | Set together at approval or rejection |

**Business constraint**: only one `PENDING` request may exist per store at
a time (enforced in the service layer — `changeRequestRepository.findByStoreIdAndStatus`
— not a DB constraint). A store must be `ACTIVE` to submit one at all;
`store_settings` continues to serve `pending`/`rejected` stores' direct
edits, since those haven't been approved against a claimed identity yet.

**Indexes**: `store_id`, `status`.

---

## product

### `products`

| Column | Type | Notes |
|---|---|---|
| `store_id` | uuid, FK → `stores`, `on delete cascade`, not null | |
| `name`, `description` | varchar/text, not null | |
| `slug` | varchar, not null | **Unique per-store only** (`unique (store_id, slug)`), not globally — the old dedicated `GET /stores/:storeSlug/products/:productSlug` endpoint no longer exists server-side; the frontend composes the same lookup from `GET /api/stores/{slug}` + `GET /api/stores/{storeId}/products` instead (see [`api-contracts.md#products`](api-contracts.md#products)) |
| `category` | varchar, not null | Same `StoreCategory` enum as `stores.category` — **must equal the owning store's own category** (`requireCategoryMatchesStore`, `409` otherwise); a product's category is no longer independently choosable |
| `price` | integer, not null | Cents |
| `compare_at_price` | integer, nullable | "Was" price, rendered as a strikethrough only when strictly greater than `price` |
| `stock_quantity` | integer, not null | |
| `track_stock` | boolean, default true | When false, `stock_quantity` is ignored entirely — status is never auto-forced to out-of-stock and checkout/decrement skip this product. Effectively forced `false` whenever the owning store's `stock_management_enabled` is `false`, regardless of what's stored here |
| `status` | varchar, not null | `"active" \| "draft" \| "out-of-stock"` — auto-forced to `"out-of-stock"` whenever `track_stock` is true and `stock_quantity = 0`, enforced server-side in `ProductService`, not a DB constraint |
| `sku` | varchar, nullable | **Not unique** — a deliberate, still-open product decision (see `roadmap.md`'s "Duplicate-SKU validation" item), not an oversight |
| `rating`, `review_count` | double / integer, default 0 | Same "no backing `Review` entity" caveat as `stores` |

**Unique constraint**: `(store_id, slug)`.
**Indexes**: `store_id`, `category`, `sku`; `gin` trigram on `lower(name)`,
`lower(description)`.

**Business constraint**: a draft product is invisible to anyone but its
owning seller — `GET /api/products/{id}` 404s (not 403s) a draft for a
non-owner, so a stranger probing IDs can't distinguish "doesn't exist" from
"exists but is a draft."

### `product_images`

Its own child entity/table rather than a JSON/`@ElementCollection` array,
specifically so individual images can be reordered or removed independently
later.

| Column | Type | Notes |
|---|---|---|
| `product_id` | uuid, FK → `products`, `on delete cascade`, not null | |
| `url` | varchar, not null | `FileStorageService` reference, not a resolved URL |
| `alt` | varchar, not null | |
| `sort_order` | integer, default 0 (`V8`) | Index 0 (lowest) is the product's primary image — shown as the thumbnail everywhere only one image fits. Added by `V8`, explicit rather than relying on `created_at` (not a reliable distinguisher between images uploaded in the same request); backfilled from existing rows' insertion order at migration time |

**Index**: `product_id`.

---

## order

### `orders`

| Column | Type | Notes |
|---|---|---|
| `order_number` | varchar, unique, not null | Format `{countryCode}-YYYYMMDD-####` (4 random digits, e.g. `AU-20260813-4821`) — collision is theoretically possible (`Random.nextInt`, no retry loop), not eliminated by a DB constraint beyond the plain uniqueness index |
| `store_id` | uuid, FK → `stores`, not null | An order belongs to exactly one store — mixed-store checkouts are rejected before an order is ever created |
| `subtotal`, `shipping_fee`, `platform_fee`, `total` | integer, not null | Cents; `total = subtotal + shipping_fee` — the platform fee is deducted from the seller's payout, never added to what the buyer pays |
| `delivery_method` | varchar, default `'shipping'` (`V6`) | `"shipping" \| "pickup"` — a pickup order has `shipping_fee = 0` and only `shipping_full_name`/`shipping_phone` populated (no address); there's no separate pickup-location field, buyers coordinate over WhatsApp |
| `status` | varchar, default `'pending'` | `"pending" \| "confirmed" \| "shipped" \| "delivered" \| "cancelled"` — transitions enforced server-side by `OrderService.updateStatus`'s `ALLOWED_STATUS_TRANSITIONS` map, not just a frontend dropdown |
| `payment_method` | varchar, not null | `"payhere" \| "cod" \| "bank-transfer" \| "stripe"` |
| `payment_status` | varchar, not null | `"unpaid" \| "paid" \| "refunded"` |
| `receipt_url` | varchar, nullable | Bank-transfer buyer-uploaded proof of payment; only ever set for `payment_method = 'bank-transfer'` |
| `last_reminder_sent_at` | timestamptz, nullable | Backend-only, **never exposed** in `OrderResponse`; set by `ReceiptReminderJob` each time it emails a bank-transfer buyer about a missing receipt |
| `shipping_full_name`, `shipping_phone`, `shipping_address_line1`, `shipping_city`, `shipping_state`, `shipping_postal_code` | varchar, all nullable at the DB level | Embedded `ShippingDetails` snapshot, captured fresh at checkout — nullable here only because the same embeddable is reused on `buyers` (optional there); "required for an order" is enforced by request validation, not a DB constraint |
| `buyer_email` | varchar, not null | Receipt/notification destination, collected at every checkout, guest or not |
| `buyer_id` | uuid, FK → `buyers`, nullable | Set only when the buyer was signed in at checkout — derived **server-side** from the authenticated session, never a client-supplied field (a prior IDOR-shaped gap this closes) |
| `tracking_number`, `courier_service_name` | varchar, nullable | Required together the moment a seller marks an order `shipped` |
| `courier_receipt_url` | varchar, nullable | Optional proof-of-handover upload, same "shipped" transition |
| `stripe_payment_intent_id` | varchar, nullable (`V5`) | Set once Stripe's `checkout.session.completed` arrives; needed to issue a refund against the right connected-account charge later |

**Indexes**: `store_id`, `buyer_id`, `status`, `created_at`.

**Business constraint — `OrderItem` fields are immutable snapshots**,
never re-derived from the live `Product` — preserved unchanged from the
original design. See [OrderItem](#orderitem) below.

### OrderItem

Table `order_items`, child of `orders`, cascade-deleted with it.

| Column | Type | Notes |
|---|---|---|
| `order_id` | uuid, FK → `orders`, `on delete cascade`, not null | |
| `product_id` | uuid, **not a foreign key** | See top-of-document note on snapshot patterns — a product can be deleted without breaking historical orders |
| `product_name`, `product_image_url` | varchar, not null | Snapshot at order-creation time |
| `unit_price` | integer, not null | Cents, snapshot of the product's price **at order-creation time** — never re-read from the live product afterward |
| `quantity` | integer, not null | |

**Index**: `order_id`.

### OrderTimelineEntry

Table `order_timeline_entries`, child of `orders`. Append-only — a new row
is added on every status change or payment event; existing rows are never
edited or removed.

| Column | Type | Notes |
|---|---|---|
| `order_id` | uuid, FK → `orders`, `on delete cascade`, not null | |
| `status` | varchar, not null | The order's status *at the time of this entry* |
| `label` | varchar, not null | Human-readable, e.g. `"Order confirmed by seller"` |
| `"timestamp"` | timestamptz, not null | Quoted in the migration — a reserved-ish word in some contexts |
| `note` | text, nullable | Free-text, set on some transitions (bank-transfer rejection reason, seller notes) |

**Index**: `order_id`.

---

## payout

Two parallel, mirror-image ledgers exist because different payment methods
put money on different sides: **PayHere** (only) routes through the
platform's own merchant account, so the platform owes the seller money
(`payouts`). **COD and bank-transfer** pay the seller directly (bank-transfer
literally shows the seller's own bank details at checkout), so the seller
owes the platform its fee (`fee_collections`). **Stripe Connect direct
charges never touch either** — Stripe settles automatically at charge time
(the seller's connected account gets the net, the platform's
`application_fee_amount` is deducted at the source) — see
[`api-contracts.md#stripe-connect`](api-contracts.md#stripe-connect-payments-au).
Both ledgers are admin-only to create/settle; a seller only ever reads them.

### `payouts`

| Column | Type | Notes |
|---|---|---|
| `store_id` | uuid, FK → `stores`, not null | |
| `subtotal`, `platform_fee`, `net` | integer, not null | Cents; `net = subtotal - platform_fee`, summed across every included order |
| `status` | varchar, default `'scheduled'` | `"scheduled" \| "paid"` — no `"failed"` state, since there's no real automated bank integration to fail |
| `paid_at` | timestamptz, nullable | |
| `bank_reference` | varchar, nullable | Admin-recorded free text once the transfer is actually sent |

**Index**: `store_id`.

**Eligibility rule** (service-layer, not a DB view): a PayHere order counts
toward a store's "available for payout" total when `status = 'delivered'`
AND `payment_status = 'paid'` AND it isn't already part of an existing
`payouts` batch for that store.

### PayoutOrderRef

Table `payout_order_refs`, child of `payouts`.

| Column | Type | Notes |
|---|---|---|
| `payout_id` | uuid, FK → `payouts`, `on delete cascade`, not null | |
| `order_id` | uuid, **not a foreign key** | Same snapshot reasoning as `order_items.product_id` |
| `order_number` | varchar, not null | |
| `subtotal`, `platform_fee`, `net` | integer, not null | Snapshot of that order's totals **at the time the payout batch was created**, so a payout's amount stays accurate even if the underlying order somehow changed later |

**Index**: `payout_id`.

### `fee_collections` (`V4`)

Structurally identical to `payouts`, opposite direction — see the group
intro above.

| Column | Type | Notes |
|---|---|---|
| `store_id` | uuid, FK → `stores`, not null | |
| `subtotal` | integer, not null | Informational context, not itself owed |
| `platform_fee` | integer, not null | The actual amount owed **to** the platform **by** the seller |
| `status` | varchar, default `'pending'` | `"pending" \| "collected"` |
| `collected_at` | timestamptz, nullable | |
| `reference` | varchar, nullable | Admin-recorded free text |

**Index**: `store_id`.

**Eligibility rule**: a COD or bank-transfer order counts once
`status = 'delivered'` AND `payment_status = 'paid'` AND not already part
of an existing `fee_collections` batch for that store.

### FeeCollectionOrderRef

Table `fee_collection_order_refs`, child of `fee_collections` — mirrors
`PayoutOrderRef` exactly, minus the `net` column (there's no "net" concept
on this side; the whole `platform_fee` is what's owed).

| Column | Type | Notes |
|---|---|---|
| `fee_collection_id` | uuid, FK → `fee_collections`, `on delete cascade`, not null | |
| `order_id` | uuid, **not a foreign key** | |
| `order_number` | varchar, not null | |
| `subtotal`, `platform_fee` | integer, not null | Snapshot at batch-creation time |

**Index**: `fee_collection_id`.

---

## Entities that still don't exist

Carried forward from the earlier version of this document — still true
against the current schema:

- **Review** — `rating`/`review_count` on both `stores` and `products` are
  denormalized display numbers with no submission flow, reviewer identity,
  or review text anywhere in the schema.
- **Category** — `StoreCategory` remains a fixed, code-level 8-value enum
  (`store/StoreCategory.kt`, mirrored in the frontend), not a
  database-backed, admin-manageable table.
- **Address book** — `buyers` still supports exactly one saved address
  (the embedded `shipping_*` columns), overwritten on every signed-in
  checkout; no multi-address `Address` entity.
- **Cart** — still entirely client-side (Zustand + browser storage on the
  frontend), never persisted server-side; only a cart's *contents* reach
  the backend, as `POST /api/orders`' `items` array.
