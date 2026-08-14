# Feature Index

> Cross-references: [`overview.md`](../../docs/overview.md) ·
> [`frontend-architecture.md`](frontend-architecture.md) ·
> [`user-flows.md`](user-flows.md) · [`ui-components.md`](ui-components.md)

Every implemented feature, with its pages, components, and full
documentation file. "Pages" are file paths under `src/app/`; "Components"
are the app-specific (non-`ui/`) components primarily responsible for the
feature — shared primitives (`Button`, `Card`, etc.) are omitted here and
covered in [`ui-components.md`](ui-components.md).

| # | Feature | Description | Related pages | Related components | Docs |
|---|---|---|---|---|---|
| 1 | Marketplace browsing & search | Home page, category shortcuts, keyword search with sort and a products/stores tab | `src/app/(marketplace)/page.tsx`, `src/app/(marketplace)/search/page.tsx` | `CategoryFilter`, `SearchBar`, `ProductCard`, `StoreCard`, `MobileNav`, `SiteHeader`, `SiteFooter` | [`features/marketplace-browsing.md`](features/marketplace-browsing.md) |
| 2 | Store & product detail | Public storefront page and product detail page, related products | `src/app/(marketplace)/stores/[slug]/page.tsx`, `src/app/(marketplace)/stores/[slug]/products/[productSlug]/page.tsx` | `StoreProductGrid`, `AddToCartControls`, `RatingStars`, `PriceDisplay` | [`features/store-and-product-detail.md`](features/store-and-product-detail.md) |
| 3 | Cart | Single-seller-per-order cart, add/update/remove, cross-store conflict handling, persisted client-side; reconciles against live product data on load — flags deleted products, refreshes stale prices | `src/app/(marketplace)/cart/page.tsx` | `CartDrawer`, `AddToCartControls`, `QuantityStepper`, `useCart`/`useCartReconciliation` hooks, `useCartStore` | [`features/cart.md`](features/cart.md) |
| 4 | Checkout | Shipping form (now collects email for the receipt), COD/PayHere selection, order creation, stock decrement; prefills from and saves to a signed-in buyer's default address | `src/app/(marketplace)/checkout/page.tsx`, `checkout-form.tsx` | (page-local form, no dedicated sub-components) | [`features/checkout.md`](features/checkout.md) |
| 5 | Order tracking (buyer) | Post-checkout confirmation page and order-number + phone lookup for returning buyers; bank-transfer orders show a distinct "Payment pending" state with upload/cancel actions until a receipt is confirmed | `src/app/(marketplace)/orders/[orderId]/page.tsx`, `src/app/(marketplace)/track-order/page.tsx` | `OrderStatusBadge`, `CancelOrderButton` | [`features/order-tracking.md`](features/order-tracking.md) |
| 6 | Seller auth & onboarding | Mock sign-in (any email); onboarding now creates a **real** `Store`+`StoreSettings` in `pending` verification status, collecting NIC/business-reg/bank details; sign-out; session-gated dashboard | `src/app/login/page.tsx`, `src/app/onboarding/page.tsx`, `src/proxy.ts` | `PendingVerificationBanner`, `SellerStoreProvider`/`useSellerStoreId` (server actions in `src/lib/actions/auth.ts`) | [`features/seller-auth.md`](features/seller-auth.md) |
| 7 | Seller dashboard overview | Revenue/pending-order/product/fee stat cards, low-stock alert, recent orders table | `src/app/dashboard/page.tsx` | `StatCard`, `OrderStatusBadge`, `TableRowSkeleton`, `EmptyState` | [`features/seller-dashboard-overview.md`](features/seller-dashboard-overview.md) |
| 8 | Product management (seller) | Product list, create, edit, delete with confirmation | `src/app/dashboard/products/page.tsx`, `.../products/new/page.tsx`, `.../products/[productId]/edit/page.tsx` | `ProductForm`, `ImageUploader` | [`features/product-management.md`](features/product-management.md) |
| 9 | Order management (seller) | Order list with status filters, order detail, status-transition workflow | `src/app/dashboard/orders/page.tsx`, `.../orders/[orderId]/page.tsx` | `OrderStatusSelect`, `OrderStatusBadge` | [`features/order-management.md`](features/order-management.md) |
| 10 | Payouts | Real `Payout` ledger: available/scheduled/paid, read-only for the seller — release only via `/admin` | `src/app/dashboard/payouts/page.tsx` | `StatCard`, `Badge` | [`features/payouts.md`](features/payouts.md) |
| 11 | Store settings | Contact info, payout bank account, COD/online payment toggles | `src/app/dashboard/settings/page.tsx` | (page-local form) | [`features/store-settings.md`](features/store-settings.md) |
| 12 | Global navigation & layout chrome | Site header/footer, mobile nav, dashboard sidebar/mobile nav, cart drawer | `src/app/layout.tsx`, `src/app/(marketplace)/layout.tsx`, `src/app/dashboard/layout.tsx` | `SiteHeader`, `SiteFooter`, `MobileNav`, `DashboardSidebarContent`, `DashboardMobileNav`, `CartDrawer`, `Logo` | Covered within each feature doc above; no standalone doc |
| 13 | Platform admin (mock) | Unauthenticated internal tool: approve/reject pending stores (with rejection reason), create payout batches per store, mark payouts paid | `src/app/admin/page.tsx` | (page-local; reuses `Dialog`, `Badge`, `TableRowSkeleton`, `EmptyState`) | [`features/seller-auth.md`](features/seller-auth.md#admin-not-a-real-role) |
| 14 | Buyer accounts (optional) | Real (no-password) register/sign-in, one saved address, order history — guest checkout unaffected and remains the default | `src/app/account/register/page.tsx`, `src/app/account/login/page.tsx`, `src/app/account/page.tsx` | `AccountView`, `useBuyerAccountLink` (server actions in `src/lib/actions/auth.ts`) | [`features/buyer-accounts.md`](features/buyer-accounts.md) |
| 15 | Bookings | Opt-in second storefront mode (`StoreSettings.bookingsEnabled`) for stores that sell services instead of/alongside products: service CRUD, weekly availability + exceptions, computed slot picker, guest/buyer booking checkout (COD/bank-transfer Pro-gated identically to orders), seller status workflow, and a polymorphic Payout/Fee Collection ledger shared with orders | `src/app/dashboard/services/page.tsx`, `.../availability/page.tsx`, `.../bookings/page.tsx`, `src/app/(marketplace)/stores/[slug]/services/[serviceSlug]/page.tsx`, `.../bookings/[bookingId]/page.tsx`, `.../track-booking/page.tsx` | `ServiceForm`, `ServiceCard`, `StoreServiceGrid`, `ServiceBookingForm`, `BookingStatusBadge`, `BookingStatusSelect`, `CancelBookingButton` | [`features/bookings.md`](features/bookings.md) |

## Not-implemented / partial features to be aware of

These are referenced by the UI, mock data, or business copy but have no
working implementation. See [`roadmap.md`](../../docs/roadmap.md) and
[`gaps-and-assumptions.md`](../../docs/gaps-and-assumptions.md) for full detail — don't
assume they exist when planning backend work.

- **Real transactional email** — order-lifecycle emails go through a real
  backend abstraction now (`EmailService`/`OrderNotifier`, see
  [`features/notification-emails.md`](features/notification-emails.md)),
  but the only implementation is a mock that logs instead of sending; no
  provider (SES etc.) is wired up yet.
- **Buyer account passwords** — `/account/register`/`/account/login` are a
  real account system (unlike seller `/login`), but there is still no
  password/OTP/magic-link — see
  [`features/buyer-accounts.md`](features/buyer-accounts.md) and
  [`gaps-and-assumptions.md`](../../docs/gaps-and-assumptions.md).
- **`/login` → store lookup for returning sellers** — `/onboarding` now
  creates a real `Store`/`StoreSettings` row per application (in `pending`
  status until admin-approved), but there is still no `User`/`Seller`
  entity, so `/login` has no way to route a returning email back to the
  store it created — it always signs into the single hardcoded demo store.
  See [`gaps-and-assumptions.md`](../../docs/gaps-and-assumptions.md#undocumented--unclear-assumptions-worth-confirming-with-product).
- **`/admin` has no authentication** — the mock store-approval/payout-release
  tool is reachable by anyone who finds the URL. See
  [`gaps-and-assumptions.md`](../../docs/gaps-and-assumptions.md#admin-has-no-authentication-or-authorization-at-all).
- **Product reviews** — `rating`/`reviewCount` are static display numbers
  on `Store`/`Product`; there is no review submission UI or endpoint.
- **Real image upload** — `ImageUploader` is a URL-paste field, no file
  upload/storage integration.
- **Real payment gateway** — "PayHere" is a selectable payment method that
  immediately marks the order `paymentStatus: "paid"` client-side; there is
  no actual gateway call, redirect, or webhook.
- **Dark mode toggle** — `next-themes` is installed but not wired into any
  provider or UI control.
