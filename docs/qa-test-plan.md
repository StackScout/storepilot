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

### 1a. Product search — relevance (real Postgres full-text search, not substring matching)

**Precondition:** at least two products whose names/descriptions share one
word but differ in others (e.g. "Colombian Single-Origin Coffee Beans" and
"House Blend Ground Coffee").

| ID | Steps | Expected Result |
|---|---|---|
| SRCH-01 | Search two words that only both appear together in one product's name/description (e.g. `beans coffee`, order doesn't matter). | Only the product containing both words matches — confirms implicit AND across words, not a literal-substring match. |
| SRCH-02 | Search the singular form of a word that only appears in a product's name/description as a plural (e.g. `bean` when the name says "Beans"). | Still matches — English stemming, a real improvement over old substring matching. |
| SRCH-03 | Search a genuine partial word/fragment that isn't a real dictionary word on its own (e.g. the first few letters of a product name, `colomb`). | Still returns a result via the trigram-index fallback — recall is preserved even for non-lexeme queries. |
| SRCH-04 | Search with the default sort ("Best match" — the sort pill relabels itself when a query is present). | Results are ordered by relevance to the query, not by creation date. |
| SRCH-05 | With an active search query, switch to an explicit sort (Price: low to high / high to low / Top rated). | The explicit sort overrides relevance ranking — same override behavior as a plain category browse. |
| SRCH-06 | Combine a search query with a category filter and/or price range. | All filters apply together — the relevance-ranked result set is still narrowed by category/price, not replaced by it. |

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
| CHK-13 | With a saved address book (2+ addresses, see ADDR section), open the "Use a different saved address" selector at checkout, select a non-default address. | The picker shows each address's label/name and street summary — not a raw ID — and selecting one fills the shipping fields with that address. |

---

## 3. Coupons

**Precondition:** one store-scoped coupon (created by a seller, scoped to
their store) and one platform-wide coupon (created by an admin, no store).

| ID | Steps | Expected Result |
|---|---|---|
| CPN-01 | As a seller, create a store coupon (percent-off or fixed-amount). | Saved; visible in the store's coupon list with its code, type, and value. |
| CPN-02 | As a buyer, preview/apply the store coupon's code at that store's checkout. | Discount is calculated and reflected in the order total before submission. |
| CPN-03 | Attempt to apply that same store coupon's code at a **different** store's checkout. | Rejected — a store coupon is scoped to its own store only. |
| CPN-04 | As an admin, create a platform-wide coupon (no store). | Saved; visible in the admin coupon list. |
| CPN-05 | As a buyer, apply the platform coupon's code at checkout for **any** store. | Accepted regardless of which store — confirms platform scope. |
| CPN-06 | Apply the platform coupon's code at a **booking** checkout. | Also accepted — coupon preview/redemption applies to both product and booking checkout. |
| CPN-07 | Enter an invalid/expired/non-existent coupon code. | Clear rejection, no discount applied, checkout otherwise unaffected. |
| CPN-08 | As a seller, edit or delete a coupon that's never been used. | Succeeds. |
| CPN-09 | As an admin, edit or delete a platform coupon. | Succeeds, and it stops applying at any store's checkout immediately afterward. |

---

## 4. Order Fulfillment

