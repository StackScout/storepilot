"use client";

import { use } from "react";
import Link from "next/link";
import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import { toast } from "sonner";
import { ArrowLeft, CalendarX, Check, ExternalLink, Loader2, X } from "lucide-react";
import { Button, buttonVariants } from "@/components/ui/button";
import { Card, CardContent } from "@/components/ui/card";
import { Separator } from "@/components/ui/separator";
import { BookingStatusSelect } from "@/components/dashboard/booking-status-select";
import { EmptyState } from "@/components/shared/empty-state";
import { toApiUrl } from "@/lib/api-client";
import { formatCurrency } from "@/lib/currency";
import { formatDateTime, bookingPaymentMethodLabel } from "@/lib/format";
import { usePlatformConfig } from "@/hooks/use-platform-config";
import { bookingsService } from "@/services";

export default function DashboardBookingDetailPage({
  params,
}: {
  params: Promise<{ bookingId: string }>;
}) {
  const { bookingId } = use(params);
  const queryClient = useQueryClient();
  const { currencyCode, currencySymbol, currencyLocale } = usePlatformConfig();
  const currency = { code: currencyCode, symbol: currencySymbol, locale: currencyLocale };

  const { data: booking, isLoading } = useQuery({
    queryKey: ["booking", bookingId],
    queryFn: () => bookingsService.getBookingById(bookingId),
  });

  const verifyMutation = useMutation({
    mutationFn: (approved: boolean) => bookingsService.verifyBookingBankTransfer(bookingId, approved),
    onSuccess: (_, approved) => {
      queryClient.invalidateQueries({ queryKey: ["booking", bookingId] });
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

  if (!booking) {
    return <EmptyState icon={CalendarX} title="Booking not found" />;
  }

  const netPayout = booking.servicePrice - booking.platformFee;

  return (
    <div className="max-w-4xl space-y-6">
      <Link href="/dashboard/bookings" className={buttonVariants({ variant: "ghost", size: "sm" })}>
        <ArrowLeft className="size-3.5" /> Back to bookings
      </Link>

      <div className="flex flex-col gap-3 sm:flex-row sm:items-center sm:justify-between">
        <div>
          <h1 className="text-2xl font-bold">{booking.bookingNumber}</h1>
          <p className="text-muted-foreground text-sm">Requested {formatDateTime(booking.createdAt)}</p>
        </div>
        <BookingStatusSelect booking={booking} />
      </div>

      <div className="grid gap-6 lg:grid-cols-3">
        <Card className="lg:col-span-2">
          <CardContent className="space-y-4">
            <h2 className="font-semibold">Appointment</h2>
            <div className="space-y-1 text-sm">
              <p className="font-medium">{booking.serviceName}</p>
              <p className="text-muted-foreground">
                {formatDateTime(booking.scheduledStart)} – {formatDateTime(booking.scheduledEnd)}
              </p>
            </div>

            <Separator />

            <div className="space-y-2 text-sm">
              <div className="flex justify-between">
                <span className="text-muted-foreground">Service price</span>
                <span>{formatCurrency(booking.servicePrice, currency)}</span>
              </div>
              <div className="text-danger-foreground flex justify-between">
                <span>Platform fee</span>
                <span>-{formatCurrency(booking.platformFee, currency)}</span>
              </div>
              <Separator />
              <div className="flex justify-between text-base font-semibold">
                <span>Your payout</span>
                <span>{formatCurrency(netPayout, currency)}</span>
              </div>
            </div>

            {booking.cancellationReason ? (
              <>
                <Separator />
                <div className="space-y-1">
                  <h3 className="text-sm font-semibold">Cancellation reason</h3>
                  <p className="text-muted-foreground text-sm">{booking.cancellationReason}</p>
                </div>
              </>
            ) : null}

            <Separator />

            <div className="space-y-3">
              <h3 className="text-sm font-semibold">Timeline</h3>
              <ol className="space-y-2">
                {booking.timeline.map((entry, i) => (
                  <li key={i} className="text-sm">
                    <span className="font-medium">{entry.label}</span>{" "}
                    <span className="text-muted-foreground">{formatDateTime(entry.timestamp)}</span>
                    {entry.note ? <p className="text-muted-foreground text-xs">{entry.note}</p> : null}
                  </li>
                ))}
              </ol>
            </div>
          </CardContent>
        </Card>

        <div className="space-y-6">
          <Card>
            <CardContent className="space-y-2">
              <h2 className="font-semibold">Customer</h2>
              <p className="text-sm">{booking.buyerName}</p>
              <p className="text-muted-foreground text-sm">{booking.buyerPhone}</p>
              <p className="text-muted-foreground text-sm">{booking.buyerEmail}</p>
            </CardContent>
          </Card>

          <Card>
            <CardContent className="space-y-2">
              <h2 className="font-semibold">Payment</h2>
              <p className="text-sm">{bookingPaymentMethodLabel(booking.paymentMethod)}</p>
              <p className="text-muted-foreground text-sm capitalize">Status: {booking.paymentStatus}</p>
            </CardContent>
          </Card>

          {booking.paymentMethod === "bank-transfer" ? (
            <Card>
              <CardContent className="space-y-3">
                <h2 className="font-semibold">Payment receipt</h2>
                {!booking.receiptUrl ? (
                  <p className="text-muted-foreground text-sm">
                    Waiting for the buyer to upload their transfer receipt.
                  </p>
                ) : (
                  <>
                    <Button
                      render={
                        <a href={toApiUrl(booking.receiptUrl)} target="_blank" rel="noopener noreferrer" />
                      }
                      variant="outline"
                      size="sm"
                      className="w-full"
                    >
                      <ExternalLink className="size-3.5" /> View receipt
                    </Button>
                    {booking.paymentStatus === "unpaid" ? (
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
                        {booking.paymentStatus === "paid" ? "Payment confirmed." : null}
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
