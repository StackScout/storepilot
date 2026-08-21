"use client";

import { use, useEffect, useState } from "react";
import Image from "next/image";
import Link from "next/link";
import { toast } from "sonner";
import { CheckCircle2, Clock, FileText, Loader2, MessageCircle, PackageX, RotateCcw, Truck, Upload, XCircle } from "lucide-react";
import { Button } from "@/components/ui/button";
import { Card, CardContent } from "@/components/ui/card";
import { Input } from "@/components/ui/input";
import { Label } from "@/components/ui/label";
import { Select, SelectContent, SelectItem, SelectTrigger, SelectValue } from "@/components/ui/select";
import { Separator } from "@/components/ui/separator";
import { Textarea } from "@/components/ui/textarea";
import { CancelOrderButton } from "@/components/marketplace/cancel-order-button";
import { EmptyState } from "@/components/shared/empty-state";
import { OrderStatusBadge } from "@/components/shared/order-status-badge";
import { PriceDisplay } from "@/components/shared/price-display";
import { StatusTimeline } from "@/components/shared/status-timeline";
import { formatCurrency } from "@/lib/currency";
import { paymentMethodLabel, returnReasonLabel, returnStatusLabel } from "@/lib/format";
import { cn } from "@/lib/utils";
import { PENDING_GATEWAY_ORDER_KEY } from "@/lib/constants";
import { usePlatformConfig } from "@/hooks/use-platform-config";
import { useCart } from "@/hooks/use-cart";
import { useLiveStatus } from "@/hooks/use-live-status";
import { ordersService, returnsService, storesService } from "@/services";
import type { Order, ReturnReasonCategory, ReturnRequest, Store, StorePublicSettings } from "@/types";

