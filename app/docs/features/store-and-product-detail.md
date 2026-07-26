# Feature: Store & Product Detail

> Index: [`feature-index.md`](../feature-index.md) · Architecture:
> [`frontend-architecture.md`](../frontend-architecture.md) · API:
> [`api-contracts.md`](../../../docs/api-contracts.md)

## Purpose

Give a store its own public storefront page (branding, rating, product
grid, WhatsApp contact) and give each product its own detail page (images,
price, stock, description, related products) that buyers land on from
search, category browsing, or a store page.

## Business rules

- A store page 404s (`notFound()`) if the slug doesn't resolve to a store.
- A product page 404s if the store+product slug combination doesn't
  resolve. Products are looked up by **`(storeSlug, productSlug)`** together,
  not by a globally-unique product slug — two different stores could have
  products with the same `slug` value without colliding.
- "Related products" on a product page = up to 4 other products from the
  **same store**, excluding the current product. There is no
  cross-store "similar products" recommendation.
- Out-of-stock products still render (not hidden), with an "Out of stock"
  badge overlay and a disabled add-to-cart button; low stock (`≤ 5`) shows
  an amber "Only N left" badge instead.
- WhatsApp contact links are built client-side from `store.whatsappNumber`
  by stripping non-digits: `https://wa.me/<digits>` — no validation that
  the stored number is a valid/reachable WhatsApp number.

## User stories

- As a buyer, I want to see a store's branding, rating, location, and full
  product catalog so I can decide whether to trust and buy from it.
- As a buyer, I want to message a store directly on WhatsApp before buying.
- As a buyer, I want to view full product details (images, price, stock,
  description, SKU, category) before adding to cart.
- As a buyer, I want to see other products from the same store while
  viewing one product.

## Pages

| Path | Component | Type | Notes |
|---|---|---|---|
| `/stores/[slug]` | `src/app/(marketplace)/stores/[slug]/page.tsx` | Server | `generateMetadata` sets page title from store name; 404s via `notFound()` |
| `/stores/[slug]/products/[productSlug]` | `src/app/(marketplace)/stores/[slug]/products/[productSlug]/page.tsx` | Server | Same `generateMetadata`/`notFound()` pattern |

## Components

- `StoreProductGrid` (`components/marketplace/store-product-grid.tsx`) —
  **client** component embedded in the otherwise-server store page; seeds
  from server-rendered `initialProducts`, then a `useQuery` with
  `staleTime: 0` refetches so the seller's own just-made edits appear
  immediately on their own storefront view. See
  [`frontend-architecture.md`](../frontend-architecture.md) for why this
  split exists.
- `AddToCartControls` (`components/marketplace/add-to-cart-controls.tsx`) —
  quantity stepper + add-to-cart button + the cross-store-conflict dialog;
  documented in depth in [`features/cart.md`](cart.md).
- `RatingStars`, `PriceDisplay` (shared) — see
  [`ui-components.md`](../ui-components.md).
- `ProductCard` (marketplace) — used for the related-products grid.

## Hooks

`AddToCartControls` uses `useCart()` (see [`features/cart.md`](cart.md)).
The store/product pages themselves use no hooks (Server Components).

## Context providers

None specific; `StoreProductGrid` relies on the root `QueryClientProvider`.

## State management

Server Component pages: none. `StoreProductGrid`: TanStack Query only, no
local component state. `AddToCartControls`: local `useState` for quantity
and the conflict-dialog open flag, plus the Zustand cart store via
`useCart()`.

## Forms

None on these pages (add-to-cart is a stepper + button, not a form).

## Validation

None beyond the 404 checks above. Quantity is clamped to
`[1, stockQuantity]` by `QuantityStepper`'s `min`/`max` props, not by any
schema.

## Navigation flow

```
/search or / ──► /stores/[slug]  ──(product card)──►  /stores/[slug]/products/[productSlug]
/stores/[slug]/products/[productSlug] ──(breadcrumb "Home")──► /
/stores/[slug]/products/[productSlug] ──(breadcrumb store name)──► /stores/[slug]
/stores/[slug]/products/[productSlug] ──(related product card)──► another product page
/stores/[slug] or product page ──(Message on WhatsApp)──► external wa.me link (new tab)
```

## Expected backend APIs

- `GET /api/stores/:slug`
- `GET /api/stores/:storeId/products` (used server-side for the initial
  grid render and client-side by `StoreProductGrid`'s refetch)
- `GET /api/stores/:storeSlug/products/:productSlug`

See [`api-contracts.md`](../../../docs/api-contracts.md) for full shapes.

### Request models

```ts
// path params only, no body
{ slug: string } // store
{ storeSlug: string; productSlug: string } // product
```

### Response models

```ts
Store | null       // 404 if null, per current frontend behavior
Product[]          // store's product list
Product | null     // 404 if null
```

## Error handling

`notFound()` → Next.js renders the nearest `not-found.tsx` (none currently
defined — falls back to Next.js's default 404 UI). No handling exists for a
backend/network error distinct from "not found" — a thrown exception from
the service call would currently surface as an unstyled Next.js error page.
A real integration should add `not-found.tsx` and `error.tsx` for these
route segments.

## Permissions

Fully public. No draft/private product visibility rule is enforced on these
pages — **note**: `Product.status` includes `"draft"`, but
`listProductsByStore` (used by both the storefront grid and the dashboard
product list) does not filter out drafts. This means a seller's draft
products are currently visible to the public on the storefront — see
[`gaps-and-assumptions.md`](../../../docs/gaps-and-assumptions.md).

## Edge cases

- A product URL with a valid `storeSlug` but a `productSlug` belonging to a
  *different* store 404s correctly (lookup requires both to match).
- A store with zero products renders the grid's own empty state ("This
  store hasn't listed any products").
- `compareAtPriceLkr` renders a strikethrough only when it's greater than
  `priceLkr`; a `compareAtPriceLkr` lower than or equal to `priceLkr` is
  silently ignored by `PriceDisplay` (treated as "no discount").

## Future improvements

- Product image galleries (the type supports `images: ProductImage[]` but
  only the first image is ever rendered anywhere in the UI).
- Hide `draft` products from public storefront/product queries.
- Cross-store "you might also like" recommendations.
- Store-level reviews/Q&A.

## Technical notes

- The store/product detail pages are the primary example of the
  server-render-then-client-reconcile pattern described in
  [`frontend-architecture.md`](../frontend-architecture.md) — worth reading
  before changing either page's data-fetching approach.

## Dependencies

`next/image`, `next/link`, `next/navigation` (`notFound`), `lucide-react`,
`@/services`, `@/mock/categories` (`getCategoryLabel`).

## TODOs discovered

- No explicit `// TODO` comments in these files. The draft-visibility gap
  above is inferred, not code-commented — flagged here and in
  [`gaps-and-assumptions.md`](../../../docs/gaps-and-assumptions.md) as a business
  rule that needs an explicit decision before backend implementation.
