import Link from "next/link";
import { SearchBar } from "@/components/marketplace/search-bar";
import { CategoryFilter } from "@/components/marketplace/category-filter";
import { SearchResults } from "@/components/marketplace/search-results";
import { cn } from "@/lib/utils";
import { productsService, storesService } from "@/services";
import type { StoreCategory } from "@/types";

interface SearchPageProps {
  searchParams: Promise<{
    q?: string;
    category?: string;
    sort?: string;
    tab?: string;
  }>;
}

export default async function SearchPage({ searchParams }: SearchPageProps) {
  const params = await searchParams;
  const query = params.q?.trim() ?? "";
  const category = params.category as StoreCategory | undefined;
  const tab = params.tab === "stores" ? "stores" : "products";
  const sort = (params.sort as "newest" | "price-asc" | "price-desc" | "rating") ?? "newest";

  const [products, stores] = await Promise.all([
    productsService.listProducts({ query, category, sort }),
    storesService.listStores({ query, category }),
  ]);

  function tabHref(nextTab: string) {
    const search = new URLSearchParams();
    if (query) search.set("q", query);
    if (category) search.set("category", category);
    if (nextTab !== "products") search.set("tab", nextTab);
    const qs = search.toString();
    return qs ? `/search?${qs}` : "/search";
  }

  function sortHref(nextSort: string) {
    const search = new URLSearchParams();
    if (query) search.set("q", query);
    if (category) search.set("category", category);
    if (tab !== "products") search.set("tab", tab);
    if (nextSort !== "newest") search.set("sort", nextSort);
    const qs = search.toString();
    return qs ? `/search?${qs}` : "/search";
  }

  const SORT_OPTIONS: { value: string; label: string }[] = [
    { value: "newest", label: "Newest" },
    { value: "rating", label: "Top rated" },
    { value: "price-asc", label: "Price: low to high" },
    { value: "price-desc", label: "Price: high to low" },
  ];

  return (
    <div className="mx-auto max-w-7xl space-y-6 px-4 py-6 sm:px-6 lg:px-8">
      <SearchBar defaultValue={query} className="sm:hidden" />

      <div className="space-y-4">
        <CategoryFilter activeCategory={category} query={query} />

        {tab === "products" ? (
          <div className="flex flex-wrap gap-1.5">
            {SORT_OPTIONS.map((opt) => (
              <Link
                key={opt.value}
                href={sortHref(opt.value)}
                className={cn(
                  "rounded-md border px-2.5 py-1 text-xs font-medium",
                  sort === opt.value ? "border-primary text-primary" : "text-muted-foreground",
                )}
              >
                {opt.label}
              </Link>
            ))}
          </div>
        ) : null}
      </div>

      <SearchResults
        query={query}
        category={category}
        sort={sort}
        tab={tab}
        tabHrefProducts={tabHref("products")}
        tabHrefStores={tabHref("stores")}
        initialProducts={products}
        initialStores={stores}
      />
    </div>
  );
}
