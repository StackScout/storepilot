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
  priceLkr: number;
  compareAtPriceLkr?: number;
  stockQuantity: number;
  status: ProductStatus;
  sku: string;
  rating: number;
  reviewCount: number;
  createdAt: string;
  updatedAt: string;
}

export interface ProductFormInput {
  name: string;
  description: string;
  category: StoreCategory;
  priceLkr: number;
  compareAtPriceLkr?: number;
  stockQuantity: number;
  sku: string;
  status: ProductStatus;
  imageUrl: string;
}
