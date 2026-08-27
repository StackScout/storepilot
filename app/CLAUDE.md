@AGENTS.md

# StorePilot — Project Memory

Full documentation set lives in [`docs/`](docs). This file is the fast
orientation + conventions reference; read the linked doc before making any
non-trivial change in its area. **This app talks to a real backend** (the
Spring Boot/Kotlin service in `../backend`) over HTTP — there is no mock
data layer. Several docs under `docs/` still describe an earlier
mock-only/`localStorage` architecture; treat those as historical unless
verified against current source (see [General rules](#general-rules)).

## Project purpose

StorePilot is a multi-vendor e-commerce marketplace connecting small Sri
Lankan sellers with buyers (LKR pricing, WhatsApp contact, PayHere/COD
payment). Full detail, business problem, monetization model:
[`docs/overview.md`](../docs/overview.md).

## Architecture overview

Next.js 16 App Router (note: `src/proxy.ts` is this version's renamed
`middleware.ts` — same API) · React 19 · TypeScript strict · Tailwind v4 ·
shadcn/ui on `@base-ui/react` · Zustand (cart only) · TanStack Query ·
react-hook-form + zod.

**The one thing to never forget**: auth is real Cognito-backed JWT auth via
httpOnly cookies, not a client-decodable session. `src/proxy.ts` (this
version's renamed `middleware.ts`) verifies the access-token cookie's JWT
signature edge-side (via `jose`) purely to gate which shell renders
(buyer/seller/admin/signed-out) — the Spring backend re-validates the same
JWT on every real API call, so proxy-side checks are optimistic routing,
never the actual authorization boundary. Because there's no
server-decodable session, a client component that needs to know "am I
signed in, and as what" calls `useAuthSession()`
(`src/hooks/use-auth-session.ts`, wrapping `GET /api/auth/session`) instead
of receiving a session as a prop — this is also why the dashboard and
anything cart/order-related is client-rendered with React Query rather than
a Server Component. All HTTP calls go through the REST-shaped functions in
`src/services/*.service.ts`, which call the shared `apiClient`
(`src/lib/api-client.ts`) — that's the one file that knows the backend's
base URL and error shape. Full breakdown:
[`docs/frontend-architecture.md`](docs/frontend-architecture.md) (may still
describe the earlier mock architecture in places — verify against source).

Domain types and the API-client error/parsing primitives (`ApiError`,
`toApiError`, `parseBody`) are shared with the mobile app (`../mobile`) via
the `@storepilot/shared-api` workspace package
(`../packages/shared-api/src`) — `src/types/*.ts` here are thin
re-exports of it. Add or change a shared DTO there, not by hand-duplicating
it in both apps. Endpoint contracts: [`docs/api-contracts.md`](../docs/api-contracts.md);
schema: [`docs/database-model.md`](../docs/database-model.md).

Full feature list with pages/components/docs:
[`docs/feature-index.md`](docs/feature-index.md). Per-feature deep dives
(business rules, forms, validation, edge cases, TODOs) live in
[`docs/features/`](docs/features). Step-by-step user journeys:
[`docs/user-flows.md`](docs/user-flows.md). Every reusable component:
[`docs/ui-components.md`](docs/ui-components.md).

## Coding conventions

- Import domain types from the `@/types` barrel, not individual files
  under `src/types/`.
- Call services via the namespaced barrel: `import { productsService }
  from "@/services"`, not individual `*.service.ts` files.
- Forms: `react-hook-form` + `zodResolver` + inline
  `<p className="text-destructive text-xs">` error text. Match this
  pattern; don't introduce a different form library or error-display
  convention. (One page, `/track-order`, breaks this convention with plain
  `useState` — noted as an inconsistency in
  [`docs/features/order-tracking.md`](docs/features/order-tracking.md), not
  a pattern to copy.)
- Money is always integer LKR (`priceLkr`, `totalLkr`, etc.), formatted
  through `formatLkr()` from `@/lib/currency` — never hand-format currency.
- Dates formatted through `formatDate`/`formatDateTime`/`timeAgo`
  (`@/lib/format`), `en-LK` locale.
- `cn()` (`@/lib/utils`) for conditional/merged Tailwind classes — used
  wherever a component takes `className` or has variant logic.

## Folder conventions

```
src/app/            Next.js routes: (marketplace) route group (public),
                     dashboard/ (seller, session-gated), login/, onboarding/
src/components/      marketplace/ (buyer), dashboard/ (seller),
                     shared/ (both), ui/ (vendored shadcn primitives)
src/hooks/           Custom hooks: cart, auth session, seller store
                     resolution, live status polling, etc.
src/lib/             api-client.ts (the one file that talks HTTP to the
                     backend), constants.ts, currency.ts, format.ts,
                     payhere.ts, platform-config.ts, utils.ts,
                     query-keys.ts (shared React Query key factory)
src/services/        REST-shaped async functions calling apiClient (the
                     backend integration boundary)
src/store/           Zustand stores (cart-store.ts)
src/types/           Domain types, re-exported from @storepilot/shared-api
                     via index.ts
```

Full table with descriptions: [`docs/frontend-architecture.md`](docs/frontend-architecture.md#folder-structure).

## Naming conventions

- Route folders: lower-kebab or Next.js dynamic-segment syntax
  (`[orderId]`, `[slug]`).
- Component files: kebab-case (`product-card.tsx`), exporting one
  PascalCase component matching the file's purpose (`ProductCard`).
- Service functions read like REST endpoints and are documented inline
  with a `/** METHOD /path */` comment above each one — keep this comment
  style when adding new service functions; it's what
  [`docs/api-contracts.md`](../docs/api-contracts.md) was derived from.
- Client-side storage keys (cart persistence, the access-token cookie) are
  prefixed `storepilot_` (e.g. `storepilot_cart`, `storepilot_access_token`)
  — keep this prefix for any new one.

## Component conventions

- shadcn/ui primitives use `@base-ui/react`, **not Radix** — use the
  `render` prop, not `asChild`, to make a primitive render as another
  element (e.g. `<Button render={<Link href="/search" />} />`). This comes
  up constantly with `Button`, `SheetTrigger`, `SheetClose`.
- `src/components/ui/*` is vendored (via `npx shadcn add`, see
  `components.json`) — compose on top, don't hand-edit primitives.
- Shared presentational components (`EmptyState`, `PriceDisplay`,
  `RatingStars`, `OrderStatusBadge`, skeletons, `Logo`) live in
  `components/shared/` and are used by both marketplace and dashboard —
  reuse them instead of rebuilding equivalent UI. Full list:
  [`docs/ui-components.md`](docs/ui-components.md).

## State management conventions

Three deliberately separate mechanisms — don't blur them:
1. **Zustand + `persist`** — only for the cart (`src/store/cart-store.ts`),
   the one piece of state that must survive reloads client-side before
   checkout creates anything server-side. Always go through `useCart()`
   (`src/hooks/use-cart.ts`), never
   `useCartStore` directly, so the hydration guard (`isHydrated`) is
   applied — skipping it causes SSR/client mismatches.
2. **TanStack Query** — all service-backed data. `staleTime: 30_000`,
   `refetchOnWindowFocus: false` (set once in `src/app/providers.tsx`).
   Call `queryClient.invalidateQueries` on mutation success for every
   dependent list. Query keys come from the shared factory in
   `src/lib/query-keys.ts` (`queryKeys.products.byStore(storeId)`, etc.) —
   add a new entry there instead of hand-writing an inline array literal,
   so distinct query shapes never collide on the same top-level key. A
   handful of entities also have thin hook wrappers in `src/hooks/use-*.ts`
   (e.g. `use-categories.ts`) for the most-duplicated call sites; not every
   entity needs one — an inline `useQuery` pulling its key from
   `queryKeys` is still the norm for the rest.
3. **Local `useState`** — everything else (dialogs, steppers, local form
   UI state not covered by react-hook-form).

No Redux, no global Context store. Auth is httpOnly-cookie-based, so there
is no server-decodable session to read — every place that needs to know
"am I signed in, and as what" calls the client-side `useAuthSession()` hook
(`src/hooks/use-auth-session.ts`, backed by React Query + `GET
/api/auth/session`), not a server-side session read. This is also why
`(marketplace)/layout.tsx` and its pages stay Server Components that render
the same regardless of auth state — a marketplace page needing per-user
auth state (e.g. the header's buyer-account link, via
`useBuyerAccountLink`) reads it client-side rather than forcing the layout
itself into dynamic rendering, which would undo static generation for the
home page, search, and every store/product page beneath it (verified via
`next build`: `○` → `ƒ`). The dashboard, by contrast, resolves the signed-in
seller's store via `SellerStoreProvider`/`useSellerStoreId()`
(`src/hooks/use-seller-store.tsx`, `GET /api/me/store`) rather than a
hardcoded store id.

## Important business rules

- **Cart is single-store-only.** Adding a product from a different store
  than what's already in the cart is rejected client-side and must prompt
  the buyer to explicitly replace the cart — never silently merge two
  stores' items. This is a client-side UX affordance only; checkout itself
  is server-validated per order regardless (price/stock are re-checked
  against the live `Product`, never trusted from the client cart).
- **Buyer total = subtotal + flat shipping (350 LKR). Platform fee (3.5%)
  is deducted from the seller's payout, not added to the buyer's charge.**
  Full monetization model: [`docs/overview.md#monetization-model`](../docs/overview.md#monetization-model).
- **Order status is a state machine**: `pending → confirmed/cancelled`,
  `confirmed → shipped/cancelled`, `shipped → delivered`, `delivered` and
  `cancelled` are terminal. Enforced server-side in
  `OrderService.ALLOWED_STATUS_TRANSITIONS` (`../backend`), mirrored
  client-side in `components/dashboard/order-status-select.tsx` so the UI
  doesn't offer an illegal transition — keep both in sync if this changes.
  Bookings have their own parallel state machine in `BookingService`.
- **Product `status` auto-forces to `"out-of-stock"` whenever
  `stockQuantity === 0`**, regardless of what status was submitted.
- **`OrderItem` fields are immutable snapshots** taken at order-creation
  time — never join back to the live `Product` to render historical order
  data, and never let editing/deleting a product retroactively change a
  past order.
- **A seller's store is resolved from their signed-in identity**
  (`GET /api/me/store`, see `useSellerStoreId()` above) — there is no
  hardcoded demo store id. A seller who onboards can sign back into their
  own store normally.

## Design principles

- Public/SEO-relevant marketplace pages are Server Components fetching
  directly from services; anything mutable or session-scoped is a client
  component using React Query — this split is forced by auth being
  httpOnly-cookie-based with no server-decodable session (see
  [State management conventions](#state-management-conventions)), not just
  a style preference. See
  [`docs/frontend-architecture.md#application-architecture`](docs/frontend-architecture.md#application-architecture).
- Filter/search state on `/search` lives in the URL, not client state, so
  it works without JS and is linkable — preserve this pattern for any new
  filterable listing page.
- Tailwind v4, CSS-first config (no `tailwind.config.js`) — theme tokens
  are CSS custom properties in `src/app/globals.css`, remapped via
  `@theme inline`. Dark mode (`.dark` class) exists in CSS but has no
  toggle wired up anywhere yet.

## Things future Claude sessions should know before making changes

- **[`docs/gaps-and-assumptions.md`](../docs/gaps-and-assumptions.md) and
  other docs under `docs/` may still describe the pre-backend
  `localStorage`/mock-seller era** (e.g. "no ownership checks," "unsigned
  session cookie," "hardcoded demo store," "no stock re-validation") — the
  real backend (`../backend`) has since added Cognito auth, JWT cookies,
  per-seller store resolution (`GET /api/me/store`), and server-side stock
  re-validation at checkout (see `OrderService`). Verify a doc's claim
  against current backend/frontend source before trusting it; don't port a
  documented "gap" into new code as if it were still true without checking.
- **No tests exist in this repository** (`app/`; no test files, no test
  script) — the backend does have a real Kotlin/Gradle test suite
  (`../backend`, run via `./gradlew test`). Don't assume `npm test` here
  does anything meaningful without checking `package.json` first.
- Before editing `src/components/ui/*`, check whether the change should
  instead be a new component composed on top — these are vendored shadcn
  primitives.

## General rules

- Everything in `docs/` is derived from reading the actual source, not
  invented — if you find the code has changed since a doc was written,
  update the doc rather than trusting it blindly.
- Mark genuinely unknown/undecided items as TODO in the relevant doc rather
  than guessing at intended behavior.
- Cross-reference related docs rather than duplicating content — each doc
  links to the others where relevant; follow those links before doing a
  fresh full-codebase read.
