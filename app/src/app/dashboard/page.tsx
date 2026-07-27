"use client";

import Link from "next/link";
import { useQuery } from "@tanstack/react-query";
import { ClipboardList, Package, TriangleAlert, Wallet } from "lucide-react";
import { Card, CardContent } from "@/components/ui/card";
import { Button } from "@/components/ui/button";
import { StatCard } from "@/components/dashboard/stat-card";
import { OrderStatusBadge } from "@/components/shared/order-status-badge";
import { TableRowSkeleton } from "@/components/shared/loading-skeletons";
import { EmptyState } from "@/components/shared/empty-state";
import { formatLkr } from "@/lib/currency";
import { formatDate } from "@/lib/format";
import { useSellerStoreId } from "@/hooks/use-seller-store";
import { ordersService, productsService } from "@/services";

export default function DashboardOverviewPage() {
  const storeId = useSellerStoreId();
  // Revenue/pending-count below are computed from this full set, not just
  // one page — a large enough page size to cover realistic stores. A store
  // that outgrows this needs a real backend aggregate query instead of
  // summing a fetched page client-side; out of scope for now.
  const ordersQuery = useQuery({
    queryKey: ["orders", storeId, "overview"],
    queryFn: () => ordersService.listOrdersByStore(storeId, undefined, 0, 1000),
  });
  const productsQuery = useQuery({
    queryKey: ["products", "store", storeId],
    queryFn: () => productsService.listProductsByStore(storeId),
  });

  const orders = ordersQuery.data?.content ?? [];
  const products = productsQuery.data ?? [];

  const revenue = orders
    .filter((o) => o.status !== "cancelled")
    .reduce((sum, o) => sum + o.subtotalLkr, 0);
  const platformFees = orders
    .filter((o) => o.status !== "cancelled")
    .reduce((sum, o) => sum + o.platformFeeLkr, 0);
  const pendingCount = orders.filter((o) => o.status === "pending").length;
  const lowStockProducts = products.filter(
    (p) => p.trackStock && p.status !== "out-of-stock" && p.stockQuantity <= 5,
  );

  return (
    <div className="max-w-6xl space-y-6">
      <div>
        <h1 className="text-2xl font-bold">Overview</h1>
        <p className="text-muted-foreground text-sm">Welcome back — here&apos;s how your store is doing.</p>
      </div>

      <div className="grid grid-cols-1 gap-4 sm:grid-cols-2 lg:grid-cols-4">
        <StatCard label="Total revenue" value={formatLkr(revenue)} icon={Wallet} />
        <StatCard label="Pending orders" value={String(pendingCount)} icon={ClipboardList} />
        <StatCard label="Active products" value={String(products.length)} icon={Package} />
        <StatCard
          label="Platform fees (3.5%)"
          value={formatLkr(platformFees)}
          icon={Wallet}
        />
      </div>

      {lowStockProducts.length > 0 ? (
        <Card className="border-amber-300/60 bg-amber-50 dark:bg-amber-950/30">
          <CardContent className="flex items-start gap-3">
            <TriangleAlert className="mt-0.5 size-4 shrink-0 text-amber-600" />
            <div className="flex-1 space-y-1">
              <p className="text-sm font-medium">Low stock alert</p>
              <p className="text-muted-foreground text-sm">
                {lowStockProducts.map((p) => p.name).join(", ")} — running low.
              </p>
            </div>
            <Button render={<Link href="/dashboard/products" />} size="sm" variant="outline">
              Manage stock
            </Button>
          </CardContent>
        </Card>
      ) : null}

      <Card>
        <CardContent className="space-y-4">
          <div className="flex items-center justify-between">
            <h2 className="font-semibold">Recent orders</h2>
            <Link href="/dashboard/orders" className="text-primary text-sm font-medium">
              View all
            </Link>
          </div>

          {ordersQuery.isLoading ? (
            <div className="divide-y">
              <TableRowSkeleton columns={4} />
              <TableRowSkeleton columns={4} />
              <TableRowSkeleton columns={4} />
            </div>
          ) : orders.length === 0 ? (
            <EmptyState icon={ClipboardList} title="No orders yet" />
          ) : (
            <div className="-mx-6 overflow-x-auto">
              <table className="w-full text-sm">
                <thead>
                  <tr className="text-muted-foreground border-y text-left text-xs">
                    <th className="px-6 py-2 font-medium">Order</th>
                    <th className="px-6 py-2 font-medium">Customer</th>
                    <th className="px-6 py-2 font-medium">Date</th>
                    <th className="px-6 py-2 font-medium">Total</th>
                    <th className="px-6 py-2 font-medium">Status</th>
                  </tr>
                </thead>
                <tbody>
                  {orders.slice(0, 5).map((order) => (
                    <tr key={order.id} className="border-b last:border-0">
                      <td className="px-6 py-3">
                        <Link href={`/dashboard/orders/${order.id}`} className="text-primary font-medium">
                          {order.orderNumber}
                        </Link>
                      </td>
                      <td className="px-6 py-3">{order.shipping.fullName}</td>
                      <td className="text-muted-foreground px-6 py-3">{formatDate(order.createdAt)}</td>
                      <td className="px-6 py-3">{formatLkr(order.totalLkr)}</td>
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
