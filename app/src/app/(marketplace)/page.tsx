import Link from "next/link";
import { ArrowRight } from "lucide-react";
import { Button } from "@/components/ui/button";
import { ProductCard } from "@/components/marketplace/product-card";
import { CategoryFilter } from "@/components/marketplace/category-filter";
import { PopularStoresGrid } from "@/components/marketplace/popular-stores-grid";
import { productsService, storesService } from "@/services";
import { getPlatformConfig } from "@/lib/platform-config";

export default async function HomePage() {
  const [featuredProducts, storesPage, config] = await Promise.all([
    productsService.getFeaturedProducts(8),
    storesService.listStores({ size: 6 }),
    getPlatformConfig(),
  ]);
  const stores = storesPage.content;

  return (
    <div className="mx-auto max-w-7xl space-y-12 px-4 py-8 sm:px-6 lg:px-8">
      <section className="from-primary/10 via-primary/5 rounded-2xl bg-gradient-to-br to-transparent px-6 py-10 sm:px-10 sm:py-14">
        <div className="max-w-2xl space-y-4">
          <h1 className="text-3xl leading-tight font-bold text-balance sm:text-4xl">
            Shop directly from Australia&apos;s small businesses
          </h1>
          <p className="text-muted-foreground text-base sm:text-lg">
            Small-batch coffee from the Blue Mountains, certified opals from Coober Pedy,
            native botanicals from Byron Bay — discover and buy from verified local sellers,
            with secure payment or cash on delivery.
          </p>
          <div className="flex flex-wrap gap-3 pt-1">
            <Button render={<Link href="/search" />} size="lg">
              Browse products <ArrowRight className="size-4" />
            </Button>
            <Button render={<Link href="/onboarding" />} size="lg" variant="outline">
              Sell on {config.name}
            </Button>
          </div>
        </div>
      </section>

      <section className="space-y-4">
        <h2 className="text-lg font-semibold">Shop by category</h2>
        <CategoryFilter />
      </section>

      <section className="space-y-4">
        <div className="flex items-center justify-between">
          <h2 className="text-lg font-semibold">Featured products</h2>
          <Link href="/search" className="text-primary flex items-center gap-1 text-sm font-medium">
            View all <ArrowRight className="size-3.5" />
          </Link>
        </div>
        <div className="grid grid-cols-2 gap-4 sm:grid-cols-3 lg:grid-cols-4">
          {featuredProducts.map((product, i) => (
            <ProductCard key={product.id} product={product} priority={i < 4} />
          ))}
        </div>
      </section>

      <section className="space-y-4">
        <div className="flex items-center justify-between">
          <h2 className="text-lg font-semibold">Popular stores</h2>
          <Link href="/search" className="text-primary flex items-center gap-1 text-sm font-medium">
            View all <ArrowRight className="size-3.5" />
          </Link>
        </div>
        <PopularStoresGrid initialStores={stores} />
      </section>
    </div>
  );
}
