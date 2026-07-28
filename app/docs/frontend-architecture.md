# Frontend Architecture

> Cross-references: [`overview.md`](../../docs/overview.md) · [`feature-index.md`](feature-index.md)
> · [`ui-components.md`](ui-components.md) · [`api-contracts.md`](../../docs/api-contracts.md)
> · [`gaps-and-assumptions.md`](../../docs/gaps-and-assumptions.md)

Stack: Next.js 16.2.11 (App Router) · React 19.2.4 · TypeScript (strict) ·
Tailwind CSS v4 · shadcn/ui on `@base-ui/react` · Zustand · TanStack Query ·
react-hook-form + zod · sonner · lucide-react.

## Folder structure

```
app/
├── src/
│   ├── app/                     Next.js App Router routes (see Routing)
│   │   ├── (marketplace)/       Route group: public buyer storefront
│   │   ├── dashboard/           Seller admin area (session-gated)
│   │   ├── login/               Seller sign-in
│   │   ├── onboarding/          Seller "sign up" (see gaps-and-assumptions.md)
│   │   ├── layout.tsx           Root HTML layout, font, metadata
│   │   ├── providers.tsx        Client providers (React Query, Toaster)
│   │   └── globals.css          Tailwind v4 theme tokens
│   ├── components/
│   │   ├── marketplace/         Buyer-facing composed components
│   │   ├── dashboard/           Seller-facing composed components
│   │   ├── shared/               Cross-cutting presentational components
│   │   └── ui/                  shadcn/ui primitives (vendored)
│   ├── hooks/                   Custom React hooks: use-cart, use-seller-store
│   ├── lib/
│   │   ├── actions/              Server Actions (auth.ts)
│   │   ├── mock-db.ts             localStorage-backed pseudo-database
│   │   ├── session.ts             Cookie-based mock session
│   │   ├── constants.ts           Site-wide constants (fees, districts, labels)
│   │   ├── currency.ts            LKR formatting
│   │   ├── format.ts              Date/time formatting
│   │   ├── delay.ts               Artificial network latency
│   │   └── utils.ts               `cn()` classname helper
│   ├── mock/                     Seed data: stores, products, orders, categories
│   ├── services/                 REST-shaped async functions over mock-db
│   ├── store/                    Zustand stores (cart-store.ts)
│   ├── types/                    Domain TypeScript types, barrel-exported
│   └── proxy.ts                  Next.js 16's renamed `middleware.ts`
├── public/                       Static assets
├── components.json               shadcn/ui config
├── next.config.ts                Next.js config (remote image patterns)
├── tsconfig.json                 `@/*` → `src/*` path alias
├── CLAUDE.md                     Project memory doc for Claude Code
├── AGENTS.md                     Next.js version-warning note (imported by CLAUDE.md)
└── docs/                         This documentation set
```

Naming: route folders are lower-kebab or Next.js dynamic-segment syntax
(`[orderId]`, `[slug]`); component files are kebab-case
(`product-card.tsx`), exporting a single PascalCase component whose name
matches the file's purpose (`ProductCard`).

## Application architecture

There is **no real backend**. The application is a static/SSR-capable
Next.js frontend backed entirely by a `localStorage`-emulated database:

```
src/mock/*.ts (seed arrays)
   → src/lib/mock-db.ts   (mockDb.read/write — localStorage, keyed per collection)
   → src/services/*.service.ts  (async functions shaped like REST endpoints;
       each documented inline with a "/** METHOD /path */" comment; adds
       artificial latency via src/lib/delay.ts)
   → pages / components call e.g. productsService.listProducts(...)
