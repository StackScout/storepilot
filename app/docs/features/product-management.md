# Feature: Product Management (Seller)

> Index: [`feature-index.md`](../feature-index.md) · Architecture:
> [`frontend-architecture.md`](../frontend-architecture.md) · API:
> [`api-contracts.md`](../../../docs/api-contracts.md)

## Purpose

Let a seller view, create, edit, and delete their store's product catalog.

## Business rules

- `status` is one of `"active" | "draft" | "out-of-stock"`, but a product
  is **automatically forced to `"out-of-stock"`** whenever
  `stockQuantity === 0`, regardless of what status was selected/submitted
  — enforced in both `createProduct` and `updateProduct`
  (`status: input.stockQuantity === 0 ? "out-of-stock" : input.status`).
  The status `Select` in `ProductForm` only offers `"active"`/`"draft"` as
  user-choosable values — `"out-of-stock"` is never a manual choice, only a
  derived state.
- Product `slug` is auto-generated from the name at creation
  (`name.toLowerCase().replace(...)` + a 4-digit timestamp suffix) and
  **never changes on edit**, even if the name changes — editing a product's
  name does not update its URL slug.
- A new product's `rating`/`reviewCount` always start at `0`; nothing in
  the app ever increases them (no review system exists — see
  [`gaps-and-assumptions.md`](../../../docs/gaps-and-assumptions.md)).
- Only one image is manageable per product (`imageUrl` in the form),
  despite `Product.images` being typed as an array — editing replaces the
  single first image; there's no add/remove/reorder multi-image UI.
- Delete is **permanent and immediate** (no soft-delete, no undo) once
  confirmed in the dialog.