| ID | Steps | Expected Result |
|---|---|---|
| ORD-01 | As guest, visit `/track-order`, enter a valid order number + phone (step 1). | A 6-digit verification code is emailed to the order's contact email; the form advances to a code-entry step (no order data shown yet). |
| ORD-02 | Enter the wrong verification code. | Rejected with a clear error; the correct code still works afterward, up to 5 attempts before a fresh code is required. |
| ORD-03 | Enter the correct verification code. | Order detail loads. |
| ORD-04 | Request a lookup for an order number/phone combination that doesn't match anything. | Step 1 still "succeeds" (no code is actually sent) — no signal is given that distinguishes "no such order" from "code sent," preventing order-number enumeration. |
| ORD-05 | Request a second code for the same order before the first one expires (10-minute TTL). | The new code invalidates the previous one — only the latest code works. |
| ORD-06 | As a signed-in buyer who placed an order, visit account order history. | The order appears without needing to re-enter order number/phone or go through the OTP flow. |
| ORD-07 | As the buyer, cancel a `pending` order within the allowed window. | Order status becomes `cancelled`; timeline updated. |
| ORD-08 | As the buyer, attempt to cancel an order already `shipped`/`delivered`, or past any cancellation cutoff. | Cancellation is blocked/not offered. |
| ORD-09 | As the seller, open the order list and filter by status. | Only orders matching the selected status show. |
| ORD-10 | As the seller, attempt an illegal status transition (e.g. `pending → delivered` directly, skipping `confirmed`/`shipped`). | Rejected — only the next legal status(es) are selectable/accepted. |
| ORD-11 | As the seller, move an order `pending → confirmed → shipped`, entering required tracking info at the ship step. | Each transition succeeds; tracking number/carrier is mandatory to reach `shipped` and is visible to the buyer afterward. |
| ORD-12 | As the seller, mark a `shipped` order `delivered`. | Status updates; for a COD order this should also flip `paymentStatus` to paid. |
| ORD-13 | As the buyer, upload a bank-transfer receipt on a pending-payment order. | Receipt appears on the order for the seller to review; buyer sees an "awaiting verification" state. |
| ORD-14 | As the seller, verify a submitted bank-transfer receipt. | `paymentStatus` flips to paid; buyer's order view updates. |
| ORD-15 | As the seller, reject a submitted bank-transfer receipt. | Order returns to an actionable "payment pending" state for the buyer, with a visible rejection reason if the UI shows one. |
| ORD-16 | As a **different** seller (not the order's owning store), attempt to view/modify the order directly by ID via the dashboard. | Blocked/403 — ownership check enforced. |
| ORD-17 | Trigger each order-lifecycle email touchpoint (created, confirmed, shipped, delivered, cancelled) in a production-profile environment. | Each fires a real email via SES (check inbox or CloudWatch/SES logs); in a non-`aws` profile it should instead appear in application logs (logging stub), not silently vanish. |
| ORD-18 | Open an order detail page (as buyer or seller), then have the other party change its status from a separate session/tab. | The open page updates live to the new status with **no manual reload/poll** (server-sent events). |
| ORD-19 | As the buyer, place an order with a bank-transfer payment and don't upload a receipt for the configured "first reminder" threshold. | A reminder email fires (production profile) or is logged (non-`aws` profile); repeats on the configured interval until a receipt is uploaded or the order is cancelled — should not fire again after either. |

### 4a. GST tax invoices (Australia)

**Precondition:** an AU-context store with `gstRegistered` toggled on in
settings, and a second AU store with it left off.

| ID | Steps | Expected Result |
|---|---|---|
| GST-01 | As a buyer, complete an order at the GST-registered store. | The resulting order confirmation renders as a tax invoice: the seller's ABN and a "Includes GST: $X.XX" line are shown, computed as total ÷ 11 (GST-inclusive pricing), not total × 10%. |
| GST-02 | Trigger the order-confirmation email for that order (see ORD-17). | The email also includes the ABN/GST tax-invoice fields, not a plain confirmation. |
| GST-03 | Complete an order at the **non**-GST-registered store. | No ABN/GST line appears — a plain confirmation, not a tax invoice. |
| GST-04 | After GST-01's order exists, have the seller toggle `gstRegistered` **off** in settings, then revisit that same past order. | The past order still shows its original ABN/GST invoice — snapshotted at order-creation time, not re-derived from the seller's current setting. |

---

## 5. Returns & Refunds

**Precondition:** a `DELIVERED` + `PAID` order for each of the three
payment methods (Stripe, PayHere, and COD or bank-transfer), inside the
platform's configured return window.

| ID | Steps | Expected Result |
|---|---|---|
| RET-01 | As the buyer, submit a return request on a `DELIVERED` + `PAID` order within the return window, selecting a reason. | Return request created in a pending state, visible to both buyer and seller on the order. |
| RET-02 | Attempt a return request on an order that is not yet `DELIVERED`, or is unpaid. | Blocked — not eligible. |
| RET-03 | Attempt a return request outside the platform's configured return window (days since the order's *first* `DELIVERED` timeline entry). | Blocked — window has passed. |
| RET-04 | Submit a second return request on an order that already has a **pending or approved** return. | Blocked — only one active return per order. |
| RET-05 | Submit a second return request on an order whose prior return was **rejected**. | Allowed — a rejected return doesn't permanently block resubmission. |
| RET-06 | As the seller, approve a return request on a **Stripe**-paid order. | Refund is issued synchronously via Stripe on approval — no separate "mark refunded" step needed. |
| RET-07 | As the seller, approve a return request on a **COD/bank-transfer**-paid order, then mark it refunded. | Approval moves it to a refund-pending state; the seller (who received the money directly) self-attests the refund via "mark refunded." |
| RET-08 | As the seller, approve a return request on a **PayHere**-paid order. | Moves to refund-pending, but awaits an **admin** to confirm the refund (platform is the merchant of record for PayHere), not the seller. |
| RET-09 | As an admin, confirm the pending PayHere refund from RET-08. | Return marked refunded/complete. |
| RET-10 | As the seller, reject a return request with a reason. | Return marked rejected; reason visible to the buyer; see RET-05 for the resubmission behavior this unlocks. |
| RET-11 | Approve/refund a return on an order that was already included in a paid payout or collected fee-collection batch. | A reconciliation note is attached to the return (manual accounting flag) — no automatic clawback from the batch. |
| RET-12 | As the seller, view the store's Returns list. | Shows every return for that store, filterable by status. |
| RET-13 | As an admin, view the platform-wide Returns tab (on the accounting page). | Every return across every store is visible with its current status. |

