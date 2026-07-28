"use client";

import Link from "next/link";
import { useQuery } from "@tanstack/react-query";
import { LogOut, MapPin, Package, User } from "lucide-react";
import { Button } from "@/components/ui/button";
import { Card, CardContent } from "@/components/ui/card";
import { EmptyState } from "@/components/shared/empty-state";
import { OrderStatusBadge } from "@/components/shared/order-status-badge";
import { PriceDisplay } from "@/components/shared/price-display";
import { TableRowSkeleton } from "@/components/shared/loading-skeletons";
import { EditAddressDialog } from "@/components/marketplace/edit-address-dialog";
import { useSignOut } from "@/hooks/use-sign-out";
import { formatDate } from "@/lib/format";
import { ordersService, buyersService } from "@/services";

export function AccountView() {
  const signOut = useSignOut();

  const { data: buyer } = useQuery({
    queryKey: ["buyer", "me"],
    queryFn: () => buyersService.getCurrentBuyer(),
  });

  const { data: orders, isLoading } = useQuery({
    queryKey: ["orders", "me"],
    queryFn: () => ordersService.listMyOrders(),
  });

  return (
    <div className="mx-auto max-w-3xl space-y-6 px-4 py-8 sm:px-6 lg:px-8">
      <div className="flex items-center justify-between">
        <h1 className="text-2xl font-bold">My account</h1>
        <Button type="button" variant="outline" size="sm" onClick={() => signOut()}>
          <LogOut className="size-3.5" /> Sign out
        </Button>
      </div>

      <Card>
        <CardContent className="flex items-start gap-3">
          <span className="bg-primary/10 text-primary flex size-10 shrink-0 items-center justify-center rounded-full">
            <User className="size-5" />
          </span>
          <div>
            <p className="font-medium">{buyer?.name}</p>
            <p className="text-muted-foreground text-sm">{buyer?.email}</p>
            {buyer?.phone ? <p className="text-muted-foreground text-sm">{buyer.phone}</p> : null}
          </div>
        </CardContent>
      </Card>

      <Card>
        <CardContent className="space-y-2">
          <div className="flex items-center justify-between gap-2">
            <div className="flex items-center gap-2">
              <MapPin className="text-muted-foreground size-4" />
              <h2 className="font-semibold">Saved address</h2>
            </div>
            <EditAddressDialog defaultShipping={buyer?.defaultShipping} />
          </div>
          {buyer?.defaultShipping ? (
            <p className="text-muted-foreground text-sm">
              {buyer.defaultShipping.addressLine1}, {buyer.defaultShipping.city},{" "}
              {buyer.defaultShipping.state} {buyer.defaultShipping.postalCode}
            </p>
          ) : (
            <p className="text-muted-foreground text-sm">
              No saved address yet — it&apos;s saved automatically the first time you check out, or
              you can add one now.
            </p>
          )}
        </CardContent>
      </Card>

      <Card>
        <CardContent className="space-y-3">
          <div className="flex items-center gap-2">
            <Package className="text-muted-foreground size-4" />
            <h2 className="font-semibold">Order history</h2>
          </div>

          {isLoading ? (
            <div className="space-y-2">
              <TableRowSkeleton columns={1} />
              <TableRowSkeleton columns={1} />
            </div>
          ) : !orders || orders.length === 0 ? (
            <EmptyState
              icon={Package}
              title="No orders yet"
              description="Orders you place while signed in will show up here."
              action={
                <Button render={<Link href="/search" />} size="sm">
                  Browse products
                </Button>
              }
            />
          ) : (
            <div className="divide-y">
              {orders.map((order) => (
                <Link
                  key={order.id}
                  href={`/orders/${order.id}`}
                  className="hover:bg-accent/50 -mx-4 flex items-center justify-between gap-3 px-4 py-3"
                >
                  <div className="min-w-0">
                    <p className="text-sm font-medium">{order.orderNumber}</p>
                    <p className="text-muted-foreground text-xs">
                      {order.storeName} · {formatDate(order.createdAt)}
                    </p>
                  </div>
                  <div className="flex shrink-0 items-center gap-3">
                    <PriceDisplay price={order.total} size="sm" />
                    <OrderStatusBadge status={order.status} />
                  </div>
                </Link>
              ))}
            </div>
          )}
        </CardContent>
      </Card>
    </div>
  );
}
