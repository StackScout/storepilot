# Feature: Buyer Accounts

> Index: [`feature-index.md`](../feature-index.md) · Architecture:
> [`frontend-architecture.md`](../frontend-architecture.md) · API:
> [`api-contracts.md`](../../../docs/api-contracts.md)

## Purpose

Let a buyer optionally create an account to save an address and see order
history across visits, without giving up guest checkout — which remains
the default and is unchanged by this feature. This is the one auth surface
in the app where the "account" side is genuinely real (a persisted `Buyer`
row, a real email lookup at sign-in) rather than a no-op — contrast with
[`seller-auth.md`](seller-auth.md), where `/login` still signs anyone into
the same demo seller regardless of email.

## Business rules

- **Guest checkout is unaffected.** Nothing about this feature requires an
  account — checkout collects an email either way (see
  [`checkout.md`](checkout.md)), and only *tags* the resulting `Order` with
  `buyerId` when the buyer happened to be signed in.
- **`/account/register` creates a real `Buyer` row**
  (`buyersService.registerBuyer`, client-side — same
  Server-Action-can't-see-`localStorage` constraint as seller onboarding,
  see [`seller-auth.md#business-rules`](seller-auth.md#business-rules)),
  rejecting a second registration with the same email
  (`getBuyerByEmail` lookup first).
- **`/account/login` does a real lookup**, unlike seller `/login`:
  `buyersService.getBuyerByEmail(email)` either finds the account and
  signs in, or shows "No account found with that email." There is still no
  password — knowing the email is the entire "credential". Flagged
  explicitly in both pages' copy and in
  [`gaps-and-assumptions.md`](../../../docs/gaps-and-assumptions.md).
- **One saved address per buyer** (`Buyer.defaultShipping`). It's written
  automatically after every signed-in checkout (best-effort — a failure
  here doesn't block order placement) and offered as a prefill on the next
  checkout. There's no dedicated "edit my address" form; the only way to
  change it today is to check out again with a different address.
- **Order history is scoped by `Order.buyerId`**, set only when
  `CheckoutInput.buyerId` was present (i.e. signed in at checkout time).
  Guest orders — including ones placed with the *same email* as a
  registered account — never retroactively appear in that account's order
  history. There's no "claim my past guest orders" flow.
- **Buyer and seller sessions are independent.** `SessionPayload` is a
  `{ role: "seller"; ... } | { role: "buyer"; ... }` union; a browser can
  hold only one at a time (they share one cookie), so testing both roles
  together means switching sessions, not holding two simultaneously.

## User stories

- As a buyer, I want to create an account so I don't have to retype my
  address every time.
- As a returning buyer, I want to see my past orders in one place instead
  of tracking each one by order number.
- As a buyer, I want to keep checking out as a guest if I don't want an
  account — nothing should require me to sign up.

## Pages

| Path | Component | Type | Notes |
|---|---|---|---|
| `/account/register` | `src/app/account/register/page.tsx` | Client | Wrapped in `<Suspense>` (reads `redirectTo` via `useSearchParams`) — required for static generation, see [Technical notes](#technical-notes) |
| `/account/login` | `src/app/account/login/page.tsx` | Client | Same `<Suspense>` wrapping; real email lookup, not a Server Action form like seller `/login` |
| `/account` | `src/app/account/page.tsx` + `account-view.tsx` | Server shell + Client view | Server component reads the session and passes `buyerId`/`name`/`email` down; the client child fetches the full `Buyer` + order history |

## Components

No new shared components — `AccountView` (`src/app/account/account-view.tsx`)
is page-local, composed from existing shared primitives (`Card`,
`EmptyState`, `OrderStatusBadge`, `PriceDisplay`, `TableRowSkeleton`).

## Hooks

- `useBuyerAccountLink` (`src/hooks/use-buyer-account-link.ts`) — used by
  `SiteHeader`/`MobileNav` to show "Sign in" vs. the buyer's name in the
  header. Deliberately a **client-side fetch** to `/api/account/session`
  rather than a server-side session read in the shared marketplace layout
  — see [Technical notes](#technical-notes).
- Checkout's `CheckoutForm` (`src/app/(marketplace)/checkout/checkout-form.tsx`)
  uses `useQuery(["buyer", buyerId], buyersService.getBuyerById)` to fetch
  the full profile (the session payload itself only carries `buyerId`/
  `name`/`email`, not `defaultShipping`), then `reset()`s the
  react-hook-form with the saved address once it loads — same "fetch →
  `reset()`" pattern documented in
  [`store-settings.md#technical-notes`](store-settings.md#technical-notes).

## Context providers

None — no client Context was introduced for buyer session state (unlike
`SellerStoreProvider` for sellers). The session is read server-side and
passed as a prop wherever a specific page needs it (`/account`, checkout);
the header's link uses the client-fetch hook above instead, precisely to
avoid needing a session read in a layout that wraps SEO-relevant pages.

## State management

Session: httpOnly cookie only, same mechanism as seller sessions
(`src/lib/session.ts`, now a role union). Buyer profile and order history:
TanStack Query over the same `localStorage`-backed services used
everywhere else.

## Forms

- `/account/register`: react-hook-form + `zodResolver` (`name`, `email`,
  `phone?`).
- `/account/login`: plain `useState` for the single email field — no zod
  schema, matches the simplicity of the field being validated by the
  lookup itself, not by format rules.

## Validation

```ts
// /account/register
const registerSchema = z.object({
  name: z.string().min(2, "Enter your full name"),
  email: z.string().email("Enter a valid email"),
  phone: z.string().optional(),
});
```

## Navigation flow

```
/account/register ──(submit)──► buyersService.registerBuyer() [client]
                              ──► createBuyerSession(buyerId, name, email) [server action]
                              ──► redirect to `redirectTo` (default /account)

/account/login ──(submit)──► buyersService.getBuyerByEmail(email) [client]
                           ──► found: createBuyerSession(...) ──► redirect
                           ──► not found: inline error + link to register

/account (unauthenticated) ──proxy──► redirect /account/login?redirectTo=/account

Checkout (signed in) ──(place order)──► createOrder({ ..., buyerId })
                                     ──► buyersService.updateDefaultShipping() [best-effort]

Header "Sign out" ──► signOutBuyer() [server action] ──► redirect /
```

## Expected backend APIs

See [`api-contracts.md#buyer-accounts`](../../../docs/api-contracts.md#buyer-accounts)
for the full contract, including why `GET /api/account/session` is a route
handler rather than a session read inside the marketplace layout.

## Error handling

- `/account/register`: `onError` → `toast.error(error.message)`, surfacing
  the service's "email already exists" message directly.
- `/account/login`: inline error text under the email field, with a link
  to registration — no toast (this is an expected, common outcome, not a
  failure).

## Permissions

- `/account/*` (except `/login`, `/register`) requires a `role: "buyer"`
  session, enforced by `proxy.ts` — same optimistic, cookie-only check as
  `/dashboard/*`. **No ownership check exists below the proxy** — the
  service layer trusts whatever `buyerId` it's given, same caveat as every
  other role in this app. See
  [`gaps-and-assumptions.md`](../../../docs/gaps-and-assumptions.md).

## Edge cases

- Signing in with an email that has no account: inline error, not a
  redirect or silent account creation — the buyer must explicitly choose
  to register.
- A buyer with no `defaultShipping` yet (never checked out signed in):
  `/account` shows "No saved address yet"; checkout shows a blank form,
  same as a guest.
- Switching roles: signing in as a seller while a buyer session is active
  (or vice versa) overwrites the single session cookie — there is no way
  to hold both at once in one browser.

## Future improvements

- A real credential (password, OTP, or magic link) — see
  [`gaps-and-assumptions.md`](../../../docs/gaps-and-assumptions.md).
- A multi-address address book instead of one `defaultShipping` slot.
- "Claim" past guest orders that match the account's email at
  registration/sign-in time — a real product decision (is email enough
  proof of ownership?), not a pure bug fix.
- Editable profile (name/phone) — today set once at registration with no
  edit form.

## Technical notes

- **Why the account link is a client fetch, not a server-read prop**: an
  earlier version of this feature read `getSession()` inside
  `(marketplace)/layout.tsx` and passed `buyerName` down to `SiteHeader`.
  That works, but Next.js treats reading a dynamic API like the session
  cookie in a layout as forcing **every page under that layout** into
  per-request dynamic rendering — verified by `next build`, which flipped
  `/`, `/search`, and the store/product pages from `○` (static) to `ƒ`
  (dynamic). Since those are exactly the pages
  [`frontend-architecture.md`](../frontend-architecture.md) calls out as
  deliberately static for SEO, this was reverted in favor of
  `GET /api/account/session` + `useBuyerAccountLink()` — a small
  client-side fetch, deduped across `SiteHeader`/`MobileNav` by React
  Query's cache, that doesn't touch the pages' render strategy at all.
  Checkout's own session read (`(marketplace)/checkout/page.tsx`) does
  **not** have this problem, since checkout was never a static/SEO page to
  begin with.
- `/account/register` and `/account/login` both call `useSearchParams()`
  (to read `redirectTo`) and are wrapped in an internal `<Suspense>`
  boundary — required by Next.js for a client page using
  `useSearchParams()` to still statically prerender; omitting it fails the
  production build with "Missing Suspense boundary with useSearchParams".

## Dependencies

`react-hook-form`, `@hookform/resolvers/zod`, `zod`, `@tanstack/react-query`,
`sonner`, `@/services` (`buyersService`, `ordersService`), `@/lib/session`,
`@/lib/actions/auth` (`createBuyerSession`, `signOutBuyer`).

## TODOs discovered

- No explicit `// TODO` comments. Both `/account/register` and
  `/account/login` carry an in-UI disclosure that this is a
  no-password demo, quoted here for traceability: *"This is a demo
  account — no password is required, so anyone who knows your email could
  sign in as you. Don't use real personal details."*
