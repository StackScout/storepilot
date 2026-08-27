import { useEffect, useRef } from 'react';

import { getProductById } from '@/api/buyer-products';
import { useCart } from '@/hooks/use-cart';

/** Direct port of the web app's use-cart-reconciliation.ts. */
export function useCartReconciliation() {
  const { cart, isHydrated, syncItems } = useCart();
  const lastSyncedKey = useRef<string | null>(null);

  useEffect(() => {
    if (!isHydrated || cart.items.length === 0) return;
    const ids = [...new Set(cart.items.map((i) => i.productId))].sort();
    const key = ids.join(',');
    if (key === lastSyncedKey.current) return;
    lastSyncedKey.current = key;

    let cancelled = false;
    Promise.all(ids.map((id) => getProductById(id))).then((products) => {
      if (cancelled) return;
      syncItems(
        ids.map((id, i) => ({
          productId: id,
          product: products[i] ? { price: products[i]!.price, stockQuantity: products[i]!.stockQuantity, trackStock: products[i]!.trackStock } : null,
        })),
      );
    });
    return () => {
      cancelled = true;
    };
  }, [isHydrated, cart.items, syncItems]);
}
