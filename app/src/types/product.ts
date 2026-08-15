import type { StoreCategory } from "./store";

export type ProductStatus = "active" | "draft" | "out-of-stock";

export interface ProductImage {
  id: string;
  url: string;
  alt: string;
}

export interface Product {
  id: string;
  storeId: string;
  storeName: string;
  storeSlug: string;
  name: string;
  slug: string;
  description: string;
  images: ProductImage[];
  category: StoreCategory;
  price: number;
  compareAtPrice?: number;
  stockQuantity: number;
  /** Whether this product tracks stock quantity — always false if the store has disabled stock management. */
  trackStock: boolean;
  status: ProductStatus;
  sku?: string;
  rating: number;
  reviewCount: number;
  createdAt: string;
  updatedAt: string;
}

/** GET /api/products/{id}/wishlist */
export interface WishlistStatus {
  wishlisted: boolean;
}

export interface ProductFormInput {
  name: string;
  description: string;
  category: StoreCategory;
  price: number;
  compareAtPrice?: number;
  stockQuantity: number;
  trackStock: boolean;
  sku?: string;
  status: ProductStatus;
}
