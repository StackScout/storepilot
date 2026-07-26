"use client";

import { useState } from "react";
import { useRouter } from "next/navigation";
import { toast } from "sonner";
import { ShoppingCart } from "lucide-react";
import { Button } from "@/components/ui/button";
import {
  Dialog,
  DialogContent,
  DialogDescription,
  DialogFooter,
  DialogHeader,
  DialogTitle,
} from "@/components/ui/dialog";
import { QuantityStepper } from "@/components/marketplace/quantity-stepper";
import { useCart } from "@/hooks/use-cart";
import type { Product } from "@/types";

export function AddToCartControls({ product }: { product: Product }) {
  const router = useRouter();
  const { cart, addItem, replaceCartWithItem } = useCart();
  const [quantity, setQuantity] = useState(1);
  const [conflictOpen, setConflictOpen] = useState(false);

  const isOutOfStock = product.status === "out-of-stock" || product.stockQuantity === 0;

  function handleAddToCart() {
    const added = addItem(product, quantity);
    if (!added) {
      setConflictOpen(true);
      return;
    }
    toast.success(`Added ${quantity} × ${product.name} to cart`);
  }

  function handleReplaceCart() {
    replaceCartWithItem(product, quantity);
    setConflictOpen(false);
    toast.success(`Started a new cart with ${product.name}`);
  }

  return (
    <div className="space-y-3">
      <div className="flex items-center gap-3">
        <QuantityStepper
          quantity={quantity}
          onChange={setQuantity}
          max={product.stockQuantity}
        />
        <Button
          className="flex-1"
          size="lg"
          disabled={isOutOfStock}
          onClick={handleAddToCart}
        >
          <ShoppingCart className="size-4" />
          {isOutOfStock ? "Out of stock" : "Add to cart"}
        </Button>
      </div>

      <Dialog open={conflictOpen} onOpenChange={setConflictOpen}>
        <DialogContent>
          <DialogHeader>
            <DialogTitle>Start a new cart?</DialogTitle>
            <DialogDescription>
              Your cart has items from <strong>{cart.storeName}</strong>. Each order can only
              contain products from one seller. Replace your cart to add items from{" "}
              <strong>{product.storeName}</strong> instead?
            </DialogDescription>
          </DialogHeader>
          <DialogFooter>
            <Button variant="outline" onClick={() => setConflictOpen(false)}>
              Cancel
            </Button>
            <Button
              onClick={() => {
                handleReplaceCart();
                router.refresh();
              }}
            >
              Replace cart
            </Button>
          </DialogFooter>
        </DialogContent>
      </Dialog>
    </div>
  );
}