---

## 6. Bookings & Appointments

**Precondition:** Store A has `bookingsEnabled` on, at least one `active`
bookable service, a weekly availability template with at least one open
day, and lead time configured.

### 6a. Seller: service & availability management

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

### 6b. Buyer: browsing & booking checkout

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
| BCHK-12 | Enable "Repeat weekly" on the booking form and select an occurrence count, using "Pay at venue" or bank transfer. | A whole series of bookings is created in one submission, one per week, each with its own independent slot. |
| BCHK-13 | Attempt to enable "Repeat weekly" while PayHere or Stripe is the selected payment method. | Not offered/blocked — recurring bookings are only available for payment methods that don't require an upfront gateway redirect. |
| BCHK-14 | After creating a recurring series, view `/bookings/recurrence/{groupId}` (or the account/dashboard view that surfaces it). | Every occurrence in the series lists, in chronological order. |
| BCHK-15 | Within a recurring series, have another booking already occupy one specific week's slot before the series is created. | That single occurrence in the series is rejected/skipped on the conflicting week while the rest of the series still succeeds (or the whole series is rejected, confirming whichever behavior is actually implemented — document what you observe). |

### 6c. Seller: booking management

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
| BMGT-09 | Open a booking detail page, then have the buyer cancel it from a separate session/tab. | The seller's open page updates live with no reload (server-sent events, same as ORD-18). |

### 6d. Buyer: booking history & cancellation

