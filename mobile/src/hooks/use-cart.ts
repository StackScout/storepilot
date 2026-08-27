import { cartItemCount, cartSubtotal, useCartStore } from '@/store/cart-store';

/** Mirrors the web app's useCart() — always go through this, never useCartStore directly, so isHydrated is respected (AsyncStorage rehydration is async, unlike localStorage). */
export function useCart() {
  const isHydrated = useCartStore((s) => s.isHydrated);
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
