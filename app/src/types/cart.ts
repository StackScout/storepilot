export interface CartItem {
  productId: string;
  productName: string;
  productSlug: string;
  productImageUrl: string;
  unitPrice: number;
  quantity: number;
  stockQuantity: number;
  /** Mirrors Product.trackStock — when false, stockQuantity is not a real cap (quantity selectors treat it as unlimited). */
  trackStock: boolean;
  /** True once reconciliation finds the product no longer exists (seller
   *  deleted it). The item stays in the cart as a snapshot so the buyer can
   *  see what they lost, but is excluded from totals/checkout. */
  isUnavailable?: boolean;
}

export interface Cart {
  storeId: string | null;
  storeName: string | null;
  storeSlug: string | null;
  items: CartItem[];
}
