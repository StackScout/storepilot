import AsyncStorage from '@react-native-async-storage/async-storage';
import { create } from 'zustand';
import { createJSONStorage, persist } from 'zustand/middleware';

import type { Cart, CartItem, Product } from '@storepilot/shared-api';

/** Direct port of the web app's cart-store.ts — same rules (single-store-only cart, stock caps, price/stock reconciliation), swapped to AsyncStorage instead of localStorage. */
interface CartState {
  cart: Cart;
  isHydrated: boolean;
  /** Adds an item. Returns false (and does nothing) if the cart already holds items from a different store — carts are single-seller only. */
  addItem: (product: Product, quantity?: number) => boolean;
  /** Clears the cart and starts a new one with this item, used after the buyer confirms they want to replace a cart from another store. */
  replaceCartWithItem: (product: Product, quantity?: number) => void;
  updateQuantity: (productId: string, quantity: number) => void;
  removeItem: (productId: string) => void;
  clearCart: () => void;
  /** Reconciles held items against freshly-fetched product data — flags deleted products and refreshes stale price/stock snapshots. */
  syncItems: (updates: CartItemSync[]) => void;
}

export interface CartItemSync {
  productId: string;
  product: { price: number; stockQuantity: number; trackStock: boolean } | null;
}

const EMPTY_CART: Cart = { storeId: null, storeName: null, storeSlug: null, items: [] };

function availableQuantity(stockQuantity: number, trackStock: boolean): number {
  return trackStock ? stockQuantity : Infinity;
}

function toCartItem(product: Product, quantity: number): CartItem {
  return {
    productId: product.id,
    productName: product.name,
    productSlug: product.slug,
    productImageUrl: product.images[0]?.url ?? '',
    unitPrice: product.price,
    quantity,
    stockQuantity: product.stockQuantity,
    trackStock: product.trackStock,
  };
}

export const useCartStore = create<CartState>()(
  persist(
    (set, get) => ({
      cart: EMPTY_CART,
      isHydrated: false,

      addItem: (product, quantity = 1) => {
        const { cart } = get();
        if (cart.storeId && cart.storeId !== product.storeId) {
          return false;
        }

        const cap = availableQuantity(product.stockQuantity, product.trackStock);
        const existing = cart.items.find((i) => i.productId === product.id);
        const items = existing
          ? cart.items.map((i) => (i.productId === product.id ? { ...i, quantity: Math.min(i.quantity + quantity, cap) } : i))
          : [...cart.items, toCartItem(product, Math.min(quantity, cap))];

        set({
          cart: { storeId: product.storeId, storeName: product.storeName, storeSlug: product.storeSlug, items },
        });
        return true;
      },

      replaceCartWithItem: (product, quantity = 1) => {
        const cap = availableQuantity(product.stockQuantity, product.trackStock);
        set({
          cart: {
            storeId: product.storeId,
            storeName: product.storeName,
            storeSlug: product.storeSlug,
            items: [toCartItem(product, Math.min(quantity, cap))],
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
              i.productId === productId ? { ...i, quantity: Math.min(quantity, availableQuantity(i.stockQuantity, i.trackStock)) } : i,
            ),
          },
        });
      },

      removeItem: (productId) => {
        const { cart } = get();
        const items = cart.items.filter((i) => i.productId !== productId);
        set({ cart: items.length === 0 ? EMPTY_CART : { ...cart, items } });
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
              return { ...item, isUnavailable: false, unitPrice: product.price, stockQuantity: product.stockQuantity, trackStock: product.trackStock };
            }),
          },
        });
      },
    }),
    {
      name: 'storepilot_cart',
      storage: createJSONStorage(() => AsyncStorage),
      onRehydrateStorage: () => (state) => {
        if (state) state.isHydrated = true;
      },
    },
  ),
);

export function cartItemCount(cart: Cart): number {
  return cart.items.filter((i) => !i.isUnavailable).reduce((sum, i) => sum + i.quantity, 0);
}

export function cartSubtotal(cart: Cart): number {
  return cart.items.filter((i) => !i.isUnavailable).reduce((sum, i) => sum + i.unitPrice * i.quantity, 0);
}
