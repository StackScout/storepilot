export interface CartItem {
  productId: string;
  productName: string;
  productSlug: string;
  productImageUrl: string;
  unitPriceLkr: number;
  quantity: number;
  stockQuantity: number;
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
