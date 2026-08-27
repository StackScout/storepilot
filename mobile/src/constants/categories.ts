import type { StoreCategory } from '@storepilot/shared-api';

/** Mirrors the web app's mock/categories.ts. */
export interface CategoryMeta {
  value: StoreCategory;
  label: string;
}

export const CATEGORIES: CategoryMeta[] = [
  { value: 'fashion', label: 'Fashion & Apparel' },
  { value: 'food-beverage', label: 'Food & Beverage' },
  { value: 'beauty', label: 'Beauty & Wellness' },
  { value: 'handicrafts', label: 'Handicrafts' },
  { value: 'electronics', label: 'Electronics' },
  { value: 'home-living', label: 'Home & Living' },
  { value: 'jewelry', label: 'Jewelry & Gems' },
  { value: 'grocery', label: 'Grocery & Organic' },
];

export function getCategoryLabel(value: StoreCategory): string {
  return CATEGORIES.find((c) => c.value === value)?.label ?? value;
}
