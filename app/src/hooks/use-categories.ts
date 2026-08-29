"use client";

import { useQuery } from "@tanstack/react-query";
import { queryKeys } from "@/lib/query-keys";
import { categoriesService } from "@/services";
import type { Category } from "@/types";

/**
 * Replaces the old hardcoded `mock/categories.ts` (CATEGORIES/getCategoryLabel)
 * — categories are admin-managed now (see backend's CategoryController), so
 * every consumer that used to import the static array fetches this instead.
 * `staleTime` is generous since this list changes rarely (an admin action,
 * not user activity) and is used on nearly every marketplace/dashboard page.
 */
export function useCategories() {
  const { data, isLoading } = useQuery<Category[]>({
    queryKey: queryKeys.categories.all(),
    queryFn: () => categoriesService.listCategories(),
    staleTime: 5 * 60_000,
  });

  const categories = data ?? [];

  function getCategoryLabel(wireValue: string): string {
    return categories.find((c) => c.wireValue === wireValue)?.name ?? wireValue;
  }

  return { categories, getCategoryLabel, isLoading };
}