```

Key consequence: `mockDb.read()` returns the static seed unchanged during
SSR (`isBrowser` is false on the server) and only reads real
`localStorage` in the browser. This creates a hard architectural split:

- **Server components** (most of the `(marketplace)` route group) fetch
  directly from services with top-level `await`, for SEO and fast first
  paint — but they can only ever see the static seed data, never a buyer's
  or seller's local mutations.
- **Client components** (the entire `dashboard` area, cart/checkout/order
  pages) use TanStack Query so the browser can read/write through
  `localStorage` and reflect real mutations.
- **The reconciliation pattern**: paint the server-fetched result
  immediately (SEO/fast first load), then a client-side `useQuery`
  (`initialData` + `staleTime: 0`) quietly refetches so mutations invisible
  to the server (new/edited products, newly-onboarded or newly-*approved*
  stores) show up without a full reload. Four components now use this,
  since it turned out to be needed anywhere a Server Component lists or
  looks up a `Store`, not just a store's own product grid:
  - `store-product-grid.tsx` — a store's product list (the original,
    narrowest case).
  - `store-page-content.tsx` — the **entire store detail page**. This one
    is the trickiest: a Server Component `notFound()` on SSR-miss would
    permanently 404 a store that only exists client-side (e.g.
    just-onboarded, or approved via `/admin` in a different tab), so the
    page never calls `notFound()` — it always renders this client
    component with `initialStore: Store | null`, which shows its own
    "not found" UI only once the *client-side* refetch has also resolved
    to null, not from the SSR miss alone. **Trade-off accepted**: a
    genuinely bad slug now returns `200` with a "not found" UI instead of a
    real HTTP `404` — see
    [`features/store-and-product-detail.md`](features/store-and-product-detail.md).
  - `search-results.tsx` — `/search`'s product and store grids (and their
    tab-count labels, so they stay consistent with the grid contents).
  - `popular-stores-grid.tsx` — the home page's "Popular stores" section.
  - **Not applied** to the home page's "Featured products" (sorted by
    `rating`; a brand-new product always starts at `rating: 0` and so
    effectively never ranks into the top 8 regardless) — a deliberate scope
    call, not an oversight.

**To point this at a real backend**: replace the body of each function in
`src/services/*.service.ts` with a `fetch()` call. Call sites (pages,
components, hooks) should not need to change — this is the explicit design
intent (see the top-of-file comment in `src/lib/mock-db.ts`). See
[`api-contracts.md`](../../docs/api-contracts.md) for the endpoint shapes those
`fetch()` calls should target.

## Routing

Next.js App Router, file-based. Independent surfaces:

1. **`(marketplace)` route group** — public buyer storefront. Route group
   parentheses don't affect the URL; layout adds `SiteHeader` + `SiteFooter`.
2. **`dashboard/`** — seller admin area, gated by `src/proxy.ts` (Next 16's
   renamed `middleware.ts` — same `NextRequest`/`NextResponse` API,
   `config.matcher` still applies, function is just called `proxy()` instead
   of `middleware()`). Matcher: `["/dashboard/:path*", "/login", "/account/:path*"]`.
3. **`login/`** and **`onboarding/`** — standalone seller auth entry points,
   each with their own minimal layout (logo header only, no site chrome).
4. **`account/`** — buyer auth + profile area (`register`, `login`, and the
   gated `/account` page itself), same proxy-gated pattern as `dashboard/`
   but for `role: "buyer"` sessions. See
   [`features/buyer-accounts.md`](features/buyer-accounts.md).
5. **`admin/`** — mock platform-operator tool. **Not** in the proxy
   matcher — no session/role check at all, deliberately (see
   [`features/seller-auth.md#admin-not-a-real-role`](features/seller-auth.md#admin-not-a-real-role)).
   Its own minimal layout, logo + a visible "no auth in this demo" badge.

Full route table (path, rendering mode, purpose) is in
[`feature-index.md`](feature-index.md) and repeated per-feature in
`docs/features/*.md`. Dynamic segments used: `[slug]` (store),
`[productSlug]`, `[orderId]`, `[productId]`. All dynamic-segment `params`
(and `searchParams` on server pages) are typed as `Promise<...>` per Next 16
convention and awaited/`use()`-unwrapped before use.

Search-driven state (the `/search` page's query, category, sort, tab) lives
entirely in the URL (`searchParams`), not client state — filtering/sorting
works without client JS and is directly linkable/shareable.

## Layouts

| Layout | File | Applies to | Adds |
|---|---|---|---|
| Root | `src/app/layout.tsx` | Every route | `<html>`/`<body>`, Public Sans font, `Providers` wrapper, site metadata/viewport |
| Marketplace | `src/app/(marketplace)/layout.tsx` | All `(marketplace)` routes | `SiteHeader`, `SiteFooter` |
| Dashboard | `src/app/dashboard/layout.tsx` | All `/dashboard/*` routes | Desktop sidebar (`DashboardSidebarContent`) + mobile top bar (`DashboardMobileNav`), reads session server-side for the signed-in email |
| Login | `src/app/login/layout.tsx` | `/login` | Bare header with `Logo` only |
| Onboarding | `src/app/onboarding/layout.tsx` | `/onboarding` | Bare header with `Logo` only |
| Account | `src/app/account/layout.tsx` | All `/account/*` routes | `Logo` + a "Back to marketplace" link |
| Admin | `src/app/admin/layout.tsx` | `/admin` | Header with `Logo` + a visible "Internal tool — no auth in this demo" badge |

## State management

Three distinct mechanisms, deliberately not unified:

1. **Zustand (`src/store/cart-store.ts`)** — the cart, the only genuinely
   persistent *client* state. Uses `zustand/middleware`'s `persist` to
   `localStorage` under key `storepilot_cart`. Wrapped by
   `src/hooks/use-cart.ts`, which adds a hydration guard via
   `useSyncExternalStore` so server-rendered markup (always an empty cart —
   the server can't read `localStorage`) matches the first client render.
   Any page that conditionally redirects on an empty cart (e.g. checkout)
   must check `isHydrated` before trusting `cart.items.length === 0`, or it
   will incorrectly redirect on first paint before the real cart loads.
2. **TanStack Query** — all server-ish state (anything read through
   `src/services/*`). Configured once in `src/app/providers.tsx`
   (`staleTime: 30_000`, `refetchOnWindowFocus: false`). Used for both
   queries (`useQuery`) and mutations (`useMutation`), with
   `queryClient.invalidateQueries` on mutation success to refresh dependent
   lists (e.g. creating a product invalidates the `["products"]` key so the
   dashboard product list and any storefront grid refetch).
3. **Local component state (`useState`)** — everything else: dialog
   open/closed flags, form step tracking, mobile nav sheet state, the
   cart/checkout "add to different store" conflict dialog, etc.
4. **`SellerStoreProvider` (`src/hooks/use-seller-store.tsx`)** — the first
   app-specific React Context in the codebase. Carries the signed-in
   seller's `storeId` (read server-side from the session in
   `dashboard/layout.tsx`) down to every dashboard client page/component via
   `useSellerStoreId()`. Added because every dashboard page previously
   imported the hardcoded `CURRENT_SELLER_STORE_ID` constant directly —
   harmless while exactly one store could exist, a real bug once
   `/onboarding` could create a second one. Scoped **only** to
   `/dashboard/*`; nothing outside it uses this Context.

There is **no global app-level state store, no Redux**. Beyond
`SellerStoreProvider` above, React Context is otherwise used only for
shadcn/ui primitive internals (dialog/sheet/select composition), not by
other app-specific code.

## Providers

Defined in `src/app/providers.tsx`, mounted once in the root layout:

```tsx
<QueryClientProvider client={queryClient}>
  {children}
  <Toaster position="top-center" richColors />
</QueryClientProvider>
```

- `QueryClient` is created once per client mount via `useState(() => new
  QueryClient(...))` (the standard Next.js App Router pattern to avoid
  sharing a client across requests on the server).
- `Toaster` (sonner) is mounted globally; feature code just calls
  `toast.success(...)` / `toast.error(...)` — no provider wiring is needed
  at the call site.
- No theme provider is mounted despite `next-themes` being a dependency —
  see [`gaps-and-assumptions.md`](../../docs/gaps-and-assumptions.md).
- No general auth/session React Context exists — the session itself is
  still always read server-side per request (`getSession()` in Server
  Components); there's nothing analogous to a `useSession()` hook that
  exposes the *whole* session (email, role) to client components. Only the
  derived `storeId` is lifted into client state, via `SellerStoreProvider`
  (see above) — narrower in scope than a general session context on
  purpose.

## Hooks

- **`src/hooks/use-cart.ts`** — wraps `useCartStore` (Zustand) and derives
  `itemCount`/`subtotal`, exposing a hydration-safe `cart` plus the store's
  mutator functions (`addItem`, `replaceCartWithItem`, `updateQuantity`,
  `removeItem`, `clearCart`, `syncItems`).
- **`src/hooks/use-cart-reconciliation.ts`** — re-fetches each cart line's
  product on load and calls `syncItems`, flagging deleted products
  `isUnavailable` and refreshing stale prices. Called from the cart page,
  cart drawer, and checkout — see [`features/cart.md`](features/cart.md).
- **`src/hooks/use-seller-store.tsx`** — `SellerStoreProvider` +
  `useSellerStoreId()`, see [State management](#state-management) above.
- **`src/hooks/use-buyer-account-link.ts`** — client-side `useQuery` hitting
  `GET /api/account/session` to know whether a buyer is signed in, used
  only by `SiteHeader`/`MobileNav`'s account link. Deliberately a client
  fetch rather than a server-side session read in the shared marketplace
  layout — see
  [`features/buyer-accounts.md#technical-notes`](features/buyer-accounts.md#technical-notes)
  for why (a layout-level `getSession()` call was tried and reverted
  because it forced every marketplace page into dynamic rendering).
- All other "hook-like" reuse is via TanStack Query's own `useQuery` /
  `useMutation` called directly in page/component bodies (no wrapping
  custom hooks like `useProducts()` or `useOrders()` — every call site
  writes its own `queryKey`/`queryFn` inline). This is a deliberate
  simplicity choice in the current codebase, not an oversight — but it does
  mean query keys are duplicated by hand across files (e.g. `["products",
  "store", storeId]` appears in the dashboard products page, new/edit
  product pages, and the storefront product grid) — see
  [`gaps-and-assumptions.md`](../../docs/gaps-and-assumptions.md) for the
  consistency risk this creates.

## Shared components

`src/components/shared/` holds cross-cutting presentational components used
by both the marketplace and dashboard sides: `EmptyState`,
`ProductCardSkeleton`/`ProductGridSkeleton`/`StoreCardSkeleton`/`TableRowSkeleton`
(`loading-skeletons.tsx`), `Logo`, `OrderStatusBadge`, `PriceDisplay`,
`RatingStars`. Full prop/usage documentation:
[`ui-components.md`](ui-components.md).

## Styling approach

Tailwind CSS v4, **CSS-first configuration** — there is no
`tailwind.config.js`. `src/app/globals.css`:

```css
@import "tailwindcss";
@import "tw-animate-css";
@import "shadcn/tailwind.css";
@custom-variant dark (&:is(.dark *));
@theme inline { /* remaps CSS custom properties into Tailwind theme tokens */ }
:root { /* light theme tokens */ }
.dark { /* dark theme tokens */ }
```

- Theme tokens (colors, radius) are plain CSS custom properties, remapped
  into Tailwind's `@theme inline` block so utilities like `bg-primary`,
  `text-muted-foreground`, `rounded-lg` resolve to them.
- Dark mode is purely the `.dark` class variant on an ancestor element —
  **nothing in the app currently adds that class** (no toggle, no
  `next-themes` `ThemeProvider`), so dark styles exist in CSS but are
  presently unreachable in the running app. See
  [`gaps-and-assumptions.md`](../../docs/gaps-and-assumptions.md).
- Primary color: Amazon-style orange (`#FF9900` light / `#FFA41C` dark),
  on a crisp white/light-gray neutral scale in light mode and a dark navy
  scale in dark mode (`src/app/globals.css`). Font: Google "Public Sans"
  via `next/font/google`, exposed as the `--font-public-sans` CSS variable
  and wired to Tailwind's `--font-sans`.
