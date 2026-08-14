"use client";

import { useState } from "react";
import { toast } from "sonner";
import { Loader2 } from "lucide-react";
import { Button } from "@/components/ui/button";
import {
  Dialog,
  DialogClose,
  DialogContent,
  DialogDescription,
  DialogFooter,
  DialogHeader,
  DialogTitle,
  DialogTrigger,
} from "@/components/ui/dialog";
import { bookingsService } from "@/services";
import type { Booking } from "@/types";

/** Mirrors CancelOrderButton — the store's lead-time cutoff is enforced server-side, surfaced here via a plain error toast rather than a proactive client-side check. */
export function CancelBookingButton({
  bookingId,
  onCancelled,
}: {
  bookingId: string;
  onCancelled: (booking: Booking) => void;
}) {
  const [isCancelling, setIsCancelling] = useState(false);

  async function handleConfirm() {
    setIsCancelling(true);
    try {
      const updated = await bookingsService.cancelBooking(bookingId);
      onCancelled(updated);
      toast.success("Booking cancelled.");
    } catch {
      toast.error("Couldn't cancel this booking — it may be too close to the appointment time. Contact the store directly.");
    } finally {
      setIsCancelling(false);
    }
  }

  return (
    <Dialog>
      <DialogTrigger
        render={<Button type="button" variant="outline" size="sm" className="text-destructive" />}
      >
        Cancel booking
      </DialogTrigger>
      <DialogContent>
        <DialogHeader>
          <DialogTitle>Cancel this booking?</DialogTitle>
          <DialogDescription>This can&apos;t be undone.</DialogDescription>
        </DialogHeader>
        <DialogFooter>
          <DialogClose render={<Button variant="outline" />}>Keep booking</DialogClose>
          <Button variant="destructive" disabled={isCancelling} onClick={handleConfirm}>
            {isCancelling ? <Loader2 className="size-4 animate-spin" /> : null}
            Yes, cancel booking
          </Button>
        </DialogFooter>
      </DialogContent>
    </Dialog>
  );
}
