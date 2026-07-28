"use client";

import { useQuery } from "@tanstack/react-query";
import { Clock, Landmark, Wallet } from "lucide-react";
import { Card, CardContent } from "@/components/ui/card";
import { Badge } from "@/components/ui/badge";
import { StatCard } from "@/components/dashboard/stat-card";
import { TableRowSkeleton } from "@/components/shared/loading-skeletons";
import { EmptyState } from "@/components/shared/empty-state";
import { formatCurrency } from "@/lib/currency";
import { formatDate } from "@/lib/format";
import { usePlatformConfig } from "@/hooks/use-platform-config";
import { useSellerStoreId } from "@/hooks/use-seller-store";
import { payoutsService, storesService } from "@/services";

export default function DashboardPayoutsPage() {
  const storeId = useSellerStoreId();
  const { name, currencyCode, currencySymbol, currencyLocale, platformFeePercent } = usePlatformConfig();
  const currency = { code: currencyCode, symbol: currencySymbol, locale: currencyLocale };

  const { data: payouts, isLoading } = useQuery({
    queryKey: ["payouts", storeId],
    queryFn: () => payoutsService.listPayoutsByStore(storeId),
  });
  const { data: eligibleOrders } = useQuery({
    queryKey: ["payout-eligible-orders", storeId],
    queryFn: () => payoutsService.getEligibleOrdersForPayout(storeId),
  });
  const { data: settings } = useQuery({
    queryKey: ["store-settings", storeId],
    queryFn: () => storesService.getStoreSettings(storeId),
  });

  const availableAmount = (eligibleOrders ?? []).reduce(
    (sum, o) => sum + (o.subtotal - o.platformFee),
    0,
  );
  const scheduledAmount = (payouts ?? [])
    .filter((p) => p.status === "scheduled")
    .reduce((sum, p) => sum + p.net, 0);
  const paidAmount = (payouts ?? [])
    .filter((p) => p.status === "paid")
    .reduce((sum, p) => sum + p.net, 0);

  return (
    <div className="max-w-6xl space-y-6">
      <div>
        <h1 className="text-2xl font-bold">Payouts</h1>
        <p className="text-muted-foreground text-sm">
          {name} deducts a {settings?.transactionFeePercent ?? platformFeePercent}% transaction fee and holds
          your share until an order is delivered, then releases it in a scheduled payout.
        </p>
      </div>

      <div className="grid grid-cols-1 gap-4 sm:grid-cols-3">
        <StatCard label="Available (awaiting payout run)" value={formatCurrency(availableAmount, currency)} icon={Clock} />
        <StatCard label="Scheduled (bank transfer pending)" value={formatCurrency(scheduledAmount, currency)} icon={Wallet} />
        <StatCard label="Paid out (all time)" value={formatCurrency(paidAmount, currency)} icon={Landmark} />
      </div>

      {settings ? (
        <Card>
          <CardContent className="flex flex-wrap items-center justify-between gap-4">
            <div>
              <h2 className="font-semibold">Payout account</h2>
              <p className="text-muted-foreground text-sm">
                {settings.bankName} · {settings.bankAccountName} · {settings.bankAccountNumber}
              </p>
            </div>
            <Badge variant="secondary">Payouts released by {name}</Badge>
          </CardContent>
        </Card>
      ) : null}

      <Card>
        <CardContent>
          <h2 className="mb-1 font-semibold">Payout history</h2>
          <p className="text-muted-foreground mb-4 text-xs">
            Payout runs are created and released by {name}, not requested by you — this is a
            read-only ledger of what&apos;s been scheduled and paid.
          </p>
          {isLoading ? (
            <div className="divide-y">
              <TableRowSkeleton columns={5} />
              <TableRowSkeleton columns={5} />
            </div>
          ) : !payouts || payouts.length === 0 ? (
            <EmptyState icon={Wallet} title="No payouts yet" />
          ) : (
            <div className="-mx-6 overflow-x-auto">
              <table className="w-full text-sm">
                <thead>
                  <tr className="text-muted-foreground border-y text-left text-xs">
                    <th className="px-6 py-2 font-medium">Payout</th>
                    <th className="px-6 py-2 font-medium">Created</th>
                    <th className="px-6 py-2 font-medium">Orders</th>
                    <th className="px-6 py-2 font-medium">Net amount</th>
                    <th className="px-6 py-2 font-medium">Status</th>
                    <th className="px-6 py-2 font-medium">Paid</th>
                  </tr>
                </thead>
                <tbody>
                  {payouts.map((payout) => (
                    <tr key={payout.id} className="border-b last:border-0">
                      <td className="px-6 py-3 font-medium">{payout.id}</td>
                      <td className="text-muted-foreground px-6 py-3">{formatDate(payout.createdAt)}</td>
                      <td className="text-muted-foreground px-6 py-3">{payout.orders.length}</td>
                      <td className="px-6 py-3 font-medium">{formatCurrency(payout.net, currency)}</td>
                      <td className="px-6 py-3">
                        <Badge
                          className={
                            payout.status === "paid"
                              ? "border-0 bg-emerald-100 text-emerald-800 dark:bg-emerald-950 dark:text-emerald-300"
                              : "border-0 bg-amber-100 text-amber-800 dark:bg-amber-950 dark:text-amber-300"
                          }
                        >
                          {payout.status === "paid" ? "Paid" : "Scheduled"}
                        </Badge>
                      </td>
                      <td className="text-muted-foreground px-6 py-3">
                        {payout.paidAt ? formatDate(payout.paidAt) : "—"}
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