- Utility-first throughout; no CSS Modules, no styled-components, no global
  custom classes beyond the theme tokens above. `cn()`
  (`src/lib/utils.ts`, `clsx` + `tailwind-merge`) is the standard way to
  conditionally combine/override Tailwind classes — used in nearly every
  component that takes a `className` prop or conditional variant.

## Design system

shadcn/ui, configured via `components.json`:

```json
{ "style": "base-nova", "baseColor": "neutral", "iconLibrary": "lucide", "cssVariables": true }
```

- Built on **`@base-ui/react`**, not Radix — the primitive library shadcn is
  more commonly seen paired with. The practical, constantly-recurring
  difference: use the **`render` prop**, not `asChild`, to make a primitive
  render as a different element:

  ```tsx
  <Button render={<Link href="/search" />} size="lg">Browse products</Button>
  ```

  This appears anywhere a `Button`, `SheetTrigger`, or `SheetClose` needs to
  act as a navigational link.
- Variants are authored with `class-variance-authority` (`cva`) — see
  `src/components/ui/button.tsx` for the canonical pattern (variant ×
  size matrix, `defaultVariants`, merged via `cn(buttonVariants({...}))`).
- `src/components/ui/*` is **vendored** (added via `npx shadcn add ...` per
  `components.json`) — treat these as generated/managed, prefer composing
  new components on top rather than hand-editing the primitives.
