"use client";

import { useState } from "react";
import Image from "next/image";
import Link from "next/link";
import { ShoppingCart, Trash2 } from "lucide-react";
import { Button, buttonVariants } from "@/components/ui/button";
import { Separator } from "@/components/ui/separator";
import {
  Sheet,
  SheetContent,
  SheetHeader,
  SheetTitle,
  SheetFooter,
  SheetTrigger,
  SheetClose,
} from "@/components/ui/sheet";
import { Badge } from "@/components/ui/badge";
import { QuantityStepper } from "@/components/marketplace/quantity-stepper";
import { PriceDisplay } from "@/components/shared/price-display";
import { EmptyState } from "@/components/shared/empty-state";
import { useCart } from "@/hooks/use-cart";
import { useCartReconciliation } from "@/hooks/use-cart-reconciliation";
import { cn } from "@/lib/utils";
import { formatCurrency } from "@/lib/currency";
import { usePlatformConfig } from "@/hooks/use-platform-config";

export function CartDrawer() {
  const [open, setOpen] = useState(false);
  const { cart, itemCount, subtotal, updateQuantity, removeItem } = useCart();
  useCartReconciliation();
  const { currencyCode, currencySymbol, currencyLocale } = usePlatformConfig();
  const currency = { code: currencyCode, symbol: currencySymbol, locale: currencyLocale };

  return (
    <Sheet open={open} onOpenChange={setOpen}>
      <SheetTrigger
        render={<Button variant="outline" size="icon" className="relative" aria-label="Open cart" />}
      >
        <ShoppingCart className="size-4.5" />
        {itemCount > 0 ? (
          <span className="bg-primary text-primary-foreground absolute -top-1.5 -right-1.5 flex size-4.5 items-center justify-center rounded-full text-[10px] font-semibold">
            {itemCount > 9 ? "9+" : itemCount}
          </span>
        ) : null}
      </SheetTrigger>
      <SheetContent className="flex w-full flex-col sm:max-w-sm">
        <SheetHeader>
          <SheetTitle>
            Your cart {cart.storeName ? `· ${cart.storeName}` : ""}
          </SheetTitle>
        </SheetHeader>

        {cart.items.length === 0 ? (
          <div className="px-4">
            <EmptyState
              icon={ShoppingCart}
              title="Your cart is empty"
              description="Browse stores and add products to get started."
            />
          </div>
        ) : (
          <>
            <div className="flex-1 space-y-4 overflow-y-auto px-4">
              {cart.items.map((item) => (
                <div
                  key={item.productId}
                  className={cn("flex gap-3", item.isUnavailable && "opacity-50")}
                >
                  {item.isUnavailable ? (
                    <div className="bg-muted relative size-16 shrink-0 overflow-hidden rounded-md">
                      <Image
                        src={item.productImageUrl}
                        alt={item.productName}
                        fill
                        sizes="64px"
                        className="object-cover grayscale"
                      />
                    </div>
                  ) : (
                    <Link
                      href={`/stores/${cart.storeSlug}/products/${item.productSlug}`}
                      className="bg-muted relative size-16 shrink-0 overflow-hidden rounded-md"
                    >
                      <Image
                        src={item.productImageUrl}
                        alt={item.productName}
                        fill
                        sizes="64px"
                        className="object-cover"
                      />
                    </Link>
                  )}
                  <div className="min-w-0 flex-1 space-y-1.5">
                    {item.isUnavailable ? (
                      <p className="line-clamp-2 text-sm leading-snug font-medium">
                        {item.productName}
                      </p>
                    ) : (
                      <Link
                        href={`/stores/${cart.storeSlug}/products/${item.productSlug}`}
                        className="line-clamp-2 block text-sm leading-snug font-medium hover:underline"
                      >
                        {item.productName}
                      </Link>
                    )}
                    {item.isUnavailable ? (
                      <Badge variant="destructive">No longer available</Badge>
                    ) : (
                      <PriceDisplay price={item.unitPrice} size="sm" />
                    )}
                    <div className="flex items-center justify-between">
                      {item.isUnavailable ? (
                        <span className="text-muted-foreground text-xs">Qty {item.quantity}</span>
                      ) : (
                        <QuantityStepper
                          size="sm"
                          quantity={item.quantity}
                          max={item.trackStock ? item.stockQuantity : undefined}
                          onChange={(q) => updateQuantity(item.productId, q)}
                        />
                      )}
                      <Button
                        variant="ghost"
                        size="icon"
                        className="text-muted-foreground size-7"
                        onClick={() => removeItem(item.productId)}
                        aria-label={`Remove ${item.productName}`}
                      >
                        <Trash2 className="size-3.5" />
                      </Button>
                    </div>
                  </div>
                </div>
              ))}
            </div>

            <SheetFooter className="gap-3 border-t pt-4">
              <div className="flex items-center justify-between text-sm">
                <span className="text-muted-foreground">Subtotal</span>
                <span className="font-semibold">{formatCurrency(subtotal, currency)}</span>
              </div>
              <Separator />
              <SheetClose
                render={<Link href="/checkout" className={buttonVariants({ size: "lg", className: "w-full" })} />}
              >
                Checkout
              </SheetClose>
              <SheetClose
                render={
                  <Link
                    href="/cart"
                    className={buttonVariants({ variant: "outline", size: "lg", className: "w-full" })}
                  />
                }
              >
                View cart
              </SheetClose>
            </SheetFooter>
          </>
        )}
      </SheetContent>
    </Sheet>
  );
}
