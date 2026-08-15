import Link from "next/link";
import { SearchBar } from "@/components/marketplace/search-bar";
import { CategoryFilter } from "@/components/marketplace/category-filter";
import { PriceRangeFilter } from "@/components/marketplace/price-range-filter";
import { SearchResults } from "@/components/marketplace/search-results";
import { SaveSearchButton } from "@/components/marketplace/save-search-button";
import { cn } from "@/lib/utils";
import { productsService, storesService } from "@/services";
import type { StoreCategory } from "@/types";

interface SearchPageProps {
  searchParams: Promise<{
    q?: string;
    category?: string;
    sort?: string;
    tab?: string;
    page?: string;
    minPrice?: string;
    maxPrice?: string;
  }>;
}

const PAGE_SIZE = 24;

export default async function SearchPage({ searchParams }: SearchPageProps) {
  const params = await searchParams;
  const query = params.q?.trim() ?? "";
  const category = params.category as StoreCategory | undefined;
  const tab = params.tab === "stores" ? "stores" : "products";
  const sort = (params.sort as "newest" | "price-asc" | "price-desc" | "rating") ?? "newest";
  const minPrice = params.minPrice ? Number(params.minPrice) : undefined;
  const maxPrice = params.maxPrice ? Number(params.maxPrice) : undefined;
  // URL is 1-indexed for readability ("page=2"); the API is 0-indexed.
  const page = Math.max(0, (Number(params.page) || 1) - 1);

  const [productsPage, storesPage] = await Promise.all([
    productsService.listProducts({
      query,
      category,
      sort,
      minPrice,
      maxPrice,
      page: tab === "products" ? page : 0,
      size: PAGE_SIZE,
    }),
    storesService.listStores({ query, category, page: tab === "stores" ? page : 0, size: PAGE_SIZE }),
  ]);

  function baseParams() {
    const search = new URLSearchParams();
    if (query) search.set("q", query);
    if (category) search.set("category", category);
    return search;
  }

  function tabHref(nextTab: string) {
    const search = baseParams();
    if (nextTab !== "products") search.set("tab", nextTab);
    const qs = search.toString();
    return qs ? `/search?${qs}` : "/search";
  }

  function sortHref(nextSort: string) {
    const search = baseParams();
    if (tab !== "products") search.set("tab", tab);
    if (nextSort !== "newest") search.set("sort", nextSort);
    if (minPrice) search.set("minPrice", String(minPrice));
    if (maxPrice) search.set("maxPrice", String(maxPrice));
    const qs = search.toString();
    return qs ? `/search?${qs}` : "/search";
  }

  function pageHref(nextPage1Indexed: number) {
    const search = baseParams();
    if (tab !== "products") search.set("tab", tab);
    if (sort !== "newest") search.set("sort", sort);
    if (minPrice) search.set("minPrice", String(minPrice));
    if (maxPrice) search.set("maxPrice", String(maxPrice));
    if (nextPage1Indexed > 1) search.set("page", String(nextPage1Indexed));
    const qs = search.toString();
    return qs ? `/search?${qs}` : "/search";
  }

  function savedQueryString() {
    const search = baseParams();
    if (sort !== "newest") search.set("sort", sort);
    if (minPrice) search.set("minPrice", String(minPrice));
    if (maxPrice) search.set("maxPrice", String(maxPrice));
    return search.toString();
  }

  const activePage = tab === "products" ? productsPage : storesPage;
  const prevHref = page > 0 ? pageHref(page) : null;
  const nextHref = page + 1 < activePage.totalPages ? pageHref(page + 2) : null;

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
          <div className="flex flex-wrap items-center justify-between gap-3">
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
            <div className="flex items-center gap-2">
              <PriceRangeFilter
                query={query}
                category={category}
                sort={sort}
                minPrice={minPrice}
                maxPrice={maxPrice}
              />
              <SaveSearchButton queryString={savedQueryString()} />
            </div>
          </div>
        ) : null}
      </div>

      <SearchResults
        query={query}
        category={category}
        sort={sort}
        minPrice={minPrice}
        maxPrice={maxPrice}
        tab={tab}
        page={page}
        tabHrefProducts={tabHref("products")}
        tabHrefStores={tabHref("stores")}
        prevHref={prevHref}
        nextHref={nextHref}
        initialProducts={productsPage}
        initialStores={storesPage}
      />
    </div>
  );
}
