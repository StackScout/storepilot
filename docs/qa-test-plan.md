# StorePilot — Manual QA Test Plan

> End-to-end manual test cases for every implemented feature, organized to
> match the epics in [`docs/feature-epics.md`](feature-epics.md). Written
> for a QA tester with no prior codebase knowledge — each section states
> its own preconditions.
>
> **Test data conventions used below:**
> - "Store A" = any approved (`verificationStatus: active`) store with at
>   least one product and, where noted, bookings enabled with at least
>   one active service.
> - "Free-plan store" / "Pro-plan store" = a store whose seller's
>   `SellerPlan` is `FREE` / `PRO` respectively — required to test the
>   Pro-gating cases correctly.
> - Run the full pass against a real deployed environment, not
>   `localhost`, at least once before sign-off — some behavior (SES
>   emails, S3-backed uploads, PayHere/Stripe redirects) only engages
>   under the `aws` profile.
> - Where a case says "as guest," ensure you are signed out of any buyer
>   account first.

---

## 1. Marketplace Browsing & Discovery

| ID | Steps | Expected Result |
|---|---|---|
| MKT-01 | Load the home page. | Category shortcuts and a product/store grid render without error; page is server-rendered (view source shows content, not just a loading shell). |
| MKT-02 | Click a category shortcut. | Search results filter to that category only. |
| MKT-03 | Enter a keyword matching a known product name in the search bar. | Matching products appear in the Products tab. |
| MKT-04 | Enter a keyword matching a known store name. | Matching stores appear in the Stores tab. |
| MKT-05 | Enter a keyword matching nothing. | An empty state renders — no error, no crash. |
| MKT-06 | Change the sort order on search results. | Result order changes accordingly (e.g. price low→high actually reorders). |
| MKT-07 | Bookmark/copy a search URL with a query + filter, then open it in a new tab. | The same filtered results load directly from the URL — filter state is not client-only. |
| MKT-08 | Visit a store's public page (`/stores/{slug}`) for a store with both products and bookable services. | Both a Products section and a Services section render; Products section appears first. |
| MKT-09 | Visit a store's public page for a store with bookings enabled but **zero** products. | Only the Services section renders; no empty "Products" section/heading appears. |
| MKT-10 | Visit a store's public page for a store with bookings **disabled** and products. | Only the Products section renders; no Services section, no broken link to it. |
| MKT-11 | Visit a store's public page for a store with bookings enabled but **zero active services**. | Services section is omitted (behaves like MKT-10), not shown empty. |
| MKT-12 | On a product detail page, cycle through multiple product images. | Gallery/carousel navigates correctly; primary image shown first. |
| MKT-13 | Visit a product detail page for a product belonging to a `pending`/unverified store. | 404 or not-found — unverified stores' products are not publicly reachable. |
| MKT-14 | Visit a product detail page for a `draft`-status product as a random visitor. | Not visible/404 (only the owning seller should see drafts). |

---

## 2. Cart & Checkout (Products)

**Precondition:** a browser with no existing cart.

