# Feature: Marketplace Browsing & Search

> Index: [`feature-index.md`](../feature-index.md) · Architecture:
> [`frontend-architecture.md`](../frontend-architecture.md) · API:
> [`api-contracts.md`](../../../docs/api-contracts.md)

## Purpose

Let anonymous buyers discover products and stores: a home page with
featured products and popular stores, category shortcuts, and a combined
search/filter/sort experience across both products and stores.

## Business rules

- No account is required to browse.
- Featured products are the 8 highest-rated products platform-wide
  (`getFeaturedProducts`), not curated/sponsored — there is no "featured"
  flag on `Product`.
- Popular stores on the home page are simply the first 6 stores returned by
  `listStores({ limit: 6 })` — there is no ranking by popularity, sales
  volume, or rating; "popular" is aspirational copy, not an actual sort
  (`listStores` has no sort parameter at all).
- Search matches product `name`, `description`, or `storeName`
  (case-insensitive substring); store search matches `name`, `tagline`, or
  `address.city`. This is naive substring matching, not full-text search —
  see [Future improvements](#future-improvements).
- Category is a fixed 8-value enum (`StoreCategory` — fashion,
  food-beverage, beauty, handicrafts, electronics, home-living, jewelry,
  grocery); there is no dynamic/admin-managed category list.
- Sort options for products: `newest` (default), `rating`, `price-asc`,
  `price-desc`. Stores have no sort option in the UI.
- All filter/sort/tab state lives in the URL query string
  (`?q=&category=&sort=&tab=`), not client state — the page is a Server
  Component that re-fetches on every navigation.

## User stories

- As a buyer, I want to see popular products and stores on the home page so
  I can start browsing without searching.
- As a buyer, I want to filter by category so I only see relevant products.
- As a buyer, I want to search by keyword and see matching products and
  stores separately (via tabs).
- As a buyer, I want to sort search results by price or rating.
- As a buyer, I want to share or bookmark a specific search/filter via URL.

## Pages

| Path | Component | Type | Notes |
|---|---|---|---|
| `/` | `src/app/(marketplace)/page.tsx` | Server | `Promise.all([getFeaturedProducts(8), listStores({ limit: 6 })])` |
| `/search` | `src/app/(marketplace)/search/page.tsx` | Server | Reads `searchParams` (`q`, `category`, `sort`, `tab`), calls both `listProducts` and `listStores` in parallel every load |

## Components

- `CategoryFilter` (`components/marketplace/category-filter.tsx`) — pill row
  of category links, builds hrefs preserving `q`, highlights active
  category. Reusable via `basePath` prop (defaults to `/search`).
- `SearchBar` (`components/marketplace/search-bar.tsx`) — plain HTML
  `<form action="/search" method="GET">`, no client JS required to search.
- `ProductCard` / `StoreCard` — grid item renderers (see
  [`ui-components.md`](../ui-components.md)).
- `MobileNav`, `SiteHeader`, `SiteFooter` — page chrome (marketplace layout).
- `EmptyState` — "No products found" / "No stores found" states.

## Hooks

None — both pages are Server Components with no client-side data fetching.

## Context providers

None specific to this feature (inherits root `QueryClientProvider`/`Toaster`,
unused here since there's no client-side query on these pages).

## State management

None client-side. All state is the URL search string, parsed server-side.

## Forms

`SearchBar` is a native GET form (`name="q"`), no react-hook-form/zod — it's
a simple keyword field, not validated.

## Validation

None. `category` and `sort` query params are cast with `as` (unchecked) in
`search/page.tsx` — an invalid `category` value simply matches nothing
(since `Product.category === params.category` never equals a bogus string);
an invalid `sort` falls through to the `default: "newest"` case in
`listProducts`. No error is surfaced to the user for a malformed query
string.

## Navigation flow

```
/  ──(Browse products)──►  /search
/  ──(category chip)────►  /search?category=<cat>
/  ──(product/store card)──► /stores/[slug] or /stores/[slug]/products/[productSlug]
/search ──(tab)──────────►  /search?tab=stores (preserves q, category)
/search ──(sort link)────►  /search?sort=<opt> (preserves q, category, tab)
/search ──(category chip)──► /search?category=<cat> (preserves q)
```

## Expected backend APIs

See [`api-contracts.md`](../../../docs/api-contracts.md) for full request/response
shapes.

- `GET /api/products?category=&query=&sort=&limit=`
- `GET /api/products/featured?limit=`
- `GET /api/stores?category=&query=&limit=`

### Request models

```ts
// GET /api/products
{ category?: StoreCategory; query?: string; sort?: "newest"|"price-asc"|"price-desc"|"rating"; limit?: number }
// GET /api/stores
{ category?: StoreCategory; query?: string; limit?: number }
```

### Response models

```ts
Product[]  // see database-model.md#product
Store[]    // see database-model.md#store
```

## Error handling

Frontend today: none beyond an empty-array result rendering `EmptyState`.
There is no loading state (Server Components block on `await`), no error
boundary, and no distinction between "no results" and "the request failed."
A real API integration should add an `error.tsx` boundary for these routes
and decide what a failed search should render (retry affordance vs. silent
empty state).

## Permissions

Public, unauthenticated. No rate limiting exists or is planned for in the
frontend.

## Edge cases

- Empty query + no category → returns everything, sorted `newest`
  (products) / insertion order (stores).
- Both `q` and `category` set with no matches → distinct empty-state copy
  ("Nothing matched `"..."`." vs. "Try a different category.").
- `limit` on `listStores`/`listProducts` truncates but the frontend never
  requests a "page 2" — there is **no pagination UI anywhere in the
  marketplace**. At real-world catalog sizes this will need to change; see
  [Future improvements](#future-improvements).
- Category pill row scrolls horizontally on mobile (`overflow-x-auto`) —
  no visual indicator that it's scrollable beyond the cut-off last pill.

## Future improvements

- Real search (full-text / trigram / external search service) — substring
  matching over an in-memory array won't scale to a real catalog.
- Pagination or infinite scroll for both products and stores.
- Store sort options (rating, newest, most products).
- A true "featured" mechanism (curated or algorithmic) distinct from "top
  rated."
- Debounced live search-as-you-type (currently full page navigation per
  keystroke-free form submit).

## Technical notes

- Both pages run fully server-side; there is no client JS cost for
  filtering, which is good for SEO and low-JS environments, but means every
  filter change is a full navigation/round-trip once a backend replaces the
  in-memory array (currently free/instant since it's local Node compute
  over a small array).
- `productsService.listProducts` and `storesService.listStores` are called
  independently and in parallel per page load — no combined/aggregated
  backend endpoint exists or is needed given current usage.

## Dependencies

`next/link`, `lucide-react` icons, `@/services` (`productsService`,
`storesService`), `@/mock/categories` (`CATEGORIES`), `@/lib/utils` (`cn`).

## TODOs discovered

- None marked explicitly in code (no `// TODO` comments found in this
  feature's files). Gaps listed above are inferred from missing
  functionality, not code comments.
