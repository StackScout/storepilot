"use client";

import { use, useEffect, useState } from "react";
import Link from "next/link";
import { toast } from "sonner";
import { CalendarX, CheckCircle2, Clock, Loader2, MessageCircle, Upload, XCircle } from "lucide-react";
import { Button } from "@/components/ui/button";
import { Card, CardContent } from "@/components/ui/card";
import { Input } from "@/components/ui/input";
import { Separator } from "@/components/ui/separator";
import { CancelBookingButton } from "@/components/marketplace/cancel-booking-button";
import { EmptyState } from "@/components/shared/empty-state";
import { BookingStatusBadge } from "@/components/shared/booking-status-badge";
import { StatusTimeline } from "@/components/shared/status-timeline";
import { formatCurrency } from "@/lib/currency";
import { formatDateTime, bookingPaymentMethodLabel } from "@/lib/format";
import { cn } from "@/lib/utils";
import { usePlatformConfig } from "@/hooks/use-platform-config";
import { useLiveStatus } from "@/hooks/use-live-status";
import { bookingsService, storesService } from "@/services";
import type { Booking, Store, StorePublicSettings } from "@/types";

export default function BookingConfirmationPage({
  params,
}: {
  params: Promise<{ bookingId: string }>;
}) {
  const { bookingId } = use(params);
  const { currencyCode, currencySymbol, currencyLocale } = usePlatformConfig();
  const currency = { code: currencyCode, symbol: currencySymbol, locale: currencyLocale };
  const [booking, setBooking] = useState<Booking | null | undefined>(undefined);
  const [store, setStore] = useState<Store | null>(null);
  const [storeSettings, setStoreSettings] = useState<StorePublicSettings | null>(null);
  const [seriesBookings, setSeriesBookings] = useState<Booking[]>([]);
  const [receiptFile, setReceiptFile] = useState<File | null>(null);
  const [isUploadingReceipt, setIsUploadingReceipt] = useState(false);

  useEffect(() => {
    let cancelled = false;
    bookingsService.getBookingById(bookingId).then(async (found) => {
      if (cancelled) return;
      setBooking(found);
      if (found) {
        const [s, settings, series] = await Promise.all([
          storesService.getStoreById(found.storeId),
          found.paymentMethod === "bank-transfer" ? storesService.getPublicStoreSettings(found.storeId) : null,
          found.recurrenceGroupId ? bookingsService.listBookingsByRecurrenceGroup(found.recurrenceGroupId) : [],
        ]);
        if (!cancelled) {
          setStore(s);
          setStoreSettings(settings);
          setSeriesBookings(series);
        }
      }
    });
    return () => {
      cancelled = true;
    };
  }, [bookingId]);

  // Live-updates the page without a manual refresh once the seller acts — see BookingController.subscribeToEvents.
  useLiveStatus(booking ? `/api/bookings/${bookingId}/events` : null, bookingsService.normalizeBooking, setBooking);

  async function handleUploadReceipt() {
    if (!receiptFile || !booking) return;
    setIsUploadingReceipt(true);
    try {
      const updated = await bookingsService.uploadBookingReceipt(booking.id, receiptFile);
      setBooking(updated);
      setReceiptFile(null);
      toast.success("Receipt uploaded — the store will verify it shortly.");
    } catch {
      toast.error("Couldn't upload your receipt. Please try again.");
    } finally {
      setIsUploadingReceipt(false);
    }
  }

  if (booking === undefined) {
    return (
      <div className="flex justify-center py-24">
        <Loader2 className="text-muted-foreground size-6 animate-spin" />
      </div>
    );
  }

  if (booking === null) {
    return (
      <div className="mx-auto max-w-2xl px-4 py-16 sm:px-6 lg:px-8">
        <EmptyState
          icon={CalendarX}
          title="Booking not found"
          description="This booking may have been made in a different browser session."
          action={<Button render={<Link href="/" />}>Back to home</Button>}
        />
      </div>
    );
  }

  const whatsappHref = store ? `https://wa.me/${store.whatsappNumber.replace(/[^0-9]/g, "")}` : "#";
  const isCancelled = booking.status === "cancelled" || booking.status === "no-show";
  const isPaymentPending =
    booking.paymentMethod === "bank-transfer" && booking.paymentStatus === "unpaid" && !isCancelled;
  const isReceiptMissing = isPaymentPending && !booking.receiptUrl;
  const isCancellable = booking.status === "pending" || booking.status === "confirmed";

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
          {isCancelled ? "Booking cancelled" : isPaymentPending ? "Payment pending" : "Booking requested!"}
        </h1>
        <p className="text-muted-foreground text-sm">
          Booking <span className="font-medium">{booking.bookingNumber}</span> at {booking.serviceName}
        </p>
      </div>

      <Card className="mt-8">
        <CardContent className="space-y-5">
          <div className="flex items-center justify-between">
            <h2 className="font-semibold">Booking status</h2>
            <BookingStatusBadge status={booking.status} />
          </div>

          <StatusTimeline entries={booking.timeline} />

          <Separator />

          <div className="space-y-1">
            <h2 className="font-semibold">Appointment</h2>
            <p className="text-sm">{booking.serviceName}</p>
            <p className="text-muted-foreground text-sm">
              {formatDateTime(booking.scheduledStart)} – {formatDateTime(booking.scheduledEnd)}
            </p>
          </div>

          {seriesBookings.length > 1 ? (
            <>
              <Separator />
              <div className="space-y-2">
                <h2 className="font-semibold">Weekly series ({seriesBookings.length} sessions)</h2>
                <div className="divide-y">
                  {seriesBookings.map((session) => (
                    <Link
                      key={session.id}
                      href={`/bookings/${session.id}`}
                      className={cn(
                        "hover:bg-accent/50 -mx-1 flex items-center justify-between gap-3 rounded-md px-1 py-2 text-sm",
                        session.id === booking.id && "bg-accent/50 font-medium",
                      )}
                    >
                      <span>{formatDateTime(session.scheduledStart)}</span>
                      <BookingStatusBadge status={session.status} />
                    </Link>
                  ))}
                </div>
              </div>
            </>
          ) : null}

          <Separator />

          <div className="space-y-2 text-sm">
            {booking.discountAmount > 0 ? (
              <div className="text-success-foreground flex justify-between">
                <span>Discount{booking.couponCode ? ` (${booking.couponCode})` : ""}</span>
                <span>-{formatCurrency(booking.discountAmount, currency)}</span>
              </div>
            ) : null}
            <div className="flex justify-between text-base font-semibold">
              <span>Total</span>
              <span>{formatCurrency(booking.total, currency)}</span>
            </div>
            <p className="text-muted-foreground pt-1 text-xs">
              Paying by {bookingPaymentMethodLabel(booking.paymentMethod)}
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
                    <p className="font-medium">Transfer {formatCurrency(booking.total, currency)} to:</p>
                    <p>{storeSettings.bankName}</p>
                    <p>{storeSettings.bankAccountName}</p>
                    <p className="font-mono">{storeSettings.bankAccountNumber}</p>
                  </div>
                ) : null}
                {booking.receiptUrl ? (
                  <p className="text-muted-foreground text-sm">
                    Receipt uploaded — awaiting the store&apos;s verification.
                  </p>
                ) : (
                  <div className="space-y-2">
                    <p className="text-muted-foreground text-sm">
                      Upload a photo or PDF of your payment receipt so the store can verify it.
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
                      <CancelBookingButton bookingId={booking.id} onCancelled={setBooking} />
                    </div>
                  </div>
                )}
              </div>
            </>
          ) : null}

          <Separator />

          <div>
            <h2 className="mb-2 font-semibold">Your details</h2>
            <p className="text-sm">{booking.buyerName}</p>
            <p className="text-muted-foreground text-sm">{booking.buyerPhone}</p>
            <p className="text-muted-foreground text-sm">{booking.buyerEmail}</p>
          </div>

          {isCancellable && !isPaymentPending ? (
            <>
              <Separator />
              <CancelBookingButton bookingId={booking.id} onCancelled={setBooking} />
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
          <MessageCircle className="size-4" /> Message store
        </Button>
        <Button render={<Link href="/search" />} className="flex-1">
          Continue browsing
        </Button>
      </div>
    </div>
  );
}
