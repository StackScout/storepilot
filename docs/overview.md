# StorePilot — Product Overview

> Cross-references: [`api-contracts.md`](api-contracts.md) ·
> [`database-model.md`](database-model.md) ·
> [`app/docs/frontend-architecture.md`](../app/docs/frontend-architecture.md) ·
> [`app/docs/feature-index.md`](../app/docs/feature-index.md) ·
> [`app/docs/user-flows.md`](../app/docs/user-flows.md) ·
> [`roadmap.md`](roadmap.md) · [`gaps-and-assumptions.md`](gaps-and-assumptions.md)

## Purpose of the application

StorePilot is a **multi-vendor e-commerce marketplace** connecting small,
independent businesses with local buyers. It lets a seller list and sell
products online without building their own storefront, and lets buyers
discover, browse, and buy from many independent local sellers through one
consistent shopping experience.

This is a real, production-style application: a Next.js 16 frontend
(`app/`) backed by a Spring Boot/Kotlin API (`backend/`) with a real
Postgres database, AWS Cognito authentication, S3-backed file storage,
Stripe (Connect + Billing), PayHere, and a durable audit-log system. An
earlier phase of this project was a frontend-only, `localStorage`-backed
prototype with no real backend — that phase is long superseded; do not
trust `app/AGENTS.md`'s framing where it still describes "no real backend"
or "mock sign-in." Everything below is verified against the current
backend source and Flyway migrations, not the old prototype's design.

## Business problem

Small sellers (home businesses, craftspeople, boutique labels, small food
producers) typically sell through informal channels — social media posts,
word of mouth — with no dedicated storefront, no structured catalog, no
order/inventory tracking, and no formal online payment option beyond ad
hoc bank transfers or cash. StorePilot's premise is to give these sellers:

- A branded storefront page (`/stores/[slug]`) without needing their own
  website, with a generated avatar/color-block placeholder until they
  upload a real logo.
- A structured product catalog with categories, stock tracking, and
  pricing — a product's category is locked to match its store's own
  category.
- A seller dashboard for products, orders, and payouts, gated behind a
  real admin approval workflow before the store goes live.
- A choice of payment methods, independently toggleable per store: online
  payment (Stripe Connect or PayHere, depending on deployment country),
  Cash on Delivery, and direct bank transfer with buyer-uploaded receipt +
  manual seller verification.
- A direct communication channel to buyers via WhatsApp (every store has a
  `whatsappNumber`; product/order pages and pickup-order coordination both
  surface a "Message on WhatsApp" action).

For buyers, the problem being solved is discovery and trust: one place to
search across many small sellers by category, and check out with a single
consistent flow, rather than messaging each seller individually.

## Deployment model: multi-country, one deployment per country

