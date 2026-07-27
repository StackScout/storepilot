import { Button } from "@/components/ui/button";
import { Input } from "@/components/ui/input";
import type { StoreCategory } from "@/types";

/**
 * A plain GET form, not a controlled client component — filter state lives
 * in the URL (see CLAUDE.md's search-page convention), so this works
 * without JS and is linkable/bookmarkable like every other filter here.
 */
export function PriceRangeFilter({
  query,
  category,
  sort,
  minPriceLkr,
  maxPriceLkr,
}: {
  query: string;
  category?: StoreCategory;
  sort: string;
  minPriceLkr?: number;
  maxPriceLkr?: number;
}) {
  return (
    <form action="/search" method="get" className="flex items-center gap-1.5">
      {query ? <input type="hidden" name="q" value={query} /> : null}
      {category ? <input type="hidden" name="category" value={category} /> : null}
      {sort !== "newest" ? <input type="hidden" name="sort" value={sort} /> : null}
      <Input
        type="number"
        name="minPrice"
        placeholder="Min"
        defaultValue={minPriceLkr}
        min={0}
        className="h-7 w-20 text-xs"
        aria-label="Minimum price (LKR)"
      />
      <span className="text-muted-foreground text-xs">–</span>
      <Input
        type="number"
        name="maxPrice"
        placeholder="Max"
        defaultValue={maxPriceLkr}
        min={0}
        className="h-7 w-20 text-xs"
        aria-label="Maximum price (LKR)"
      />
      <Button type="submit" size="xs" variant="outline">
        Apply
      </Button>
    </form>
  );
}
