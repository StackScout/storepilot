"use client";

import { use, useEffect, useState } from "react";
import Image from "next/image";
import Link from "next/link";
import { toast } from "sonner";
import { CheckCircle2, Circle, Clock, FileText, Loader2, MessageCircle, PackageX, Truck, Upload, XCircle } from "lucide-react";
import { Button } from "@/components/ui/button";
import { Card, CardContent } from "@/components/ui/card";
import { Input } from "@/components/ui/input";
import { Separator } from "@/components/ui/separator";
import { CancelOrderButton } from "@/components/marketplace/cancel-order-button";
import { EmptyState } from "@/components/shared/empty-state";
import { OrderStatusBadge } from "@/components/shared/order-status-badge";
import { PriceDisplay } from "@/components/shared/price-display";
import { formatCurrency } from "@/lib/currency";
import { formatDateTime, paymentMethodLabel } from "@/lib/format";
import { cn } from "@/lib/utils";
import { PENDING_GATEWAY_ORDER_KEY } from "@/lib/constants";
import { usePlatformConfig } from "@/hooks/use-platform-config";
import { useCart } from "@/hooks/use-cart";
import { ordersService, storesService } from "@/services";
import type { Order, Store, StoreSettings } from "@/types";

export default function OrderTrackingPage({
  params,
}: {
  params: Promise<{ orderId: string }>;
}) {
  const { orderId } = use(params);
  const { currencyCode, currencySymbol, currencyLocale } = usePlatformConfig();
  const currency = { code: currencyCode, symbol: currencySymbol, locale: currencyLocale };
  const { clearCart } = useCart();
  const [order, setOrder] = useState<Order | null | undefined>(undefined);
  const [store, setStore] = useState<Store | null>(null);
  const [storeSettings, setStoreSettings] = useState<StoreSettings | null>(null);
  const [receiptFile, setReceiptFile] = useState<File | null>(null);
  const [isUploadingReceipt, setIsUploadingReceipt] = useState(false);

  useEffect(() => {
    let cancelled = false;
    ordersService.getOrderById(orderId).then(async (found) => {
      if (cancelled) return;
      setOrder(found);
      if (found) {
        const [s, settings] = await Promise.all([
          storesService.getStoreById(found.storeId),
          found.paymentMethod === "bank-transfer" ? storesService.getStoreSettings(found.storeId) : null,
        ]);
        if (!cancelled) {
          setStore(s);
          setStoreSettings(settings);
        }
      }
    });
    return () => {
      cancelled = true;
    };
  }, [orderId]);

  // Checkout deliberately leaves the cart untouched when redirecting to a
  // payment gateway (PayHere/Stripe), so a declined/cancelled payment
  // doesn't strand the buyer with an empty cart — see checkout-form.tsx.
  // Only clear it here, once this exact order (recorded right before the
  // redirect) comes back paid — never for an arbitrary order-page visit,
  // which could otherwise wipe an unrelated cart from the same store.
  useEffect(() => {
    if (!order || order.paymentStatus !== "paid") return;
    if (sessionStorage.getItem(PENDING_GATEWAY_ORDER_KEY) !== order.id) return;
    sessionStorage.removeItem(PENDING_GATEWAY_ORDER_KEY);
    clearCart();
  }, [order, clearCart]);

  async function handleUploadReceipt() {
    if (!receiptFile || !order) return;
    setIsUploadingReceipt(true);
    try {
      const updated = await ordersService.uploadReceipt(order.id, receiptFile);
      setOrder(updated);
      setReceiptFile(null);
      toast.success("Receipt uploaded — the seller will verify it shortly.");
    } catch {
      toast.error("Couldn't upload your receipt. Please try again.");
    } finally {
      setIsUploadingReceipt(false);
    }
  }

  if (order === undefined) {
    return (
      <div className="flex justify-center py-24">
        <Loader2 className="text-muted-foreground size-6 animate-spin" />
      </div>
    );
  }

  if (order === null) {
    return (
      <div className="mx-auto max-w-2xl px-4 py-16 sm:px-6 lg:px-8">
        <EmptyState
          icon={PackageX}
          title="Order not found"
          description="This order may have been created in a different browser session, since orders are stored locally for this demo."
          action={
            <Button render={<Link href="/" />}>Back to home</Button>
          }
        />
      </div>
    );
  }

  const whatsappHref = store ? `https://wa.me/${store.whatsappNumber.replace(/[^0-9]/g, "")}` : "#";
  const isCancelled = order.status === "cancelled";
  const isPaymentPending =
    order.paymentMethod === "bank-transfer" && order.paymentStatus === "unpaid" && !isCancelled;
  const isReceiptMissing = isPaymentPending && !order.receiptUrl;

  return (
    <div className="mx-auto max-w-3xl px-4 py-8 sm:px-6 lg:px-8">
      <div className="space-y-1 text-center">
        <span
          className={cn(
            "mx-auto flex size-12 items-center justify-center rounded-full",
            isCancelled && "bg-red-100 dark:bg-red-950",
            !isCancelled && isPaymentPending && "bg-amber-100 dark:bg-amber-950",
            !isCancelled && !isPaymentPending && "bg-emerald-100 dark:bg-emerald-950",
          )}
        >
          {isCancelled ? (
            <XCircle className="size-6 text-red-600" />
          ) : isPaymentPending ? (
            <Clock className="size-6 text-amber-600" />
          ) : (
            <CheckCircle2 className="size-6 text-emerald-600" />
          )}
        </span>
        <h1 className="pt-2 text-2xl font-bold">
          {isCancelled ? "Order cancelled" : isPaymentPending ? "Payment pending" : "Order placed!"}
        </h1>
        <p className="text-muted-foreground text-sm">
          Order <span className="font-medium">{order.orderNumber}</span> from {order.storeName}
        </p>
        {isCancelled ? (
          <p className="text-muted-foreground text-xs">
            This order was cancelled before payment was completed.
          </p>
        ) : isPaymentPending ? (
          <p className="text-muted-foreground text-xs">
            Your order is reserved but not yet confirmed — upload your payment receipt below to
            complete it.
          </p>
        ) : (
          <p className="text-muted-foreground text-xs">
            A receipt has been sent to <span className="font-medium">{order.buyerEmail}</span>
          </p>
        )}
      </div>

      <Card className="mt-8">
        <CardContent className="space-y-5">
          <div className="flex items-center justify-between">
            <h2 className="font-semibold">Order status</h2>
            <OrderStatusBadge status={order.status} />
          </div>

          <ol className="space-y-4">
            {order.timeline.map((entry, i) => (
              <li key={i} className="flex gap-3">
                <span className="mt-0.5">
                  {i === order.timeline.length - 1 ? (
                    <CheckCircle2 className="text-primary size-4" />
                  ) : (
                    <Circle className="text-muted-foreground size-4" />
                  )}
                </span>
                <div>
                  <p className="text-sm font-medium">{entry.label}</p>
                  <p className="text-muted-foreground text-xs">{formatDateTime(entry.timestamp)}</p>
                  {entry.note ? <p className="text-muted-foreground text-xs">{entry.note}</p> : null}
                </div>
              </li>
            ))}
          </ol>

          {order.trackingNumber && order.courierServiceName ? (
            <>
              <Separator />
              <div className="bg-muted/50 space-y-1.5 rounded-lg p-4 text-sm">
                <p className="flex items-center gap-1.5 font-medium">
                  <Truck className="size-4" /> Shipped via {order.courierServiceName}
                </p>
                <p className="text-muted-foreground">
                  Tracking number: <span className="text-foreground font-medium">{order.trackingNumber}</span>
                </p>
                {order.courierReceiptUrl ? (
                  <a
                    href={order.courierReceiptUrl}
                    target="_blank"
                    rel="noopener noreferrer"
                    className="text-primary flex items-center gap-1.5 underline-offset-4 hover:underline"
                  >
                    <FileText className="size-3.5" /> View courier receipt
                  </a>
                ) : null}
              </div>
            </>
          ) : null}

          <Separator />

          <div>
            <h2 className="mb-3 font-semibold">Items</h2>
            <div className="space-y-3">
              {order.items.map((item) => (
                <div key={item.productId} className="flex items-center gap-3">
                  <div className="bg-muted relative size-14 shrink-0 overflow-hidden rounded-md">
                    <Image
                      src={item.productImageUrl}
                      alt={item.productName}
                      fill
                      sizes="56px"
                      className="object-cover"
                    />
                  </div>
                  <div className="min-w-0 flex-1">
                    <p className="line-clamp-1 text-sm">{item.productName}</p>
                    <p className="text-muted-foreground text-xs">Qty {item.quantity}</p>
                  </div>
                  <PriceDisplay price={item.unitPrice * item.quantity} size="sm" />
                </div>
              ))}
            </div>
          </div>

          <Separator />

          <div className="space-y-2 text-sm">
            <div className="flex justify-between">
              <span className="text-muted-foreground">Subtotal</span>
              <span>{formatCurrency(order.subtotal, currency)}</span>
            </div>
            <div className="flex justify-between">
              <span className="text-muted-foreground">Shipping</span>
              <span>{formatCurrency(order.shippingFee, currency)}</span>
            </div>
            <div className="flex justify-between text-base font-semibold">
              <span>Total</span>
              <span>{formatCurrency(order.total, currency)}</span>
            </div>
            <p className="text-muted-foreground pt-1 text-xs">
              Paying by {paymentMethodLabel(order.paymentMethod)}
            </p>
          </div>

          {isPaymentPending ? (
            <>
              <Separator />
              <div
                className={cn(
                  "space-y-3 rounded-lg p-3.5",
                  isReceiptMissing && "border border-amber-300/60 bg-amber-50 dark:bg-amber-950/30",
                )}
              >
                <div className="flex items-center justify-between">
                  <h2 className="font-semibold">Bank transfer</h2>
                  {isReceiptMissing ? (
                    <span className="rounded-full bg-amber-100 px-2 py-0.5 text-xs font-medium text-amber-800 dark:bg-amber-900 dark:text-amber-300">
                      Action required
                    </span>
                  ) : null}
                </div>
                {storeSettings ? (
                  <div className="bg-muted/50 space-y-1 rounded-lg border p-3.5 text-sm">
                    <p className="font-medium">Transfer {formatCurrency(order.total, currency)} to:</p>
                    <p>{storeSettings.bankName}</p>
                    <p>{storeSettings.bankAccountName}</p>
                    <p className="font-mono">{storeSettings.bankAccountNumber}</p>
                  </div>
                ) : null}
                {order.receiptUrl ? (
                  <p className="text-muted-foreground text-sm">
                    Receipt uploaded — awaiting the seller&apos;s verification. You&apos;ll see your
                    order confirmed here once they&apos;ve checked it.
                  </p>
                ) : (
                  <div className="space-y-2">
                    <p className="text-muted-foreground text-sm">
                      Upload a photo or PDF of your payment receipt so the seller can verify it.
                    </p>
                    <Input
                      type="file"
                      accept="image/jpeg,image/png,image/webp,application/pdf"
                      onChange={(e) => setReceiptFile(e.target.files?.[0] ?? null)}
                    />
                    <div className="flex flex-wrap items-center gap-2">
                      <Button
                        type="button"
                        size="sm"
                        disabled={!receiptFile || isUploadingReceipt}
                        onClick={handleUploadReceipt}
                      >
                        {isUploadingReceipt ? (
                          <Loader2 className="size-4 animate-spin" />
                        ) : (
                          <Upload className="size-4" />
                        )}
                        Upload receipt
                      </Button>
                      <CancelOrderButton orderId={order.id} onCancelled={setOrder} />
                    </div>
                  </div>
                )}
              </div>
            </>
          ) : null}

          <Separator />

          <div>
            <h2 className="mb-2 font-semibold">Delivering to</h2>
            <p className="text-sm">{order.shipping.fullName}</p>
            <p className="text-muted-foreground text-sm">
              {order.shipping.addressLine1}, {order.shipping.city}, {order.shipping.state}{" "}
              {order.shipping.postalCode}
            </p>
            <p className="text-muted-foreground text-sm">{order.shipping.phone}</p>
            <p className="text-muted-foreground text-sm">{order.buyerEmail}</p>
          </div>
        </CardContent>
      </Card>

      <div className="mt-6 flex flex-col gap-3 sm:flex-row">
        <Button
          render={<a href={whatsappHref} target="_blank" rel="noopener noreferrer" />}
          variant="outline"
          className="flex-1"
        >
          <MessageCircle className="size-4" /> Message seller
        </Button>
        <Button render={<Link href="/search" />} className="flex-1">
          Continue shopping
        </Button>
      </div>
    </div>
  );
}