This is not a single fixed market. The backend is built to serve **one
country per deployment**, selected by a single `platform_settings.country_code`
value (`GET /api/platform-config`) — never a shared, multi-tenant-by-country
database (see `PlatformProperties`' doc comment: "each country gets its own
separate database... infra is per-country, never shared"). Everything
country-specific branches off that one value: which identity-verification
fields a seller must provide (`nic_number`+`business_registration_number`
for `"LK"` vs. `driver_licence_number`+`abn` for `"AU"`), which online
payment gateway is live (PayHere for `"LK"`, Stripe Connect for `"AU"` —
each is currently hard-disabled outside its home country, both client- and
server-side), and which `states` reference-table rows are seeded (e.g.
Australian states/territories vs. Sri Lankan districts).

**The current default deployment target is Australia**, not Sri Lanka —
this is a real shift from this project's earlier phase. `PlatformProperties`'
bootstrap defaults are AUD/`"AU"`/Stripe-oriented (its own doc comment:
*"the SL launch is on hold pending business registration/legal setup, so AU
is the near-term deployment this codebase actually needs to boot as by
default"*). PayHere/Sri Lanka support still exists fully in the codebase
(schema, validation, checkout flow) but isn't the active default; a Sri
Lanka deployment would override the platform config via env vars, the same
way an Australia deployment does today.

## Target users

| User | Description |
|---|---|
| **Buyer / shopper** | Anyone browsing the public marketplace. Checkout works fully as a guest (name/phone/address/email entered at checkout); an optional buyer account (real Cognito credentials) adds a saved default address and cross-visit order history. |
| **Seller / merchant** | A business owner who onboards through `/onboarding`, creating a real Cognito identity + `Seller` + `Store` row. Multiple independent sellers/stores exist for real — there is no single hardcoded demo store. |
| **Platform admin** | StorePilot staff, who review and approve/reject new stores, review post-approval verification-change requests, release payouts, collect owed platform fees, and manage other admin accounts. A real, Cognito-gated role — never self-registered (see below). |

## Accounts and authentication

All three account types are backed by real AWS Cognito identities — not
mock or session-cookie-only. See
[`api-contracts.md#auth`](api-contracts.md#auth) for the full endpoint
contract.

- **Buyer and seller are mutually exclusive identities.** Registering picks
  one Cognito group (`buyer` immediately, or nothing yet for a seller-track
  signup — see below); an account can never hold both. New email/password
  accounts go through an app-owned 6-digit email-verification code before
  they can sign in at all.
- **Seller.** Registering as a seller grants no Cognito group by itself —
  a seller only becomes `ROLE_SELLER` the moment they complete onboarding
  (`POST /api/stores`), which creates their `Seller` row, their `Store`
  (in `pending` verification status), and grants the Cognito group all in
  one transaction.
- **Buyer.** Can also sign in via Google (Cognito Hosted UI OAuth), which
  JIT-provisions the `buyer` group on first sign-in. A buyer row is also
  JIT-provisioned for a guest checkout under a given email, and gets
  linked (not duplicated) if that same person later creates a real
  account.
- **Admin.** No self-registration path exists at all. The first admin is
  bootstrapped out-of-band via `infra/scripts/create-admin.sh`; every
  admin after that is invited in-app by an existing one
  (`POST /api/admin/admins`), which creates the Cognito user directly in
  the `admin` group. `ROLE_ADMIN` is always re-checked from the JWT's
  `cognito:groups` claim on every request — never cached or inferred from
  a local database row's existence.

## Store approval and the verification-change-request flow

A new store starts `verification_status: pending` and is invisible on
every public listing. An admin reviews the seller's submitted identity
details (driver's licence + ABN, or NIC + business registration, depending
on deployment country — plus uploaded proof documents) and either approves
it (immediately visible everywhere, `isVerified: true`) or rejects it with
a reason shown to the seller.

**Once a store reaches `active`, its identity-verification fields are
frozen against direct edits.** A seller can no longer change `sellerType`,
`driverLicenceNumber`/`abn`, `nicNumber`/`businessRegistrationNumber`, or
their supporting documents through the ordinary settings save — that call
now returns `409`. Instead, they submit a
**verification change request** (`POST /api/stores/{storeId}/verification-change-requests`),
a proposed diff against their current settings that sits in `PENDING`
status (only one at a time per store) until an admin explicitly approves
or rejects it. Approval copies the proposed fields onto the real
`store_settings` row; rejection leaves the store's real settings
untouched. This closes the gap where a seller could quietly change what
legal/business identity they claim to be *after* an admin already approved
the store against the original claim — any such change now requires a
fresh review. See
[`api-contracts.md#store-verification-change-requests`](api-contracts.md#store-verification-change-requests)
and [`database-model.md#store_verification_change_requests-v12`](database-model.md#store_verification_change_requests-v12).

Every step of this — approval, rejection, a seller's settings update, a
change request submitted/approved/rejected — is written to a durable,
write-once `audit_logs` table (see
[Audit log](#audit-log-and-admin-notifications) below), viewable at
`/admin/audit-log`.

## Payments

Four checkout payment methods exist in the schema; which ones a given
store can actually offer depends on deployment country, the store's own
settings toggles, and — new in this phase — the seller's plan tier (see
[Monetization](#monetization-and-seller-plans) below).

- **Stripe Connect** (Australia-only currently) — each seller onboards
  their own Stripe **Standard** connected account
  (`POST /api/stores/{storeId}/stripe-connect/onboard`). Checkout creates
  a Stripe Checkout Session as a **direct charge on the seller's own
  connected account** (not the platform's account with a transfer
  afterward), with `application_fee_amount` set to the platform's cut —
  Stripe deducts it automatically at charge time. This means Stripe orders
  never sit in either the payout or fee-collection ledger; the money never
  passes through platform custody at all. A cancelled, already-paid Stripe
  order triggers a real refund call (including the platform's own fee)
  before the order is allowed to show as refunded.
- **PayHere** (Sri Lanka-only currently) — a hidden-form checkout redirect
  with a server-computed signature hash (merchant secret never reaches the
  browser), confirmed asynchronously via a signed webhook. Unlike Stripe,
  PayHere settles into the **platform's own** merchant account, so a
  PayHere sale genuinely owes the seller a payout — see
  [Payouts and fee collections](#payouts-and-fee-collections) below.
- **Cash on Delivery** — flips to `paid` when the seller marks the order
  delivered.
- **Bank transfer** — the store's own bank details are shown to the buyer
  at checkout; the buyer uploads a receipt image/PDF, and the seller
  explicitly approves or rejects it (`POST /api/orders/{id}/verify-bank-transfer`).
  A buyer can self-cancel an unpaid, no-receipt-yet bank-transfer order;
  once a receipt is uploaded, only the seller's decision moves it forward.

Every store must keep at least one payment method enabled at all times —
the settings save rejects a combination that would leave zero.

## Monetization and seller plans

The core mechanism is unchanged from the original design: StorePilot takes
a **percentage-based platform (transaction) fee** on each sale, deducted
from the seller's payout, not charged as a separate line item to the
buyer. Buyer-facing total is `subtotal + shippingFee` (a flat fee, unless
the delivery method is pickup, which is free); the platform fee never
appears in what the buyer pays.

- **Default fee rate**: `platform_settings.platform_fee_percent`
  (currently `2.0%` in the Australia-default bootstrap config —
  confirm the live value via `GET /api/platform-config`, since this row is
  editable per-deployment without a redeploy). A store's own
  `transaction_fee_percent` (settable, defaults to the platform rate at
  onboarding) is the actual value used at checkout — the platform-wide
  constant is only a fallback for a store with no settings row yet.
- **What's new in this phase — a real seller subscription tier
  (`SellerPlan`: `free` | `pro`), billed through a genuine Stripe
  Subscription on the platform's own account** (separate entirely from
  Stripe Connect above — see
  [`api-contracts.md#billing`](api-contracts.md#billing)). **Cash on
  Delivery and bank transfer are gated behind the Pro plan** — a free-plan
  seller can only accept whichever online-payment gateway is live for
  their country. This is enforced twice: silently forced off at the
  settings-save layer, and rejected outright (`409`) at order-creation
  time as defense against a stale/bypassed client. The default Pro price
  is `platform_settings.pro_monthly_price_cents` (bootstrap default $9.90/
  month) — again, live-editable, not a frontend constant. Cancelling Pro
  keeps access through the already-paid period (standard SaaS UX), then
  reverts to `free` via the Stripe subscription-deleted webhook.
- No listing fees or ad placements exist.

**Worth flagging** (see the note filed against `gaps-and-assumptions.md`):
because online-payment defaults to *off* for a fresh Australia deployment
(no working Stripe-equivalent existed for AU at platform-defaults time
until Connect shipped, and a seller must still complete their own Connect
onboarding before `stripeChargesEnabled` is even true) while COD/bank
transfer are Pro-gated, a brand-new **free**-plan Australian seller can
land in a state with no payment method actually available at checkout time
unless they either upgrade to Pro or explicitly enable online payment and
finish Stripe onboarding. This is a real product-shape question, not
resolved here — see `gaps-and-assumptions.md`.

## Payouts and fee collections

Two parallel, admin-operated ledgers exist because different payment
methods put the money on different sides:

- **Payouts** (`payouts`/`payout_order_refs`) — PayHere orders only. The
  platform's own merchant account received the charge, so it owes the
  seller their net share. An admin bundles eligible orders
  (`delivered` + `paid`, not already batched) into a scheduled payout, then
  marks it paid once the bank transfer actually goes out.
- **Fee collections** (`fee_collections`/`fee_collection_order_refs`) — COD
  and bank-transfer orders. The seller received the money directly (COD in
  cash/on delivery, bank-transfer straight into their own account), so
  they owe the platform its fee. Same admin-batches-then-marks-settled
  shape, opposite direction.
- **Stripe Connect orders appear in neither** — Stripe already settled the
  platform's cut automatically at charge time via `application_fee_amount`.

Both ledgers are read-only for the seller (`/dashboard/payouts`); only an
admin can create or settle a batch. See
[`features/payouts.md`](../app/docs/features/payouts.md) for the
seller-facing UI and [`database-model.md#payout`](database-model.md#payout)
for the schema.

## File storage

Product images, store logos/banners, seller verification documents, bank-
transfer receipts, and courier proof-of-handover uploads are all real file
uploads (`multipart/form-data`), not URL-paste fields. Two interchangeable
backends, selected by Spring profile: local disk (default, dev) and Amazon
S3 (`@Profile("aws")`, production). Every stored reference is an internal
path/key, resolved to a fetchable URL fresh at read time — an S3-backed
deployment signs a presigned URL with its own expiry rather than persisting
a fixed one, so callers must always resolve through the storage service,
never assume a stored value is directly fetchable.

## Audit log and admin notifications

A single, write-once `audit_logs` table (`/admin/audit-log`) is the
durable record of **both** admin actions (store approved/rejected, admin
invited, admin login, payout marked paid, fee collection marked collected,
verification change approved/rejected) **and** seller-initiated changes to
their own store (settings updated, verification change requested) — see
[`database-model.md#audit_logs-v9`](database-model.md#audit_logs-v9). This
is distinct from `admin_notifications`, a lighter unread/read activity
feed fired only for the two seller actions an admin has no other way to
observe in real time (bank-detail changes, a new verification change
request) — each also sends a best-effort email to a configured admin
address.

## Core features

Frontend-facing feature behavior is documented in depth under
[`app/docs/features/`](../app/docs/features/) — this table links out rather
than re-explaining each one; see
[`app/docs/feature-index.md`](../app/docs/feature-index.md) for the full
index with pages/components.

| Feature | Summary |
|---|---|
| Marketplace browsing & search | Home page, category filters, keyword search, sort — see [`features/marketplace-browsing.md`](../app/docs/features/marketplace-browsing.md) |
| Store & product detail | Public storefront and product pages — see [`features/store-and-product-detail.md`](../app/docs/features/store-and-product-detail.md) |
| Cart | Client-side, single-store-per-order cart, reconciled against live product data on load — see [`features/cart.md`](../app/docs/features/cart.md) |
| Checkout | Guest or signed-in, COD/online-payment/bank-transfer/pickup — see [`features/checkout.md`](../app/docs/features/checkout.md) |
| Order tracking | Post-checkout confirmation, order-number/phone lookup, bank-transfer receipt upload — see [`features/order-tracking.md`](../app/docs/features/order-tracking.md) |
| Seller auth & onboarding | Real Cognito accounts; onboarding creates a real `Store` in `pending` status — see [`features/seller-auth.md`](../app/docs/features/seller-auth.md) |
| Seller dashboard overview | Revenue/order/stock stat cards, pending-verification banner — see [`features/seller-dashboard-overview.md`](../app/docs/features/seller-dashboard-overview.md) |
| Product management | Seller product CRUD with real image upload — see [`features/product-management.md`](../app/docs/features/product-management.md) |
| Order management | Seller order list, detail, server-enforced status workflow — see [`features/order-management.md`](../app/docs/features/order-management.md) |
| Payouts | Real settlement ledger, read-only for the seller — see [`features/payouts.md`](../app/docs/features/payouts.md) |
| Store settings | Contact/bank/payment-method configuration, post-approval verification-change requests | [`features/store-settings.md`](../app/docs/features/store-settings.md) |
| Platform admin | Real, Cognito-gated `/admin`: store review, verification change requests, payouts, fee collections, admin management, audit log | Not yet a dedicated `app/docs/features/` deep-dive; see [`api-contracts.md#admin--store-review`](api-contracts.md#admin--store-review) |

## What changed since the prototype phase

For anyone who last read this project's docs during the original
frontend-only phase: there is no more `localStorage`-as-database, no more
"any email signs in," no more unauthenticated `/admin`, and no more
single-hardcoded-demo-store design. Every entity described above is a real
Postgres table (see [`database-model.md`](database-model.md)), every
endpoint is a real Spring Boot controller enforcing real authorization
(see [`api-contracts.md`](api-contracts.md)), and the business model itself
has grown a second axis (seller plan tier) beyond the original flat
percentage fee. `gaps-and-assumptions.md` and `roadmap.md` remain current
and track what's still genuinely open (MFA, review/rating submission, a
buyer address book, etc.) — read those for what's left, not this document.
