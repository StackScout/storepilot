# Feature: Seller Authentication & Onboarding

> Index: [`feature-index.md`](../feature-index.md) · Architecture:
> [`frontend-architecture.md`](../frontend-architecture.md) · API:
> [`api-contracts.md`](../../../docs/api-contracts.md)

## Purpose

Gate the seller dashboard behind a session, and provide a real "sign up"
entry point for new sellers that creates a genuine, distinct `Store` +
`StoreSettings` row and puts it through a (mock) verification workflow
before it's visible to buyers. **Sign-in itself is still entirely mocked**
— there is no credential verification and no email→store lookup — read this
whole document before assuming any of it reflects intended production auth
behavior.

## Business rules

- **`/login`: any non-empty email signs in as the same single mock seller**
  (`CURRENT_SELLER_STORE_ID = "store-01"`, Ceylon Spice Co.). There is no
  password, no OTP, no credential store, and no per-email seller lookup.
  This is unchanged and still a real gap — see [Future improvements](#future-improvements).
- **`/onboarding` now really creates a store.** Submitting the form:
  1. Calls `storesService.createStore(...)` **client-side** — a new `Store`
     with a generated `id`/`slug`, `verificationStatus: "pending"`,
     `isVerified: false`, and all counters (`rating`, `reviewCount`,
     `productCount`, `followerCount`) at zero.
  2. Calls `storesService.updateStoreSettings(store.id, {...})` (an
     **upsert** — see [`database-model.md`](../../../docs/database-model.md)) with the
     collected contact/bank/verification fields.
  3. Calls the `createSellerSession(storeId, email)` Server Action, which
     only sets the session cookie — it does **not** create anything, because
     Server Actions run in Node.js where the `localStorage`-backed mock DB
     is a no-op (see `src/lib/mock-db.ts`). Steps 1–2 **must** happen
     client-side first; this is why `createSellerSession` takes a `storeId`
     parameter instead of generating one itself.
- **Onboarding now collects real verification fields**, not just store
  branding: `sellerType` ("individual" | "business"), `nicNumber` (always
  required), `businessRegistrationNumber` (required only if
  `sellerType === "business"`, enforced via a zod `.superRefine`), plus bank
  account details (`bankName`, `bankAccountName`, `bankAccountNumber`) and a
  terms-agreement checkbox. See [`database-model.md`](../../../docs/database-model.md)
  for where each field lives (`Store` vs. `StoreSettings`).
- **New stores start `verificationStatus: "pending"`** and are invisible to
  every public read path (`listStores`, `getStoreBySlug` filter to
  `"active"` only). A pending seller can still sign in and use the full
  dashboard (add products, edit settings) — only the *public* storefront is
  gated, matching how real marketplaces let sellers prep their catalog
  during review. The dashboard shows a persistent banner
  (`PendingVerificationBanner`) while `verificationStatus !== "active"`.
- **Approval/rejection only happens via `/admin`** (see
  [Admin (not a real role)](#admin-not-a-real-role)), never by the seller
  themselves — approving sets both `verificationStatus: "active"` **and**
  `isVerified: true` (the platform is vouching for the seller at that
  point); rejecting sets `verificationStatus: "rejected"` and stores an
  admin-supplied `rejectionReason` on `StoreSettings`, shown in the
  dashboard banner.
- Session payload: `{ role: "seller", storeId, email }`, stored as
  **unsigned, unencrypted base64 JSON** in an `httpOnly` cookie
  (`storepilot_session`, 7-day `maxAge`). Explicitly documented in source as a
  demo shortcut, not production-ready.
- Route protection (`src/proxy.ts`, Next 16's `middleware.ts`): unauthenticated
  visitors to `/dashboard/*` are redirected to `/login?redirectTo=<path>`;
  authenticated sellers visiting `/login` are redirected to `/dashboard`.
  **`/admin` is not in the matcher at all** — see below.
- Sign-out deletes the session cookie and redirects to `/login`.

## Seller-store context (new)

Every dashboard page previously hardcoded `CURRENT_SELLER_STORE_ID` — fine
when only one store could ever exist, but wrong the moment onboarding could
create a second one. `src/hooks/use-seller-store.tsx` now provides:

- `SellerStoreProvider` — mounted once in `src/app/dashboard/layout.tsx`,
  given the storeId from the (server-read) session.
- `useSellerStoreId()` — called by every dashboard page/component that
  previously imported `CURRENT_SELLER_STORE_ID` directly (products, orders,
  settings, payouts pages, `DashboardSidebarContent`,
  `PendingVerificationBanner`, the new-product page). Throws if used outside
  the provider.

`CURRENT_SELLER_STORE_ID` still exists in `src/mock/stores.ts` and is still
used by `/login` (which has no way to know which store an arbitrary email
belongs to) and as a defensive fallback in `dashboard/layout.tsx`.

## Admin (not a real role)

`/admin` (`src/app/admin/`) is a **minimal, unauthenticated** internal tool,
not a real platform-operator role:

- Not covered by `proxy.ts`'s matcher (`["/dashboard/:path*", "/login"]`) —
  anyone who knows the URL can reach it, in dev or production. This is a
  deliberate, explicitly-flagged demo shortcut, not an oversight.
- Two sections: **pending store applications** (approve/reject, with a
  reason required to reject) and **payout runs** (create a payout batch per
  store from its currently-eligible delivered+paid orders; mark a scheduled
  payout as paid with an optional bank reference).
- All actions call `storesService`/`payoutsService` functions directly from
  client-side mutations — no dedicated Server Actions were introduced for
  admin, since (like the rest of the dashboard) these need to run in the
  browser to see `localStorage` (see `src/lib/mock-db.ts`).
- A real backend **must** put this behind a genuine admin role/auth before
  shipping anything like it to production.

## User stories

- As a seller, I want to sign in with my email to access my dashboard.
- As a prospective seller, I want to apply with my store and verification
  details, and understand my store won't be public until approved.
- As a seller, I want to know why my application was rejected.
- As a seller, I want to sign out.
- As an unauthenticated visitor, I should be redirected to sign in if I try
  to reach the dashboard directly, and returned to where I was headed after
  signing in.
- As a platform operator, I want to review a new seller's NIC/business
  registration/bank details before their store goes live, and control when
  their earnings are actually paid out.

## Pages

| Path | Component | Type | Notes |
|---|---|---|---|
| `/login` | `src/app/login/page.tsx` | Server | Renders a plain `<form action={signInAsSeller}>` (Server Action, no client JS needed); reads `redirectTo`/`error` from `searchParams` |
| `/onboarding` | `src/app/onboarding/page.tsx` | Client | react-hook-form + zod; on submit, creates the Store/Settings client-side then establishes the session, via `useMutation` |
| `/admin` | `src/app/admin/page.tsx` | Client | No auth. Store approval + payout runs — see above |

## Components

`src/components/dashboard/pending-verification-banner.tsx` (reads the
signed-in store's `verificationStatus` via `useSellerStoreId` +
`useQuery`). Otherwise page-local form markup and shared UI primitives
(`Card`, `Input`, `Select`, `RadioGroup`, `Checkbox`, `Label`, `Button`,
`Dialog`).

## Hooks

`/onboarding` uses `useForm` (react-hook-form), `useMutation`
(`@tanstack/react-query`), and `useRouter`. `/login` is a Server Component —
no hooks. `/admin` uses `useQuery`/`useMutation` extensively (see
[`payouts.md`](payouts.md)).

## Context providers

`SellerStoreProvider` (see above) — the first app-specific React Context in
the codebase; previously there was none. No auth/session Context exists —
every place that needs the *session itself* (as opposed to just the
storeId) still calls `getSession()` server-side directly.

## State management

Session: httpOnly cookie only. Store/settings creation and admin actions:
TanStack Query mutations over the same `localStorage`-backed services used
everywhere else.

## Forms

- `/login`: uncontrolled native `<form action={signInAsSeller}>` — a Server
  Action receiving `FormData` directly, no client-side validation at all
  (only the HTML `required` attribute on the email input).
- `/onboarding`: react-hook-form + `zodResolver`, now normalized onto
  `useMutation` (previously a bare async function with no `onError`
  handling — see [`gaps-and-assumptions.md`](../../../docs/gaps-and-assumptions.md)
  entry, now resolved).

## Validation

```ts
// onboarding
const onboardingSchema = z.object({
  storeName: z.string().min(3, "Enter your store name"),
  category: z.string().min(1, "Select a category"),
  tagline: z.string().min(5, "Add a short tagline"),
  description: z.string().min(20, "Describe your store in a bit more detail (min 20 characters)"),
  city: z.string().min(2, "Enter your city/town"),
  district: z.string().min(1, "Select a district"),
  whatsappNumber: z.string().min(9, "Enter a valid WhatsApp number"),
  contactEmail: z.string().email("Enter a valid email"),
  sellerType: z.enum(["individual", "business"]),
  nicNumber: z.string().min(10, "Enter a valid NIC number"),
  businessRegistrationNumber: z.string().optional(), // required via superRefine if business
  bankName: z.string().min(2, "Enter your bank name"),
  bankAccountName: z.string().min(2, "Enter the account holder name"),
  bankAccountNumber: z.string().min(4, "Enter the account number"),
  agreeToTerms: z.boolean().refine((v) => v, "You must agree to continue"),
});
```

`/login`'s server action only checks `String(formData.get("email") ??
"").trim()` is non-empty; on failure it redirects back to `/login` with
`?error=missing-email`.

## Navigation flow

```
GET /dashboard (no session) ──proxy──► redirect /login?redirectTo=/dashboard
/login ──(submit, empty email)──► redirect /login?error=missing-email&redirectTo=...
/login ──(submit, any email)──► createSession() ──► redirect <redirectTo || /dashboard>
GET /login (has session) ──proxy──► redirect /dashboard

/onboarding ──(submit)──► storesService.createStore() [client]
                       ──► storesService.updateStoreSettings() [client, upsert]
                       ──► createSellerSession(storeId, email) [server action]
                       ──► toast success ──► router.push(/dashboard)

/admin ──(Approve)──► setStoreVerificationStatus(id, "active") ──► store now public
/admin ──(Reject + reason)──► setStoreVerificationStatus(id, "rejected", reason)
/admin ──(Create payout batch)──► payoutsService.createPayout(storeId)
/admin ──(Mark as paid)──► payoutsService.markPayoutPaid(payoutId, reference?)

Dashboard sidebar ──(Sign out)──► deleteSession() ──► redirect /login
```

## Expected backend APIs

Real endpoints this mock currently stands in for:

- `POST /api/auth/login` — real credential verification (password, OTP, or
  magic link — TODO: product to decide which).
- `POST /api/auth/register` — now much closer to real: must persist a new
  `Seller`/`User` record (still missing, see
  [`database-model.md`](../../../docs/database-model.md)) alongside the `Store` +
  `StoreSettings` creation this mock already does client-side.
- `POST /api/auth/logout`.
- `GET /api/auth/session` *(proposed)*.
- `POST /api/admin/stores/:id/verification` — approve/reject, **must** be
  behind real admin auth.
- `POST /api/stores/:id/payouts`, `PATCH /api/payouts/:id/paid` — see
  [`payouts.md`](payouts.md) and [`api-contracts.md`](../../../docs/api-contracts.md).

## Error handling

- `/login`: the only surfaced error is "missing email" (as a query param,
  not thrown/caught). No "invalid credentials" state exists because no
  credentials are checked.
- `/onboarding`: now uses `useMutation`'s `onError` → `toast.error(...)`,
  consistent with every other mutation in the app (previously a bare
  `await` with no error handling — resolved).
- `/admin`: every mutation has an `onError` toast; no optimistic UI.

## Permissions

- `/dashboard/*` requires a `role: "seller"` session per `proxy.ts`.
  **Ownership is still not enforced anywhere below the proxy** — every
  dashboard page now correctly uses the *signed-in* seller's `storeId` (via
  `useSellerStoreId()`) instead of a hardcoded constant, but the **service
  layer itself** still does zero ownership checks (e.g.
  `productsService.updateProduct(id, input)` never verifies the product
  belongs to the caller's store). This is still invisible in the demo
  (nothing exercises two sellers editing each other's data), but remains a
  **must-fix for a real backend** — see
  [`api-contracts.md`](../../../docs/api-contracts.md#authorization).
- `/admin` has **no permission check of any kind**. See
  [Admin (not a real role)](#admin-not-a-real-role).

## Edge cases

- Signing in via `/login` with different emails always lands on the same
  store/dashboard — `/onboarding` is the only way to reach a second store in
  this demo.
- `redirectTo` is taken from a query param and used directly in a
  `redirect()` call with no allow-list/relative-path check — a backend
  replacement should validate this is a same-origin relative path to avoid
  an open-redirect vector.
- The session cookie is `httpOnly` (good) but unsigned (bad) — a client
  with the ability to set cookies could forge a session for any `storeId`.
- **A session can outlive the store it points to.** Since stores live in
  `localStorage` (cleared by the user, or by a schema change during
  development — see next point) while the session lives in a cookie, it's
  possible to be "signed in" to a `storeId` that no longer resolves to
  anything. Every place that reads the store handles this (falls back to
  "Your store" / hides the storefront link / shows nothing) rather than
  crashing, but there's no automatic sign-out or repair.
- **`mockDb` has no schema migration story.** `src/lib/mock-db.ts` seeds
  `localStorage` once, the first time a given key is read, from whatever
  the current seed constant is — it never re-seeds or migrates an existing
  cached collection. If a seed object's shape changes (as happened during
  this feature: `Store` gained `verificationStatus`), a browser with an
  **older** cached copy in `localStorage` will silently have `undefined`
  for the new field until that key is cleared. This is a real limitation
  of the mock persistence layer, not just a one-off testing hiccup — worth
  fixing (e.g. a version stamp + reseed-on-mismatch) if the mock layer's
  lifetime extends much further.
- A brand-new store's `generateMetadata` (SSR, for the `<title>` tag) will
  say "Store not found" until the client-side reconciliation on
  `store-page-content.tsx` resolves — cosmetic only (see
  [`store-and-product-detail.md`](store-and-product-detail.md)).

## Future improvements

- Real credential-based or passwordless auth for `/login`, including a real
  email→seller→store lookup (today only `/onboarding` produces a
  non-default store).
- Signed/encrypted session (e.g. via `jose`).
- Ownership checks on every seller-scoped service function, not just at the
  `/dashboard` proxy gate.
- Real auth for `/admin`, or fold its actions into the regular seller admin
  surface with a proper role check.
- Password reset / email verification flows.
- Automated identity verification (see the platform's broader
  seller-verification discussion) — this mock only *collects* NIC/business
  registration numbers as text for human review, it doesn't validate them
  against any registry or run any ID-document/liveness check.

## Technical notes

- `signInAsSeller`/`createSellerSession`/`signOutSeller` are Next.js Server
  Actions (`"use server"`, `src/lib/actions/auth.ts`). `createSellerSession`
  (renamed from the old `createSellerAccount`) takes `(storeId, email)` —
  it no longer creates anything itself, see
  [Business rules](#business-rules) above for why.

## Dependencies

`next/navigation` (`redirect`, `useRouter`), `next/headers` (`cookies`,
server-only), `react-hook-form`, `@hookform/resolvers/zod`, `zod`, `sonner`,
`@tanstack/react-query`, `@/lib/session`, `@/lib/actions/auth`,
`@/hooks/use-seller-store`, `@/services` (`storesService`,
`payoutsService`), `@/mock/stores` (`CURRENT_SELLER_STORE_ID`).

## TODOs discovered

- No explicit `// TODO` comments, but multiple **explanatory comments that
  function as flagged shortcuts**, quoted here for traceability:
  - `session.ts`: *"Mock session store: the payload is plain base64 JSON in
    the cookie, not signed or encrypted... before this ever touches real
    accounts, swap this for a signed/encrypted session."*
  - `auth.ts`: *"Mock auth: any email signs in as the single demo seller. A
    real implementation would verify credentials against a user record
    here."*
  - `proxy.ts`: *"Optimistic auth check... real authorization still happens
    server-side wherever data is fetched"* — per [Permissions](#permissions),
    still does not happen anywhere in the service layer.
  - `stores.service.ts`'s `createStore`: *"Must be called client-side... a
    Server Action can't see localStorage."*
  - `admin/layout.tsx`: renders a visible `Badge` reading "Internal tool —
    no auth in this demo" directly in the UI, not just in source comments.
