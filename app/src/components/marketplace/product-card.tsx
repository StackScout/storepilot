import Image from "next/image";
import Link from "next/link";
import { PriceDisplay } from "@/components/shared/price-display";
import { Badge } from "@/components/ui/badge";
import { StatusBadge } from "@/components/shared/status-badge";
import { WishlistButton } from "@/components/marketplace/wishlist-button";
import type { Product } from "@/types";

export function ProductCard({
  product,
  priority = false,
}: {
  product: Product;
  priority?: boolean;
}) {
  const isOutOfStock = product.status === "out-of-stock";
  const isLowStock = !isOutOfStock && product.trackStock && product.stockQuantity <= 5;

  return (
    <Link
      href={`/stores/${product.storeSlug}/products/${product.slug}`}
      className="group focus-visible:ring-ring block rounded-lg outline-none focus-visible:ring-2"
    >
      <div className="bg-muted relative aspect-square overflow-hidden rounded-lg">
        <Image
          src={product.images[0]?.url}
          alt={product.images[0]?.alt ?? product.name}
          fill
          priority={priority}
          sizes="(max-width: 640px) 50vw, (max-width: 1024px) 33vw, 25vw"
          className="object-cover transition-transform duration-300 group-hover:scale-105"
        />
        {isOutOfStock ? (
          <div className="absolute inset-0 flex items-center justify-center bg-black/50">
            <Badge variant="secondary">Out of stock</Badge>
          </div>
        ) : isLowStock ? (
          <StatusBadge tone="warning" className="absolute top-2 left-2">
            Only {product.stockQuantity} left
          </StatusBadge>
        ) : null}
        {!isOutOfStock && product.compareAtPrice ? (
          <Badge className="bg-primary text-primary-foreground absolute top-2 right-2 border-0">
            Sale
          </Badge>
        ) : null}
        <WishlistButton productId={product.id} className="absolute bottom-2 right-2 z-10" />
      </div>
      <div className="mt-2.5 space-y-1">
        <p className="text-muted-foreground truncate text-xs">{product.storeName}</p>
        <h3 className="line-clamp-2 text-sm leading-snug font-medium">{product.name}</h3>
        <PriceDisplay
          price={product.price}
          compareAtPrice={product.compareAtPrice}
          size="sm"
        />
      </div>
    </Link>
  );
}
