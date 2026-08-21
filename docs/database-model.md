# Database Model

> Cross-references: [`api-contracts.md`](api-contracts.md) ·
> [`overview.md`](overview.md) · [`gaps-and-assumptions.md`](gaps-and-assumptions.md) ·
> [`roadmap.md`](roadmap.md)

Real Postgres schema, managed by Flyway migrations under
`backend/src/main/resources/db/migration/` (`V1__init_schema.sql` through
`V14__polymorphic_ledger_refs.sql`, read in full for this
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
  — this is real database-side search, not an in-memory filter. Store
  search is still purely substring-matched today; **product** search
  (`GET /api/products` with a `query` param) has since moved to real
  relevance-ranked full-text search — see `products.search_vector` below.

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
| `timezone` | varchar, default `'Australia/Sydney'` (`V13`) | IANA zone id — converts a resolved booking weekly-availability window into absolute slot `Instant`s. Deployment-wide, not per-store — see [`## booking`](#booking-v13) |

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
after insert. Covers admin-initiated actions (store approve/reject, admin
invited, payout/fee-collection marked settled, verification-change-request
approve/reject), seller-initiated changes to their own store/account
(settings updated, verification change requested, store closed, seller
account deleted), and buyer-initiated account deletion — see
`AuditAction.kt` for the full enumerated list. `STORE_CLOSED`/
`SELLER_ACCOUNT_DELETED`/`BUYER_ACCOUNT_DELETED` rows are written
**before** the acting `Seller`/`Buyer` row is anonymized (see
`SellerAccountService`/`BuyerAccountService`), so `actor_email`/
`description` durably preserve the real pre-anonymization identity as the
compliance evidence trail even though the source row no longer does.

| Column | Type | Notes |
|---|---|---|
| `actor_email` | varchar, not null | Always populated, even when `actor_id` can't be |
| `actor_id` | uuid, **nullable, no FK constraint** | See below |
| `action` | varchar, not null | One of `AuditAction`'s wire values |
| `target_type`, `target_id` | varchar, nullable | e.g. `"store"` / the store's UUID |
| `description` | text, not null | Pre-rendered human-readable summary — **not** reconstructed from the other fields at read time, so the log stays meaningful even after the target row is renamed or deleted |

**Why `actor_id` has no FK constraint**: it's a polymorphic reference — the
same column holds an `Admin.id`, `Seller.id`, or `Buyer.id`, whichever
actually performed the action (which one it is isn't stored explicitly,
but is always inferable from `action`, e.g. `STORE_APPROVED` is always an
admin, `BUYER_ACCOUNT_DELETED` is always a buyer). A single FK can't point
at three different tables. The column stays nullable at the schema level
for this same reason (a future actor-less action is a legitimate
possibility), even though every current write path
(`record`/`recordAsSeller`/`recordAsBuyer`) always resolves a real actor.
The lack of an FK is also what lets a `SELLER_ACCOUNT_DELETED`/
`BUYER_ACCOUNT_DELETED` row
outlive the source row being anonymized right after — a real FK would
either block the anonymization or cascade into rewriting history.
`actor_email` is what's actually relied on to identify the actor in the
UI; `actor_id` is best-effort cross-referencing only.

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
| `verification_status` | varchar, default `'pending'` | `"pending" \| "active" \| "rejected" \| "closed"` — gates every public read; `pending`/`active`/`rejected` are set only by the admin approval workflow, but `closed` is seller-initiated and terminal (`POST /api/stores/{storeId}/close`, see `api-contracts.md#stores`) — a store is never reopened once closed |
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
| `bookings_enabled` | boolean, default false (`V13`) | Opt-in, off by default, same reasoning as `pickup_enabled` — gates whether the store's bookable-services section exists at all. Not itself Pro-gated; see [`## booking`](#booking-v13) |
| `gst_registered` | boolean, default false (`V27`) | Self-declared, opt-in — GST registration is turnover-based (mandatory above A$75k/year, optional below it), never implied by `abn` presence alone. Read at order-creation time to decide whether to snapshot a tax invoice onto the order — see `orders.seller_abn`/`orders.gst_amount` below |

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
| `search_vector` | `tsvector`, generated always as ... stored (`V29`) | `setweight(to_tsvector('english', name), 'A') \|\| setweight(to_tsvector('english', description), 'B')` — a name match ranks above the same term only in the description. Powers `GET /api/products`'s text-search path (`ProductRepository.searchFullText`) via `ts_rank`-ordered relevance, not the plain browse path (`ProductSpecifications`), which never touches this column |

**Unique constraint**: `(store_id, slug)`.
**Indexes**: `store_id`, `category`, `sku`; `gin` trigram on `lower(name)`,
`lower(description)` (kept as an OR'd recall fallback for a search query
that doesn't tokenize into a real lexeme match); `gin` on `search_vector`
(`V29`) — the primary product-search index.

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
| `seller_abn`, `gst_amount` | varchar / integer, both nullable (`V27`) | Both set together, only when `store_settings.gst_registered` was `true` at order-creation time — an immutable tax-invoice snapshot, never re-derived from the store's current settings. `gst_amount` is cents, `total / 11`. See `store_settings.gst_registered` above |

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

## booking (`V13`)

A second storefront mode alongside products — stores that sell time
(appointments) instead of goods. `BookableService`/`Booking` are **parallel
aggregates** to `Product`/`Order`, not extensions of them: a service has no
stock/SKU/compare-price concept, and a booking has no delivery-method/
shipping-fee/tracking concept. A store's mode (products-only /
services-only / both) is entirely **derived** from data — `products.count()
> 0` and (`store_settings.bookings_enabled AND` an active service exists) —
there is no separate "store type" column.

### `bookable_services`

| Column | Type | Notes |
|---|---|---|
| `store_id` | uuid, FK → `stores`, `on delete cascade`, not null | |
| `name`, `slug`, `description` | varchar/varchar/text, not null | `slug` unique per `(store_id, slug)`, same shape as `products.slug` |
| `category` | varchar, not null | Locked to the owning store's own `category` — identical rule to `products.category` |
| `price` | integer, not null | Cents |
| `duration_minutes` | integer, not null | Drives slot-chunking — see `weekly_availability_rules`/`availability_exceptions` below |
| `buffer_minutes` | integer, default `0` | Gap enforced after each booking of this service before the next slot opens |
| `status` | varchar, not null | `"active" \| "draft"` — no `"out-of-stock"` analog |

**Index**: `store_id`. Deletion is refused (service-layer, not a DB
constraint) while any non-terminal `bookings` row still references it.

### `bookable_service_images`

Table child of `bookable_services`, identical shape to `product_images`
(`url`, `alt`, `sort_order`, index 0 is primary).

### `store_availability`

One row per store (`store_id` is both PK and FK, `@MapsId`, same shape as
`store_settings`) — the lead-time policy shared by every bookable service.

| Column | Type | Notes |
|---|---|---|
| `lead_time_minutes` | integer, default `120` | Minimum notice required before a slot can be booked; reused as the buyer-initiated cancellation cutoff (one number, both directions) |

### `weekly_availability_rules`

A store's recurring weekly open-hours template — exactly 7 rows once
configured, one per weekday.

| Column | Type | Notes |
|---|---|---|
| `store_id` | uuid, FK → `stores`, `on delete cascade`, not null | Unique with `day_of_week` |
| `day_of_week` | integer, not null | 1 (Monday) .. 7 (Sunday) — `java.time.DayOfWeek.getValue()` |
| `is_open` | boolean, not null | |
| `open_time`, `close_time` | time, nullable | Required together when `is_open = true` (check constraint) |

**Store-level, not per-service** — all of a store's bookable services share
one template in v1; see `AvailabilityService.computeSlots` for the
slot-generation algorithm (computed on read, never materialized as rows).

### `availability_exceptions`

A date-specific override — either a closure (`is_open = false`, e.g. a
public holiday) or a special one-off opening (`is_open = true`, e.g. a
normally-closed Sunday opened specially). Unique per `(store_id,
exception_date)`; wins outright over `weekly_availability_rules` for that
date. Same `open_time`/`close_time`-required-together check constraint as
above, plus an optional `note` shown to buyers on the booking page.

### `bookings`

| Column | Type | Notes |
|---|---|---|
| `booking_number` | varchar, unique, not null | Format `BK-{countryCode}-YYYYMMDD-####`, same generator style as `order_number` |
| `store_id` | uuid, FK → `stores`, `on delete cascade`, not null | |
| `service_id` | uuid, FK → `bookable_services`, not null | **A real foreign key**, unlike `order_items.product_id` — a service can't be allowed to disappear out from under a future appointment (deletion is refused instead, see above) |
| `service_name`, `service_price`, `service_duration_minutes` | varchar/integer/integer, not null | Immutable snapshot at booking-creation time, same principle as `OrderItem` |
| `scheduled_start`, `scheduled_end` | timestamptz, not null | `end` stored explicitly (not derived) so the slot-overlap query is a plain range comparison |
| `platform_fee`, `total` | integer, not null | Cents; `platform_fee` computed identically to `orders.platform_fee` (the store's `transaction_fee_percent`, `HALF_UP` rounded) |
| `status` | varchar, not null | `"pending" \| "confirmed" \| "completed" \| "cancelled" \| "no-show"` — no `"shipped"` analog. Transitions: `pending → confirmed\|cancelled`; `confirmed → completed\|cancelled\|no-show`; the rest terminal |
| `payment_method`, `payment_status` | varchar, not null | **Reuses `orders.payment_method`/`payment_status`'s wire values verbatim** — no new enum. `"cod"` reads as "Pay at venue" in the booking UI (frontend copy only) |
| `receipt_url` | varchar, nullable | Bank-transfer proof, same as `orders.receipt_url` |
| `stripe_payment_intent_id` | varchar, nullable | Same purpose as `orders.stripe_payment_intent_id` |
| `buyer_name`, `buyer_phone`, `buyer_email` | varchar, not null | Collected at booking time — a booking has no separate "shipping details" embeddable, these three fields are it |
| `buyer_id` | uuid, FK → `buyers`, nullable | Same guest-booking-allowed pattern as `orders.buyer_id` |
| `cancelled_at`, `cancellation_reason` | timestamptz/text, nullable | |

**Indexes**: `store_id`, `service_id`, `buyer_id`, and a **partial index**
`(service_id, scheduled_start, scheduled_end) WHERE status NOT IN
('cancelled', 'no-show')` — the slot-overlap query's hot path.

**Booking payment methods mirror order payment methods exactly, including
Pro-plan gating**: PayHere (LK)/Stripe (AU) available to any plan;
bank-transfer and "Pay at venue" (`cod`) are Pro-only, enforced with the
same settings-clamp + write-time-defense-in-depth pattern as
`codEnabled`/`bankTransferEnabled` on orders (see `store_settings` below).

**Independent per-service capacity** (confirmed product decision, not a
technical limitation): two different services on the same store can be
booked for the same time slot — the slot-overlap check only looks at
existing bookings *of the same service*. A store wanting single-provider
"only one appointment at a time across all services" behavior isn't
supported in v1 (no staff/capacity concept exists).

### `booking_timeline_entries`

Table child of `bookings`, identical append-only shape to
`order_timeline_entries`.

### `store_settings.bookings_enabled` (`V13`)

New column on the existing `store_settings` table (see below) — plain
`boolean not null default false`, opt-in, identical mechanism to
`pickup_enabled`. Gates whether the store's Services section exists at all
on its storefront; **not itself Pro-gated** (only the two payment methods
above are).

### `platform_settings.timezone` (`V13`)

New column on the existing `platform_settings` table (see below) — an IANA
zone id (e.g. `"Australia/Sydney"`), used to convert a resolved weekly
open-hours window into absolute booking-slot `Instant`s. Deployment-wide,
not per-store — this codebase is already single-deployment-per-country
(PayHere=LK-only, Stripe=AU-only), so one zone matches every other
country-specific default already stored here.

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
Both are **polymorphic since `V14`**: one payout (or fee-collection) batch
can bundle both order-sourced and booking-sourced income for the same
store — see `PayoutOrderRef`/`FeeCollectionOrderRef` below.

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

### PayoutOrderRef (`PayoutSourceRef` since `V14`)

Table `payout_order_refs` (kept as-is — only the Kotlin class was renamed),
child of `payouts`. **Polymorphic since `V14`**: each row snapshots either
an order or a booking, never both.

| Column | Type | Notes |
|---|---|---|
| `payout_id` | uuid, FK → `payouts`, `on delete cascade`, not null | |
| `order_id`, `order_number` | uuid/varchar, nullable | `order_id` **not a foreign key**, same snapshot reasoning as `order_items.product_id` |
| `booking_id`, `booking_number` (`V14`) | uuid/varchar, nullable | Same non-FK reasoning as `order_id` |
| `subtotal`, `platform_fee`, `net` | integer, not null | Snapshot of that order's/booking's totals **at the time the payout batch was created** — for a booking, `subtotal` is the service price |

**Constraint**: `check ((order_id is not null)::int + (booking_id is not
null)::int = 1)` — exactly one source per row.

**Index**: `payout_id`, plus a partial index on `booking_id` (`V14`).

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

**Eligibility rule**: a COD/bank-transfer order or a `cod`/bank-transfer
booking counts once `status = 'delivered'` (order) or `'completed'`
(booking) AND `payment_status = 'paid'` AND not already part of an
existing `fee_collections` batch for that store.

### FeeCollectionOrderRef (`FeeCollectionSourceRef` since `V14`)

Table `fee_collection_order_refs`, child of `fee_collections` — mirrors
`PayoutOrderRef`/`PayoutSourceRef` exactly (same `V14` polymorphism,
constraint, and partial index), minus the `net` column (there's no "net"
concept on this side; the whole `platform_fee` is what's owed).

| Column | Type | Notes |
|---|---|---|
| `fee_collection_id` | uuid, FK → `fee_collections`, `on delete cascade`, not null | |
| `order_id`, `order_number` | uuid/varchar, nullable | |
| `booking_id`, `booking_number` (`V14`) | uuid/varchar, nullable | |
| `subtotal`, `platform_fee` | integer, not null | Snapshot at batch-creation time |

**Index**: `fee_collection_id`, plus a partial index on `booking_id`.

---

## return_requests (`V26`)

A buyer's post-delivery return/refund request against one order — own
table rather than a field on `orders`, same "proposed change needing a
decision" shape as `store_verification_change_requests`. `orders.status`
is never touched by this feature; only `orders.payment_status` flips
`paid` -> `refunded`, once money has actually moved (or, for Stripe,
synchronously on seller approval).

| Column | Type | Notes |
|---|---|---|
| `order_id` | uuid, FK → `orders`, not null | Direct FK, not a snapshot-UUID like `payout_order_refs.order_id` — this is a per-order relationship, not a cross-store batch |
| `reason_category` | varchar, not null | `"defective" \| "wrong-item" \| "not-as-described" \| "changed-mind" \| "other"` |
| `reason_note` | text, nullable | |
| `status` | varchar, default `'requested'` | `"requested" \| "approved" \| "rejected" \| "refund-pending" \| "refunded"` — see [`api-contracts.md#returns--refunds`](api-contracts.md#returns--refunds) for the full transition table |
| `seller_decision_note` | text, nullable | |
| `refund_reference` | varchar, nullable | Set once `refunded`, by whichever actor confirmed it |
| `settlement_reconciliation_note` | text, nullable | Snapshotted once, at seller-approval time, if the order was already in a `payouts`/`fee_collections` batch — see below |
| `decided_at`, `refunded_at` | timestamptz, nullable | |

**Index**: `order_id`, `status`.

**Eligibility rule** (service-layer): a buyer may create a return when
`orders.status = 'delivered'` AND `payment_status = 'paid'`, within
`platform_settings.return_window_days` (added in the same `V26`
migration, default `30`) of the order's *earliest* `delivered` timeline
entry, and no existing request on that order with a status other than
`rejected`.

**Refund execution differs by `orders.payment_method`**: Stripe refunds
synchronously on seller approval (reusing `StripeService.refundPayment`);
PayHere/COD/bank-transfer have no live refund API, so they land on
`refund-pending` until a human confirms the money actually moved —
PayHere is admin-confirmed (the platform's own merchant account holds
that money, same reasoning as `payouts`), COD/bank-transfer is
seller-self-attested (the seller already holds that money directly, same
trust model as `verifyBankTransfer`'s inbound receipt confirmation).

**No automatic clawback**: if `settlement_reconciliation_note` is set
because the order was already in a settled (`paid`/`collected`)
`payouts`/`fee_collections` batch, real money already changed hands for
that order before the return was approved — there's no line-item-removal
mechanism for either ledger in this schema, so reconciling that overlap
is a manual admin step, not something this table automates.

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