| ID | Steps | Expected Result |
|---|---|---|
| BTRK-01 | As guest, visit `/track-booking`, request a lookup code for a valid booking number + phone. | A 6-digit code is emailed to the booking's contact email (same two-step OTP flow as ORD-01, not a single-step lookup). |
| BTRK-02 | As a signed-in buyer, check the account "Booking history" section. | Bookings placed while signed in appear without re-entering booking number/phone or the OTP flow. |
| BTRK-03 | Cancel a booking well before its scheduled time / outside the lead-time cutoff. | Succeeds; status becomes `cancelled`, its slot becomes bookable again. |
| BTRK-04 | Attempt to cancel a booking inside the lead-time cutoff (e.g. starting in 30 minutes with a 120-minute lead time). | Blocked, same cutoff used for the booking-lead-time rule. |
| BTRK-05 | Trigger each booking-lifecycle email touchpoint (created, confirmed, cancelled). | Fires correctly, mirroring ORD-17. |
| BTRK-06 | Have a booking's scheduled time fall inside the configured "reminder before" window without it starting yet. | A one-shot reminder email fires (production profile) or is logged (non-`aws` profile) — never repeats for the same booking. |

### 6e. Cross-feature / ledger

| ID | Steps | Expected Result |
|---|---|---|
| BLEDGER-01 | As admin, create a payout batch for a store that has **both** eligible PayHere orders and eligible PayHere-paid bookings outstanding. | A single batch includes both — confirms polymorphic ledger, not two separate settlement paths. |
| BLEDGER-02 | As admin, create a fee-collection batch for a store with both COD/bank-transfer orders and bookings owed. | Same combined behavior for the platform's side of the ledger. |
| BLEDGER-03 | Inspect a payout/fee-collection batch's line items. | Each line clearly identifies whether it's order- or booking-sourced (order number vs. booking number), never ambiguous or missing both. |

---

## 7. Reviews

**Precondition:** a buyer with a `DELIVERED` order containing a specific
product, and (separately) a buyer with either a `DELIVERED` order or a
`COMPLETED` booking at a given store.

| ID | Steps | Expected Result |
|---|---|---|
| REV-01 | As a buyer with a `DELIVERED` order for a product, submit a product review with a rating and comment. | Review saves; the product's average rating recomputes to include it. |
| REV-02 | As a buyer with **no** order for that product, attempt to submit a product review. | Blocked — a verified purchase (delivered order containing that product) is required. |
| REV-03 | Submit a second review for the same product you already reviewed. | Rejected (409) — one review per buyer per product. |
| REV-04 | As a buyer with a `DELIVERED` order **or** a `COMPLETED` booking at a store, submit a store-level review. | Succeeds — store reviews accept either purchase type, not just orders. |
| REV-05 | As a buyer with neither a delivered order nor a completed booking at that store, attempt a store review. | Blocked. |
| REV-06 | View a product/store page after several reviews exist. | Average rating and review count are accurate and match what was submitted. |

---

## 8. Messaging

| ID | Steps | Expected Result |
|---|---|---|
| MSG-01 | As a buyer, click "Message seller" on a store page and send a first message. | A conversation is created (or reused if one already exists between this buyer and store) and the message appears in the thread. |
| MSG-02 | As the seller, view the store's conversations list. | The new conversation appears with an unread indicator. |
| MSG-03 | As the seller, open the conversation and reply. | Reply appears in the thread; buyer's unread indicator updates when they view it. |
| MSG-04 | As the buyer, view their own conversations list under account messages. | Every conversation with any store they've messaged appears, with the most recent message/unread state. |
| MSG-05 | As a different buyer (not part of the conversation), attempt to open it directly by ID. | Blocked/403 — messaging is scoped to its two participants only. |
| MSG-06 | As a different seller (not the store in the conversation), attempt to view it. | Blocked/403. |

---

## 9. Multi-Factor Authentication (MFA)

**Applies identically to seller and admin accounts** — not offered to
buyers.

| ID | Steps | Expected Result |
|---|---|---|
| MFA-01 | As a seller (or admin), open the security/settings page and start MFA setup. | A QR code and manual-entry secret are shown. |
| MFA-02 | Scan the QR code into an authenticator app and enter the current 6-digit code to confirm. | MFA is enabled; setup screen confirms it's now active. |
| MFA-03 | Sign out, then sign back in with the correct password. | Login pauses at an MFA challenge step (not immediately signed in) — a 6-digit code prompt appears. |
| MFA-04 | Enter an incorrect MFA code. | Rejected with a clear error; correct code still works on retry. |
| MFA-05 | Enter the correct MFA code. | Sign-in completes normally. |
| MFA-06 | Disable MFA from the security settings page. | MFA turns off; the next sign-in no longer challenges for a code. |
| MFA-07 | Repeat MFA-01–06 as an **admin** account. | Behaves identically — MFA is not seller-specific. |

