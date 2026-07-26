"use client";

import { useSyncExternalStore } from "react";
import { useCartStore, cartItemCount, cartSubtotal } from "@/store/cart-store";

const emptySubscribe = () => () => {};

/**
 * True only once the client has mounted. Used as a hydration guard so
 * server-rendered markup (always an empty cart) matches the first client
 * render, avoiding a mismatch before localStorage has been read.
 */
function useIsHydrated() {
  return useSyncExternalStore(
    emptySubscribe,
    () => true,
    () => false,
  );
}

export function useCart() {
  const isHydrated = useIsHydrated();
  const cart = useCartStore((s) => s.cart);
  const addItem = useCartStore((s) => s.addItem);
  const replaceCartWithItem = useCartStore((s) => s.replaceCartWithItem);
  const updateQuantity = useCartStore((s) => s.updateQuantity);
  const removeItem = useCartStore((s) => s.removeItem);
  const clearCart = useCartStore((s) => s.clearCart);
  const syncItems = useCartStore((s) => s.syncItems);

  const safeCart = isHydrated ? cart : { storeId: null, storeName: null, storeSlug: null, items: [] };

  return {
    cart: safeCart,
    itemCount: cartItemCount(safeCart),
    subtotal: cartSubtotal(safeCart),
    isHydrated,
    addItem,
    replaceCartWithItem,
    updateQuantity,
    removeItem,
    clearCart,
    syncItems,
  };
}
