@AGENTS.md

# IslandCart — Project Memory

Full documentation set lives in [`docs/`](docs). This file is the fast
orientation + conventions reference; read the linked doc before making any
non-trivial change in its area. **This is a frontend-only demo/prototype —
there is no real backend.**

## Project purpose

IslandCart is a multi-vendor e-commerce marketplace connecting small Sri
Lankan sellers with buyers (LKR pricing, WhatsApp contact, PayHere/COD
payment). Full detail, business problem, monetization model:
[`docs/overview.md`](../docs/overview.md).

## Architecture overview

Next.js 16 App Router (note: `src/proxy.ts` is this version's renamed
`middleware.ts` — same API) · React 19 · TypeScript strict · Tailwind v4 ·
shadcn/ui on `@base-ui/react` · Zustand (cart only) · TanStack Query ·
react-hook-form + zod.

**The one thing to never forget**: there is no backend. Everything routes
through `src/lib/mock-db.ts`, a `localStorage`-backed pseudo-database
seeded from `src/mock/*.ts`, wrapped by REST-shaped functions in
`src/services/*.service.ts`. Server Components can only see the static
seed (not `localStorage`), which is why the dashboard and anything
cart/order-related is client-rendered with React Query. Full breakdown:
[`docs/frontend-architecture.md`](docs/frontend-architecture.md).

To replace the mock backend with a real one: implement
[`docs/api-contracts.md`](../docs/api-contracts.md) against the schema in
[`docs/database-model.md`](../docs/database-model.md), then change only the
bodies of functions in `src/services/*.service.ts` — call sites should not
need to change.

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
src/hooks/           Custom hooks (currently just use-cart.ts)
src/lib/             actions/ (Server Actions), mock-db.ts, session.ts,
                     constants.ts, currency.ts, format.ts, delay.ts, utils.ts
src/mock/            Seed data + categories config
src/services/        REST-shaped async functions over mock-db (the
                     boundary to replace when a real backend exists)
src/store/           Zustand stores (cart-store.ts)
src/types/           Domain types, barrel-exported via index.ts
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
- `localStorage` collection keys are prefixed `islandcart_` (e.g.
  `islandcart_products`, `islandcart_cart`) — keep this prefix for any new
  collection.

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
   the one piece of state that must survive reloads without a backend.
   Always go through `useCart()` (`src/hooks/use-cart.ts`), never
   `useCartStore` directly, so the hydration guard (`isHydrated`) is
   applied — skipping it causes SSR/client mismatches.
2. **TanStack Query** — all service-backed data. `staleTime: 30_000`,
   `refetchOnWindowFocus: false` (set once in `src/app/providers.tsx`).
   Call `queryClient.invalidateQueries` on mutation success for every
   dependent list. There is no shared hooks layer (`useProducts()` etc.)
   today — query keys are hand-written inline at each call site; keep new
   ones consistent with existing patterns (e.g. `["products", "store",
   storeId]`) to avoid cache-key drift.
3. **Local `useState`** — everything else (dialogs, steppers, local form
   UI state not covered by react-hook-form).

No Redux, no global Context store, no general `useSession()` hook — the
session is read server-side via `getSession()` and passed down as a prop
wherever a specific page needs it. **One deliberate exception**: the site
header's buyer-account link uses a client-side fetch
(`useBuyerAccountLink`, hitting `GET /api/account/session`) instead of a
server-side session read, specifically because `(marketplace)/layout.tsx`
wraps every marketplace page — reading `getSession()` there would force
the home page, search, and every store/product page out of static
generation (verified via `next build`: `○` → `ƒ`). Don't "simplify" this
back to a layout-level session read without re-checking the build output.
See [`docs/features/buyer-accounts.md#technical-notes`](docs/features/buyer-accounts.md#technical-notes).

## Important business rules

- **Cart is single-store-only.** Adding a product from a different store
  than what's already in the cart is rejected client-side and must prompt
  the buyer to explicitly replace the cart — never silently merge two
  stores' items. Must also be re-enforced server-side once a real backend
  exists (a client can bypass the Zustand check).
- **Buyer total = subtotal + flat shipping (350 LKR). Platform fee (3.5%)
  is deducted from the seller's payout, not added to the buyer's charge.**
  Full monetization model: [`docs/overview.md#monetization-model`](../docs/overview.md#monetization-model).
- **Order status is a state machine**: `pending → confirmed/cancelled`,
  `confirmed → shipped/cancelled`, `shipped → delivered`, `delivered` and
  `cancelled` are terminal. Currently enforced only in
  `components/dashboard/order-status-select.tsx` — **not** in the service
  layer. Any new code path that changes order status must respect this.
- **Product `status` auto-forces to `"out-of-stock"` whenever
  `stockQuantity === 0`**, regardless of what status was submitted.
- **`OrderItem` fields are immutable snapshots** taken at order-creation
  time — never join back to the live `Product` to render historical order
  data, and never let editing/deleting a product retroactively change a
  past order.
- **The single mock seller is `CURRENT_SELLER_STORE_ID = "store-01"`**
  (`src/mock/stores.ts`) — every dashboard page keys off this constant.
  There is no real per-seller store association yet.

## Design principles

- Public/SEO-relevant marketplace pages are Server Components fetching
  directly from services; anything mutable or session-scoped is a client
  component using React Query — this split is forced by the
  `localStorage`-as-backend architecture, not just a style preference. See
  [`docs/frontend-architecture.md#application-architecture`](docs/frontend-architecture.md#application-architecture).
- Filter/search state on `/search` lives in the URL, not client state, so
  it works without JS and is linkable — preserve this pattern for any new
  filterable listing page.
- Tailwind v4, CSS-first config (no `tailwind.config.js`) — theme tokens
  are CSS custom properties in `src/app/globals.css`, remapped via
  `@theme inline`. Dark mode (`.dark` class) exists in CSS but has no
  toggle wired up anywhere yet.

## Things future Claude sessions should know before making changes

- **Read [`docs/gaps-and-assumptions.md`](../docs/gaps-and-assumptions.md)
  before implementing any backend endpoint or touching auth/checkout/order
  status.** It lists known security gaps (no ownership checks, no stock
  re-validation, unsigned session cookie), data-model inconsistencies
  (platform fee computed globally despite a per-store field existing,
  `codEnabled`/`onlinePaymentEnabled` saved but never read at checkout),
  and missing entities (no real `Seller`/`User`, `Review`, or
  `Payout`/`Settlement` records exist). Don't port these gaps into a real
  backend as if they were intended behavior — most are explicitly flagged
  as needing a fix or a product decision, not a faithful mock-to-real port.
- **Onboarding creates a real store, but `/login` doesn't know about it** —
  `/onboarding` persists a real `Store` + `StoreSettings` row (status
  `"pending"` until approved via `/admin`) and signs the session into it.
  There's still no `User`/`Seller` entity, so `/login` always signs into
  the hardcoded `CURRENT_SELLER_STORE_ID` demo store regardless of email —
  a returning seller who onboarded and signed out cannot sign back into
  their own store via `/login` today. See
  [`docs/gaps-and-assumptions.md`](../docs/gaps-and-assumptions.md).
- **No ownership/ownership checks exist in the mock service layer at
  all** — invisible with one seller, critical to add for a real
  multi-tenant backend. See
  [`docs/api-contracts.md#authorization`](../docs/api-contracts.md#authorization).
- **No tests exist in this repository** (no test files, no test script).
  Don't assume `npm test` does anything meaningful without checking
  `package.json` first.
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
