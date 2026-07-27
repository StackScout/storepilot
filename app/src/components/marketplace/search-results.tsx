"use client";

import Link from "next/link";
import { useQuery } from "@tanstack/react-query";
import { ChevronLeft, ChevronRight, PackageSearch, Store as StoreIcon } from "lucide-react";
import { Button } from "@/components/ui/button";
import { ProductCard } from "@/components/marketplace/product-card";
import { StoreCard } from "@/components/marketplace/store-card";
import { EmptyState } from "@/components/shared/empty-state";
import { cn } from "@/lib/utils";
import { productsService, storesService } from "@/services";
import type { PageResponse, Product, Store, StoreCategory } from "@/types";

/**
 * Same client-reconciliation pattern as `store-product-grid.tsx`: paints
 * the server-fetched results immediately (SEO/fast first load), then
 * quietly refetches client-side so newly-created/approved stores and
 * products — invisible to Server Components, see src/lib/mock-db.ts —
 * show up without a full reload. Filtering/sorting/pagination all happen
 * server-side now (see products.service.ts / stores.service.ts) — this
 * component never sees more than one page's worth of rows.
 */
export function SearchResults({
  query,
  category,
  sort,
  minPriceLkr,
  maxPriceLkr,
  tab,
  page,
  tabHrefProducts,
  tabHrefStores,
  prevHref,
  nextHref,
  initialProducts,
  initialStores,
}: {
  query: string;
  category?: StoreCategory;
  sort: "newest" | "price-asc" | "price-desc" | "rating";
  minPriceLkr?: number;
  maxPriceLkr?: number;
  tab: "products" | "stores";
  page: number;
  tabHrefProducts: string;
  tabHrefStores: string;
  prevHref: string | null;
  nextHref: string | null;
  initialProducts: PageResponse<Product>;
  initialStores: PageResponse<Store>;
}) {
  const { data: products } = useQuery({
    queryKey: ["products", "search", query, category, sort, minPriceLkr, maxPriceLkr, tab === "products" ? page : 0],
    queryFn: () =>
      productsService.listProducts({
        query,
        category,
        sort,
        minPriceLkr,
        maxPriceLkr,
        page: tab === "products" ? page : 0,
      }),
    initialData: initialProducts,
    staleTime: 0,
  });
  const { data: stores } = useQuery({
    queryKey: ["stores", "search", query, category, tab === "stores" ? page : 0],
    queryFn: () => storesService.listStores({ query, category, page: tab === "stores" ? page : 0 }),
    initialData: initialStores,
    staleTime: 0,
  });

  const activeTotalPages = tab === "products" ? products.totalPages : stores.totalPages;

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
          <PackageSearch className="size-4" /> Products ({products.totalElements})
        </Link>
        <Link
          href={tabHrefStores}
          className={cn(
            "flex items-center gap-1.5 rounded-md px-3 py-1.5 text-sm font-medium",
            tab === "stores" ? "bg-accent" : "text-muted-foreground hover:bg-accent/50",
          )}
        >
          <StoreIcon className="size-4" /> Stores ({stores.totalElements})
        </Link>
      </div>

      {tab === "products" ? (
        products.content.length > 0 ? (
          <div className="grid grid-cols-2 gap-4 sm:grid-cols-3 lg:grid-cols-4">
            {products.content.map((product) => (
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
      ) : stores.content.length > 0 ? (
        <div className="grid grid-cols-1 gap-4 sm:grid-cols-2 lg:grid-cols-3">
          {stores.content.map((store) => (
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

      {activeTotalPages > 1 ? (
        <div className="flex items-center justify-between">
          <p className="text-muted-foreground text-sm">
            Page {page + 1} of {activeTotalPages}
          </p>
          <div className="flex gap-2">
            {prevHref ? (
              <Button render={<Link href={prevHref} />} variant="outline" size="sm">
                <ChevronLeft className="size-3.5" /> Previous
              </Button>
            ) : (
              <Button variant="outline" size="sm" disabled>
                <ChevronLeft className="size-3.5" /> Previous
              </Button>
            )}
            {nextHref ? (
              <Button render={<Link href={nextHref} />} variant="outline" size="sm">
                Next <ChevronRight className="size-3.5" />
              </Button>
            ) : (
              <Button variant="outline" size="sm" disabled>
                Next <ChevronRight className="size-3.5" />
              </Button>
            )}
          </div>
        </div>
      ) : null}
    </div>
  );
}
