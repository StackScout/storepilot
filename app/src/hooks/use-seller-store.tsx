"use client";

import { createContext, useContext } from "react";
import { useQuery } from "@tanstack/react-query";
import { Loader2, Store as StoreIcon } from "lucide-react";
import Link from "next/link";
import { storesService } from "@/services";
import { EmptyState } from "@/components/shared/empty-state";
import { Button } from "@/components/ui/button";

/**
 * Resolves the signed-in seller's storeId itself (GET /api/me/store) rather
 * than receiving it as a prop from a server-rendered session — there is no
 * server-decodable session anymore, only a JWT cookie the backend
 * validates. Every dashboard page below still calls useSellerStoreId() the
 * same way as before; this component just owns the loading/not-yet-a-seller
 * states so none of those call sites need to change.
 */
const SellerStoreContext = createContext<string | null>(null);

export function SellerStoreProvider({ children }: { children: React.ReactNode }) {
  const { data: store, isLoading } = useQuery({
    queryKey: ["seller-store", "me"],
    queryFn: () => storesService.getMyStore(),
    staleTime: 30_000,
  });

  if (isLoading) {
    return (
      <div className="flex justify-center py-24">
        <Loader2 className="text-muted-foreground size-6 animate-spin" />
      </div>
    );
  }

  if (!store) {
    // Shouldn't normally happen — the seller Cognito group is only ever
    // granted alongside creating a Store row (see onboarding) — but avoid
    // crashing if it somehow does.
    return (
      <div className="mx-auto max-w-2xl px-4 py-16 sm:px-6 lg:px-8">
        <EmptyState
          icon={StoreIcon}
          title="No store found for your account"
          description="Finish onboarding to create your store before accessing the dashboard."
          action={<Button render={<Link href="/onboarding" />}>Start onboarding</Button>}
        />
      </div>
    );
  }

  return <SellerStoreContext.Provider value={store.id}>{children}</SellerStoreContext.Provider>;
}

export function useSellerStoreId(): string {
  const storeId = useContext(SellerStoreContext);
  if (!storeId) {
    throw new Error("useSellerStoreId() must be used within the dashboard's SellerStoreProvider");
  }
  return storeId;
}
