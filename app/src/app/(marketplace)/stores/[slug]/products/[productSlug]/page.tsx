import Link from "next/link";
import { notFound } from "next/navigation";
import type { Metadata } from "next";
import { ChevronRight, MapPin, PackageCheck } from "lucide-react";
import { PriceDisplay } from "@/components/shared/price-display";
import { RatingStars } from "@/components/shared/rating-stars";
import { AddToCartControls } from "@/components/marketplace/add-to-cart-controls";
import { ProductCard } from "@/components/marketplace/product-card";
import { ProductGallery } from "@/components/marketplace/product-gallery";
import { CopyLinkButton } from "@/components/shared/copy-link-button";
import { productsService, storesService } from "@/services";

interface ProductPageProps {
  params: Promise<{ slug: string; productSlug: string }>;
}

export async function generateMetadata({ params }: ProductPageProps): Promise<Metadata> {
  const { slug, productSlug } = await params;
  const product = await productsService.getProductBySlug(slug, productSlug);
  return { title: product ? product.name : "Product not found" };
}

export default async function ProductPage({ params }: ProductPageProps) {
  const { slug, productSlug } = await params;
  const product = await productsService.getProductBySlug(slug, productSlug);
  if (!product) notFound();

  const [store, storeProducts] = await Promise.all([
    storesService.getStoreBySlug(slug),
    productsService.listProductsByStore(product.storeId),
  ]);

  const relatedProducts = storeProducts.filter((p) => p.id !== product.id).slice(0, 4);

  return (
    <div className="mx-auto max-w-7xl px-4 py-6 sm:px-6 lg:px-8">
      <nav className="text-muted-foreground mb-4 flex items-center gap-1 text-xs">
        <Link href="/" className="hover:text-foreground">
          Home
        </Link>
        <ChevronRight className="size-3" />
        <Link href={`/stores/${slug}`} className="hover:text-foreground">
          {product.storeName}
        </Link>
        <ChevronRight className="size-3" />
        <span className="text-foreground line-clamp-1">{product.name}</span>
      </nav>

      <div className="grid gap-8 lg:grid-cols-2">
        <ProductGallery images={product.images} productName={product.name} />

        <div className="space-y-5">
          <div className="space-y-2">
            <Link
              href={`/stores/${slug}`}
              className="text-primary flex items-center gap-1 text-sm font-medium"
            >
              {product.storeName}
            </Link>
            <div className="flex items-start justify-between gap-3">
              <h1 className="text-2xl font-bold text-balance">{product.name}</h1>
              <CopyLinkButton
                path={`/stores/${slug}/products/${product.slug}`}
                className="shrink-0"
              />
            </div>
            <div className="flex flex-wrap items-center gap-3">
              <RatingStars rating={product.rating} reviewCount={product.reviewCount} />
              {store ? (
                <span className="text-muted-foreground flex items-center gap-1 text-xs">
                  <MapPin className="size-3.5" /> {store.address.city}
                </span>
              ) : null}
            </div>
          </div>

          <PriceDisplay
            price={product.price}
            compareAtPrice={product.compareAtPrice}
            size="lg"
          />

          {product.status !== "out-of-stock" && (
            <p className="text-muted-foreground flex items-center gap-1.5 text-sm">
              <PackageCheck className="size-4 text-emerald-600" />
              {product.trackStock && product.stockQuantity <= 5
                ? `Only ${product.stockQuantity} left in stock`
                : "In stock, ready to ship"}
            </p>
          )}

          <AddToCartControls product={product} />

          <div className="space-y-1.5 border-t pt-5">
            <h2 className="text-sm font-semibold">Description</h2>
            <p className="text-muted-foreground text-sm leading-relaxed">{product.description}</p>
          </div>

          <dl className="grid grid-cols-2 gap-3 border-t pt-5 text-sm">
            {product.sku ? (
              <div>
                <dt className="text-muted-foreground">SKU</dt>
                <dd className="font-medium">{product.sku}</dd>
              </div>
            ) : null}
            <div>
              <dt className="text-muted-foreground">Category</dt>
              <dd className="font-medium capitalize">{product.category.replace("-", " ")}</dd>
            </div>
          </dl>
        </div>
      </div>

      {relatedProducts.length > 0 ? (
        <section className="mt-14 space-y-4">
          <h2 className="text-lg font-semibold">More from {product.storeName}</h2>
          <div className="grid grid-cols-2 gap-4 sm:grid-cols-3 lg:grid-cols-4">
            {relatedProducts.map((p) => (
              <ProductCard key={p.id} product={p} />
            ))}
          </div>
        </section>
      ) : null}
    </div>
  );
}
