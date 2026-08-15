"use client";

import { useState } from "react";
import { useMutation, useQueryClient } from "@tanstack/react-query";
import { toast } from "sonner";
import { Loader2 } from "lucide-react";
import { Select, SelectContent, SelectItem, SelectTrigger, SelectValue } from "@/components/ui/select";
import {
  Dialog,
  DialogContent,
  DialogFooter,
  DialogHeader,
  DialogTitle,
} from "@/components/ui/dialog";
import { Button } from "@/components/ui/button";
import { Label } from "@/components/ui/label";
import { Textarea } from "@/components/ui/textarea";
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
  const [dialogStatus, setDialogStatus] = useState<BookingStatus | null>(null);
  const [note, setNote] = useState("");

  const mutation = useMutation({
    mutationFn: (params: { status: BookingStatus; note?: string }) =>
      bookingsService.updateBookingStatus(booking.id, params.status, params.note),
    onSuccess: (updated) => {
      queryClient.invalidateQueries({ queryKey: ["bookings"] });
      queryClient.invalidateQueries({ queryKey: ["booking", booking.id] });
      toast.success(`Booking ${updated.bookingNumber} marked as ${BOOKING_STATUS_LABELS[updated.status]}`);
      setDialogStatus(null);
      setNote("");
    },
    onError: () => toast.error("Couldn't update booking status. Try again."),
  });

  const options = NEXT_STATUS_OPTIONS[booking.status];
  const isLocked = options.length === 1;

  return (
    <>
      <Select
        value={booking.status}
        disabled={isLocked || mutation.isPending}
        onValueChange={(value) => setDialogStatus(value as BookingStatus)}
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

      <Dialog
        open={dialogStatus !== null}
        onOpenChange={(open) => {
          if (!open) setDialogStatus(null);
        }}
      >
        <DialogContent>
          <DialogHeader>
            <DialogTitle>
              Mark booking as {dialogStatus ? BOOKING_STATUS_LABELS[dialogStatus] : ""}
            </DialogTitle>
          </DialogHeader>
          <div className="space-y-1.5">
            <Label htmlFor="bookingStatusNote">Note for the buyer (optional)</Label>
            <Textarea
              id="bookingStatusNote"
              rows={3}
              value={note}
              onChange={(e) => setNote(e.target.value)}
              placeholder="e.g. Please arrive 10 minutes early"
            />
          </div>
          <DialogFooter>
            <Button variant="outline" onClick={() => setDialogStatus(null)}>
              Cancel
            </Button>
            <Button
              disabled={mutation.isPending}
              onClick={() => dialogStatus && mutation.mutate({ status: dialogStatus, note: note.trim() || undefined })}
            >
              {mutation.isPending ? <Loader2 className="size-4 animate-spin" /> : null}
              Confirm
            </Button>
          </DialogFooter>
        </DialogContent>
      </Dialog>
    </>
  );
}
