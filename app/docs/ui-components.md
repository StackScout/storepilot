# UI Components

> Cross-references: [`frontend-architecture.md`](frontend-architecture.md) ·
> [`feature-index.md`](feature-index.md)

Every reusable component in `src/components/`, grouped by directory.
`src/components/ui/*` (shadcn primitives) are vendored — documented as a
table (they're generic, not app-specific) rather than per-component prose;
everything else gets full detail.

## `components/shared/` — cross-cutting, used by both marketplace and dashboard

### `EmptyState`
- **File**: `shared/empty-state.tsx`
- **Purpose**: consistent "nothing here" placeholder (icon + title +
  optional description + optional action).
- **Props**: `icon: LucideIcon` (required); `title: string` (required);
  `description?: string`; `action?: React.ReactNode`; `className?: string`.
- **Dependencies**: `lucide-react` (icon type only), `cn()`.
- **Used in**: cart page/drawer (empty cart), search page (no
  results), order tracking (not found), dashboard products/orders/payouts
  (no data yet), store product grid (no products).
- **Future reuse**: any new list/collection view needing a zero-state.

### `ProductCardSkeleton` / `ProductGridSkeleton` / `StoreCardSkeleton` / `TableRowSkeleton`
- **File**: `shared/loading-skeletons.tsx`
- **Purpose**: loading placeholders matching the shape of their
  corresponding real components.
- **Props**: `ProductGridSkeleton({ count = 8 })`; `TableRowSkeleton({
  columns = 4 })`; the other two take no props.
- **Dependencies**: `components/ui/skeleton`.
- **Used in**: dashboard overview/products/orders/payouts tables
  (`TableRowSkeleton`). **Note**: `ProductCardSkeleton`,
  `ProductGridSkeleton`, and `StoreCardSkeleton` are exported but **not
  currently used anywhere** in the app (no Server Component page has a
  loading skeleton for product/store grids since those pages block on
  `await` rather than streaming) — dead code today, kept presumably for
  future `loading.tsx`/Suspense adoption.
- **Future reuse**: wiring up Next.js `loading.tsx` files for the
  marketplace routes would be the natural use for the currently-unused
  skeletons.

### `Logo`
- **File**: `shared/logo.tsx`
- **Purpose**: brand mark + site name, as a link.
- **Props**: `href?: string` (default `"/"`), `className?: string`.
- **Dependencies**: `next/link`, `lucide-react` (`Store` icon), `cn()`,
  `SITE_NAME` constant.
- **Used in**: site header, mobile nav, login/onboarding layouts, dashboard
  sidebar (with `href="/dashboard"`).

### `OrderStatusBadge`
- **File**: `shared/order-status-badge.tsx`
- **Purpose**: color-coded status pill for an `OrderStatus`.
- **Props**: `status: OrderStatus` (required), `className?: string`.
- **Dependencies**: `components/ui/badge`, `cn()`, `ORDER_STATUS_LABELS`
  constant. Color map (`STATUS_CLASSES`) is defined **only here** — the
  single source of truth for order-status colors.
- **Used in**: order confirmation page, dashboard overview/orders
  list/order detail, payouts table.
- **Future reuse**: any future order-adjacent surface (e.g. an admin
  back-office) should reuse this rather than reinventing status colors.

### `PriceDisplay`
- **File**: `shared/price-display.tsx`
- **Purpose**: consistent LKR price rendering, with optional
  strikethrough "compare-at" price.
- **Props**: `priceLkr: number` (required); `compareAtPriceLkr?: number`;
  `size?: "sm" | "md" | "lg"` (default `"md"`); `className?: string`.
- **Dependencies**: `formatLkr()` (`@/lib/currency`), `cn()`.
- **Used in**: product card, product detail, cart page/drawer, checkout
  summary, order confirmation/detail, dashboard products/orders tables.
- **Note**: discount styling only triggers when `compareAtPriceLkr >
  priceLkr` — an equal or lower "compare-at" is silently treated as "no
  discount."

### `RatingStars`
- **File**: `shared/rating-stars.tsx`
- **Purpose**: 5-star rating display with numeric rating and optional
  review count.
- **Props**: `rating: number` (required); `reviewCount?: number`; `size?:
  number` (default `14`, pixels); `className?: string`.
- **Dependencies**: `lucide-react` (`Star`), `cn()`.
- **Used in**: store card, store page header, product detail page.
- **Note**: display-only, rounds `rating` to the nearest whole star for
  fill state — there is no interactive/submit-a-rating variant anywhere
  (no review-submission feature exists — see
  [`database-model.md`](../../docs/database-model.md#review-missing-entity)).

---

## `components/marketplace/` — buyer-facing

### `SiteHeader` / `SiteFooter`
- **Files**: `marketplace/site-header.tsx`, `marketplace/site-footer.tsx`
- **Purpose**: global page chrome for the `(marketplace)` route group.
- **Props**: none (no configuration — fully static composition).
- **Dependencies**: `Logo`, `SearchBar`, `CartDrawer`, `MobileNav`,
  `useBuyerAccountLink` (`@/hooks/use-buyer-account-link`, header only, is
  why `SiteHeader` is now a **client** component — see
  [`features/buyer-accounts.md`](features/buyer-accounts.md#technical-notes)
  for why this is a client-side fetch rather than a server-read prop);
  `next/link`, `SITE_TAGLINE` (footer only).
- **Used in**: `(marketplace)/layout.tsx` only.

### `MobileNav`
- **File**: `marketplace/mobile-nav.tsx`
- **Purpose**: hamburger menu (Sheet) for small screens — home, browse
  products, track order, sell-on-IslandCart, seller dashboard, and account
  (sign in / "Hi, `<first name>`") links.
- **Props**: none (reads `useBuyerAccountLink()` internally, same as
  `SiteHeader` — both call the hook independently and React Query dedupes
  the underlying fetch).
- **State**: local `open` (`useState`).
- **Dependencies**: `components/ui/sheet`, `Logo`, `useBuyerAccountLink`.
- **Used in**: `SiteHeader` only.

### `SearchBar`
- **File**: `marketplace/search-bar.tsx`
- **Purpose**: keyword search input, native GET form to `/search`.
- **Props**: `defaultValue?: string` (default `""`), `className?: string`.
- **Dependencies**: `components/ui/input`, `lucide-react` (`Search`),
  `cn()`.
- **Used in**: `SiteHeader` (desktop + mobile variants), `/search` page
  (mobile-only variant).

### `CartDrawer`
- **File**: `marketplace/cart-drawer.tsx`
- **Purpose**: header cart icon + badge, opens a slide-out mini-cart.
- **Props**: none (reads cart state via `useCart()` internally).
- **State**: local `open` (`useState`), plus the shared Zustand cart store.
- **Dependencies**: `components/ui/sheet`, `QuantityStepper`,
  `PriceDisplay`, `EmptyState`, `useCart`, `formatLkr`.
- **Used in**: `SiteHeader` only.
- Full behavior: [`features/cart.md`](features/cart.md).

### `CategoryFilter`
- **File**: `marketplace/category-filter.tsx`
- **Purpose**: horizontal pill row linking to `?category=<value>`.
- **Props**: `activeCategory?: StoreCategory`; `query?: string`
  (preserved in generated hrefs); `basePath?: string` (default
  `"/search"`).
- **Dependencies**: `CATEGORIES` (`@/mock/categories`), `cn()`.
- **Used in**: home page (`basePath` default), `/search` page.
- **Future reuse**: the `basePath` prop already supports reuse on any
  future category-filterable listing page.

### `ProductCard`
- **File**: `marketplace/product-card.tsx`
- **Purpose**: grid tile for a product — image, out-of-stock/low-stock/sale
  badges, store name, product name, price.
- **Props**: `product: Product` (required); `priority?: boolean` (default
  `false`, passed to `next/image` for above-the-fold LCP images).
- **Dependencies**: `next/image`, `next/link`, `PriceDisplay`,
  `components/ui/badge`.
- **Used in**: home page, search page, store page (via `StoreProductGrid`),
  product detail page (related products).

### `StoreCard`
- **File**: `marketplace/store-card.tsx`
- **Purpose**: grid tile for a store — banner, logo, name, verified badge,
  tagline, rating, city, category + product count.
- **Props**: `store: Store` (required).
- **Dependencies**: `next/image`, `next/link`, `RatingStars`,
  `getCategoryLabel` (`@/mock/categories`).
- **Used in**: home page, search page.

### `StoreProductGrid`
- **File**: `marketplace/store-product-grid.tsx`
- **Purpose**: renders a store's products; the server→client reconciliation
  point described in [`frontend-architecture.md`](frontend-architecture.md).
- **Props**: `storeId: string` (required); `initialProducts: Product[]`
  (required — used as React Query's `initialData`).
- **Dependencies**: `@tanstack/react-query`, `ProductCard`, `EmptyState`,
  `@/services` (`productsService`).
- **Used in**: store page only.
- **Note**: **not currently reused** on the product-detail page's "related
  products" section (that section renders a plain grid of `ProductCard`
  directly, with no client refetch) — worth noting if the two are ever
  expected to behave identically.

### `AddToCartControls`
- **File**: `marketplace/add-to-cart-controls.tsx`
- **Purpose**: quantity stepper + add-to-cart button + cross-store conflict
  dialog, for the product detail page.
- **Props**: `product: Product` (required).
- **State**: `quantity` (`useState`, default `1`), `conflictOpen`
  (`useState`).
- **Dependencies**: `components/ui/dialog`, `QuantityStepper`, `useCart`,
  `sonner`, `next/navigation` (`useRouter`, for `router.refresh()` after
  replacing the cart).
- **Used in**: product detail page only.
- Full behavior: [`features/cart.md`](features/cart.md).

### `QuantityStepper`
- **File**: `marketplace/quantity-stepper.tsx`
- **Purpose**: generic bounded +/- quantity control.
- **Props**: `quantity: number` (required); `onChange: (q: number) => void`
  (required); `max?: number` (default `99`); `min?: number` (default `1`);
  `size?: "sm" | "default"` (default `"default"`).
- **Dependencies**: `components/ui/button`, `lucide-react` (`Minus`,
  `Plus`).
- **Used in**: cart page, cart drawer, add-to-cart controls.
- **Future reuse**: fully generic — reusable for any future numeric
  stepper need (e.g. a seller-side "quick stock adjust" control).

---

## `components/dashboard/` — seller-facing

### `DashboardSidebarContent`
- **File**: `dashboard/dashboard-sidebar.tsx`
- **Purpose**: nav links (Overview/Products/Orders/Payouts/Settings),
  active-route highlighting, store identity card, "view storefront" link,
  sign-out form. Rendered both directly (desktop sidebar) and inside the
  mobile nav's `Sheet`.
- **Props**: `sellerEmail?: string`.
- **Dependencies**: `next/navigation` (`usePathname`), `Logo`,
  `components/ui/button`, `useSellerStoreId` (`@/hooks/use-seller-store`) +
  `@tanstack/react-query` (fetches the seller's actual store — no longer a
  static `MOCK_STORES` lookup, since a seller's store may only exist in
  `localStorage`), `signOutSeller` (`@/lib/actions/auth`).
- **Business rule**: the "View storefront" link is only shown when the
  fetched store's `verificationStatus === "active"`; otherwise shows
  "Storefront hidden until approved" (no link).
- **Used in**: `dashboard/layout.tsx` (desktop), `DashboardMobileNav`.

### `PendingVerificationBanner`
- **File**: `dashboard/pending-verification-banner.tsx`
- **Purpose**: surfaces a store's `verificationStatus` at the top of every
  dashboard page — renders nothing when `"active"`, an amber "under
  review" card when `"pending"`, a red card with the rejection reason when
  `"rejected"`.
- **Props**: none (reads `useSellerStoreId()` internally).
- **Dependencies**: `useSellerStoreId` (`@/hooks/use-seller-store`),
  `@tanstack/react-query` (`staleTime: 0`, so a just-approved/rejected
  store is reflected without a hard reload), `@/services`
  (`storesService.getStoreById`).
- **Used in**: `dashboard/layout.tsx` only (rendered above `{children}`,
  so it appears on every dashboard route).

### `DashboardMobileNav`
- **File**: `dashboard/dashboard-mobile-nav.tsx`
- **Purpose**: hamburger trigger + `Sheet` wrapping
  `DashboardSidebarContent` for small screens; auto-closes on route change.
- **Props**: `sellerEmail?: string`.
- **State**: `open`, `lastPathname` (both `useState`) — closes the sheet by
  comparing `pathname` to `lastPathname` **during render** (not in an
  effect), per an explicit in-source comment explaining this avoids an
  extra commit/flash.
- **Dependencies**: `components/ui/sheet`, `DashboardSidebarContent`.
- **Used in**: `dashboard/layout.tsx` only.

### `StatCard`
- **File**: `dashboard/stat-card.tsx`
- **Purpose**: labeled metric tile with an icon, optional trend text.
- **Props**: `label: string`; `value: string`; `icon: LucideIcon`;
  `trend?: string`; `trendDirection?: "up" | "down"`.
- **Dependencies**: `components/ui/card`, `cn()`.
- **Used in**: dashboard overview, payouts page.
- **Note**: `trend`/`trendDirection` are supported but **never passed**
  anywhere today — no period-over-period comparison exists yet (see
  [`roadmap.md`](../../docs/roadmap.md)).

### `ImageUploader`
- **File**: `dashboard/image-uploader.tsx`
- **Purpose**: product-image "upload" — actually a URL-paste field with
  preview and a "use a sample image" shortcut. Explicitly documented in
  source as an MVP placeholder for a real upload flow.
- **Props**: `value: string`; `onChange: (url: string) => void`; `error?:
  string`.
- **Dependencies**: `next/image`, `components/ui/input`, `button`, `label`,
  `lucide-react` (`ImageOff`, `Shuffle`).
- **Used in**: `ProductForm` only.
- **Future reuse**: the `value`/`onChange`/`error` contract is intended to
  stay stable when a real upload endpoint replaces the internals — see
  [`features/product-management.md`](features/product-management.md#technical-notes).

### `ProductForm`
- **File**: `dashboard/product-form.tsx`
- **Purpose**: shared create/edit product form.
- **Props**: `initialProduct?: Product`; `onSubmit: (input:
  ProductFormInput) => void`; `isSubmitting: boolean`; `submitLabel?:
  string` (default `"Save product"`).
- **Dependencies**: `react-hook-form`, `@hookform/resolvers/zod`, `zod`,
  `ImageUploader`, `CATEGORIES` (`@/mock/categories`).
- **Used in**: new-product page, edit-product page.

### `OrderStatusSelect`
- **File**: `dashboard/order-status-select.tsx`
- **Purpose**: status-transition dropdown enforcing the allowed-next-status
  state machine (see
  [`features/order-management.md`](features/order-management.md)).
- **Props**: `order: Order` (required).
- **Dependencies**: `@tanstack/react-query` (`useMutation`,
  `useQueryClient`), `sonner`, `components/ui/select`, `@/services`
  (`ordersService`), `ORDER_STATUS_LABELS`.
- **Used in**: dashboard order-detail page only.
- **Note**: this file is the **single source of truth** for the status
  state machine on the frontend — the service layer doesn't independently
  enforce it (see [`api-contracts.md`](../../docs/api-contracts.md)).

---

## `components/ui/` — shadcn/ui primitives (vendored)

Managed via `components.json` (`npx shadcn add ...`), built on
`@base-ui/react` (not Radix — use the `render` prop, not `asChild`, to
polymorphically render as another element). Treat as generated; prefer
composing new components on top rather than editing these directly.

| Component | File | Underlying primitive |
|---|---|---|
| `Avatar` | `avatar.tsx` | `@base-ui/react/avatar` |
| `Badge` | `badge.tsx` | plain `span` + `cva` variants |
| `Breadcrumb` | `breadcrumb.tsx` | composed `nav`/`ol`/`li` |
| `Button`, `buttonVariants` | `button.tsx` | `@base-ui/react/button` + `cva` |
| `Card`, `CardContent`, etc. | `card.tsx` | plain `div` composition |
| `Checkbox` | `checkbox.tsx` | `@base-ui/react/checkbox` |
| `Dialog` family | `dialog.tsx` | `@base-ui/react/dialog` |
| `DropdownMenu` family | `dropdown-menu.tsx` | `@base-ui/react/menu` |
| `Input` | `input.tsx` | plain `input` + styling |
| `Label` | `label.tsx` | `@base-ui/react/field` (or plain `label`) |
| `RadioGroup`, `RadioGroupItem` | `radio-group.tsx` | `@base-ui/react/radio` |
| `Select` family | `select.tsx` | `@base-ui/react/select` |
| `Separator` | `separator.tsx` | `@base-ui/react/separator` |
| `Sheet` family | `sheet.tsx` | `@base-ui/react/dialog` (side-sliding variant) |
| `Skeleton` | `skeleton.tsx` | plain `div` + pulse animation |
| `Toaster` | `sonner.tsx` | wraps `sonner`'s `<Toaster />` |
| `Table` family | `table.tsx` | plain `table` composition |
| `Tabs` family | `tabs.tsx` | `@base-ui/react/tabs` |
| `Textarea` | `textarea.tsx` | plain `textarea` + styling |

Not individually documented here (generic, self-explanatory, no app-specific
props beyond what shadcn/`@base-ui/react` already document upstream).