---

## 10. Seller Accounts & Onboarding

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
| SELL-13 | Upgrade to Pro (see Epic 13 billing cases), then retry SELL-12. | COD/bank-transfer can now be enabled. |
| SELL-14 | Edit a store's Facebook/Instagram social links from settings. | Applies immediately, no change-request/re-verification step — these are public profile links, not identity-verification fields. |
| SELL-15 | Sign up as a brand-new seller via "Continue with Google." | No Cognito group is granted yet; redirected straight to `/onboarding` — Google sign-up alone doesn't make someone a seller, creating a store does. |
| SELL-16 | Sign in via Google using an account already registered (and grouped) as a **buyer**. | Rejected back to the seller login page with a clear "this account is registered as a buyer" message — cannot cross into the seller flow via Google. |
| SELL-17 | Sign in via Google using an account already grouped as a seller. | Signs in directly to the dashboard, no re-onboarding. |

---

## 11. Seller Dashboard

| ID | Steps | Expected Result |
|---|---|---|
| DASH-01 | Load the dashboard overview for a store with existing orders/products. | Revenue, pending-order, product-count, and fee stat cards show correct, consistent numbers. |
| DASH-02 | Drop a stock-managed product's quantity to below its low-stock threshold. | Low-stock alert/banner appears on the dashboard. |
| DASH-03 | Load the dashboard for a brand-new store with zero orders/products. | Stat cards show zero-states, not errors or `NaN`/`undefined`. |
| DASH-04 | With revenue/fees higher this week than last week, load the dashboard overview. | Revenue and fee stat cards show a "+X.X% vs last week" trend indicator. |
| DASH-05 | With revenue/fees lower this week than last week. | Trend indicator shows "-X.X% vs last week." |
| DASH-06 | With zero revenue last week and some revenue this week. | Trend indicator reads "New this week" rather than a nonsensical percentage (division by zero). |
| DASH-07 | With zero revenue in both weeks. | No trend indicator is shown at all (not "0%" or "New this week"). |
| PROD-01 | Create a new product with multiple images, set one as primary. | Product saves; storefront/detail pages show the chosen primary image first. |
| PROD-02 | Edit a product's price/stock/status. | Changes reflected immediately on the storefront (subject to CART-08's reconciliation on the buyer side). |
| PROD-03 | Set a product's `stockQuantity` to 0. | Status auto-forces to "out of stock" regardless of what was submitted. |
| PROD-04 | Delete a product. | Removed from listings; **existing past orders referencing it still display correctly** (snapshot data, not a live join). |
| PROD-05 | Attempt to set a product's category outside the store's approved category. | Rejected — same lock as bookable services (BSVC-04). |
| PROD-06 | Leave SKU blank when creating a product. | Saves fine; SKU field is hidden (not shown as blank) wherever products render. |
| PROD-07 | Edit an existing product and upload a **new** set of images. | The new set fully replaces the old images (not appended); the storefront reflects the new primary image. |
| PROD-08 | Edit an existing product without touching the images field at all. | Existing images are left untouched — editing price/description doesn't force a re-upload or clear images. |
| PAYOUT-01 | As seller, view the Payouts page. | Read-only ledger of PayHere-sourced payouts (available/scheduled/paid) — no action buttons to release funds. |
| PAYOUT-02 | As seller, view the Fee Collections page. | Read-only ledger of COD/bank-transfer amounts owed to the platform. |

### 11a. Booking analytics (Pro-gated)

