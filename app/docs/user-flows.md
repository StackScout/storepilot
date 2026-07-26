# User Flows

> Cross-references: [`feature-index.md`](feature-index.md) ·
> [`docs/features/*`](features) · [`overview.md`](../../docs/overview.md)

Step-by-step journeys through the app as currently implemented. Flows the
task brief names that **don't exist** in this codebase (buyer registration,
buyer profile management, true merchant registration) are called out
explicitly rather than invented — see the end of this document.

## 1. Browse marketplace (buyer)

1. Buyer lands on `/` (home page, Server Component).
2. Sees hero banner, category shortcuts (`CategoryFilter`), featured
   products (top 8 by rating), popular stores (first 6 stores).
3. Clicks a category chip → navigates to `/search?category=<cat>`.
4. Clicks "Browse products" → navigates to `/search`.
5. Clicks a product/store card → navigates to product/store detail page.

Docs: [`features/marketplace-browsing.md`](features/marketplace-browsing.md)

## 2. Search products

1. Buyer types a keyword into `SearchBar` (header, or `/search`'s mobile
   bar) and submits (native GET form) → navigates to `/search?q=<term>`.
2. `/search` (Server Component) fetches products and stores matching `q`
   (and `category`, if set) in parallel.
3. Buyer switches between "Products" / "Stores" tabs (`?tab=stores`,
   preserves `q`/`category`).
4. Buyer picks a sort option (products tab only): Newest / Top rated /
   Price low-high / Price high-low (`?sort=<opt>`).
5. Buyer clicks a category chip to further narrow results
   (`?category=<cat>`, preserves `q`).
6. Zero matches → empty state with contextual copy.

Docs: [`features/marketplace-browsing.md`](features/marketplace-browsing.md)

## 3. View store & product detail

1. From a store card or a product's breadcrumb, buyer reaches
   `/stores/[slug]`.
2. Sees banner, logo, name, verified badge (if any), rating, city,
   follower count, category, description, and a "Message on WhatsApp"
   button (opens `wa.me/<number>` in a new tab).
3. Scrolls the store's product grid (`StoreProductGrid` — server-rendered
   then client-refetched).
4. Clicks a product → `/stores/[slug]/products/[productSlug]`.
5. Sees image, price (with strikethrough compare-at price if discounted),
   stock status, rating, description, SKU, category, and "More from
   [store]" related products (same store, up to 4).
6. Either store or product not found → 404 page.

Docs: [`features/store-and-product-detail.md`](features/store-and-product-detail.md)

## 4. Add to cart (including cross-store conflict)

1. On a product page, buyer adjusts quantity (`QuantityStepper`, bounded by
   `stockQuantity`) and clicks "Add to cart".
2. **If cart is empty or already holds items from the same store**: item
   added/merged, success toast shown, header cart badge updates.
3. **If cart holds items from a different store**: a dialog appears —
   "Start a new cart? Your cart has items from `<other store>`... Replace
   your cart to add items from `<this store>` instead?" Buyer chooses
   Cancel (stays as-is) or "Replace cart" (clears old cart, starts fresh
   with the new item, page refreshes).

Docs: [`features/cart.md`](features/cart.md)

## 5. View / edit cart

1. Buyer opens the cart drawer (header icon) **or** navigates to `/cart`.
2. On load, the cart re-fetches every held product (`useCartReconciliation`)
   — a product the seller deleted appears greyed out with a "No longer
   available" badge instead of vanishing or showing stale data; a product
   whose price changed shows the current price.
3. Sees line items (image, name, price, quantity stepper, remove button)
   and a subtotal/shipping/total summary — unavailable items are excluded
   from both.
4. Adjusts quantity (clamped to stock) or removes an item — updates
   instantly (Zustand, no network call).
