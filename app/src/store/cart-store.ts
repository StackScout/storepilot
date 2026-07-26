import { create } from "zustand";
import { persist, createJSONStorage } from "zustand/middleware";
import type { Cart, CartItem, Product } from "@/types";

interface CartState {
  cart: Cart;
  /** Adds an item. Returns false (and does nothing) if the cart already holds
   *  items from a different store — MVP carts are single-seller only. */
  addItem: (product: Product, quantity?: number) => boolean;
  /** Clears the cart and starts a new one with this item, used after the
   *  buyer confirms they want to replace a cart from another store. */
  replaceCartWithItem: (product: Product, quantity?: number) => void;
  updateQuantity: (productId: string, quantity: number) => void;
  removeItem: (productId: string) => void;
  clearCart: () => void;
  /** Reconciles held items against freshly-fetched product data (see
   *  useCartReconciliation) — flags deleted products and refreshes stale
   *  price/stock snapshots. */
  syncItems: (updates: CartItemSync[]) => void;
}

export interface CartItemSync {
  productId: string;
  /** Present product state, or null if the product no longer exists. */
  product: { priceLkr: number; stockQuantity: number } | null;
}

const EMPTY_CART: Cart = { storeId: null, storeName: null, storeSlug: null, items: [] };

function toCartItem(product: Product, quantity: number): CartItem {
  return {
    productId: product.id,
    productName: product.name,
    productSlug: product.slug,
    productImageUrl: product.images[0]?.url ?? "",
    unitPriceLkr: product.priceLkr,
    quantity,
    stockQuantity: product.stockQuantity,
  };
}

export const useCartStore = create<CartState>()(
  persist(
    (set, get) => ({
      cart: EMPTY_CART,

      addItem: (product, quantity = 1) => {
        const { cart } = get();
        if (cart.storeId && cart.storeId !== product.storeId) {
          return false;
        }

        const existing = cart.items.find((i) => i.productId === product.id);
        const items = existing
          ? cart.items.map((i) =>
              i.productId === product.id
                ? { ...i, quantity: Math.min(i.quantity + quantity, product.stockQuantity) }
                : i,
            )
          : [...cart.items, toCartItem(product, Math.min(quantity, product.stockQuantity))];

        set({
          cart: {
            storeId: product.storeId,
            storeName: product.storeName,
            storeSlug: product.storeSlug,
            items,
          },
        });
        return true;
      },

      replaceCartWithItem: (product, quantity = 1) => {
        set({
          cart: {
            storeId: product.storeId,
            storeName: product.storeName,
            storeSlug: product.storeSlug,
            items: [toCartItem(product, Math.min(quantity, product.stockQuantity))],
          },
        });
      },

      updateQuantity: (productId, quantity) => {
        const { cart } = get();
        if (quantity <= 0) {
          get().removeItem(productId);
          return;
        }
        set({
          cart: {
            ...cart,
            items: cart.items.map((i) =>
              i.productId === productId
                ? { ...i, quantity: Math.min(quantity, i.stockQuantity) }
                : i,
            ),
          },
        });
      },

      removeItem: (productId) => {
        const { cart } = get();
        const items = cart.items.filter((i) => i.productId !== productId);
        set({
          cart: items.length === 0 ? EMPTY_CART : { ...cart, items },
        });
      },

      clearCart: () => set({ cart: EMPTY_CART }),

      syncItems: (updates) => {
        const { cart } = get();
        if (cart.items.length === 0) return;
        const byId = new Map(updates.map((u) => [u.productId, u.product]));
        set({
          cart: {
            ...cart,
            items: cart.items.map((item) => {
              if (!byId.has(item.productId)) return item;
              const product = byId.get(item.productId);
              if (!product) return { ...item, isUnavailable: true };
              return {
                ...item,
                isUnavailable: false,
                unitPriceLkr: product.priceLkr,
                stockQuantity: product.stockQuantity,
              };
            }),
          },
        });
      },
    }),
    {
      name: "islandcart_cart",
      storage: createJSONStorage(() => localStorage),
    },
  ),
);

export function cartItemCount(cart: Cart): number {
  return cart.items.filter((i) => !i.isUnavailable).reduce((sum, i) => sum + i.quantity, 0);
}

export function cartSubtotal(cart: Cart): number {
  return cart.items
    .filter((i) => !i.isUnavailable)
    .reduce((sum, i) => sum + i.unitPriceLkr * i.quantity, 0);
}
