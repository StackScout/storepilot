"use client";

import { useEffect, useRef } from "react";
import { productsService } from "@/services";
import { useCart } from "@/hooks/use-cart";

/**
 * Re-fetches each cart line's product whenever the cart's product set
 * changes (on mount, or after items are added/removed elsewhere), so a
 * seller-deleted product or a price change is reflected instead of showing
 * an indefinitely stale snapshot. Deleted products are flagged
 * `isUnavailable` rather than silently dropped, so the buyer can see what
 * they lost and remove it themselves.
 */
export function useCartReconciliation() {
  const { cart, isHydrated, syncItems } = useCart();
  const lastSyncedKey = useRef<string | null>(null);

  useEffect(() => {
    if (!isHydrated || cart.items.length === 0) return;
    const ids = [...new Set(cart.items.map((i) => i.productId))].sort();
    const key = ids.join(",");
    if (key === lastSyncedKey.current) return;
    lastSyncedKey.current = key;

    let cancelled = false;
    Promise.all(ids.map((id) => productsService.getProductById(id))).then((products) => {
      if (cancelled) return;
      syncItems(
        ids.map((id, i) => ({
          productId: id,
          product: products[i]
            ? { priceLkr: products[i]!.priceLkr, stockQuantity: products[i]!.stockQuantity }
            : null,
        })),
      );
    });
    return () => {
      cancelled = true;
    };
  }, [isHydrated, cart.items, syncItems]);
}
