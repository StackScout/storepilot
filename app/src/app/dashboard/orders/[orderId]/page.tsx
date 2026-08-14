"use client";

import { use } from "react";
import Image from "next/image";
import Link from "next/link";
import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import { toast } from "sonner";
import { ArrowLeft, Check, ExternalLink, Loader2, PackageX, X } from "lucide-react";
import { Button, buttonVariants } from "@/components/ui/button";
import { Card, CardContent } from "@/components/ui/card";
import { Separator } from "@/components/ui/separator";
import { OrderStatusSelect } from "@/components/dashboard/order-status-select";
import { EmptyState } from "@/components/shared/empty-state";
import { PriceDisplay } from "@/components/shared/price-display";
import { toApiUrl } from "@/lib/api-client";
import { formatCurrency } from "@/lib/currency";
import { formatDateTime, paymentMethodLabel } from "@/lib/format";
import { usePlatformConfig } from "@/hooks/use-platform-config";
import { ordersService } from "@/services";

export default function DashboardOrderDetailPage({
  params,
}: {
  params: Promise<{ orderId: string }>;
}) {
  const { orderId } = use(params);
  const queryClient = useQueryClient();
  const { currencyCode, currencySymbol, currencyLocale } = usePlatformConfig();
  const currency = { code: currencyCode, symbol: currencySymbol, locale: currencyLocale };

  const { data: order, isLoading } = useQuery({
    queryKey: ["order", orderId],
    queryFn: () => ordersService.getOrderById(orderId),
  });

  const verifyMutation = useMutation({
    mutationFn: (approved: boolean) => ordersService.verifyBankTransfer(orderId, approved),
    onSuccess: (_, approved) => {
      queryClient.invalidateQueries({ queryKey: ["order", orderId] });
      toast.success(approved ? "Payment confirmed" : "Receipt rejected");
    },
    onError: () => toast.error("Couldn't update the payment. Please try again."),
  });

  if (isLoading) {
    return (
      <div className="flex justify-center py-24">
        <Loader2 className="text-muted-foreground size-6 animate-spin" />
      </div>
    );
  }

  if (!order) {
    return <EmptyState icon={PackageX} title="Order not found" />;
  }

  const netPayout = order.subtotal - order.platformFee;

  return (
    <div className="max-w-4xl space-y-6">
      <Link href="/dashboard/orders" className={buttonVariants({ variant: "ghost", size: "sm" })}>
        <ArrowLeft className="size-3.5" /> Back to orders
      </Link>

      <div className="flex flex-col gap-3 sm:flex-row sm:items-center sm:justify-between">
        <div>
          <h1 className="text-2xl font-bold">{order.orderNumber}</h1>
          <p className="text-muted-foreground text-sm">
            Placed {formatDateTime(order.createdAt)}
          </p>
        </div>
        <OrderStatusSelect order={order} />
      </div>

      <div className="grid gap-6 lg:grid-cols-3">
        <Card className="lg:col-span-2">
          <CardContent className="space-y-4">
            <h2 className="font-semibold">Items</h2>
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
                    <p className="line-clamp-1 text-sm font-medium">{item.productName}</p>
                    <p className="text-muted-foreground text-xs">
                      Qty {item.quantity} × {formatCurrency(item.unitPrice, currency)}
                    </p>
                  </div>
                  <PriceDisplay price={item.unitPrice * item.quantity} size="sm" />
                </div>
              ))}
            </div>

            <Separator />

            <div className="space-y-2 text-sm">
              <div className="flex justify-between">
                <span className="text-muted-foreground">Subtotal</span>
                <span>{formatCurrency(order.subtotal, currency)}</span>
              </div>
              <div className="flex justify-between">
                <span className="text-muted-foreground">Shipping (buyer paid)</span>
                <span>{formatCurrency(order.shippingFee, currency)}</span>
              </div>
              <div className="text-danger-foreground flex justify-between">
                <span>Platform fee (3.5%)</span>
                <span>-{formatCurrency(order.platformFee, currency)}</span>
              </div>
              <Separator />
              <div className="flex justify-between text-base font-semibold">
                <span>Your payout</span>
                <span>{formatCurrency(netPayout, currency)}</span>
              </div>
            </div>
          </CardContent>
        </Card>

        <div className="space-y-6">
          <Card>
            <CardContent className="space-y-2">
              <h2 className="font-semibold">Customer</h2>
              <p className="text-sm">{order.shipping.fullName}</p>
              <p className="text-muted-foreground text-sm">{order.shipping.phone}</p>
            </CardContent>
          </Card>

          <Card>
            <CardContent className="space-y-2">
              <h2 className="font-semibold">{order.deliveryMethod === "pickup" ? "Pickup" : "Delivery address"}</h2>
              {order.deliveryMethod === "pickup" ? (
                <p className="text-muted-foreground text-sm">
                  The buyer will collect this order in person — contact them on{" "}
                  {order.shipping.phone} to arrange a time.
                </p>
              ) : (
                <p className="text-muted-foreground text-sm">
                  {order.shipping.addressLine1}
                  <br />
                  {order.shipping.city}, {order.shipping.state}
                  <br />
                  {order.shipping.postalCode}
                </p>
              )}
            </CardContent>
          </Card>

          <Card>
            <CardContent className="space-y-2">
              <h2 className="font-semibold">Payment</h2>
              <p className="text-sm capitalize">{paymentMethodLabel(order.paymentMethod)}</p>
              <p className="text-muted-foreground text-sm capitalize">Status: {order.paymentStatus}</p>
            </CardContent>
          </Card>

          {order.paymentMethod === "bank-transfer" ? (
            <Card>
              <CardContent className="space-y-3">
                <h2 className="font-semibold">Payment receipt</h2>
                {!order.receiptUrl ? (
                  <p className="text-muted-foreground text-sm">
                    Waiting for the buyer to upload their transfer receipt.
                  </p>
                ) : (
                  <>
                    <Button
                      render={
                        <a href={toApiUrl(order.receiptUrl)} target="_blank" rel="noopener noreferrer" />
                      }
                      variant="outline"
                      size="sm"
                      className="w-full"
                    >
                      <ExternalLink className="size-3.5" /> View receipt
                    </Button>
                    {order.paymentStatus === "unpaid" ? (
                      <div className="flex gap-2">
                        <Button
                          type="button"
                          size="sm"
                          className="flex-1"
                          disabled={verifyMutation.isPending}
                          onClick={() => verifyMutation.mutate(true)}
                        >
                          <Check className="size-3.5" /> Confirm payment
                        </Button>
                        <Button
                          type="button"
                          variant="outline"
                          size="sm"
                          className="flex-1"
                          disabled={verifyMutation.isPending}
                          onClick={() => verifyMutation.mutate(false)}
                        >
                          <X className="size-3.5" /> Reject
                        </Button>
                      </div>
                    ) : (
                      <p className="text-muted-foreground text-sm">
                        {order.paymentStatus === "paid" ? "Payment confirmed." : null}
                      </p>
                    )}
                  </>
                )}
              </CardContent>
            </Card>
          ) : null}
        </div>
      </div>
    </div>
  );
}