5. Removing the last item empties the cart → empty state ("Browse
   products").
6. Clicks "Proceed to checkout" → `/checkout`, or "Continue shopping" →
   `/search`.

Docs: [`features/cart.md`](features/cart.md)

## 6. Checkout

1. Buyer on `/checkout` (redirected to `/cart` if the cart is empty).
2. If signed in (flow 23), the form prefills full name/phone/address/email
   from the buyer's saved default address once it loads; a small note says
   "Prefilled from your saved address — feel free to edit it below."
3. Fills shipping form: full name, **email** (where the receipt is sent),
   phone, address, city, district (select), postal code.
4. Chooses payment method: Cash on Delivery (default) or PayHere (online).
5. Reviews order summary (items, subtotal, flat shipping fee, total) in a
   sticky sidebar — same reconciliation as the cart page; "Place order" is
   disabled with an inline warning if any item is no longer available.
6. Clicks "Place order".
   - **Success**: cart cleared, a mock receipt email is "sent" (logged
     only — see [`features/checkout.md`](features/checkout.md)), success
     toast, redirected to `/orders/[order.id]`. Stock decremented
     server-side for each item. If signed in, this address is saved as the
     buyer's new default (best-effort).
   - **Failure**: generic error toast, stays on page, cart retained.

Docs: [`features/checkout.md`](features/checkout.md)

## 7. View order confirmation (buyer, immediately after checkout)

1. Buyer lands on `/orders/[orderId]` right after placing an order (no
   login required — the URL itself is the "credential").
2. Sees confirmation header, order number, status badge, full status
   timeline, items, totals breakdown, delivery address, payment method.
3. Can click "Message seller" (WhatsApp) or "Continue shopping".
4. If the order can't be found (e.g. different browser/device), sees an
   empty state explaining the localStorage-based limitation of this build.

Docs: [`features/order-tracking.md`](features/order-tracking.md)

## 8. Track an order later (buyer, no direct link)

1. Buyer navigates to `/track-order` (footer/header link).
2. Enters order number and the phone number used at checkout.
3. Submits → looked up by order number (exact, case-insensitive) + last 9
   digits of phone.
   - **Found**: redirected to `/orders/[order.id]` (same page as flow 7).
   - **Not found**: inline error message, stays on page.

Docs: [`features/order-tracking.md`](features/order-tracking.md)

## 9. Contact seller via WhatsApp

Available from: store page, product page, order confirmation page. Always
opens `https://wa.me/<digits-only-number>` in a new tab — no in-app
messaging exists.

## 10. Seller sign-in (mock)

1. Seller navigates to `/login` (directly, or redirected here by `proxy.ts`
   when hitting `/dashboard/*` without a session — `redirectTo` preserved
   in the query string).
2. Enters **any** email (no password field exists) and submits.
3. **Empty email** → redirected back to `/login?error=missing-email`, error
   text shown.
4. **Any non-empty email** → session cookie set (role `seller`, hardcoded
   `storeId: "store-01"`, the entered email) → redirected to `redirectTo`
   or `/dashboard`.

Docs: [`features/seller-auth.md`](features/seller-auth.md)

## 11. Seller "sign up" / onboarding (creates a real, pending store)

1. Prospective seller navigates to `/onboarding`.
2. Fills store name, tagline, category, description, city, district,
   WhatsApp number, contact email/phone, seller type (Individual/Business),
   NIC number, business registration number (required only if Business),
   and bank account details (bank name, account name, account number) —
   all client-validated (zod, with `sellerType`-conditional requirements).
3. Checks "I agree to IslandCart's seller terms" (required to submit).
4. Submits → a **real** `Store` + `StoreSettings` row is created
   client-side (`storesService.createStore` /
   `updateStoreSettings`), with `verificationStatus: "pending"`, then a
   session is created for the new store (`createSellerSession`) and the
   seller is redirected to `/dashboard`.
5. Seller lands on their **own new store's** dashboard (not the demo
   store), but sees a pending-verification banner (flow 12) and their
   storefront is not yet publicly visible — see flow 22 for how it becomes
   active.

Docs: [`features/seller-auth.md`](features/seller-auth.md) — the remaining
gap is a `User`/`Seller` account record: there's still no way for `/login`
to map a returning email back to the store this flow created (flow 10
always signs into the hardcoded demo store). See
[`gaps-and-assumptions.md`](../../docs/gaps-and-assumptions.md).

## 12. Seller dashboard overview

1. After sign-in, seller lands on `/dashboard`.
2. If their store's `verificationStatus` is `"pending"` or `"rejected"`,
   sees a banner (`PendingVerificationBanner`) above everything else —
   amber "your application is under review" or red "rejected: `<reason>`".
   A `"rejected"` store's storefront stays hidden; nothing in the UI lets
   the seller resubmit today (would require re-running `/onboarding`,
   which creates a second, separate store).
3. Sees stat cards: total revenue, pending orders, active products
   (mislabeled — includes drafts/out-of-stock too, see
   [`features/seller-dashboard-overview.md`](features/seller-dashboard-overview.md)),
   platform fees.
3. Sees a low-stock alert banner if any active product has `≤5` units left,
   with a link to the products page.
4. Sees a recent-orders table (latest 5), each linking to its detail page.

