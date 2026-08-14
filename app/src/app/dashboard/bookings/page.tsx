"use client";

import { useState } from "react";
import Link from "next/link";
import { useQuery } from "@tanstack/react-query";
import { CalendarClock } from "lucide-react";
import { Card, CardContent } from "@/components/ui/card";
import { BookingStatusBadge } from "@/components/shared/booking-status-badge";
import { EmptyState } from "@/components/shared/empty-state";
import { TableRowSkeleton } from "@/components/shared/loading-skeletons";
import { formatCurrency } from "@/lib/currency";
import { formatDateTime, bookingPaymentMethodLabel } from "@/lib/format";
import { cn } from "@/lib/utils";
import { BOOKING_STATUS_LABELS } from "@/lib/constants";
import { useSellerStoreId } from "@/hooks/use-seller-store";
import { usePlatformConfig } from "@/hooks/use-platform-config";
import { bookingsService } from "@/services";
import type { BookingStatus } from "@/types";

const FILTERS: { label: string; value: BookingStatus | "all" }[] = [
  { label: "All", value: "all" },
  { label: BOOKING_STATUS_LABELS.pending, value: "pending" },
  { label: BOOKING_STATUS_LABELS.confirmed, value: "confirmed" },
  { label: BOOKING_STATUS_LABELS.completed, value: "completed" },
  { label: BOOKING_STATUS_LABELS.cancelled, value: "cancelled" },
  { label: BOOKING_STATUS_LABELS["no-show"], value: "no-show" },
];

export default function DashboardBookingsPage() {
  const storeId = useSellerStoreId();
  const { currencyCode, currencySymbol, currencyLocale } = usePlatformConfig();
  const currency = { code: currencyCode, symbol: currencySymbol, locale: currencyLocale };
  const [filter, setFilter] = useState<BookingStatus | "all">("all");

  const { data: bookings, isLoading } = useQuery({
    queryKey: ["bookings", storeId, filter],
    queryFn: () => bookingsService.listBookingsByStore(storeId, filter === "all" ? undefined : filter),
  });

  return (
    <div className="max-w-6xl space-y-6">
      <div>
        <h1 className="text-2xl font-bold">Bookings</h1>
        <p className="text-muted-foreground text-sm">Track and manage incoming appointments.</p>
      </div>

      <div className="flex flex-wrap gap-2">
        {FILTERS.map((f) => (
          <button
            key={f.value}
            onClick={() => setFilter(f.value)}
            className={cn(
              "rounded-full border px-3 py-1.5 text-sm font-medium transition-colors",
              filter === f.value ? "bg-primary text-primary-foreground border-primary" : "hover:bg-accent",
            )}
          >
            {f.label}
          </button>
        ))}
      </div>

      <Card>
        <CardContent>
          {isLoading ? (
            <div className="divide-y">
              <TableRowSkeleton columns={6} />
              <TableRowSkeleton columns={6} />
              <TableRowSkeleton columns={6} />
            </div>
          ) : !bookings || bookings.length === 0 ? (
            <EmptyState icon={CalendarClock} title="No bookings found" description="Try a different filter." />
          ) : (
            <div className="-mx-6 overflow-x-auto">
              <table className="w-full text-sm">
                <thead>
                  <tr className="text-muted-foreground border-y text-left text-xs">
                    <th className="px-6 py-2 font-medium">Booking</th>
                    <th className="px-6 py-2 font-medium">Customer</th>
                    <th className="px-6 py-2 font-medium">Service</th>
                    <th className="px-6 py-2 font-medium">When</th>
                    <th className="px-6 py-2 font-medium">Total</th>
                    <th className="px-6 py-2 font-medium">Payment</th>
                    <th className="px-6 py-2 font-medium">Status</th>
                  </tr>
                </thead>
                <tbody>
                  {bookings.map((booking) => (
                    <tr key={booking.id} className="border-b last:border-0">
                      <td className="px-6 py-3">
                        <Link href={`/dashboard/bookings/${booking.id}`} className="text-primary font-medium">
                          {booking.bookingNumber}
                        </Link>
                      </td>
                      <td className="px-6 py-3">{booking.buyerName}</td>
                      <td className="text-muted-foreground px-6 py-3">{booking.serviceName}</td>
                      <td className="text-muted-foreground px-6 py-3">
                        {formatDateTime(booking.scheduledStart)}
                      </td>
                      <td className="px-6 py-3">{formatCurrency(booking.total, currency)}</td>
                      <td className="text-muted-foreground px-6 py-3">
                        {bookingPaymentMethodLabel(booking.paymentMethod)}
                      </td>
                      <td className="px-6 py-3">
                        <BookingStatusBadge status={booking.status} />
                      </td>
                    </tr>
                  ))}
                </tbody>
              </table>
            </div>
          )}
        </CardContent>
      </Card>
    </div>
  );
}
