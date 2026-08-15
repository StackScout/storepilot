"use client";

import { useState } from "react";
import { useMutation, useQueryClient } from "@tanstack/react-query";
import { toast } from "sonner";
import { Loader2 } from "lucide-react";
import { Select, SelectContent, SelectItem, SelectTrigger, SelectValue } from "@/components/ui/select";
import {
  Dialog,
  DialogContent,
  DialogDescription,
  DialogFooter,
  DialogHeader,
  DialogTitle,
} from "@/components/ui/dialog";
import { Button } from "@/components/ui/button";
import { Input } from "@/components/ui/input";
import { Label } from "@/components/ui/label";
import { Textarea } from "@/components/ui/textarea";
import { ordersService } from "@/services";
import { ORDER_STATUS_LABELS } from "@/lib/constants";
import type { Order, OrderStatus } from "@/types";

const NEXT_STATUS_OPTIONS: Record<OrderStatus, OrderStatus[]> = {
  pending: ["pending", "confirmed", "cancelled"],
  confirmed: ["confirmed", "shipped", "cancelled"],
  shipped: ["shipped", "delivered"],
  delivered: ["delivered"],
  cancelled: ["cancelled"],
};

export function OrderStatusSelect({ order }: { order: Order }) {
  const queryClient = useQueryClient();
  const [dialogStatus, setDialogStatus] = useState<OrderStatus | null>(null);
  const [trackingNumber, setTrackingNumber] = useState("");
  const [courierServiceName, setCourierServiceName] = useState("");
  const [courierReceipt, setCourierReceipt] = useState<File | null>(null);
  const [note, setNote] = useState("");

  const mutation = useMutation({
    mutationFn: (
      params: { status: OrderStatus; trackingNumber?: string; courierServiceName?: string; courierReceipt?: File; note?: string },
    ) =>
      ordersService.updateOrderStatus(order.id, params.status, {
        trackingNumber: params.trackingNumber,
        courierServiceName: params.courierServiceName,
        courierReceipt: params.courierReceipt,
        note: params.note,
      }),
    onSuccess: (updated) => {
      queryClient.invalidateQueries({ queryKey: ["orders"] });
      queryClient.invalidateQueries({ queryKey: ["order", order.id] });
      toast.success(`Order ${updated.orderNumber} marked as ${ORDER_STATUS_LABELS[updated.status]}`);
      setDialogStatus(null);
      setTrackingNumber("");
      setCourierServiceName("");
      setCourierReceipt(null);
      setNote("");
    },
    onError: () => toast.error("Couldn't update order status. Try again."),
  });

  const options = NEXT_STATUS_OPTIONS[order.status];
  const isLocked = options.length === 1;
  const isShipDialog = dialogStatus === "shipped";

  function handleStatusChange(value: OrderStatus) {
    setDialogStatus(value);
  }

  function handleConfirm() {
    if (!dialogStatus) return;
    if (isShipDialog && (!trackingNumber.trim() || !courierServiceName.trim())) return;
    mutation.mutate({
      status: dialogStatus,
      trackingNumber: isShipDialog ? trackingNumber.trim() : undefined,
      courierServiceName: isShipDialog ? courierServiceName.trim() : undefined,
      courierReceipt: isShipDialog ? (courierReceipt ?? undefined) : undefined,
      note: note.trim() || undefined,
    });
  }

  return (
    <>
      <Select
        value={order.status}
        disabled={isLocked || mutation.isPending}
        onValueChange={(value) => handleStatusChange(value as OrderStatus)}
      >
        <SelectTrigger className="w-[160px]">
          <SelectValue />
        </SelectTrigger>
        <SelectContent>
          {options.map((status) => (
            <SelectItem key={status} value={status}>
              {ORDER_STATUS_LABELS[status]}
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
              {isShipDialog ? "Mark order as shipped" : `Mark order as ${dialogStatus ? ORDER_STATUS_LABELS[dialogStatus] : ""}`}
            </DialogTitle>
            {isShipDialog ? (
              <DialogDescription>
                Tracking number and courier are required — the buyer gets an email with these
                details as soon as you confirm.
              </DialogDescription>
            ) : null}
          </DialogHeader>
          <div className="space-y-4">
            {isShipDialog ? (
              <>
                <div className="space-y-1.5">
                  <Label htmlFor="trackingNumber">Tracking number</Label>
                  <Input
                    id="trackingNumber"
                    value={trackingNumber}
                    onChange={(e) => setTrackingNumber(e.target.value)}
                    placeholder="e.g. LK123456789"
                  />
                </div>
                <div className="space-y-1.5">
                  <Label htmlFor="courierServiceName">Courier service</Label>
                  <Input
                    id="courierServiceName"
                    value={courierServiceName}
                    onChange={(e) => setCourierServiceName(e.target.value)}
                    placeholder="e.g. Australia Post, StarTrack, Sendle"
                  />
                </div>
                <div className="space-y-1.5">
                  <Label htmlFor="courierReceipt">Courier receipt (optional)</Label>
                  <Input
                    id="courierReceipt"
                    type="file"
                    accept="image/jpeg,image/png,image/webp,application/pdf"
                    onChange={(e) => setCourierReceipt(e.target.files?.[0] ?? null)}
                  />
                </div>
              </>
            ) : null}
            <div className="space-y-1.5">
              <Label htmlFor="statusNote">Note for the buyer (optional)</Label>
              <Textarea
                id="statusNote"
                rows={3}
                value={note}
                onChange={(e) => setNote(e.target.value)}
                placeholder="e.g. Packed and ready for pickup by the courier"
              />
            </div>
          </div>
          <DialogFooter>
            <Button variant="outline" onClick={() => setDialogStatus(null)}>
              Cancel
            </Button>
            <Button
              disabled={(isShipDialog && (!trackingNumber.trim() || !courierServiceName.trim())) || mutation.isPending}
              onClick={handleConfirm}
            >
              {mutation.isPending ? <Loader2 className="size-4 animate-spin" /> : null}
              {isShipDialog ? "Confirm shipped" : "Confirm"}
            </Button>
          </DialogFooter>
        </DialogContent>
      </Dialog>
    </>
  );
}
