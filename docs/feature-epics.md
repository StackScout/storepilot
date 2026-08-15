# StorePilot — Feature Epics & Backlog

> A project-management-style view of the codebase: every implemented
> capability grouped into epics (for onboarding a PM/stakeholder), and
> everything known to be missing or incomplete (for roadmap planning).
> Companion to [`docs/roadmap.md`](roadmap.md) and
> [`docs/gaps-and-assumptions.md`](gaps-and-assumptions.md) — those two
> files had drifted out of sync with each other and with the current
> codebase (e.g. both still described buyer accounts as password-less
> after a real Cognito password check was added), so this doc was built
> fresh against the actual current source rather than by trusting either
> one. Cross-reference [`app/docs/feature-index.md`](../app/docs/feature-index.md)
> for exact file paths per feature.
>
> Generated 2026-08-14, updated 2026-08-15 after the Nice-to-have backlog
> round (Epic 11). Re-verify against source before treating any line
> here as current after significant future changes — this is a snapshot,
> not a live view.

---

## Part 1 — Implemented (Epics)

### Epic 1: Marketplace Browsing & Discovery
Public, unauthenticated storefront for buyers to find stores/products/services.

- Home page with category shortcuts
- Keyword search with sort, tabbed Products/Stores results
- Public store profile page (contact, social links, rating, follower count)
- Product detail page with multi-image gallery/carousel
- Service detail page with availability-driven slot picker (see Epic 4)
- Derived 3-mode storefront: a store shows a Products section, a Services
  section, both, or neither — never a manual toggle, purely derived from
  `products.count() > 0` and `bookingsEnabled && active services exist`
- Product and store reviews (star rating + optional comment), gated to
  verified purchases (a delivered order for a product review; a delivered
  order or completed booking for a store review) — no moderation queue,
  reviews go live immediately. Product and store rating/reviewCount are
  each their own running average, not aggregated together

### Epic 2: Cart & Checkout (Products)
- Single-store-per-cart enforcement (adding a second store's product
  requires explicit "replace cart" confirmation)
- Cart persisted client-side (Zustand + `localStorage`), reconciled
  against live product data on every load — flags deleted products,
  refreshes stale prices/stock
- Checkout form: shipping/pickup delivery method, buyer email capture,
  payment method selection dynamically limited to what the store has
  enabled (COD / bank transfer / PayHere / Stripe) and to what the
  seller's plan allows (COD + bank transfer are Pro-only)
- Server-side stock re-validation and price re-computation at order
  creation (never trusts client-submitted prices)
- Order confirmation page with a distinct "payment pending" state for
  bank-transfer orders (upload receipt / cancel)

### Epic 3: Order Fulfillment
- Guest order/booking tracking by number + phone, strengthened with a
  second-factor emailed one-time code (order/booking number + phone
  alone only starts the lookup; the actual record isn't returned until
  the code is verified)
- Signed-in buyer order history
- Buyer-initiated cancellation (blocked once a cutoff/status makes it
  unsafe)
- Seller order management: list with status filters, detail view,
  enforced status state machine (`pending → confirmed/cancelled`,
  `confirmed → shipped/cancelled`, `shipped → delivered`), mandatory
  tracking info on ship, optional courier receipt upload
- Bank-transfer receipt upload (buyer) + seller verify/reject workflow
- Order-lifecycle transactional emails (created, confirmed, shipped,
  delivered, cancelled) via a pluggable `EmailService` (SES in
  production, logging stub otherwise)

### Epic 4: Bookings & Appointments
Second storefront mode for stores that sell services/time instead of (or
alongside) physical products. Full design rationale, business rules, and
the two explicitly-confirmed product decisions (independent per-service
capacity; booking payment methods mirror order payment methods including
identical Pro-plan gating) are in
[`app/docs/features/bookings.md`](../app/docs/features/bookings.md).