- Icons: `lucide-react` exclusively, sized with Tailwind `size-*` utilities.
- Full component-by-component documentation (props, usage sites, reuse
  potential): [`ui-components.md`](ui-components.md).

## Important architectural decisions

- **Mock backend swappable behind a service boundary.** All data access
  funnels through `src/services/*.service.ts`; the explicit intent (stated
  in `mock-db.ts`) is that a real backend integration only touches those
  files, not call sites. Any new data-dependent feature should follow this
  pattern rather than reading `localStorage` or mock arrays directly from a
  component.
- **Server components for public/SEO-relevant pages, client components for
  anything mutable or session-scoped.** This isn't a stylistic preference —
  it's forced by the server/`localStorage` split described above. When
  porting to a real backend with a real database, this split could in
  principle relax (server components could read live data too), but the
  cart's client-only nature will likely still force checkout/cart pages to
  stay client-rendered.
- **URL-driven filter state on `/search`** rather than client state, so
  filtering works with JS disabled and results are linkable — a pattern to
  preserve if the search page grows more filters.
- **Single-store carts and orders.** Enforced at the Zustand store level
  (`addItem` refuses cross-store additions) rather than at checkout time —
  by the time checkout runs, cross-store conflicts are already impossible.
  A real backend must still validate this server-side (see
  [`api-contracts.md`](../../docs/api-contracts.md)) since a malicious client could
  post a mixed-store order directly.
- **No ownership/authorization checks anywhere in the current service
  layer** (product edit/delete, order status update, store settings) —
  invisible today because only one seller/store exists in the mock data,
  but this is a **must-fix** for any real multi-tenant backend. Flagged in
  detail in [`gaps-and-assumptions.md`](../../docs/gaps-and-assumptions.md) and
  [`api-contracts.md`](../../docs/api-contracts.md).
- **No typed API client / no OpenAPI schema** exists yet — services return
  domain types directly. When a real backend lands, consider generating a
  typed client from the API contract to keep the two in sync.
