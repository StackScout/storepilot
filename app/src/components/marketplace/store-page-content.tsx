"use client";

import Image from "next/image";
import Link from "next/link";
import { useQuery } from "@tanstack/react-query";
import { BadgeCheck, MapPin, MessageCircle, PackageX, Users } from "lucide-react";
import { StoreProductGrid } from "@/components/marketplace/store-product-grid";
import { RatingStars } from "@/components/shared/rating-stars";
import { EmptyState } from "@/components/shared/empty-state";
import { Button } from "@/components/ui/button";
import { getCategoryLabel } from "@/mock/categories";
import { productsService, storesService } from "@/services";
import type { Product, Store } from "@/types";

/**
 * Same reconciliation pattern as store-product-grid.tsx, but for the store
 * itself: a store created/approved after this page was last built statically
 * is invisible to the Server Component fetch (see src/lib/mock-db.ts), so
 * this refetches client-side and only shows "not found" once that refetch
 * has actually resolved to null — not from the SSR miss alone.
 */
export function StorePageContent({
  slug,
  initialStore,
  initialProducts,
}: {
  slug: string;
  initialStore: Store | null;
  initialProducts: Product[];
}) {
  const { data: store, isLoading } = useQuery({
    queryKey: ["store", "slug", slug],
    queryFn: () => storesService.getStoreBySlug(slug),
    initialData: initialStore,
    staleTime: 0,
  });
  const { data: products } = useQuery({
    queryKey: ["products", "store", store?.id],
    queryFn: () => (store ? productsService.listProductsByStore(store.id) : Promise.resolve([])),
    initialData: initialProducts,
    enabled: !!store,
    staleTime: 0,
  });

  if (!store) {
    if (isLoading) return null;
    return (
      <div className="mx-auto max-w-2xl px-4 py-16 sm:px-6 lg:px-8">
        <EmptyState
          icon={PackageX}
          title="Store not found"
          description="This store may not exist, or isn't public yet."
          action={
            <Button render={<Link href="/search" />}>Browse other stores</Button>
          }
        />
      </div>
    );
  }

  const whatsappHref = `https://wa.me/${store.whatsappNumber.replace(/[^0-9]/g, "")}`;

  return (
    <div>
      <div className="bg-muted relative h-40 w-full sm:h-56">
        <Image src={store.bannerUrl} alt="" fill priority sizes="100vw" className="object-cover" />
      </div>

      <div className="mx-auto max-w-7xl px-4 sm:px-6 lg:px-8">
        <div className="-mt-12 flex flex-col gap-4 sm:flex-row sm:items-end sm:justify-between">
          <div className="flex items-start gap-4">
            <div className="border-background bg-muted relative size-24 shrink-0 overflow-hidden rounded-full border-4">
              <Image src={store.logoUrl} alt={store.name} fill sizes="96px" className="object-cover" />
            </div>
            <div className="pt-14 sm:pt-16">
              <div className="flex items-center gap-1.5">
                <h1 className="text-xl font-bold sm:text-2xl">{store.name}</h1>
                {store.isVerified ? <BadgeCheck className="text-primary size-5" /> : null}
              </div>
              <p className="text-muted-foreground text-sm">{store.tagline}</p>
            </div>
          </div>
          <Button
            render={<a href={whatsappHref} target="_blank" rel="noopener noreferrer" />}
            size="lg"
            className="shrink-0"
          >
            <MessageCircle className="size-4" /> Message on WhatsApp
          </Button>
        </div>

        <div className="text-muted-foreground mt-4 flex flex-wrap items-center gap-x-5 gap-y-2 border-b pb-6 text-sm">
          <RatingStars rating={store.rating} reviewCount={store.reviewCount} />
          <span className="flex items-center gap-1">
            <MapPin className="size-3.5" /> {store.address.city}, {store.address.province}
          </span>
          <span className="flex items-center gap-1">
            <Users className="size-3.5" /> {store.followerCount.toLocaleString()} followers
          </span>
          <span>{getCategoryLabel(store.category)}</span>
        </div>

        <p className="text-muted-foreground max-w-3xl py-6 text-sm leading-relaxed">
          {store.description}
        </p>

        <div className="space-y-4 pb-16">
          <h2 className="text-lg font-semibold">Products</h2>
          <StoreProductGrid storeId={store.id} initialProducts={products} />
        </div>
      </div>
    </div>
  );
}