| ID | Steps | Expected Result |
|---|---|---|
| CART-01 | Add a product from Store A to the cart. | Cart icon/badge updates; item appears in cart drawer and `/cart`. |
| CART-02 | Increase/decrease quantity in the cart. | Subtotal recalculates live; quantity cannot go below 1 or above available stock (if stock-managed). |
| CART-03 | Remove an item from the cart. | Item disappears; subtotal updates; empty-cart state shows if it was the last item. |
| CART-04 | With Store A's product already in the cart, attempt to add a product from a **different** store B. | Blocked with an explicit prompt to replace the cart — never silently merged. |
| CART-05 | Confirm the "replace cart" prompt from CART-04. | Cart now contains only Store B's item. |
| CART-06 | Reload the browser (or reopen after closing the tab) with items in the cart. | Cart contents persist (not lost on reload). |
| CART-07 | While a product is in the cart, have the seller (in another session/tab) delete that product, then reload the cart page. | The item is flagged "No longer available" / greyed out, excluded from subtotal; checkout is blocked until removed. |
| CART-08 | While a product is in the cart, have the seller change its price, then reload the cart page. | Cart shows the updated price, not the stale one. |
| CART-09 | Proceed to checkout with an empty cart (e.g. via direct URL). | Redirected away / blocked — checkout is not reachable with nothing to buy. |
| CHK-01 | From a non-empty cart, go to checkout. | Shipping/contact form renders with delivery-method and payment-method choices. |
| CHK-02 | On a store with `pickupEnabled`, select "Pickup" as delivery method. | Shipping address fields are hidden/not required; order total excludes shipping fee. |
| CHK-03 | On a store with `pickupEnabled` off, attempt checkout. | Only shipping delivery is offered; shipping fee applied. |
| CHK-04 | On a store with only PayHere enabled, view the payment method options. | Only PayHere appears — COD/bank-transfer are not offered. |
| CHK-05 | On a **Free-plan** store with COD/bank-transfer flags stored as enabled (a stale/legacy config), attempt to submit an order with COD selected. | Server rejects with a 409/error ("doesn't offer cod payments") even if the option rendered — confirms write-time Pro-gating, not just a UI hide. |
| CHK-06 | On a **Pro-plan** store with COD enabled, submit an order with COD. | Order succeeds, created with `paymentStatus: unpaid`/`pending`, no card details ever collected. |
| CHK-07 | Submit checkout leaving a required field blank (e.g. phone). | Inline validation error; submission blocked client-side. |
| CHK-08 | Submit checkout for a stock-managed product with quantity exceeding current stock (simulate by having stock drop between add-to-cart and submit). | Server rejects with 409 — no order created, no stock oversold. |
| CHK-09 | Complete checkout successfully. | Redirected to order confirmation page showing order number, items, total, and status. |
| CHK-10 | Complete a bank-transfer checkout. | Confirmation page shows a distinct "payment pending" banner with an "upload receipt" action, not a generic success state. |
| CHK-11 | Complete a PayHere checkout (sandbox). | Redirected to PayHere's hosted payment page; on completion, redirected back and order eventually reflects paid status once the webhook lands. |
| CHK-12 | Complete a Stripe checkout (test mode) for an AU store. | Redirected to Stripe Checkout; on completion, redirected back with order marked paid once webhook lands. |

---

## 3. Order Fulfillment

