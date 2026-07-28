"use client";

import Image from "next/image";
import Link from "next/link";
import { ShoppingCart, Trash2, ArrowRight, ArrowLeft, TriangleAlert } from "lucide-react";
import { Button } from "@/components/ui/button";
import { Badge } from "@/components/ui/badge";
import { Separator } from "@/components/ui/separator";
import { Card, CardContent } from "@/components/ui/card";
import { QuantityStepper } from "@/components/marketplace/quantity-stepper";
import { PriceDisplay } from "@/components/shared/price-display";
import { EmptyState } from "@/components/shared/empty-state";
import { useCart } from "@/hooks/use-cart";
import { useCartReconciliation } from "@/hooks/use-cart-reconciliation";
import { cn } from "@/lib/utils";
import { formatCurrency } from "@/lib/currency";
import { usePlatformConfig } from "@/hooks/use-platform-config";

export default function CartPage() {
  const { cart, subtotal, isHydrated, updateQuantity, removeItem } = useCart();
  useCartReconciliation();
  const hasUnavailable = cart.items.some((i) => i.isUnavailable);
  const { currencyCode, currencySymbol, currencyLocale, flatShippingFee } = usePlatformConfig();
  const currency = { code: currencyCode, symbol: currencySymbol, locale: currencyLocale };

  if (isHydrated && cart.items.length === 0) {
    return (
      <div className="mx-auto max-w-3xl px-4 py-16 sm:px-6 lg:px-8">
        <EmptyState
          icon={ShoppingCart}
          title="Your cart is empty"
          description="Add products from a store to see them here."
          action={
            <Button render={<Link href="/search" />}>Browse products</Button>
          }
        />
      </div>
    );
  }

  return (
    <div className="mx-auto max-w-5xl px-4 py-8 sm:px-6 lg:px-8">
      <h1 className="text-2xl font-bold">Your cart</h1>
      {cart.storeName ? (
        <p className="text-muted-foreground mt-1 text-sm">Items from {cart.storeName}</p>
      ) : null}

      <div className="mt-6 grid gap-8 lg:grid-cols-3">
        <div className="divide-y rounded-lg border lg:col-span-2">
          {hasUnavailable ? (
            <div className="text-muted-foreground flex items-start gap-2 p-4 text-sm">
              <TriangleAlert className="mt-0.5 size-4 shrink-0 text-amber-600" />
              <span>
                Some items are no longer available and won&apos;t be included in your order.
                Remove them to continue.
              </span>
            </div>
          ) : null}
          {cart.items.map((item) => (
            <div
              key={item.productId}
              className={cn("flex gap-4 p-4", item.isUnavailable && "opacity-50")}
            >
              <div className="bg-muted relative size-20 shrink-0 overflow-hidden rounded-md sm:size-24">
                <Image
                  src={item.productImageUrl}
                  alt={item.productName}
                  fill
                  sizes="96px"
                  className={cn("object-cover", item.isUnavailable && "grayscale")}
                />
              </div>
              <div className="flex min-w-0 flex-1 flex-col justify-between">
                <div>
                  <p className="line-clamp-2 text-sm font-medium sm:text-base">
                    {item.productName}
                  </p>
                  {item.isUnavailable ? (
                    <Badge variant="destructive" className="mt-1.5">
                      No longer available
                    </Badge>
                  ) : (
                    <PriceDisplay price={item.unitPrice} size="sm" className="mt-1" />
                  )}
                </div>
                <div className="flex items-center justify-between pt-2">
                  {item.isUnavailable ? (
                    <span className="text-muted-foreground text-xs">Qty {item.quantity}</span>
                  ) : (
                    <QuantityStepper
                      quantity={item.quantity}
                      max={item.trackStock ? item.stockQuantity : undefined}
                      onChange={(q) => updateQuantity(item.productId, q)}
                    />
                  )}
                  <Button
                    variant="ghost"
                    size="sm"
                    className="text-muted-foreground"
                    onClick={() => removeItem(item.productId)}
                  >
                    <Trash2 className="size-3.5" /> Remove
                  </Button>
                </div>
              </div>
            </div>
          ))}
        </div>

        <Card className="h-fit">
          <CardContent className="space-y-4">
            <h2 className="font-semibold">Order summary</h2>
            <div className="space-y-2 text-sm">
              <div className="flex justify-between">
                <span className="text-muted-foreground">Subtotal</span>
                <span>{formatCurrency(subtotal, currency)}</span>
              </div>
              <div className="flex justify-between">
                <span className="text-muted-foreground">Shipping (estimate)</span>
                <span>{formatCurrency(flatShippingFee, currency)}</span>
              </div>
              <Separator />
              <div className="flex justify-between text-base font-semibold">
                <span>Total</span>
                <span>{formatCurrency(subtotal + flatShippingFee, currency)}</span>
              </div>
            </div>
            <Button render={<Link href="/checkout" />} size="lg" className="w-full">
              Proceed to checkout <ArrowRight className="size-4" />
            </Button>
            <Button render={<Link href="/search" />} variant="ghost" size="sm" className="w-full">
              <ArrowLeft className="size-3.5" /> Continue shopping
            </Button>
          </CardContent>
        </Card>
      </div>
    </div>
  );
}
