import type { StoreCategory } from "@/types";

export interface CategoryMeta {
  value: StoreCategory;
  label: string;
  icon:
    | "shirt"
    | "utensils"
    | "sparkles"
    | "hand"
    | "smartphone"
    | "home"
    | "gem"
    | "shopping-basket";
}

export const CATEGORIES: CategoryMeta[] = [
  { value: "fashion", label: "Fashion & Apparel", icon: "shirt" },
  { value: "food-beverage", label: "Food & Beverage", icon: "utensils" },
  { value: "beauty", label: "Beauty & Wellness", icon: "sparkles" },
  { value: "handicrafts", label: "Handicrafts", icon: "hand" },
  { value: "electronics", label: "Electronics", icon: "smartphone" },
  { value: "home-living", label: "Home & Living", icon: "home" },
  { value: "jewelry", label: "Jewelry & Gems", icon: "gem" },
  { value: "grocery", label: "Grocery & Organic", icon: "shopping-basket" },
];

export function getCategoryLabel(value: StoreCategory): string {
  return CATEGORIES.find((c) => c.value === value)?.label ?? value;
}
