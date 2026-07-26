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
import { ordersService } from "@/services";
import type { Order } from "@/types";

export function CancelOrderButton({
  orderId,
  onCancelled,
}: {
  orderId: string;
  onCancelled: (order: Order) => void;
}) {
  const [isCancelling, setIsCancelling] = useState(false);

  async function handleConfirm() {
    setIsCancelling(true);
    try {
      const updated = await ordersService.cancelOrder(orderId);
      onCancelled(updated);
      toast.success("Order cancelled.");
    } catch {
      toast.error("Couldn't cancel the order. Please try again.");
    } finally {
      setIsCancelling(false);
    }
  }

  return (
    <Dialog>
      <DialogTrigger
        render={<Button type="button" variant="outline" size="sm" className="text-destructive" />}
      >
        Cancel order
      </DialogTrigger>
      <DialogContent>
        <DialogHeader>
          <DialogTitle>Cancel this order?</DialogTitle>
          <DialogDescription>
            This can&apos;t be undone. If you&apos;ve already made the bank transfer, upload your
            receipt instead of cancelling.
          </DialogDescription>
        </DialogHeader>
        <DialogFooter>
          <DialogClose render={<Button variant="outline" />}>Keep order</DialogClose>
          <Button variant="destructive" disabled={isCancelling} onClick={handleConfirm}>
            {isCancelling ? <Loader2 className="size-4 animate-spin" /> : null}
            Yes, cancel order
          </Button>
        </DialogFooter>
      </DialogContent>
    </Dialog>
  );
}