| ID | Steps | Expected Result |
|---|---|---|
| ANL-01 | As a **Free**-plan seller with bookings enabled, visit the analytics page. | An "Upgrade to Pro" upsell card shows instead of any booking data. |
| ANL-02 | As a **Pro**-plan seller with zero bookings, visit the analytics page. | A distinct "no bookings yet" empty state shows — not the Pro-upsell card, not an error. |
| ANL-03 | As a Pro-plan seller with completed, paid bookings, visit the analytics page. | Total bookings, revenue, no-show rate %, repeat-customer rate %, and a "Top services" breakdown (name/count/revenue) all render with correct figures. |
| ANL-04 | With some cancelled and no-show bookings among the total. | A summary line shows cancelled/no-show counts out of the total, separate from the main stat cards. |
| ANL-05 | Attempt to call the analytics endpoint directly (e.g. via browser devtools/API client) as a Free-plan seller. | Confirm server-side gating exists, not just a client-side hide — the monetization boundary shouldn't be bypassable by skipping the UI. |

### 11b. Danger zone — store closure & account deletion

**Precondition:** a Pro or Free-plan seller with a store that has no
orders/bookings in flight and nothing owed either direction (for the
"eligible to close" cases) — and separately, one with something still in
flight (for the "blocked" cases).

| ID | Steps | Expected Result |
|---|---|---|
| DANGER-01 | As a seller, open the dashboard settings "Danger zone" and export account data. | Downloads/returns a JSON bundle of the seller's own profile, store, settings, products, orders, bookings, payouts, fee collections, reviews, and coupons. |
| DANGER-02 | With a **non-terminal** order or booking still open (or a fee collection/payout still owed either direction), attempt to close the store. | Blocked with a specific reason identifying exactly what's still outstanding — not a generic error. |
| DANGER-03 | With nothing outstanding, close the store. | Store's `verificationStatus` becomes `closed`; it immediately drops out of marketplace search/browse, but its name/slug still render correctly on past buyers' order history. |
| DANGER-04 | Attempt to close an already-closed store again. | No-op / doesn't error — idempotent. |
| DANGER-05 | Before closing the store, attempt to delete the seller account from the danger zone. | The "Delete account" action is disabled/blocked — a seller with an open store cannot delete their account first. |
| DANGER-06 | After closing the store, delete the seller account, typing the seller's own email to confirm. | Account deleted: Stripe subscription cancelled, Stripe Connect disconnected, store settings' contact/bank fields redacted, seller signed out and can no longer log in. Store/order history remains, personal details stripped, for tax purposes. |
| DANGER-07 | Type the wrong email into the delete-confirmation field. | The delete button stays disabled — exact-match required. |
| DANGER-08 | As a seller who never onboarded a store at all, open the danger zone and delete the account directly. | Allowed immediately — no store-closure precondition applies when there's no store to close. |

---

## 12. Payments & Monetization

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
| PAY-09 | As a seller, view the per-store Stripe settlements view. | Read-only reconciliation list of Stripe-paid orders/bookings for that store — no action buttons, since Stripe pays out automatically at charge time. |
| PAY-10 | As an admin, view the platform-wide Stripe settlements view. | Same read-only reconciliation, across every store. |
| PRO-01 | As a Free-plan seller, start the Pro upgrade flow. | Redirected to a Stripe Checkout session for the subscription. |
| PRO-02 | Complete the Pro upgrade payment (test mode). | Redirected back; `SellerPlan` flips to `PRO`; previously greyed-out payment-method toggles (COD/bank-transfer) become available. |
| PRO-03 | Cancel/abandon the Pro checkout mid-flow. | Returns to the app with plan unchanged (still Free); no partial/corrupted state. |
| PRO-04 | Inspect the billing webhook handling by simulating a subscription-cancelled event (if a test path exists) or downgrading in Stripe's dashboard. | `SellerPlan` reverts to Free; COD/bank-transfer settings clamp back off. |

---

## 13. Buyer Accounts

