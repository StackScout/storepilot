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
  const [shipDialogOpen, setShipDialogOpen] = useState(false);
  const [trackingNumber, setTrackingNumber] = useState("");
  const [courierServiceName, setCourierServiceName] = useState("");
  const [courierReceipt, setCourierReceipt] = useState<File | null>(null);

  const mutation = useMutation({
    mutationFn: (
      params: { status: OrderStatus; trackingNumber?: string; courierServiceName?: string; courierReceipt?: File },
    ) =>
      ordersService.updateOrderStatus(order.id, params.status, {
        trackingNumber: params.trackingNumber,
        courierServiceName: params.courierServiceName,
        courierReceipt: params.courierReceipt,
      }),
    onSuccess: (updated) => {
      queryClient.invalidateQueries({ queryKey: ["orders"] });
      queryClient.invalidateQueries({ queryKey: ["order", order.id] });
      toast.success(`Order ${updated.orderNumber} marked as ${ORDER_STATUS_LABELS[updated.status]}`);
      setShipDialogOpen(false);
      setTrackingNumber("");
      setCourierServiceName("");
      setCourierReceipt(null);
    },
    onError: () => toast.error("Couldn't update order status. Try again."),
  });

  const options = NEXT_STATUS_OPTIONS[order.status];
  const isLocked = options.length === 1;

  function handleStatusChange(value: OrderStatus) {
    if (value === "shipped") {
      setShipDialogOpen(true);
      return;
    }
    mutation.mutate({ status: value });
  }

  function handleConfirmShipped() {
    if (!trackingNumber.trim() || !courierServiceName.trim()) return;
    mutation.mutate({
      status: "shipped",
      trackingNumber: trackingNumber.trim(),
      courierServiceName: courierServiceName.trim(),
      courierReceipt: courierReceipt ?? undefined,
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

      <Dialog open={shipDialogOpen} onOpenChange={setShipDialogOpen}>
        <DialogContent>
          <DialogHeader>
            <DialogTitle>Mark order as shipped</DialogTitle>
            <DialogDescription>
              Tracking number and courier are required — the buyer gets an email with these
              details as soon as you confirm.
            </DialogDescription>
          </DialogHeader>
          <div className="space-y-4">
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
                placeholder="e.g. Domex, Pronto, Sri Lanka Post"
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
          </div>
          <DialogFooter>
            <Button variant="outline" onClick={() => setShipDialogOpen(false)}>
              Cancel
            </Button>
            <Button
              disabled={!trackingNumber.trim() || !courierServiceName.trim() || mutation.isPending}
              onClick={handleConfirmShipped}
            >
              {mutation.isPending ? <Loader2 className="size-4 animate-spin" /> : null}
              Confirm shipped
            </Button>
          </DialogFooter>
        </DialogContent>
      </Dialog>
    </>
  );
}