const RETURN_REASON_OPTIONS: ReturnReasonCategory[] = [
  "defective",
  "wrong-item",
  "not-as-described",
  "changed-mind",
  "other",
];

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
  const [storeSettings, setStoreSettings] = useState<StorePublicSettings | null>(null);
  const [receiptFile, setReceiptFile] = useState<File | null>(null);
  const [isUploadingReceipt, setIsUploadingReceipt] = useState(false);
  const [returns, setReturns] = useState<ReturnRequest[]>([]);
  const [returnReason, setReturnReason] = useState<ReturnReasonCategory>("defective");
  const [returnNote, setReturnNote] = useState("");
  const [isSubmittingReturn, setIsSubmittingReturn] = useState(false);

  useEffect(() => {
    let cancelled = false;
    ordersService.getOrderById(orderId).then(async (found) => {
      if (cancelled) return;
      setOrder(found);
      if (found) {
        const [s, settings, returnRequests] = await Promise.all([
          storesService.getStoreById(found.storeId),
          found.paymentMethod === "bank-transfer" ? storesService.getPublicStoreSettings(found.storeId) : null,
          found.status === "delivered" ? returnsService.listReturnsForOrder(found.id) : Promise.resolve([]),
        ]);
        if (!cancelled) {
          setStore(s);
          setStoreSettings(settings);
          setReturns(returnRequests);
        }
      }
    });
    return () => {
      cancelled = true;
    };
  }, [orderId]);

  // Live-updates the page (status, timeline, receipt) without a manual refresh once the seller acts — see OrderController.subscribeToEvents.
  useLiveStatus(order ? `/api/orders/${orderId}/events` : null, ordersService.normalizeOrder, setOrder);

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

  async function handleRequestReturn() {
    if (!order) return;
    setIsSubmittingReturn(true);
    try {
      const created = await returnsService.createReturnRequest(order.id, {
        reasonCategory: returnReason,
        reasonNote: returnNote.trim() || undefined,
      });
      setReturns((prev) => [created, ...prev]);
      setReturnNote("");
      toast.success("Return requested — the seller will review it shortly.");
    } catch (err) {
      toast.error(err instanceof Error ? err.message : "Couldn't request a return. Please try again.");
    } finally {
      setIsSubmittingReturn(false);
    }
  }

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
            isCancelled && "bg-danger",
            !isCancelled && isPaymentPending && "bg-warning",
            !isCancelled && !isPaymentPending && "bg-success",
          )}
        >
          {isCancelled ? (
            <XCircle className="text-danger-foreground size-6" />
          ) : isPaymentPending ? (
            <Clock className="text-warning-foreground size-6" />
          ) : (
            <CheckCircle2 className="text-success-foreground size-6" />
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
          {order.sellerAbn && order.gstAmount != null ? (
            <div className="bg-muted/50 space-y-0.5 rounded-lg p-3.5 text-sm">
              <p className="font-semibold">Tax Invoice</p>
              <p className="text-muted-foreground text-xs">
                {order.storeName} — ABN {order.sellerAbn}
              </p>
            </div>
          ) : null}

          <div className="flex items-center justify-between">
            <h2 className="font-semibold">Order status</h2>
            <OrderStatusBadge status={order.status} />
          </div>

          <StatusTimeline entries={order.timeline} />

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
            {order.discountAmount > 0 ? (
              <div className="text-success-foreground flex justify-between">
                <span>Discount{order.couponCode ? ` (${order.couponCode})` : ""}</span>
                <span>-{formatCurrency(order.discountAmount, currency)}</span>
              </div>
            ) : null}
            <div className="flex justify-between">
              <span className="text-muted-foreground">{order.deliveryMethod === "pickup" ? "Pickup" : "Shipping"}</span>
              <span>
                {order.deliveryMethod === "pickup" ? "Free" : formatCurrency(order.shippingFee, currency)}
              </span>
            </div>
            <div className="flex justify-between text-base font-semibold">
              <span>Total</span>
              <span>{formatCurrency(order.total, currency)}</span>
            </div>
            {order.gstAmount != null ? (
              <div className="text-muted-foreground flex justify-between text-xs">
                <span>Includes GST</span>
                <span>{formatCurrency(order.gstAmount, currency)}</span>
              </div>
            ) : null}
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
                  isReceiptMissing && "border-warning bg-warning/60 border",
                )}
              >
                <div className="flex items-center justify-between">
                  <h2 className="font-semibold">Bank transfer</h2>
                  {isReceiptMissing ? (
                    <span className="bg-warning text-warning-foreground rounded-full px-2 py-0.5 text-xs font-medium">
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
            <h2 className="mb-2 font-semibold">
              {order.deliveryMethod === "pickup" ? "Pickup" : "Delivering to"}
            </h2>
            <p className="text-sm">{order.shipping.fullName}</p>
            {order.deliveryMethod === "pickup" ? (
              <p className="text-muted-foreground text-sm">
                Collect from {store?.name ?? "the seller"}
                {store ? ` — ${store.address.city}, ${store.address.state}` : ""}. Message the
                seller to arrange a time.
              </p>
            ) : (
              <p className="text-muted-foreground text-sm">
                {order.shipping.addressLine1}, {order.shipping.city}, {order.shipping.state}{" "}
                {order.shipping.postalCode}
              </p>
            )}
            <p className="text-muted-foreground text-sm">{order.shipping.phone}</p>
            <p className="text-muted-foreground text-sm">{order.buyerEmail}</p>
          </div>

          {order.status === "delivered" ? (
            <>
              <Separator />
              <div className="space-y-3">
                <h2 className="font-semibold">Return &amp; refund</h2>
                {returns.length > 0 && returns[0].status !== "rejected" ? (
                  <div className="bg-muted/50 space-y-1 rounded-lg border p-3.5 text-sm">
                    <div className="flex items-center justify-between">
                      <p className="font-medium">{returnReasonLabel(returns[0].reasonCategory)}</p>
                      <span className="text-muted-foreground text-xs">{returnStatusLabel(returns[0].status)}</span>
                    </div>
                    {returns[0].reasonNote ? (
                      <p className="text-muted-foreground">{returns[0].reasonNote}</p>
                    ) : null}
                    {returns[0].sellerDecisionNote ? (
                      <p className="text-muted-foreground">Seller note: {returns[0].sellerDecisionNote}</p>
                    ) : null}
                    {returns[0].status === "refunded" ? (
                      <p className="text-success-foreground">Your refund has been processed.</p>
                    ) : returns[0].status === "refund-pending" ? (
                      <p className="text-muted-foreground">Approved — your refund is being processed.</p>
                    ) : null}
                  </div>
                ) : (
                  <div className="space-y-2">
                    {returns.length > 0 ? (
                      <p className="text-muted-foreground text-sm">
                        Your previous return request was declined
                        {returns[0].sellerDecisionNote ? `: ${returns[0].sellerDecisionNote}` : "."} You can submit
                        a new one below.
                      </p>
                    ) : (
                      <p className="text-muted-foreground text-sm">
                        Not what you expected? Request a return within the store&apos;s return window.
                      </p>
                    )}
                    <div className="space-y-1.5">
                      <Label htmlFor="returnReason" className="text-xs font-normal">
                        Reason
                      </Label>
                      <Select value={returnReason} onValueChange={(v) => setReturnReason(v as ReturnReasonCategory)}>
                        <SelectTrigger id="returnReason" className="w-full">
                          <SelectValue>{(v: ReturnReasonCategory) => returnReasonLabel(v)}</SelectValue>
                        </SelectTrigger>
                        <SelectContent>
                          {RETURN_REASON_OPTIONS.map((reason) => (
                            <SelectItem key={reason} value={reason}>
                              {returnReasonLabel(reason)}
                            </SelectItem>
                          ))}
                        </SelectContent>
                      </Select>
                    </div>
                    <div className="space-y-1.5">
                      <Label htmlFor="returnNote" className="text-xs font-normal">
                        Details (optional)
                      </Label>
                      <Textarea
                        id="returnNote"
                        rows={2}
                        value={returnNote}
                        onChange={(e) => setReturnNote(e.target.value)}
                        placeholder="Tell the seller more about the issue"
                      />
                    </div>
                    <Button type="button" size="sm" disabled={isSubmittingReturn} onClick={handleRequestReturn}>
                      {isSubmittingReturn ? (
                        <Loader2 className="size-4 animate-spin" />
                      ) : (
                        <RotateCcw className="size-4" />
                      )}
                      Request return
                    </Button>
                  </div>
                )}
              </div>
            </>
          ) : null}
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