- Per-store `bookingsEnabled` toggle in settings
- Bookable service CRUD (name, price, duration, buffer, category locked
  to the store's own category, images, active/draft status)
- Store availability: one weekly open-hours template (7 rows) plus
  date-specific exceptions (closures or special one-off openings),
  slots computed on read (no materialization job). A service inherits the
  store's weekly template by default; a seller can opt one service into
  its own weekly-hours override instead — exceptions always stay
  store-wide (a holiday closure applies to every service regardless)
- Guest/buyer booking checkout: slot picker, buyer details, payment
  method (COD/bank-transfer Pro-gated exactly like orders; PayHere/Stripe
  free-tier available), server-side slot re-validation at submission
- Seller booking management: list, detail, status workflow (`pending →
  confirmed/cancelled`, `confirmed → completed/cancelled/no-show`),
  bank-transfer receipt review
- Buyer booking history + guest tracking by booking number + phone +
  buyer-initiated cancellation (cutoff-based, same lead-time policy as
  booking creation)
- Booking-lifecycle transactional emails, mirroring the order notifier
- Payout/FeeCollection ledgers made polymorphic so a single settlement
  batch can include both order- and booking-sourced income for a store

### Epic 5: Seller Accounts & Onboarding
- Seller registration/login via Amazon Cognito (real password check,
  email verification code flow, Google OAuth option), with opt-in TOTP
  multi-factor authentication (enroll/disable from settings, a
  challenge-response step at login when enrolled)
- Store onboarding: collects business details, country-conditional
  verification documents (NIC + business registration for Sri Lanka,
  ABN for Australia), plan selection (Free/Pro), creates a `pending`
  store awaiting admin approval
- Pending-verification banner shown to a seller whose store isn't yet
  approved
- Store settings: contact info, payout bank account, delivery method
  toggles (pickup), payment method toggles (COD/bank-transfer/online),
  bookings toggle — with Pro-plan clamping on the Pro-gated toggles
- Store verification change-request workflow: a seller requests a
  change to already-approved verification info; an admin reviews and
  approves/rejects rather than the change applying instantly

### Epic 6: Seller Dashboard
- Overview: revenue/pending-order/product/fee stat cards, low-stock
  alert, recent orders table
- Product management: CRUD, multi-image upload with a settable primary
  image, optional per-product/per-store stock management, SKU (optional,
  hidden when unset), category locked to the store's approved category
- Payouts ledger (read-only for the seller; PayHere-sourced income the
  platform owes the seller, released only via `/admin`)
- Fee collections ledger (read-only; COD/bank-transfer income the seller
  owes the platform, settled only via `/admin`)
- Navigation, sidebar, and dashboard-scoped Services/Availability/
  Bookings links that only render once `bookingsEnabled` is on

### Epic 7: Payments & Monetization
- PayHere integration (Sri Lanka): checkout payload/hash generation,
  asynchronous notify-webhook signature verification, both order- and
  booking-aware
- Stripe Connect integration (Australia): standard-account onboarding,
  checkout session creation, webhook handling, refunds — both order- and
  booking-aware
- Cash-on-delivery / bank-transfer: receipt upload + seller
  verify/reject, gated to the seller's Pro plan (settings-layer clamp +
  independent write-time defense-in-depth re-check, applied identically
  to orders and bookings)
- Pro-plan subscription billing: a separate Stripe Checkout flow
  (platform's own revenue, distinct from marketplace transaction
  payments) that upgrades a seller's `SellerPlan`
- Per-store platform transaction fee percentage, computed once at
  order/booking creation and never recalculated after the fact

### Epic 8: Buyer Accounts
- Registration/login via Cognito (real password check, email
  verification)
- A real address book (add/edit/delete any number of saved addresses,
  mark one default, pick a different saved one at checkout)
- Order history and booking history (guest checkout remains the default
  — signing in is optional, not required)

### Epic 9: Platform Admin
- Admin authentication via Cognito (`ROLE_ADMIN`), with the same opt-in
  TOTP MFA available from `/admin/settings`; admins are never
  self-registered — the first is bootstrapped out-of-band
  (`infra/scripts/create-admin.sh`), further admins are invited in-app
- Store approval/rejection (with a required rejection reason)
- Store directory with detail cards
- Payout batch creation and mark-paid (now spans eligible orders *and*
  bookings for a store in one batch)
- Fee collection batch creation and mark-paid (same combined
  order+booking eligibility)
- Accounting summary view
- Admin invite + admin list
- Durable audit log of every verification decision, admin invite, and
  settlement action
- Store verification change-request review queue

### Epic 10: Platform Configuration & Infrastructure
- Multi-country/currency platform configuration (currency, country
  code, region list, ABN-vs-NIC verification requirement, IANA
  timezone, default fee percentage) served from `platform_settings`,
  not hardcoded per deploy
- Pluggable file storage (S3 in production, local disk otherwise) for
  product images, order receipts, and seller verification documents
- Pluggable transactional email delivery (SES in production, a logging
  stub otherwise)
- Infrastructure as CloudFormation (storage/IAM/security/compute/CI-CD
  stacks) with a documented manual deploy path
  (`infra/scripts/sync-and-deploy.sh`, rsync+SSH) and a GitHub Actions
  workflow (`workflow_dispatch`-triggered, OIDC-authenticated, deploys
  via S3 + SSM `RunShellScript` rather than SSH — usable even when the
  operator's own IP isn't in the SSH security-group allowlist)

