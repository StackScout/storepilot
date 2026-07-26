"use client";

import { useMutation, useQueryClient } from "@tanstack/react-query";
import { toast } from "sonner";
import { Select, SelectContent, SelectItem, SelectTrigger, SelectValue } from "@/components/ui/select";
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

  const mutation = useMutation({
    mutationFn: (status: OrderStatus) => ordersService.updateOrderStatus(order.id, status),
    onSuccess: (updated) => {
      queryClient.invalidateQueries({ queryKey: ["orders"] });
      queryClient.invalidateQueries({ queryKey: ["order", order.id] });
      toast.success(`Order ${updated.orderNumber} marked as ${ORDER_STATUS_LABELS[updated.status]}`);
    },
    onError: () => toast.error("Couldn't update order status. Try again."),
  });

  const options = NEXT_STATUS_OPTIONS[order.status];
  const isLocked = options.length === 1;

  return (
    <Select
      value={order.status}
      disabled={isLocked || mutation.isPending}
      onValueChange={(value) => mutation.mutate(value as OrderStatus)}
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
  );
}
