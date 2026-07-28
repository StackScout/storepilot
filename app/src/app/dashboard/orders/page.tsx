"use client";

import { useState } from "react";
import Link from "next/link";
import { useQuery } from "@tanstack/react-query";
import { ChevronLeft, ChevronRight, ClipboardList } from "lucide-react";
import { Button } from "@/components/ui/button";
import { Card, CardContent } from "@/components/ui/card";
import { OrderStatusBadge } from "@/components/shared/order-status-badge";
import { EmptyState } from "@/components/shared/empty-state";
import { TableRowSkeleton } from "@/components/shared/loading-skeletons";
import { formatCurrency } from "@/lib/currency";
import { formatDate, paymentMethodLabel } from "@/lib/format";
import { cn } from "@/lib/utils";
import { ORDER_STATUS_LABELS } from "@/lib/constants";
import { useSellerStoreId } from "@/hooks/use-seller-store";
import { usePlatformConfig } from "@/hooks/use-platform-config";
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

const PAGE_SIZE = 20;

export default function DashboardOrdersPage() {
  const storeId = useSellerStoreId();
  const { currencyCode, currencySymbol, currencyLocale } = usePlatformConfig();
  const currency = { code: currencyCode, symbol: currencySymbol, locale: currencyLocale };
  const [filter, setFilter] = useState<OrderStatus | "all">("all");
  const [page, setPage] = useState(0);

  const { data, isLoading } = useQuery({
    queryKey: ["orders", storeId, filter, page],
    queryFn: () =>
      ordersService.listOrdersByStore(storeId, filter === "all" ? undefined : filter, page, PAGE_SIZE),
  });

  const filteredOrders = data?.content ?? [];
  const totalPages = data?.totalPages ?? 0;

  function handleFilterChange(next: OrderStatus | "all") {
    setFilter(next);
    setPage(0);
  }

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
            onClick={() => handleFilterChange(f.value)}
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
                      <td className="px-6 py-3">{formatCurrency(order.total, currency)}</td>
                      <td className="text-muted-foreground px-6 py-3">
                        {paymentMethodLabel(order.paymentMethod)}
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

      {totalPages > 1 ? (
        <div className="flex items-center justify-between">
          <p className="text-muted-foreground text-sm">
            Page {page + 1} of {totalPages}
          </p>
          <div className="flex gap-2">
            <Button
              variant="outline"
              size="sm"
              disabled={page === 0}
              onClick={() => setPage((p) => Math.max(0, p - 1))}
            >
              <ChevronLeft className="size-3.5" /> Previous
            </Button>
            <Button
              variant="outline"
              size="sm"
              disabled={page + 1 >= totalPages}
              onClick={() => setPage((p) => p + 1)}
            >
              Next <ChevronRight className="size-3.5" />
            </Button>
          </div>
        </div>
      ) : null}
    </div>
  );
}