| ID | Steps | Expected Result |
|---|---|---|
| ORD-01 | As guest, visit `/track-order`, enter a valid order number + phone. | Order detail loads. |
| ORD-02 | Same as ORD-01 but with a wrong phone number. | Lookup fails with an error, no order data leaked. |
| ORD-03 | As a signed-in buyer who placed an order, visit account order history. | The order appears without needing to re-enter order number/phone. |
| ORD-04 | As the buyer, cancel a `pending` order within the allowed window. | Order status becomes `cancelled`; timeline updated. |
| ORD-05 | As the buyer, attempt to cancel an order already `shipped`/`delivered`, or past any cancellation cutoff. | Cancellation is blocked/not offered. |
| ORD-06 | As the seller, open the order list and filter by status. | Only orders matching the selected status show. |
| ORD-07 | As the seller, attempt an illegal status transition (e.g. `pending → delivered` directly, skipping `confirmed`/`shipped`). | Rejected — only the next legal status(es) are selectable/accepted. |
| ORD-08 | As the seller, move an order `pending → confirmed → shipped`, entering required tracking info at the ship step. | Each transition succeeds; tracking number/carrier is mandatory to reach `shipped` and is visible to the buyer afterward. |
| ORD-09 | As the seller, mark a `shipped` order `delivered`. | Status updates; for a COD order this should also flip `paymentStatus` to paid. |
| ORD-10 | As the buyer, upload a bank-transfer receipt on a pending-payment order. | Receipt appears on the order for the seller to review; buyer sees an "awaiting verification" state. |
| ORD-11 | As the seller, verify a submitted bank-transfer receipt. | `paymentStatus` flips to paid; buyer's order view updates. |
| ORD-12 | As the seller, reject a submitted bank-transfer receipt. | Order returns to an actionable "payment pending" state for the buyer, with a visible rejection reason if the UI shows one. |
| ORD-13 | As a **different** seller (not the order's owning store), attempt to view/modify the order directly by ID via the dashboard. | Blocked/403 — ownership check enforced. |
| ORD-14 | Trigger each order-lifecycle email touchpoint (created, confirmed, shipped, delivered, cancelled) in a production-profile environment. | Each fires a real email via SES (check inbox or CloudWatch/SES logs); in a non-`aws` profile it should instead appear in application logs (logging stub), not silently vanish. |

---

## 4. Bookings & Appointments

**Precondition:** Store A has `bookingsEnabled` on, at least one `active`
bookable service, a weekly availability template with at least one open
day, and lead time configured.

### 4a. Seller: service & availability management

| ID | Steps | Expected Result |
|---|---|---|
| BSVC-01 | As the seller, toggle Bookings **on** in store settings on a store that had it off. | Services/Availability/Bookings links appear in the dashboard sidebar; storefront Services section becomes reachable once a service exists. |
| BSVC-02 | Toggle Bookings **off**. | Sidebar links disappear; storefront Services section stops showing (existing bookings/services aren't deleted). |
| BSVC-03 | Create a new bookable service with name, price, duration, buffer, and category. | Service saved; category defaults to/only allows the store's own approved category. |
| BSVC-04 | Attempt to set a service's category to something other than the store's approved category. | Rejected/not selectable — category is locked, same rule as products. |
| BSVC-05 | Edit an existing service's price/duration. | Changes save; **existing bookings keep their original snapshot price/duration**, unaffected. |
| BSVC-06 | Set a service to `draft` status. | Service disappears from the public storefront but remains editable/visible in the dashboard. |
| BSVC-07 | Attempt to delete a service that has a non-terminal (pending/confirmed) booking against it. | Blocked with a clear error — deletion is refused while active bookings reference it. |
| BSVC-08 | Delete a service with no active bookings (or only cancelled/completed ones). | Deletes successfully. |
| BAVAIL-01 | Set weekly hours: mark several days open with start/end times, others closed. | Saves; a start time later than end time on an "open" day is rejected client- and server-side. |
| BAVAIL-02 | Set a lead-time value (e.g. 120 minutes). | Saves and is reflected in slot availability (see BCHK-04) and cancellation cutoff (BTRK-04). |
| BAVAIL-03 | Add a date-specific exception marking a normally-open day as closed (e.g. a holiday). | That date shows zero slots regardless of the weekly template, with the note visible to buyers if provided. |
| BAVAIL-04 | Add a date-specific exception opening a normally-closed day (e.g. a special Sunday opening) with its own hours. | That date shows slots based on the exception's hours, not the weekly template. |
| BAVAIL-05 | Delete a date exception. | The date reverts to whatever the weekly template says for that weekday. |
| BAVAIL-06 | Set a weekly rule for **every** day of the week, including Sunday, and reload the availability page. | All 7 days persist and redisplay correctly — specifically confirms Sunday doesn't error (regression case for a real bug found during verification: `dayOfWeek` persistence). |

### 4b. Buyer: browsing & booking checkout

| ID | Steps | Expected Result |
|---|---|---|
| BCHK-01 | Visit a service's detail page. | Description, price, duration, and a slot picker render. |
| BCHK-02 | Browse the slot picker across multiple weeks/dates. | Only dates/times inside the store's open hours (minus lead time, minus exceptions) show as bookable. |
| BCHK-03 | Attempt to view/select a slot within the store's configured lead time from now (e.g. 30 minutes from now with a 120-minute lead time). | That slot is not offered. |
| BCHK-04 | Book a slot, then immediately try to book the **same slot for the same service** in a second browser/tab. | The second attempt is rejected server-side (already taken) even if the picker hadn't refreshed yet — confirms server-side re-validation, not just client trust. |
| BCHK-05 | Book two **different services** on the same store for the **same time slot**. | Both succeed — confirms independent per-service capacity is intentional, not a bug. |
| BCHK-06 | As guest, fill in name/email/phone, select a slot and a payment method available on this store's plan, and submit. | Booking created; confirmation page shows booking number, service, time, and status. |
| BCHK-07 | On a **Free-plan** store, attempt to submit a booking with "Pay at venue" (COD) or bank transfer selected. | Rejected server-side (409/error), regardless of whether the option rendered — same Pro-gating as CHK-05. |
| BCHK-08 | On a **Pro-plan** store, submit a booking with "Pay at venue." | Succeeds; booking starts unpaid/pending, flips to paid once marked `completed` by the seller. |
| BCHK-09 | Submit a bank-transfer booking. | Confirmation shows a "payment pending" state with a receipt-upload action, mirroring order behavior. |
| BCHK-10 | Complete a PayHere or Stripe booking checkout (sandbox/test mode) on an eligible store. | Redirected to the gateway, returns successfully, booking eventually reflects paid status once the webhook lands. |
| BCHK-11 | Leave a required buyer field blank and submit. | Inline validation blocks submission. |

### 4c. Seller: booking management

| ID | Steps | Expected Result |
|---|---|---|
| BMGT-01 | View the bookings list, filter by status. | Correct subset shows. |
| BMGT-02 | Open a booking's detail view. | Shows buyer info, service snapshot (name/price/duration as booked, even if the service was edited since), timeline, payment status. |
| BMGT-03 | Move a booking `pending → confirmed`. | Succeeds; buyer-facing status updates. |
| BMGT-04 | Attempt an illegal transition (e.g. `pending → completed` directly). | Rejected — same state-machine enforcement as orders. |
| BMGT-05 | Mark a `confirmed`, COD-paid booking `completed`. | `paymentStatus` auto-flips to paid, mirroring order COD-on-delivery behavior. |
| BMGT-06 | Mark a booking `no-show`. | Status updates; the slot is freed for a future booking of the same service (no longer counted as an active overlap). |
| BMGT-07 | Review and verify a bank-transfer receipt on a booking. | `paymentStatus` flips to paid. |
| BMGT-08 | Review and reject a bank-transfer receipt on a booking. | Returns to an actionable pending-payment state. |

### 4d. Buyer: booking history & cancellation

| ID | Steps | Expected Result |
|---|---|---|
| BTRK-01 | As guest, visit `/track-booking`, enter a valid booking number + phone. | Booking detail loads. |
| BTRK-02 | As a signed-in buyer, check the account "Booking history" section. | Bookings placed while signed in appear without re-entering booking number/phone. |
| BTRK-03 | Cancel a booking well before its scheduled time / outside the lead-time cutoff. | Succeeds; status becomes `cancelled`, its slot becomes bookable again. |
| BTRK-04 | Attempt to cancel a booking inside the lead-time cutoff (e.g. starting in 30 minutes with a 120-minute lead time). | Blocked, same cutoff used for the booking-lead-time rule. |
| BTRK-05 | Trigger each booking-lifecycle email touchpoint (created, confirmed, cancelled). | Fires correctly, mirroring ORD-14. |

### 4e. Cross-feature / ledger

| ID | Steps | Expected Result |
|---|---|---|
| BLEDGER-01 | As admin, create a payout batch for a store that has **both** eligible PayHere orders and eligible PayHere-paid bookings outstanding. | A single batch includes both — confirms polymorphic ledger, not two separate settlement paths. |
| BLEDGER-02 | As admin, create a fee-collection batch for a store with both COD/bank-transfer orders and bookings owed. | Same combined behavior for the platform's side of the ledger. |
| BLEDGER-03 | Inspect a payout/fee-collection batch's line items. | Each line clearly identifies whether it's order- or booking-sourced (order number vs. booking number), never ambiguous or missing both. |

---

## 5. Seller Accounts & Onboarding

| ID | Steps | Expected Result |
|---|---|---|
| SELL-01 | Register a new seller account with a valid email/password. | Account created; email verification code sent. |
| SELL-02 | Enter an incorrect verification code. | Rejected with a clear error; correct code still works afterward. |
| SELL-03 | Enter the correct verification code. | Account verified; can now sign in. |
| SELL-04 | Attempt to sign in before verifying email. | Blocked with an "email not verified" message and a resend option. |
| SELL-05 | Sign in with correct credentials. | Redirected into the seller flow (onboarding if no store yet, dashboard if one exists). |
| SELL-06 | Sign in with an incorrect password. | Rejected, generic error (no user-enumeration hint). |
| SELL-07 | Complete onboarding for a Sri Lanka-context store: business details + NIC + business registration document upload + plan selection. | Store created in `pending` status; documents attached; pending-verification banner shows on the dashboard. |
| SELL-08 | Complete onboarding for an Australia-context store: business details + ABN + plan selection. | Same as SELL-07 but with ABN instead of NIC/BR, confirming country-conditional fields. |
| SELL-09 | While a store is `pending`, attempt to view its public storefront page as a random visitor. | Not visible/404 — unverified stores aren't public. |
| SELL-10 | Change a setting on an already-approved store that requires re-verification (per whatever fields are configured as change-request-gated). | A change request is created, not applied instantly; store remains on its previous approved values until an admin acts. |
| SELL-11 | Toggle a **non**-verification-sensitive setting (e.g. a payment-method flag within plan limits). | Applies immediately, no change-request needed. |
| SELL-12 | As a seller on Free plan, attempt to enable COD or bank-transfer in settings. | Blocked/clamped to off — Pro-only. |
| SELL-13 | Upgrade to Pro (see Epic 7 billing cases), then retry SELL-12. | COD/bank-transfer can now be enabled. |

---

## 6. Seller Dashboard

| ID | Steps | Expected Result |
|---|---|---|
| DASH-01 | Load the dashboard overview for a store with existing orders/products. | Revenue, pending-order, product-count, and fee stat cards show correct, consistent numbers. |
| DASH-02 | Drop a stock-managed product's quantity to below its low-stock threshold. | Low-stock alert/banner appears on the dashboard. |
| DASH-03 | Load the dashboard for a brand-new store with zero orders/products. | Stat cards show zero-states, not errors or `NaN`/`undefined`. |
| PROD-01 | Create a new product with multiple images, set one as primary. | Product saves; storefront/detail pages show the chosen primary image first. |
| PROD-02 | Edit a product's price/stock/status. | Changes reflected immediately on the storefront (subject to CART-08's reconciliation on the buyer side). |
| PROD-03 | Set a product's `stockQuantity` to 0. | Status auto-forces to "out of stock" regardless of what was submitted. |
| PROD-04 | Delete a product. | Removed from listings; **existing past orders referencing it still display correctly** (snapshot data, not a live join). |
| PROD-05 | Attempt to set a product's category outside the store's approved category. | Rejected — same lock as bookable services (BSVC-04). |
| PROD-06 | Leave SKU blank when creating a product. | Saves fine; SKU field is hidden (not shown as blank) wherever products render. |
| PAYOUT-01 | As seller, view the Payouts page. | Read-only ledger of PayHere-sourced payouts (available/scheduled/paid) — no action buttons to release funds. |
| PAYOUT-02 | As seller, view the Fee Collections page. | Read-only ledger of COD/bank-transfer amounts owed to the platform. |

---

## 7. Payments & Monetization

| ID | Steps | Expected Result |
|---|---|---|
| PAY-01 | Complete a PayHere sandbox payment for an order. | `PayHereController.notify` webhook processes; order flips to paid without the buyer needing to do anything further. |
| PAY-02 | Complete a PayHere sandbox payment for a booking. | Same as PAY-01, confirms the booking-aware webhook fallback path. |
| PAY-03 | Send a PayHere notify webhook with a tampered/invalid signature (or simulate via an invalid hash). | Rejected — payment status is not updated on an unverified notification. |
| PAY-04 | Complete a Stripe Connect test-mode checkout for an order on an AU store. | Webhook processes; order flips to paid. |
| PAY-05 | Complete a Stripe Connect test-mode checkout for a booking. | Same, confirms booking-aware Stripe webhook path. |
| PAY-06 | Issue a refund on a Stripe-paid order/booking from wherever that action is exposed. | Refund processes via Stripe; local payment status reflects it. |
| PAY-07 | A seller with Stripe Connect **not yet onboarded** attempts to enable Stripe/online payment. | Blocked or prompted to complete Connect onboarding first — cannot accept Stripe payments without it. |
| PAY-08 | Complete Stripe Connect standard-account onboarding as a seller. | Returns to the app with `stripeEnabled`/`stripeChargesEnabled` now true. |
| PRO-01 | As a Free-plan seller, start the Pro upgrade flow. | Redirected to a Stripe Checkout session for the subscription. |
| PRO-02 | Complete the Pro upgrade payment (test mode). | Redirected back; `SellerPlan` flips to `PRO`; previously greyed-out payment-method toggles (COD/bank-transfer) become available. |
| PRO-03 | Cancel/abandon the Pro checkout mid-flow. | Returns to the app with plan unchanged (still Free); no partial/corrupted state. |
| PRO-04 | Inspect the billing webhook handling by simulating a subscription-cancelled event (if a test path exists) or downgrading in Stripe's dashboard. | `SellerPlan` reverts to Free; COD/bank-transfer settings clamp back off. |

---

## 8. Buyer Accounts

| ID | Steps | Expected Result |
|---|---|---|
| BUY-01 | Register a new buyer account. | Verification code sent; account unusable for sign-in until verified (mirrors SELL-01–04). |
| BUY-02 | Verify and sign in. | Signed in; account page reachable. |
| BUY-03 | Save a default shipping address on the account page. | Saved; pre-fills automatically on the next checkout. |
| BUY-04 | Complete a checkout while signed in. | Resulting order is associated with the buyer account and appears in order history without a separate lookup. |
| BUY-05 | Complete a checkout as a **guest** (not signed in). | Still succeeds — guest checkout remains fully functional and is not blocked or nagged into registering. |
| BUY-06 | View order history and booking history on the account page. | Both sections present, each showing only that buyer's own orders/bookings. |
| BUY-07 | Sign out, then attempt to view the account page. | Redirected to sign-in — account pages are session-gated. |

---

## 9. Platform Admin

**Precondition:** an admin account exists (bootstrapped out-of-band or
invited by an existing admin) and you are signed in as admin.

| ID | Steps | Expected Result |
|---|---|---|
| ADM-01 | As a non-admin (or signed out), attempt to load `/admin` or call an `/api/admin/**` endpoint directly. | Blocked/redirected — no admin surface reachable without `ROLE_ADMIN`. |
| ADM-02 | View the pending-store queue. | Every store awaiting approval lists with its submitted verification details/documents. |
| ADM-03 | Approve a pending store. | Store `verificationStatus` becomes `active`; it becomes publicly visible (see MKT-13/SELL-09 reversal). |
| ADM-04 | Reject a pending store, providing a rejection reason. | Store marked rejected with the reason stored/visible to the seller. |
| ADM-05 | Open the store directory and a store's detail card. | Full profile, settings, and history visible to the admin. |
| ADM-06 | Create a payout batch for a store with eligible PayHere income. | Batch created in a pending/scheduled state. |
| ADM-07 | Mark a payout batch as paid. | Status updates; reflected on the seller's read-only Payouts page. |
| ADM-08 | Create and mark-paid a fee-collection batch for a store with COD/bank-transfer income owed. | Mirrors ADM-06/07 for the platform's receivable side. |
| ADM-09 | View the accounting summary page. | Aggregate figures reconcile with the sum of individual store payout/fee-collection data. |
| ADM-10 | Invite a new admin by email. | Invitation sent/created; the invited user gains admin access once accepted (per whatever flow is implemented — confirm it's not instant without any acceptance step, if that's the intended design). |
| ADM-11 | View the audit log after performing ADM-03/04/06/07. | Each action appears as a distinct, attributed entry (who, what, when) — durable, not just a toast notification. |
| ADM-12 | Review a store's verification change-request queue (see SELL-10) and approve one. | The store's live verification data updates to the requested values. |
| ADM-13 | Reject a verification change request. | Store's data remains unchanged; requester can see the rejection. |

---

## 10. Platform Configuration & Cross-Cutting

| ID | Steps | Expected Result |
|---|---|---|
| CFG-01 | Load the app under each configured country/currency profile (e.g. Sri Lanka / Australia). | Currency formatting, region dropdown options, and required verification fields (NIC/BR vs. ABN) all match that country. |
| CFG-02 | Confirm the platform's configured timezone is used to resolve booking slot boundaries (see BCHK-02) rather than the buyer's local browser timezone. | Slot times displayed and stored align with the platform's configured IANA timezone, not whatever timezone the test device happens to be set to. |
| CFG-03 | Upload a product image, an order receipt, and a seller verification document in a production (`aws` profile) environment. | All three land in the correct S3 bucket/path and are retrievable via their served URL. |
| CFG-04 | Repeat CFG-03 in a local/non-`aws` environment. | Files land on local disk via the fallback storage implementation; app behaves identically from the user's perspective. |
| CFG-05 | Trigger any transactional email in a production environment. | Delivered via SES (verify in the recipient inbox or SES sending logs), not merely logged. |
| CFG-06 | Trigger the same email path in a non-production environment. | Appears in application logs via the logging stub; no attempt to send a real email, no error. |
| CFG-07 | Resize the browser to mobile width and repeat a representative sample of the above (home page, product page, cart, checkout, dashboard, a booking flow). | Fully responsive — no horizontal scroll, no unreachable controls, mobile nav works. |
| CFG-08 | Toggle the OS/browser's dark-mode preference. | Document current actual behavior (as of writing, dark mode is **not wired up** — this case exists to catch regressions/confirm status, not assert a specific pass/fail). |
