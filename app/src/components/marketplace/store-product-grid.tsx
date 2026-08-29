"use client";

import { useQuery } from "@tanstack/react-query";
import { Package } from "lucide-react";
import { ProductCard } from "@/components/marketplace/product-card";
import { EmptyState } from "@/components/shared/empty-state";
import { queryKeys } from "@/lib/query-keys";
import { productsService } from "@/services";
import type { Product } from "@/types";

/**
 * Renders products for a store, seeded with the server-rendered list for a
 * fast first paint / SEO, then quietly refetches on the client. The
 * dashboard's product CRUD writes to localStorage (see `src/lib/mock-db.ts`),
 * which server components can't see — this reconciles the two once the page
 * hydrates, so a seller's own edits show up on their storefront immediately.
 */
export function StoreProductGrid({
  storeId,
  initialProducts,
}: {
  storeId: string;
  initialProducts: Product[];
}) {
  const { data: products } = useQuery({
    queryKey: queryKeys.products.byStore(storeId),
    queryFn: async () => (await productsService.listProductsByStore(storeId)).content,
    initialData: initialProducts,
    staleTime: 0,
  });

  if (products.length === 0) {
    return (
      <EmptyState icon={Package} title="No products yet" description="This store hasn't listed any products." />
    );
  }

  return (
    <div className="grid grid-cols-2 gap-4 sm:grid-cols-3 lg:grid-cols-4">
      {products.map((product) => (
        <ProductCard key={product.id} product={product} />
      ))}
    </div>
  );
}