### Epic 11: Engagement, Growth & Communication
Cross-cutting features layered on top of the marketplace, booking, and
dashboard surfaces (Epics 1–4, 6) to drive buyer retention, give sellers
more revenue/insight tools, and reduce "did anything change?" friction.

- Dark mode toggle (Light/Dark/System, backed by `next-themes`,
  persisted, defaults to the OS preference) — available from the
  marketplace header and both the seller dashboard and admin sidebars
- Buyer "Follow store" action with a live follower count
- Buyer wishlists and saved searches
- Order/booking status-change notes: sellers can attach a note to any
  status transition, shown to the buyer in the timeline (the data model
  already supported this; this round added the UI)
- Real-time order/booking status push — Server-Sent Events for the
  public order/booking pages (no auth needed, the id is proof enough);
  in-app messages use polling instead, since that channel is private and
  the existing SSE hook doesn't send credentials cross-origin
- Time-based reminder notifications (e.g. upcoming-appointment
  reminders) fired on a schedule, not only at status-change points
- In-app buyer↔seller messaging — one conversation thread per
  store+buyer pair, reachable from a store's public page and both the
  buyer account area and seller dashboard
- Coupons/discount codes — platform-wide or per-store, usable on orders
  and/or bookings, with scope/expiry/max-use/minimum-subtotal rules; the
  platform fee is computed on the post-discount amount, so a coupon
  reduces the seller's payout proportionally along with the buyer's price
- Recurring/multi-session bookings — a buyer can check out a single
  weekly-repeating series (up to 12 occurrences) in one flow; a coupon
  applied to the series is only counted as one use
- Premium booking analytics add-on (Pro-plan gated) — revenue, no-show
  rate, top services by revenue, repeat-buyer rate, computed in-memory
  per store (small-business scale; revisit if profiling ever shows this
  as a hot path)
- Dashboard stat-card trend/period comparison

---

## Part 2 — Pending / Backlog

Grouped by rough priority. Items marked **(booking-specific)** are the
explicit v1 scope cuts documented in
[`app/docs/features/bookings.md`](../app/docs/features/bookings.md#explicitly-out-of-scope-for-v1);
the rest are platform-wide gaps carried forward from `roadmap.md` /
`gaps-and-assumptions.md` (re-verified — several previously-listed items
there turned out to already be resolved and are omitted here).

### Should-have (expected of a mature v1)
- **Team roles on a store (booking-specific and platform-wide)** — no
  Owner/Admin/Member concept; one seller = one store, full access.
  Explicitly deferred, not scheduled — see the Should-have backlog
  decision that picked this list up.

### Nice-to-have
Everything else from this list (dark mode, follow store, real-time
status push, timeline notes, trend comparison, wishlists/saved searches,
coupons, in-app messaging, time-based reminders, recurring bookings,
premium booking analytics) shipped and now lives in
[Epic 11](#epic-11-engagement-growth--communication). Two items remain:

- **Localization** (Sinhala/Tamil, given the Sri Lanka market).
- **Google Calendar sync for bookings (booking-specific).**

### Technical debt
- **No frontend automated tests** — backend has MockK unit-test coverage
  for several core services (including the new `BookingServiceTest`),
  but nothing on the Next.js side, and backend coverage isn't
  comprehensive across every service.
- **No pagination on several list views** — some pages still fetch a
  full collection and filter/slice client-side.
- **No typed shared API-query-hooks layer** — query keys are
  hand-written inline at each call site, risk of drift.
- **No error boundaries (`error.tsx`)** on any route.
- **GitHub Actions deploy is `workflow_dispatch`-only** — not yet wired
  to auto-deploy on push to `main`, by design until several manual runs
  prove the pipeline out end-to-end.

### Future scalability
- **Real full-text/external search** (current matching doesn't scale
  past a demo-sized catalog).
- **Automated/scheduled payout and fee-collection runs** — today an
  admin manually clicks "create batch"; no cron/background job.
- **CDN-backed image storage.**
- **Background jobs generally** (reminders, stock alerts, scheduled
  settlement) — no job runner exists yet.
- **Multi-store-per-seller support** — schema is hard 1:1 today.
- **Admin dispute-resolution tooling** — store approval and settlement
  exist; no refund-dispute or buyer-complaint workflow.
- **Category management as backend data** — `StoreCategory` is a fixed
  8-value TypeScript union today, not editable without a frontend
  deploy.
- **A shared/staff capacity model for bookings (booking-specific)** — v1
  deliberately allows independent per-service double-booking; no
  concept of "one appointment across the whole store at a time."
