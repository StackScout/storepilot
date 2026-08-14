"use client";

import { useMutation, useQueryClient } from "@tanstack/react-query";
import { toast } from "sonner";
import { Select, SelectContent, SelectItem, SelectTrigger, SelectValue } from "@/components/ui/select";
import { bookingsService } from "@/services";
import { BOOKING_STATUS_LABELS } from "@/lib/constants";
import type { Booking, BookingStatus } from "@/types";

/** Mirrors OrderStatusSelect's NEXT_STATUS_OPTIONS shape — server-side source of truth is BookingService.ALLOWED_STATUS_TRANSITIONS. */
const NEXT_STATUS_OPTIONS: Record<BookingStatus, BookingStatus[]> = {
  pending: ["pending", "confirmed", "cancelled"],
  confirmed: ["confirmed", "completed", "cancelled", "no-show"],
  completed: ["completed"],
  cancelled: ["cancelled"],
  "no-show": ["no-show"],
};

export function BookingStatusSelect({ booking }: { booking: Booking }) {
  const queryClient = useQueryClient();

  const mutation = useMutation({
    mutationFn: (status: BookingStatus) => bookingsService.updateBookingStatus(booking.id, status),
    onSuccess: (updated) => {
      queryClient.invalidateQueries({ queryKey: ["bookings"] });
      queryClient.invalidateQueries({ queryKey: ["booking", booking.id] });
      toast.success(`Booking ${updated.bookingNumber} marked as ${BOOKING_STATUS_LABELS[updated.status]}`);
    },
    onError: () => toast.error("Couldn't update booking status. Try again."),
  });

  const options = NEXT_STATUS_OPTIONS[booking.status];
  const isLocked = options.length === 1;

  return (
    <Select
      value={booking.status}
      disabled={isLocked || mutation.isPending}
      onValueChange={(value) => mutation.mutate(value as BookingStatus)}
    >
      <SelectTrigger className="w-[160px]">
        <SelectValue />
      </SelectTrigger>
      <SelectContent>
        {options.map((status) => (
          <SelectItem key={status} value={status}>
            {BOOKING_STATUS_LABELS[status]}
          </SelectItem>
        ))}
      </SelectContent>
    </Select>
  );
}
