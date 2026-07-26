"use client";

import { useState } from "react";
import Link from "next/link";
import { useQuery } from "@tanstack/react-query";
import { ClipboardList } from "lucide-react";
import { Card, CardContent } from "@/components/ui/card";
import { OrderStatusBadge } from "@/components/shared/order-status-badge";
import { EmptyState } from "@/components/shared/empty-state";
import { TableRowSkeleton } from "@/components/shared/loading-skeletons";
import { formatLkr } from "@/lib/currency";
import { formatDate } from "@/lib/format";
import { cn } from "@/lib/utils";
import { ORDER_STATUS_LABELS } from "@/lib/constants";
import { useSellerStoreId } from "@/hooks/use-seller-store";
import { ordersService } from "@/services";
import type { OrderStatus } from "@/types";

const FILTERS: { label: string; value: OrderStatus | "all" }[] = [
  { label: "All", value: "all" },
  { label: ORDER_STATUS_LABELS.pending, value: "pending" },
  { label: ORDER_STATUS_LABELS.confirmed, value: "confirmed" },
  { label: ORDER_STATUS_LABELS.shipped, value: "shipped" },
  { label: ORDER_STATUS_LABELS.delivered, value: "delivered" },
  { label: ORDER_STATUS_LABELS.cancelled, value: "cancelled" },
];

export default function DashboardOrdersPage() {
  const storeId = useSellerStoreId();
  const [filter, setFilter] = useState<OrderStatus | "all">("all");

  const { data: orders, isLoading } = useQuery({
    queryKey: ["orders", storeId],
    queryFn: () => ordersService.listOrdersByStore(storeId),
  });

  const filteredOrders = (orders ?? []).filter((o) => filter === "all" || o.status === filter);

  return (
    <div className="max-w-6xl space-y-6">
      <div>
        <h1 className="text-2xl font-bold">Orders</h1>
        <p className="text-muted-foreground text-sm">Track and fulfil incoming orders.</p>
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
              <TableRowSkeleton columns={5} />
              <TableRowSkeleton columns={5} />
              <TableRowSkeleton columns={5} />
            </div>
          ) : filteredOrders.length === 0 ? (
            <EmptyState icon={ClipboardList} title="No orders found" description="Try a different filter." />
          ) : (
            <div className="-mx-6 overflow-x-auto">
              <table className="w-full text-sm">
                <thead>
                  <tr className="text-muted-foreground border-y text-left text-xs">
                    <th className="px-6 py-2 font-medium">Order</th>
                    <th className="px-6 py-2 font-medium">Customer</th>
                    <th className="px-6 py-2 font-medium">Items</th>
                    <th className="px-6 py-2 font-medium">Date</th>
                    <th className="px-6 py-2 font-medium">Total</th>
                    <th className="px-6 py-2 font-medium">Payment</th>
                    <th className="px-6 py-2 font-medium">Status</th>
                  </tr>
                </thead>
                <tbody>
                  {filteredOrders.map((order) => (
                    <tr key={order.id} className="border-b last:border-0">
                      <td className="px-6 py-3">
                        <Link href={`/dashboard/orders/${order.id}`} className="text-primary font-medium">
                          {order.orderNumber}
                        </Link>
                      </td>
                      <td className="px-6 py-3">{order.shipping.fullName}</td>
                      <td className="text-muted-foreground px-6 py-3">
                        {order.items.reduce((sum, i) => sum + i.quantity, 0)} items
                      </td>
                      <td className="text-muted-foreground px-6 py-3">{formatDate(order.createdAt)}</td>
                      <td className="px-6 py-3">{formatLkr(order.totalLkr)}</td>
                      <td className="text-muted-foreground px-6 py-3 capitalize">
                        {order.paymentMethod === "cod" ? "COD" : "PayHere"}
                      </td>
                      <td className="px-6 py-3">
                        <OrderStatusBadge status={order.status} />
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
