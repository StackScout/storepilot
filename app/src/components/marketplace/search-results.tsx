"use client";

import Link from "next/link";
import { useQuery } from "@tanstack/react-query";
import { PackageSearch, Store as StoreIcon } from "lucide-react";
import { ProductCard } from "@/components/marketplace/product-card";
import { StoreCard } from "@/components/marketplace/store-card";
import { EmptyState } from "@/components/shared/empty-state";
import { cn } from "@/lib/utils";
import { productsService, storesService } from "@/services";
import type { Product, Store, StoreCategory } from "@/types";

/**
 * Same client-reconciliation pattern as `store-product-grid.tsx`: paints
 * the server-fetched results immediately (SEO/fast first load), then
 * quietly refetches client-side so newly-created/approved stores and
 * products — invisible to Server Components, see src/lib/mock-db.ts —
 * show up without a full reload.
 */
export function SearchResults({
  query,
  category,
  sort,
  tab,
  tabHrefProducts,
  tabHrefStores,
  initialProducts,
  initialStores,
}: {
  query: string;
  category?: StoreCategory;
  sort: "newest" | "price-asc" | "price-desc" | "rating";
  tab: "products" | "stores";
  tabHrefProducts: string;
  tabHrefStores: string;
  initialProducts: Product[];
  initialStores: Store[];
}) {
  const { data: products } = useQuery({
    queryKey: ["products", "search", query, category, sort],
    queryFn: () => productsService.listProducts({ query, category, sort }),
    initialData: initialProducts,
    staleTime: 0,
  });
  const { data: stores } = useQuery({
    queryKey: ["stores", "search", query, category],
    queryFn: () => storesService.listStores({ query, category }),
    initialData: initialStores,
    staleTime: 0,
  });

  return (
    <div className="space-y-6">
      <div className="flex gap-2">
        <Link
          href={tabHrefProducts}
          className={cn(
            "flex items-center gap-1.5 rounded-md px-3 py-1.5 text-sm font-medium",
            tab === "products" ? "bg-accent" : "text-muted-foreground hover:bg-accent/50",
          )}
        >
          <PackageSearch className="size-4" /> Products ({products.length})
        </Link>
        <Link
          href={tabHrefStores}
          className={cn(
            "flex items-center gap-1.5 rounded-md px-3 py-1.5 text-sm font-medium",
            tab === "stores" ? "bg-accent" : "text-muted-foreground hover:bg-accent/50",
          )}
        >
          <StoreIcon className="size-4" /> Stores ({stores.length})
        </Link>
      </div>

      {tab === "products" ? (
        products.length > 0 ? (
          <div className="grid grid-cols-2 gap-4 sm:grid-cols-3 lg:grid-cols-4">
            {products.map((product) => (
              <ProductCard key={product.id} product={product} />
            ))}
          </div>
        ) : (
          <EmptyState
            icon={PackageSearch}
            title="No products found"
            description={
              query ? `Nothing matched "${query}". Try a different search or category.` : "Try a different category."
            }
          />
        )
      ) : stores.length > 0 ? (
        <div className="grid grid-cols-1 gap-4 sm:grid-cols-2 lg:grid-cols-3">
          {stores.map((store) => (
            <StoreCard key={store.id} store={store} />
          ))}
        </div>
      ) : (
        <EmptyState
          icon={StoreIcon}
          title="No stores found"
          description={query ? `Nothing matched "${query}".` : "Try a different category."}
        />
      )}
    </div>
  );
}
