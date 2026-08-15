"use client";

import { useState } from "react";
import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import { toast } from "sonner";
import { Tag, Trash2 } from "lucide-react";
import { Button } from "@/components/ui/button";
import { Card, CardContent } from "@/components/ui/card";
import { StatusBadge } from "@/components/shared/status-badge";
import { EmptyState } from "@/components/shared/empty-state";
import { TableRowSkeleton } from "@/components/shared/loading-skeletons";
import {
  Dialog,
  DialogContent,
  DialogDescription,
  DialogFooter,
  DialogHeader,
  DialogTitle,
} from "@/components/ui/dialog";
import { CouponFormDialog } from "@/components/dashboard/coupon-form-dialog";
import { formatCurrency } from "@/lib/currency";
import { usePlatformConfig } from "@/hooks/use-platform-config";
import { couponsService } from "@/services";
import type { Coupon } from "@/types";

function discountLabel(coupon: Coupon, currency: { code: string; symbol: string; locale: string }): string {
  return coupon.discountType === "percent" ? `${coupon.discountValue}% off` : `${formatCurrency(coupon.discountValue, currency)} off`;
}

export default function AdminCouponsPage() {
  const queryClient = useQueryClient();
  const [couponToDelete, setCouponToDelete] = useState<Coupon | null>(null);
  const { currencyCode, currencySymbol, currencyLocale } = usePlatformConfig();
  const currency = { code: currencyCode, symbol: currencySymbol, locale: currencyLocale };

  const { data: coupons, isLoading } = useQuery({
    queryKey: ["coupons", "platform"],
    queryFn: () => couponsService.listPlatformCoupons(),
  });

  const deleteMutation = useMutation({
    mutationFn: (id: string) => couponsService.deletePlatformCoupon(id),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ["coupons"] });
      toast.success("Coupon deleted");
      setCouponToDelete(null);
    },
    onError: () => toast.error("Couldn't delete this coupon. Please try again."),
  });

  return (
    <div className="space-y-6">
      <div className="flex items-center justify-between">
        <div>
          <h1 className="text-2xl font-bold">Coupons</h1>
          <p className="text-muted-foreground text-sm">Platform-wide discount codes, valid at checkout for any store.</p>
        </div>
        <CouponFormDialog scope="platform" />
      </div>

      <Card>
        <CardContent>
          {isLoading ? (
            <div className="divide-y">
              <TableRowSkeleton columns={5} />
              <TableRowSkeleton columns={5} />
            </div>
          ) : !coupons || coupons.length === 0 ? (
            <EmptyState
              icon={Tag}
              title="No platform-wide coupons yet"
              description="Create a discount code that works across every store on the platform."
            />
          ) : (
            <div className="-mx-6 overflow-x-auto">
              <table className="w-full text-sm">
                <thead>
                  <tr className="text-muted-foreground border-y text-left text-xs">
                    <th className="px-6 py-2 font-medium">Code</th>
                    <th className="px-6 py-2 font-medium">Discount</th>
                    <th className="px-6 py-2 font-medium">Uses</th>
                    <th className="px-6 py-2 font-medium">Status</th>
                    <th className="px-6 py-2 font-medium">Actions</th>
                  </tr>
                </thead>
                <tbody>
                  {coupons.map((coupon) => (
                    <tr key={coupon.id} className="border-b last:border-0">
                      <td className="px-6 py-3 font-mono font-medium">{coupon.code}</td>
                      <td className="px-6 py-3">{discountLabel(coupon, currency)}</td>
                      <td className="px-6 py-3">
                        {coupon.usedCount}
                        {coupon.maxUses ? ` / ${coupon.maxUses}` : ""}
                      </td>
                      <td className="px-6 py-3">
                        <StatusBadge tone={coupon.active ? "success" : "neutral"}>{coupon.active ? "active" : "inactive"}</StatusBadge>
                      </td>
                      <td className="px-6 py-3">
                        <div className="flex items-center gap-1">
                          <CouponFormDialog coupon={coupon} scope="platform" />
                          <Button
                            variant="ghost"
                            size="icon"
                            className="text-destructive size-8"
                            onClick={() => setCouponToDelete(coupon)}
                          >
                            <Trash2 className="size-3.5" />
                          </Button>
                        </div>
                      </td>
                    </tr>
                  ))}
                </tbody>
              </table>
            </div>
          )}
        </CardContent>
      </Card>

      <Dialog open={!!couponToDelete} onOpenChange={(open) => !open && setCouponToDelete(null)}>
        <DialogContent>
          <DialogHeader>
            <DialogTitle>Delete coupon?</DialogTitle>
            <DialogDescription>
              This will permanently remove the code &quot;{couponToDelete?.code}&quot;. This can&apos;t be undone.
            </DialogDescription>
          </DialogHeader>
          <DialogFooter>
            <Button variant="outline" onClick={() => setCouponToDelete(null)}>
              Cancel
            </Button>
            <Button
              variant="destructive"
              disabled={deleteMutation.isPending}
              onClick={() => couponToDelete && deleteMutation.mutate(couponToDelete.id)}
            >
              Delete
            </Button>
          </DialogFooter>
        </DialogContent>
      </Dialog>
    </div>
  );
}
