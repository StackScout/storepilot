import Link from "next/link";
import { CATEGORIES } from "@/mock/categories";
import { cn } from "@/lib/utils";
import type { StoreCategory } from "@/types";

function buildHref(base: string, params: Record<string, string | undefined>) {
  const search = new URLSearchParams();
  for (const [key, value] of Object.entries(params)) {
    if (value) search.set(key, value);
  }
  const qs = search.toString();
  return qs ? `${base}?${qs}` : base;
}

export function CategoryFilter({
  activeCategory,
  query,
  basePath = "/search",
}: {
  activeCategory?: StoreCategory;
  query?: string;
  basePath?: string;
}) {
  return (
    <div className="scrollbar-hide -mx-4 flex gap-2 overflow-x-auto px-4 pb-1 sm:mx-0 sm:flex-wrap sm:px-0">
      <Link
        href={buildHref(basePath, { q: query })}
        className={cn(
          "shrink-0 rounded-full border px-3.5 py-1.5 text-sm whitespace-nowrap transition-colors",
          !activeCategory
            ? "bg-primary text-primary-foreground border-primary"
            : "hover:bg-accent",
        )}
      >
        All
      </Link>
      {CATEGORIES.map((c) => (
        <Link
          key={c.value}
          href={buildHref(basePath, { q: query, category: c.value })}
          className={cn(
            "shrink-0 rounded-full border px-3.5 py-1.5 text-sm whitespace-nowrap transition-colors",
            activeCategory === c.value
              ? "bg-primary text-primary-foreground border-primary"
              : "hover:bg-accent",
          )}
        >
          {c.label}
        </Link>
      ))}
    </div>
  );
}