| ID | Steps | Expected Result |
|---|---|---|
| BUY-01 | Register a new buyer account. | Verification code sent; account unusable for sign-in until verified (mirrors SELL-01–04). |
| BUY-02 | Verify and sign in. | Signed in; account page reachable. |
| BUY-03 | Complete a checkout while signed in. | Resulting order is associated with the buyer account and appears in order history without a separate lookup. |
| BUY-04 | Complete a checkout as a **guest** (not signed in). | Still succeeds — guest checkout remains fully functional and is not blocked or nagged into registering. |
| BUY-05 | View order history and booking history on the account page. | Both sections present, each showing only that buyer's own orders/bookings. |
| BUY-06 | Sign out, then attempt to view the account page. | Redirected to sign-in — account pages are session-gated. |
| BUY-07 | Sign up as a brand-new buyer via "Continue with Google." | Immediately assigned the buyer group and signed in — no onboarding step needed (unlike sellers). |
| BUY-08 | Sign in via Google using an account already grouped as a **seller**. | Rejected back to the buyer login page with a clear "registered as a seller" message. |

### 13a. Address book

| ID | Steps | Expected Result |
|---|---|---|
| ADDR-01 | Add a first saved address. | Saves; automatically becomes the default (only address). |
| ADDR-02 | Add a second address. | Both addresses list; the first remains default unless explicitly changed. |
| ADDR-03 | Set the second address as the new default. | Default flag moves; only one address is ever marked default at a time. |
| ADDR-04 | Edit a saved address's details. | Changes save and reflect wherever that address is shown. |
| ADDR-05 | Delete a **non-default** address. | Removed; the default address is unaffected. |
| ADDR-06 | Delete the current **default** address while another address still exists. | The next-oldest remaining address is automatically promoted to default — the buyer is never left with zero default address while any address remains. |
| ADDR-07 | Delete the only remaining address. | Address book is empty; no default exists, no error. |
| ADDR-08 | Start a checkout as a signed-in buyer with a saved default address. | Shipping fields prefill from the default address automatically. |

### 13b. Wishlist, store follows, and saved searches

| ID | Steps | Expected Result |
|---|---|---|
| WISH-01 | Add a product to the wishlist from a product card or detail page. | Wishlist icon updates to a "saved" state; product appears in the account wishlist list. |
| WISH-02 | Remove a product from the wishlist. | Icon reverts; product disappears from the list. |
| FOL-01 | Follow a store from its storefront page. | Follow button updates state; store appears in the account's followed-stores list; the store's follower count increments. |
| FOL-02 | Unfollow a store. | Reverts; follower count decrements. |
| SRCHSV-01 | From a search-results page with an active query/filters, save the search. | Saved under a name; appears in the account's saved-searches list. |
| SRCHSV-02 | Open a saved search from the account page. | Navigates back to `/search` with the exact same query/filters re-applied via the URL. |
| SRCHSV-03 | Delete a saved search. | Removed from the list. |

### 13c. Data subject access & account deletion