- **No ownership check**: `updateProduct`/`deleteProduct` operate on a
  product `id` with no verification that the product's `storeId` matches
  the signed-in seller's store. Invisible today (one seller, and the UI
  only ever links to the seller's own products) but must be enforced
  server-side for a real multi-tenant backend — see
  [`features/seller-auth.md`](seller-auth.md#permissions).

## User stories

- As a seller, I want to see a list of my products with price, stock, and
  status.
- As a seller, I want to add a new product with an image, description,
  category, price, and stock.
- As a seller, I want to edit an existing product's details.
- As a seller, I want to delete a product I no longer sell, with a
  confirmation step to avoid accidental deletion.

## Pages

| Path | Component | Type | Notes |
|---|---|---|---|
| `/dashboard/products` | `src/app/dashboard/products/page.tsx` | Client | Table + delete confirmation `Dialog` |
| `/dashboard/products/new` | `src/app/dashboard/products/new/page.tsx` | Client | `ProductForm` in create mode |
| `/dashboard/products/[productId]/edit` | `src/app/dashboard/products/[productId]/edit/page.tsx` | Client | `ProductForm` in edit mode, prefilled via `useQuery` |

## Components

- `ProductForm` (`components/dashboard/product-form.tsx`) — shared between
  create/edit, taking `initialProduct?`, `onSubmit`, `isSubmitting`,
  `submitLabel`.
- `ImageUploader` (`components/dashboard/image-uploader.tsx`) — URL-paste
  field with preview and a "use a sample image" shortcut
  (`picsum.photos/seed/product-<timestamp>/700/700`) — explicitly an MVP
  placeholder per its own source comment (see
  [Technical notes](#technical-notes)).

## Hooks

`useQuery` (list on the products page, single product on the edit page),
`useMutation` (create/update/delete), `useQueryClient` (invalidate
`["products"]` — and, on edit, also `["product", productId]` — after
create/update/delete succeed).

## Context providers

Root `QueryClientProvider` only.

## State management

- List page: `productToDelete: Product | null` (`useState`) drives the
  delete-confirmation `Dialog`'s open state and content.
- Form pages: react-hook-form's internal state within `ProductForm`.

## Forms

`ProductForm` — react-hook-form + `zodResolver`.

## Validation

```ts
const productFormSchema = z.object({
  name: z.string().min(3, "Name must be at least 3 characters"),
  description: z.string().min(10, "Add a short description (min 10 characters)"),
  category: z.enum(["fashion","food-beverage","beauty","handicrafts","electronics","home-living","jewelry","grocery"]),
  priceLkr: z.number().positive("Enter a valid price"),
  compareAtPriceLkr: z.union([z.number().positive(), z.nan()]).optional(),
  stockQuantity: z.number().int().min(0, "Stock can't be negative"),
  sku: z.string().min(2, "Enter a SKU"),
  status: z.enum(["active", "draft", "out-of-stock"]),
  imageUrl: z.string().min(1, "Add a product image"),
});
```

Note: `imageUrl` validation is `min(1)` only — **not** a URL format check
(`z.string().url()`), so any non-empty string passes client-side, including
non-URL text. `compareAtPriceLkr` accepts `NaN` (from an empty number
input) as a valid "not set" sentinel, stripped back out to `undefined`
before submission in the page's `submit()` wrapper.

## Navigation flow

```
/dashboard/products ──(New product)──► /dashboard/products/new ──(submit)──► /dashboard/products
/dashboard/products ──(edit icon)────► /dashboard/products/[id]/edit ──(submit)──► /dashboard/products
/dashboard/products ──(delete icon)──► confirmation Dialog ──(Delete)──► stays on page, list refetches
```

## Expected backend APIs

- `GET /api/stores/:storeId/products`
- `GET /api/products/:id`
- `POST /api/stores/:storeId/products`
- `PATCH /api/products/:id`
- `DELETE /api/products/:id`

See [`api-contracts.md`](../../../docs/api-contracts.md) for full shapes.

### Request models

```ts
// ProductFormInput (POST body / PATCH body)
{
  name: string; description: string; category: StoreCategory;
  priceLkr: number; compareAtPriceLkr?: number; stockQuantity: number;
  sku: string; status: "active"|"draft"|"out-of-stock"; imageUrl: string;
}
```

### Response models

```ts
Product // full object incl. generated id/slug/images[]/rating/reviewCount/timestamps
```

## Error handling

Every mutation has a generic `onError` toast ("Couldn't create/update/delete
product. Please try again.") — no field-level server error mapping exists.
A real backend returning e.g. a duplicate-SKU validation error currently has
no path to surface that specific message to the seller.

## Permissions

Requires seller session (dashboard-wide gate). No per-resource ownership
check — see [Business rules](#business-rules) above and
[`features/seller-auth.md`](seller-auth.md#permissions).

## Edge cases

- Deleting a product doesn't check whether it appears in any existing
  `Order` — since `OrderItem` snapshots product name/price/image at
  purchase time (not a live reference), deleting a product does **not**
  break historical order display. This is a good design already in place —
  preserve it in the real backend (don't cascade-delete or foreign-key
  order items to products).
- Setting `stockQuantity` back above 0 for a previously out-of-stock
  product does **not** automatically flip status back to `"active"` unless
  the seller also has `status: "active"` selected in the form at save time
  — but since `status` in the form only ever reflects what's shown
  (defaulting to the product's last saved status, which would already be
  `"out-of-stock"`), a seller restocking must manually change the status
  dropdown too. Worth a UX look, not just a docs note.
- `priceLkr`/`stockQuantity` inputs use `valueAsNumber: true` — an empty
  field becomes `NaN`, which fails the zod `positive()`/`min()` checks with
  a generic message rather than "required".

## Future improvements

- Multi-image upload/gallery, real file storage.
- Slug regeneration or explicit "custom URL slug" field on rename.
- Bulk actions (bulk status change, bulk delete).
- Duplicate-SKU validation (currently unchecked — nothing stops two
  products in the same store from sharing a SKU).
- Auto-flip `out-of-stock` → `active` when stock is replenished (or make
  the current manual behavior an explicit, documented product decision).

## Technical notes

- `ImageUploader`'s own source comment: *"MVP placeholder for a real upload
  flow (S3/Cloudinary + resumable upload). Sellers paste an image URL for
  now; the preview and validation contract are already in place for when a
  file-upload endpoint exists."* — treat the `value`/`onChange`/`error`
  prop contract as stable to build a real uploader against.

## Dependencies

`react-hook-form`, `@hookform/resolvers/zod`, `zod`, `@tanstack/react-query`,
`sonner`, `next/image`, `@/services` (`productsService`, `storesService`),
`@/mock/categories` (`CATEGORIES`), `@/hooks/use-seller-store`.

## TODOs discovered

- `image-uploader.tsx` — explicit in-source note (quoted above) that this
  is a placeholder pending a real upload endpoint. No literal `// TODO`
  string, but functions as one.
