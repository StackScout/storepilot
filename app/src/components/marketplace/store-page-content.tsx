"use client";

import Image from "next/image";
import Link from "next/link";
import { useQuery } from "@tanstack/react-query";
import { BadgeCheck, ExternalLink, MapPin, MessageCircle, PackageX, Users } from "lucide-react";
import { StoreProductGrid } from "@/components/marketplace/store-product-grid";
import { StoreServiceGrid } from "@/components/marketplace/store-service-grid";
import { ReviewsSection } from "@/components/marketplace/reviews-section";
import { FollowStoreButton } from "@/components/marketplace/follow-store-button";
import { MessageSellerButton } from "@/components/marketplace/message-seller-button";
import { RatingStars } from "@/components/shared/rating-stars";
import { EmptyState } from "@/components/shared/empty-state";
import { StoreLogoFallback, StoreBannerFallback } from "@/components/shared/store-image-fallback";
import { Button } from "@/components/ui/button";
import { queryKeys } from "@/lib/query-keys";
import { useCategories } from "@/hooks/use-categories";
import { bookableServicesService, productsService, storesService } from "@/services";
import type { BookableService, Product, Store } from "@/types";

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
  initialServices,
  bookingsEnabled,
}: {
  slug: string;
  initialStore: Store | null;
  initialProducts: Product[];
  initialServices: BookableService[];
  bookingsEnabled: boolean;
}) {
  const { getCategoryLabel } = useCategories();
  const { data: store, isLoading } = useQuery({
    queryKey: queryKeys.store.bySlug(slug),
    queryFn: () => storesService.getStoreBySlug(slug),
    initialData: initialStore,
    staleTime: 0,
  });
  const { data: products } = useQuery({
    queryKey: queryKeys.products.byStore(store?.id ?? ""),
    queryFn: async () => (store ? (await productsService.listProductsByStore(store.id)).content : []),
    initialData: initialProducts,
    enabled: !!store,
    staleTime: 0,
  });
  const { data: services } = useQuery({
    queryKey: queryKeys.bookableServices.byStore(store?.id ?? ""),
    queryFn: async () => (store ? (await bookableServicesService.listServicesByStore(store.id)).content : []),
    initialData: initialServices,
    enabled: !!store && bookingsEnabled,
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
        {store.bannerUrl ? (
          <Image src={store.bannerUrl} alt="" fill priority sizes="100vw" className="object-cover" />
        ) : (
          <StoreBannerFallback name={store.name} />
        )}
      </div>

      <div className="mx-auto max-w-7xl px-4 sm:px-6 lg:px-8">
        <div className="-mt-12 flex flex-col gap-4 sm:flex-row sm:items-end sm:justify-between">
          <div className="flex items-start gap-4">
            <div className="border-background bg-muted relative size-24 shrink-0 overflow-hidden rounded-full border-4">
              {store.logoUrl ? (
                <Image src={store.logoUrl} alt={store.name} fill sizes="96px" className="object-cover" />
              ) : (
                <StoreLogoFallback name={store.name} className="text-2xl" />
              )}
            </div>
            <div className="pt-14 sm:pt-16">
              <div className="flex items-center gap-1.5">
                <h1 className="text-xl font-bold sm:text-2xl">{store.name}</h1>
                {store.isVerified ? <BadgeCheck className="text-primary size-5" /> : null}
              </div>
              <p className="text-muted-foreground text-sm">{store.tagline}</p>
            </div>
          </div>
          <div className="flex flex-wrap shrink-0 gap-2">
            <FollowStoreButton storeId={store.id} />
            <MessageSellerButton storeId={store.id} />
            <Button
              render={<a href={whatsappHref} target="_blank" rel="noopener noreferrer" />}
              size="lg"
            >
              <MessageCircle className="size-4" /> Message on WhatsApp
            </Button>
          </div>
        </div>

        <div className="text-muted-foreground mt-4 flex flex-wrap items-center gap-x-5 gap-y-2 border-b pb-6 text-sm">
          <RatingStars rating={store.rating} reviewCount={store.reviewCount} />
          <span className="flex items-center gap-1">
            <MapPin className="size-3.5" /> {store.address.city}, {store.address.state}
          </span>
          <span className="flex items-center gap-1">
            <Users className="size-3.5" /> {store.followerCount.toLocaleString()} followers
          </span>
          <span>{getCategoryLabel(store.category)}</span>
          {[
            store.facebookUrl ? { href: store.facebookUrl, label: "Facebook" } : null,
            store.instagramUrl ? { href: store.instagramUrl, label: "Instagram" } : null,
            store.tiktokUrl ? { href: store.tiktokUrl, label: "TikTok" } : null,
          ]
            .filter((link) => link !== null)
            .map((link) => (
              <a
                key={link.label}
                href={link.href}
                target="_blank"
                rel="noopener noreferrer"
                className="hover:text-foreground flex items-center gap-1"
              >
                <ExternalLink className="size-3.5" /> {link.label}
              </a>
            ))}
        </div>

        <p className="text-muted-foreground max-w-3xl py-6 text-sm leading-relaxed">
          {store.description}
        </p>

        {(() => {
          const hasProducts = products.length > 0;
          const hasServices = (services?.length ?? 0) > 0;
          // A bookings-enabled store with zero products reads as
          // services-first — an empty product grid would just be noise, so
          // the Products section is omitted entirely rather than shown with
          // an empty state. A products-only store (bookings off) keeps the
          // existing always-show-with-empty-state behavior. See
          // docs/features/bookings.md's derived 3-mode UI.
          const showProducts = !bookingsEnabled || hasProducts;
          const showServices = bookingsEnabled;
          const productsSection = showProducts ? (
            <div key="products" className="space-y-4">
              <h2 className="text-lg font-semibold">Products</h2>
              <StoreProductGrid storeId={store.id} initialProducts={products} />
            </div>
          ) : null;
          const servicesSection = showServices ? (
            <div key="services" className="space-y-4">
              <h2 className="text-lg font-semibold">Services</h2>
              <StoreServiceGrid storeId={store.id} initialServices={services ?? []} />
            </div>
          ) : null;
          // Whichever section has content leads; Products leads when both
          // (or neither) do, matching the storefront's existing "products
          // first" mental model.
          const reviewsSection = (
            <div key="reviews" className="max-w-2xl">
              <ReviewsSection kind="store" targetId={store.id} />
            </div>
          );
          const sections = hasServices && !hasProducts
            ? [servicesSection, productsSection, reviewsSection]
            : [productsSection, servicesSection, reviewsSection];
          return <div className="space-y-10 pb-16">{sections.filter(Boolean)}</div>;
        })()}
      </div>
    </div>
  );
}
