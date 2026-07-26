"use client";

import { useQuery } from "@tanstack/react-query";
import { StoreCard } from "@/components/marketplace/store-card";
import { storesService } from "@/services";
import type { Store } from "@/types";

/** Same reconciliation pattern as store-product-grid.tsx / search-results.tsx. */
export function PopularStoresGrid({ initialStores }: { initialStores: Store[] }) {
  const { data: stores } = useQuery({
    queryKey: ["stores", "popular"],
    queryFn: () => storesService.listStores({ limit: 6 }),
    initialData: initialStores,
    staleTime: 0,
  });

  return (
    <div className="grid grid-cols-1 gap-4 sm:grid-cols-2 lg:grid-cols-3">
      {stores.map((store) => (
        <StoreCard key={store.id} store={store} />
      ))}
    </div>
  );
}