Docs: [`features/seller-dashboard-overview.md`](features/seller-dashboard-overview.md)

## 13. Create a product (seller)

1. From `/dashboard/products`, click "New product" → `/dashboard/products/new`.
2. Fill: image (paste URL or "use a sample image"), name, description,
   category, SKU, price, compare-at price (optional), stock quantity,
   status (Active/Draft).
3. Submit → validated (zod) → product created → success toast → redirected
   to `/dashboard/products`, list refetches (React Query invalidation).
4. If `stockQuantity` was `0`, the product's actual status is forced to
   `"out-of-stock"` regardless of the selected Active/Draft choice.

Docs: [`features/product-management.md`](features/product-management.md)

## 14. Edit a product (seller)

1. From `/dashboard/products`, click the edit icon on a row →
   `/dashboard/products/[productId]/edit`.
2. Form prefilled from the fetched product.
3. Edit any field, submit → same validation/forced-status rule as create →
   success toast → redirected to the product list.
4. Note: editing the product's `name` does **not** regenerate its `slug`
   (URL stays the same).

Docs: [`features/product-management.md`](features/product-management.md)

## 15. Update inventory (seller)

There is no dedicated "adjust stock" flow separate from editing a product —
inventory is one field (`stockQuantity`) inside the same edit-product form
described in flow 14. Reaching `0` auto-flips status to `"out-of-stock"`;
raising it back above `0` does **not** auto-restore `"active"` status (the
seller must also manually reselect it — see product-management doc's edge
cases).

## 16. Delete a product (seller)

1. From `/dashboard/products`, click the delete (trash) icon on a row.
2. Confirmation dialog appears, naming the product and its price.
3. Confirm → product permanently deleted, list refetches. Cancel → dialog
   closes, no change.
4. Historical orders referencing this product are unaffected (order items
   are immutable snapshots, not live references).

Docs: [`features/product-management.md`](features/product-management.md)

## 17. View & filter orders (seller)

1. From `/dashboard/orders`, seller sees a filter chip row (All / Pending /
   Confirmed / Shipped / Delivered / Cancelled).
2. Clicking a chip filters the (already-fetched) order list client-side.
3. Table shows order number, customer name, item count, date, total,
   payment method, status badge; order number links to detail.

Docs: [`features/order-management.md`](features/order-management.md)

## 18. Manage an order / update its status (seller)

1. Seller opens `/dashboard/orders/[orderId]`.
2. Sees items, subtotal/shipping/platform-fee/net-payout breakdown,
   customer contact, delivery address, payment method + status.
