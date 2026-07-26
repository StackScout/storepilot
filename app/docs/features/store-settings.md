# Feature: Store Settings

> Index: [`feature-index.md`](../feature-index.md) · Architecture:
> [`frontend-architecture.md`](../frontend-architecture.md) · API:
> [`api-contracts.md`](../../../docs/api-contracts.md)

## Purpose

Let a seller manage their store's contact info, payout bank account, and
which payment methods (COD / online / bank transfer) they accept.

## Business rules

- Editable fields: `contactEmail`, `contactPhone`, `bankName`,
  `bankAccountName`, `bankAccountNumber`, `codEnabled`,
  `onlinePaymentEnabled`, `bankTransferEnabled`.
- Unlike `codEnabled`/`onlinePaymentEnabled` (default `true`),
  `bankTransferEnabled` **defaults `false`** — it's the only toggle here
  that exposes `bankName`/`bankAccountName`/`bankAccountNumber` to buyers
  (at checkout and on the order page), reusing the same fields already
  collected above for payouts, so a seller has to consciously opt in
  rather than have it switch on silently just because those fields were
  already filled in.
- **`transactionFeePercent` is part of the `StoreSettings` type and is
  displayed** (read-only, on the [Payouts](payouts.md) page) **but has no
  input field anywhere in this settings form** — a seller cannot view or
  change their own fee rate from the UI at all. This now **does** matter:
  `orders.service.ts#createOrder` reads this exact field to compute
  `platformFeeLkr` (falling back to the global `PLATFORM_FEE_PERCENT` only
  when a store has no settings row), so the rate is real but currently
  fixed at whatever default `updateStoreSettings`'s upsert assigns — there
  is still no way to negotiate/edit a per-store rate.
- Toggling `codEnabled`/`onlinePaymentEnabled`/`bankTransferEnabled` here
  **does gate checkout**: `checkout-form.tsx` fetches the cart's store
  settings and only renders the `"cod"`/`"payhere"`/`"bank-transfer"` radio
  options that are enabled. If the buyer's currently-selected method
  becomes unavailable (e.g. a stale default), it's corrected to whichever
  method the store does offer. A store cannot save all three flags as
  `false` — enforced both client-side (zod `.refine` on this form) and
  server-side (`StoreService.upsertSettings` throws 409), so checkout can
  never end up with zero valid payment options for a store.
- Form is seeded from the fetched `StoreSettings` via `reset()` inside a
  `useEffect` once the query resolves (not via `defaultValues`, since the
  data isn't available synchronously at mount).

## User stories

- As a seller, I want to update my contact email/phone.
- As a seller, I want to set/update my payout bank account details.
- As a seller, I want to control whether I accept Cash on Delivery and/or
  online payment. *(Not actually enforced anywhere yet — see Business
  rules.)*

## Pages

| Path | Component | Type | Notes |
|---|---|---|---|
| `/dashboard/settings` | `src/app/dashboard/settings/page.tsx` | Client | Single form, no sub-pages/tabs |

## Components

No dedicated components — inline form using shared `Card`, `Input`,
`Label`, `Checkbox`, `Button` primitives.

## Hooks

`useQuery` (`["store-settings", storeId]`, `storeId` from
`useSellerStoreId()`), `useMutation`
(`storesService.updateStoreSettings`), `useQueryClient` (invalidates
`["store-settings"]` on success), `useForm` + `useEffect` (to `reset()`
once data loads).

## Context providers

Root `QueryClientProvider` only.

## State management

React Query for the fetched settings; react-hook-form for the edited
values in between fetch and save. No optimistic update — the form simply
shows a success toast after the mutation resolves and `invalidateQueries`
triggers a background refetch.

## Forms

react-hook-form + `zodResolver`.

## Validation

```ts
const settingsSchema = z.object({
  contactEmail: z.string().email("Enter a valid email"),
  contactPhone: z.string().min(9, "Enter a valid phone number"),
  bankName: z.string().min(2, "Enter a bank name"),
  bankAccountName: z.string().min(2, "Enter the account holder name"),
  bankAccountNumber: z.string().min(4, "Enter the account number"),
  codEnabled: z.boolean(),
  onlinePaymentEnabled: z.boolean(),
  bankTransferEnabled: z.boolean(),
}).refine((data) => data.codEnabled || data.onlinePaymentEnabled || data.bankTransferEnabled, {
  message: "Enable at least one payment method so buyers can check out",
  path: ["bankTransferEnabled"],
});
```

`bankAccountNumber` is validated only as a string of length ≥ 4 — no
digit-only or bank-specific format check. The `.refine` above blocks saving
all three of `codEnabled`/`onlinePaymentEnabled`/`bankTransferEnabled` as
`false`; the backend (`StoreService.upsertSettings`) enforces the same rule
independently (409 `CONFLICT`), so this can't be bypassed by calling the
API directly.

## Navigation flow

```
/dashboard (sidebar, "Store settings") ──► /dashboard/settings ──(Save settings)──► same page, toast confirms
```

No further navigation — terminal settings page.

## Expected backend APIs

- `GET /api/stores/:storeId/settings`
- `PATCH /api/stores/:storeId/settings`

See [`api-contracts.md`](../../../docs/api-contracts.md) for full shapes.

### Request model

```ts
// PATCH body — Partial<StoreSettings>, but the form always submits all
// editable fields together (not a true partial-field patch from the UI)
{
  contactEmail: string; contactPhone: string; bankName: string;
  bankAccountName: string; bankAccountNumber: string;
  codEnabled: boolean; onlinePaymentEnabled: boolean; bankTransferEnabled: boolean;
}
```

### Response model

```ts
StoreSettings // full updated object
```

## Error handling

Generic `onError` toast ("Couldn't save settings. Please try again.") — no
field-level server error mapping.

## Permissions

Requires seller session (dashboard-wide gate). The page now reads its
`storeId` via `useSellerStoreId()` (not a hardcoded constant), so it
correctly scopes to whichever store the signed-in seller actually owns —
but **no ownership check exists server-side**: `getStoreSettings`/
`updateStoreSettings(storeId, ...)` still accept a bare `storeId` with
nothing verifying it against the session. Must be enforced for a real
backend — see [`features/seller-auth.md`](seller-auth.md#permissions).

## Edge cases

- If `getStoreSettings` returns `null` (true for 7 of 8 **seed** stores,
  which predate `StoreSettings` — see
  [`features/payouts.md`](payouts.md#edge-cases); every store created via
  `/onboarding` gets a row automatically), the form still renders with its
  hardcoded `defaultValues` (empty strings, both toggles `true`).
  **Resolved**: `updateStoreSettings` is now an **upsert** — submitting in
  that state creates a full default-filled `StoreSettings` row instead of
  throwing, so this edge case no longer breaks the form.

## Future improvements

- Expose `transactionFeePercent` for editing (or explicitly decide it's
  platform-controlled and remove it from the seller-facing type/response).
- Bank account number format/masking (currently plain text, no partial
  masking on display either, unlike typical payout-account UX).

## Technical notes

- This page is a clean example of "fetch → `reset()` into a
  react-hook-form" for pre-filling an editable form from async data —
  reusable pattern if more seller-editable-profile pages are added.

## Dependencies

`react-hook-form`, `@hookform/resolvers/zod`, `zod`, `@tanstack/react-query`,
`sonner`, `@/services` (`storesService`), `@/hooks/use-seller-store`.

## TODOs discovered

- No explicit `// TODO` comments.
