import Image from "next/image";
import Link from "next/link";
import { BadgeCheck, MapPin } from "lucide-react";
import { RatingStars } from "@/components/shared/rating-stars";
import { StoreLogoFallback, StoreBannerFallback } from "@/components/shared/store-image-fallback";
import { getCategoryLabel } from "@/mock/categories";
import type { Store } from "@/types";

export function StoreCard({ store }: { store: Store }) {
  return (
    <Link
      href={`/stores/${store.slug}`}
      className="group focus-visible:ring-ring block overflow-hidden rounded-lg border outline-none focus-visible:ring-2"
    >
      <div className="bg-muted relative h-24 w-full overflow-hidden">
        {store.bannerUrl ? (
          <Image
            src={store.bannerUrl}
            alt=""
            fill
            sizes="(max-width: 640px) 100vw, 320px"
            className="object-cover transition-transform duration-300 group-hover:scale-105"
          />
        ) : (
          <StoreBannerFallback name={store.name} />
        )}
      </div>
      <div className="flex items-start gap-3 p-4">
        <div className="border-background bg-muted relative -mt-8 size-12 shrink-0 overflow-hidden rounded-full border-2">
          {store.logoUrl ? (
            <Image src={store.logoUrl} alt={store.name} fill sizes="48px" className="object-cover" />
          ) : (
            <StoreLogoFallback name={store.name} className="text-sm" />
          )}
        </div>
        <div className="min-w-0 flex-1 space-y-1">
          <div className="flex items-center gap-1">
            <h3 className="truncate text-sm font-semibold">{store.name}</h3>
            {store.isVerified ? (
              <BadgeCheck className="text-primary size-4 shrink-0" />
            ) : null}
          </div>
          <p className="text-muted-foreground line-clamp-1 text-xs">{store.tagline}</p>
          <div className="flex flex-wrap items-center gap-x-3 gap-y-1 pt-1">
            <RatingStars rating={store.rating} reviewCount={store.reviewCount} />
            <span className="text-muted-foreground inline-flex items-center gap-0.5 text-xs">
              <MapPin className="size-3" />
              {store.address.city}
            </span>
          </div>
          <p className="text-muted-foreground text-xs">
            {getCategoryLabel(store.category)} · {store.productCount} products
          </p>
        </div>
      </div>
    </Link>
  );
}