| ID | Steps | Expected Result |
|---|---|---|
| DSAR-01 | As a signed-in buyer, export account data from the account page. | Downloads/returns a JSON bundle of the buyer's own profile, addresses, orders, bookings, reviews, conversations, saved searches, wishlist, and follows. |
| DSAR-02 | Delete the buyer account, typing the buyer's own email to confirm. | Account deleted immediately — no precondition, unlike the seller flow. Addresses, saved searches, wishlist items, and follows are genuinely removed; the buyer is signed out and the Cognito identity is deleted (cannot sign back in with the old credentials). |
| DSAR-03 | Type the wrong email into the delete-confirmation field. | Delete button stays disabled. |
| DSAR-04 | After deleting a buyer account that had placed orders, view one of those past orders (e.g. via the seller's dashboard or admin). | The order still displays correctly with a redacted/anonymized buyer name and contact details — the order record itself is retained, not deleted, for tax/accounting purposes. |
| DSAR-05 | After deletion, check whether any product reviews or store-conversation messages that buyer wrote are still visible. | Still visible, now attributed to a generic "Deleted user" — review/message content and count are unaffected by the identity's removal. |

---

## 14. Platform Admin

**Precondition:** an admin account exists (bootstrapped out-of-band or
invited by an existing admin) and you are signed in as admin.

| ID | Steps | Expected Result |
|---|---|---|
| ADM-01 | As a non-admin (or signed out), attempt to load `/admin` or call an `/api/admin/**` endpoint directly. | Blocked/redirected — no admin surface reachable without `ROLE_ADMIN`. |
| ADM-02 | View the pending-store queue. | Every store awaiting approval lists with its submitted verification details/documents. |
| ADM-03 | Approve a pending store. | Store `verificationStatus` becomes `active`; it becomes publicly visible (see MKT-13/SELL-09 reversal). |
| ADM-04 | Reject a pending store, providing a rejection reason. | Store marked rejected with the reason stored/visible to the seller. |
| ADM-05 | Open the store directory and a store's detail card. | Full profile, settings, and history visible to the admin — a `closed` store shows a distinct status badge from active/pending/rejected. |
| ADM-06 | View the accounting page's per-store eligible-payouts list. | Only stores with at least one eligible order/booking are listed, each showing its eligible net total and item count — stores with nothing eligible are omitted entirely, not shown with a zero. |
| ADM-07 | Create a payout batch for a store with eligible PayHere income. | Batch created in a pending/scheduled state; that store drops off the eligible list afterward (its eligible items are now accounted for). |
| ADM-08 | Mark a payout batch as paid. | Status updates; reflected on the seller's read-only Payouts page. |
| ADM-09 | View the accounting page's per-store eligible-fee-collections list, then create and mark-paid a fee-collection batch. | Mirrors ADM-06/07/08 for the platform's receivable side. |
| ADM-10 | View the accounting summary page. | Aggregate figures reconcile with the sum of individual store payout/fee-collection data. |
| ADM-11 | Invite a new admin by email. | Invitation sent/created; the invited user gains admin access once accepted (per whatever flow is implemented — confirm it's not instant without any acceptance step, if that's the intended design). |
| ADM-12 | View the audit log after performing ADM-03/04/07/08. | Each action appears as a distinct, attributed entry (who, what, when) — durable, not just a toast notification. |
| ADM-13 | Review a store's verification change-request queue (see SELL-10) and approve one. | The store's live verification data updates to the requested values. |
| ADM-14 | Reject a verification change request. | Store's data remains unchanged; requester can see the rejection. |
| ADM-15 | Sign in as admin, then check the "Recent activity" feed on the overview page. | The admin's own sign-in does **not** appear as an activity entry — only substantive platform actions (approvals, closures, deletions, etc.) show, never routine logins. |

---

## 15. Legal Pages & Consent

| ID | Steps | Expected Result |
|---|---|---|
| LEGAL-01 | Visit `/privacy` and `/terms` directly. | Both pages render as static, readable content — no auth required. |
| LEGAL-02 | Start buyer registration and submit without checking the terms/privacy agreement checkbox. | Blocked with an inline validation error; links to `/terms` and `/privacy` open correctly (new tab). |
| LEGAL-03 | Check the agreement checkbox and complete buyer registration. | Succeeds. |
| LEGAL-04 | Repeat LEGAL-02/03 for seller registration. | Same required-checkbox behavior on the seller registration form. |
| LEGAL-05 | Complete a guest checkout (product or booking) with no signed-in account. | No separate consent checkbox is presented at checkout — confirm this is the intended design (consent is captured once, at account registration, not re-asked at every guest checkout) rather than assuming it's a missing gap. |

---

## 16. Platform Configuration & Cross-Cutting

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
| CFG-09 | Open any order/booking detail page and leave it idle for several minutes without another party changing anything. | The live-status connection (server-sent events) stays open with no errors in the console — no reconnect storm, no memory leak from a dangling `EventSource`. |
