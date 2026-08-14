# Feature: Bookings

> Index: [`feature-index.md`](../feature-index.md) · Schema:
> [`database-model.md`](../../../docs/database-model.md#booking-v13) · API:
> [`api-contracts.md`](../../../docs/api-contracts.md)

## Purpose

A second storefront mode alongside products — stores that sell time
(appointments) instead of goods, similar to keepmybooking.com. A seller
turns bookings on from Store Settings, lists bookable services, sets a
weekly availability template, and buyers pick a time slot and check out.
The platform takes its usual transaction fee, settled through the same
Payout/Fee Collection ledger products already use.

A store's storefront mode — products-only, services-only, or both — is
**entirely derived**, never a manual selection: Products shows whenever
the store has any; Services shows whenever `bookingsEnabled` is on. Both,
either, or neither can be true at once.

## Business rules

- **`BookableService`/`Booking` are parallel aggregates to `Product`/
  `Order`, not extensions of them.** A service has no stock/SKU/
  compare-price concept; a booking has no delivery-method/shipping-fee/
  tracking concept. See [`database-model.md#booking-v13`](../../../docs/database-model.md#booking-v13)
  for the full schema.
- **A service's category is locked to its store's own approved category**
  — identical rule to `Product`.
- **Availability is store-level, not per-service**, and computed on read
  (never materialized as slot rows): a weekly open-hours template (7 rows,
  one per weekday) plus date-specific exceptions (closures or special
  one-off openings). A given service's bookable slots are the resolved
  day's open window chunked into `duration + buffer`-sized pieces, minus
  anything inside the store's lead-time cutoff, minus anything overlapping
  an existing non-cancelled booking **of that same service**.
- **Independent per-service capacity (confirmed product decision, not a
  gap)**: two different services on the same store can be booked for the
  same time slot — a store selling "Haircut" and "Beard trim" can have both
  booked at 10am by two different buyers. There's no shared "one
  appointment at a time across the whole store" concept, since there's no
  staff/capacity model in v1. A genuine solo provider should avoid
  creating overlapping services, or size duration/buffer accordingly.
- **Booking payment methods mirror order payment methods exactly,
  including Pro-plan gating.** PayHere (Sri Lanka)/Stripe (Australia) are
  available on any plan. Bank-transfer and "Pay at venue" (the `cod` wire
  value, relabeled in the booking UI — not a new enum) are **Pro-only**,
  enforced with the identical settings-clamp + write-time
  defense-in-depth pattern `codEnabled`/`bankTransferEnabled` already use
  for orders.
- **Fee computation is identical to orders**: `platformFee =
  round(servicePrice × store's transactionFeePercent / 100, HALF_UP)`,
  computed once at booking-creation time and never recalculated.
- **Booking status state machine**: `pending → confirmed | cancelled`;
  `confirmed → completed | cancelled | no-show`; the rest terminal. No
  `"shipped"` analog. A "Pay at venue" booking flips to `paid` once marked
  `completed` (the appointment happened), mirroring how a COD order flips
  to `paid` on `delivered`.
- **Lead time is one number, both directions**: `StoreAvailability
  .leadTimeMinutes` (default 120) is both the minimum notice required to
  book a slot, and the cutoff inside which a buyer can no longer
  self-cancel.
- **Ledger polymorphism, not a third ledger.** A payout/fee-collection
  batch can bundle both order-sourced and booking-sourced income for the
  same store in one run — see
  [`database-model.md#payout`](../../../docs/database-model.md#payout) for
  the `PayoutSourceRef`/`FeeCollectionSourceRef` design. Stripe bookings,
  like Stripe orders, never enter either ledger (Connect direct charges
  settle automatically).
- **Slot re-validated server-side at booking time**, not just trusted from
  whatever the buyer's slot picker last fetched — closes the race between
  "buyer viewed availability" and "buyer submitted the booking."

## Explicitly out of scope for v1

Team roles (Owner/Admin/Member), coupons, Google Calendar sync, in-app
messaging, time-based "24h before your appointment" reminder emails
(booking emails only fire at status-change points, mirroring
`OrderNotifier`), a premium-analytics add-on, per-service (as opposed to
store-level) availability schedules, recurring/multi-session bookings, a
shared store-wide capacity model (see the independent-per-service-capacity
decision above).

## Pages

| Path | Type | Notes |
|---|---|---|
| `/dashboard/services`, `/new`, `/[serviceId]/edit` | Client | Service CRUD — mirrors `dashboard/products` exactly, minus stock/SKU |
| `/dashboard/availability` | Client | Weekly template editor + exceptions list |
| `/dashboard/bookings`, `/[bookingId]` | Client | List + detail, status transitions, bank-transfer receipt review |
| `/stores/[slug]` | Server + Client | Services section, content-first ordering with Products (see below) |
| `/stores/[slug]/services/[serviceSlug]` | Server + Client | Service detail + slot picker + booking checkout form |
| `/bookings/[bookingId]` | Client | Booking confirmation/status — mirrors `/orders/[orderId]` |
| `/track-booking` | Client | Guest lookup by booking number + phone — mirrors `/track-order` |
| `/account` | Client | "Booking history" card alongside the existing "Order history" card |

**Storefront section ordering**: whichever of Products/Services has
content leads; Products leads when both (or neither) do. A
`bookingsEnabled` store with zero products omits the Products section
entirely (an empty product grid would just be noise) rather than showing
an empty state — see `store-page-content.tsx`.

## Dashboard nav

`Services`/`Availability`/`Bookings` links only render in the sidebar once
the store's `StoreSettings.bookingsEnabled` is on — see
`dashboard-sidebar.tsx`.

## Backend APIs

- `GET/POST /api/stores/{storeId}/bookable-services`, `GET/PATCH/DELETE
  /api/bookable-services/{id}` — service CRUD, multipart image upload.
  Deletion refused while any non-terminal booking references the service.
- `GET/PUT /api/stores/{storeId}/availability`,
  `/availability/weekly-rules`, `POST/DELETE
  /availability/exceptions/{id}` — availability management.
- `GET /api/stores/{storeId}/bookable-services/{serviceId}/availability?from=&to=`
  — computed slots for a date range (defaults to the next 30 days).
- `POST /api/bookings`, `GET /api/bookings/{id}`, `GET
  /api/bookings/lookup`, `PATCH /api/bookings/{id}/status`, `POST
  /api/bookings/{id}/cancel`, `/receipt`, `/verify-bank-transfer` — mirror
  the equivalent order endpoints one-for-one.
- `POST /api/bookings/{id}/payhere-checkout`, `/stripe-checkout` — sibling
  methods to the order checkout builders, sharing the same hash/format
  helpers. The PayHere notify webhook and the Stripe webhook both try
  `Order` first, then `Booking`, by id.
- `GET /api/stores/{storeId}/payouts/eligible-bookings`,
  `/fee-collections/eligible-bookings` — booking eligibility, alongside
  the existing `eligible-orders` endpoints. `createBatch` on both ledgers
  bundles eligible orders and bookings into one run.

## Permissions

Same shape as orders: booking creation/lookup/cancel/receipt-upload are
guest-reachable (the booking id, or booking number + phone, is the
credential); status updates and bank-transfer verification require the
owning seller.

## Edge cases

- A store toggles `bookingsEnabled` on but hasn't added any services yet
  → Services section renders with its own `EmptyState`.
- A service is deleted while it has upcoming bookings → refused (`409`),
  not silently orphaning the bookings.
- Two buyers race for the same slot → the second `POST /api/bookings`
  re-validates server-side and is rejected once the first commits.

## Future improvements

- Per-service (not just store-level) availability schedules.
- A shared-capacity/staff model for genuinely single-provider stores.
- Coupons, Google Calendar sync, in-app messaging (all explicitly deferred
  above).
- Time-based pre-appointment reminder emails (needs new job infrastructure
  beyond the existing status-change-only notifier pattern).
- Per-store timezone (currently one deployment-wide `platform_settings
  .timezone`, matching this codebase's existing single-deployment-per-
  country model).
