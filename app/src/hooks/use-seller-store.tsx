"use client";

import { createContext, useContext } from "react";

/**
 * Carries the signed-in seller's storeId from the (server-side) session down
 * to client dashboard pages, which previously all hardcoded
 * `CURRENT_SELLER_STORE_ID` — that broke as soon as onboarding could create
 * a second, real store. Populated once in `src/app/dashboard/layout.tsx`.
 */
const SellerStoreContext = createContext<string | null>(null);

export function SellerStoreProvider({
  storeId,
  children,
}: {
  storeId: string;
  children: React.ReactNode;
}) {
  return <SellerStoreContext.Provider value={storeId}>{children}</SellerStoreContext.Provider>;
}

export function useSellerStoreId(): string {
  const storeId = useContext(SellerStoreContext);
  if (!storeId) {
    throw new Error("useSellerStoreId() must be used within the dashboard's SellerStoreProvider");
  }
  return storeId;
}