3. Uses the status `Select` (`OrderStatusSelect`) to advance the order —
   only valid next statuses are offered per the state machine (see
   [`features/order-management.md`](features/order-management.md#business-rules)).
4. On change: success toast, badge/select update, and (side effects)
   `paymentStatus` flips to `paid` (COD + delivered) or `refunded`
   (previously-paid + cancelled) where applicable.
5. `delivered`/`cancelled` orders show a disabled select (terminal states).

Docs: [`features/order-management.md`](features/order-management.md)

## 19. View payouts (seller)

1. Seller opens `/dashboard/payouts`.
2. Sees three stat cards: **Available** (eligible orders — delivered +
   paid — not yet in any payout batch), **Scheduled** (sum of the seller's
   `"scheduled"` `Payout` batches), **Paid out** (sum of `"paid"` batches).
3. Sees a payout history table (one row per `Payout` batch: date, order
   count, subtotal, platform fee, net amount, status badge, bank
   reference once paid).
4. The page is **read-only** — there is no "request payout" action. A note
   explains payout batches are created and released by IslandCart (via
   `/admin`, flow 22), not requested by the seller.

Docs: [`features/payouts.md`](features/payouts.md)

## 20. Update store settings (seller)

1. Seller opens `/dashboard/settings`.
2. Edits contact email/phone, bank name/account holder/account number,
   and toggles Cash on Delivery / Online payment.
3. Submits → validated (zod) → saved → success toast.
4. **Note**: the COD/online-payment toggles have no effect on the actual
   checkout page today (see
   [`features/store-settings.md`](features/store-settings.md#business-rules)).

Docs: [`features/store-settings.md`](features/store-settings.md)

## 21. Seller sign-out

1. From the dashboard sidebar (desktop) or mobile nav, click "Sign out".
2. Session cookie deleted, redirected to `/login`.

Docs: [`features/seller-auth.md`](features/seller-auth.md)

## 22. Review store applications & release payouts (admin, mock — no auth)

1. Anyone who navigates to `/admin` sees an internal tool badge ("no auth
   in this demo") and two sections — no session or role check gates this
   page today.
2. **Store applications**: lists every store with `verificationStatus:
   "pending"`, with its onboarding details (seller type, NIC, business
   registration number if any). "Approve" immediately flips the store to
   `verificationStatus: "active"` (now publicly discoverable everywhere —
   search, home, its own storefront URL). "Reject" opens a dialog
   requiring a reason, then flips the store to `"rejected"` and stores the
   reason on `StoreSettings.rejectionReason` (shown back to the seller via
   flow 12's banner).
3. **Payout runs**: lists every `"active"` store alongside its
   payout-eligible total; "Create payout batch" bundles **all** of a
   store's eligible orders into one new `"scheduled"` `Payout` (throws if
   there are none). Below that, a table of every `Payout` across all
   stores, with a "Mark as paid" action (optional bank reference field)
   that flips a `"scheduled"` payout to `"paid"`.
4. Nothing here checks whether the bank transfer was actually sent —
   "Mark as paid" is purely a record-keeping action for whoever operates
   this page.

Docs: [`features/seller-auth.md#admin-not-a-real-role`](features/seller-auth.md#admin-not-a-real-role),
[`features/payouts.md`](features/payouts.md)

## 23. Create a buyer account

1. Buyer clicks the account icon in the header (shows "Sign in" when
   signed out) or the "Sign in / Register" link in the mobile menu →
   `/account/login`, then "Create an account" → `/account/register`.
2. Fills name, email, phone (optional) — all client-validated (zod).
3. Submits → `buyersService.registerBuyer(...)` creates a real `Buyer` row
   client-side, rejecting a duplicate email with *"An account with this
   email already exists. Try signing in instead."* → a session is
   established (`createBuyerSession`) → redirected to `/account` (or
   `redirectTo`, if the buyer was sent here from a gated page).

Docs: [`features/buyer-accounts.md`](features/buyer-accounts.md)

## 24. Sign in / out (buyer)

1. Buyer opens `/account/login` and enters their account's email — no
   password.
2. Submits → `buyersService.getBuyerByEmail(email)` performs a **real**
   lookup (unlike seller `/login`, which signs in as the same demo seller
   regardless of what's typed).
   - **Found**: session established, redirected to `/account` or
     `redirectTo`.
   - **Not found**: inline error — *"No account found with that
     email."* — with a link to register instead.
3. From `/account`, clicking "Sign out" clears the session and redirects to
   `/` (not `/login` — there's no buyer-only area that requires the buyer
   to be signed out of).

Docs: [`features/buyer-accounts.md`](features/buyer-accounts.md)

## 25. View account (buyer)

1. Buyer navigates to `/account` (redirected to `/account/login` if not
   signed in, `redirectTo` preserved).
2. Sees their name/email/phone, their one saved address (or "No saved
   address yet — it's saved automatically the first time you check out"),
   and an order history list (empty state if none yet).
3. Clicking an order row goes to `/orders/[order.id]` — the same public
   order-confirmation page used by guests (flow 7), not a separate
   buyer-only view.
4. Only orders placed **while signed in** appear here — a guest order
   placed with the same email does not retroactively show up (no "claim
   my orders" flow).

Docs: [`features/buyer-accounts.md`](features/buyer-accounts.md)

---

## Flows requested in the brief that do NOT exist in this codebase

Per instructions, these are documented as absent rather than invented:

- **Merchant registration (real), fully** — see flow 11; a real `Store` +
  `StoreSettings` **is** created and gated behind admin approval, which
  covers most of what "real registration" implies. What's still missing is
  the **account** half: no password, no `User`/`Seller` entity, so a
  returning seller can't sign back in via `/login` and land on the store
  they created (that flow still always signs into the hardcoded demo
  store). Needs `POST /api/auth/register`'s full shape in
  [`api-contracts.md`](../../docs/api-contracts.md#post-apiauthregister).
- **Profile management (buyer)** — **partially resolved**: flows 23–25
  cover account creation, sign-in, and viewing profile/orders/address, but
  there is still no *edit* form for name/phone (set once at registration)
  and no password to manage (there isn't one).
- **Profile management (seller)** — still fully absent. The closest
  equivalent is [Store Settings](#20-update-store-settings-seller) (flow
  20), which manages the *store's* contact/bank info, not a personal seller
  profile (name, password, notification preferences, etc. — none of that
  exists either).
