/**
 * Central query-key factory — the shared "query-key/hooks layer" this
 * codebase's own conventions doc used to say didn't exist (see
 * app/CLAUDE.md's "State management conventions" section, updated
 * alongside this file). Before this, every `useQuery`/`useMutation` call
 * site hand-wrote its own array literal, which both duplicated the same
 * key across many files and, in at least one case, gave two genuinely
 * different query shapes the same top-level segment ("orders" used for
 * both a seller's store-scoped order list and a buyer's own order
 * history) — a latent risk for any future `invalidateQueries({queryKey:
 * ["orders"]})` call, since TanStack Query matches by key *prefix*.
 *
 * Every entry below inserts an explicit scope discriminator
 * ("store"/"me"/"admin") right after the entity name specifically to
 * avoid that: `invalidateQueries({ queryKey: queryKeys.orders.byStore(id) })`
 * can never accidentally also match a buyer's own `orders.mine()` cache
 * entry, or vice versa.
 *
 * Not every query in the app has been migrated onto this factory in one
 * pass (that would touch ~60 call sites) — new code and anything already
 * touched for another reason should use it; the rest can migrate
 * opportunistically. Add an entry here before hand-writing a new key
 * literal for an entity that doesn't have one yet.
 */
export const queryKeys = {
  authSession: () => ["auth-session"] as const,

  products: {
    byStore: (storeId: string) => ["products", "store", storeId] as const,
    bySlug: (slug: string) => ["products", "slug", slug] as const,
    byId: (productId: string) => ["products", "id", productId] as const,
    wishlist: () => ["products", "wishlist"] as const,
  },

  bookableServices: {
    byStore: (storeId: string) => ["bookable-services", "store", storeId] as const,
  },

  store: {
    byId: (storeId: string) => ["store", storeId] as const,
    bySlug: (slug: string) => ["store", "slug", slug] as const,
    mine: () => ["store", "mine"] as const,
    settings: (storeId: string) => ["store-settings", storeId] as const,
    publicSettings: (storeId: string) => ["store-public-settings", storeId] as const,
    stats: (storeId: string) => ["store-stats", storeId] as const,
    verificationChangeRequest: (storeId: string) => ["verification-change-request", storeId] as const,
  },

  orders: {
    byStore: (storeId: string, filter?: string, page?: number) => ["orders", "store", storeId, filter, page] as const,
    overviewByStore: (storeId: string) => ["orders", "store", storeId, "overview"] as const,
    mine: () => ["orders", "me"] as const,
    byId: (orderId: string) => ["order", orderId] as const,
  },

  bookings: {
    byStore: (storeId: string, filter?: string, page?: number) => ["bookings", "store", storeId, filter, page] as const,
    overviewByStore: (storeId: string) => ["bookings", "store", storeId, "overview"] as const,
    mine: () => ["bookings", "me"] as const,
    byId: (bookingId: string) => ["booking", bookingId] as const,
  },

  buyer: {
    me: () => ["buyer", "me"] as const,
    addresses: () => ["addresses"] as const,
    savedSearches: () => ["saved-searches"] as const,
  },

  conversations: {
    mine: () => ["conversations", "me"] as const,
    byStore: (storeId: string) => ["conversations", "store", storeId] as const,
  },

  seller: {
    plan: () => ["seller-plan"] as const,
  },

  categories: {
    all: () => ["categories"] as const,
    admin: () => ["categories", "admin"] as const,
  },

  admin: {
    pendingStoresCount: () => ["admin", "pending-stores-count"] as const,
    accountingSummary: () => ["admin", "accounting-summary"] as const,
    auditLog: (page: number, size: number) => ["admin", "audit-log", { page, size }] as const,
  },
} as const;
