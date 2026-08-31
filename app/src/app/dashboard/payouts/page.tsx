"use client";

import { useQuery } from "@tanstack/react-query";
import { Clock, CreditCard, Landmark, Lock, ReceiptText, Wallet } from "lucide-react";
import { Card, CardContent } from "@/components/ui/card";
import { Badge } from "@/components/ui/badge";
import { StatusBadge } from "@/components/shared/status-badge";
import { StatCard } from "@/components/dashboard/stat-card";
import { TableRowSkeleton } from "@/components/shared/loading-skeletons";
import { EmptyState } from "@/components/shared/empty-state";
import { formatCurrency } from "@/lib/currency";
import { formatDate } from "@/lib/format";
import { usePlatformConfig } from "@/hooks/use-platform-config";
import { useSellerRole, useSellerStoreId } from "@/hooks/use-seller-store";
import { payoutsService, storesService, ordersService } from "@/services";

export default function DashboardPayoutsPage() {
  const role = useSellerRole();
  if (role === "staff") {
    return (
      <div className="max-w-2xl">
        <Card>
          <CardContent className="py-12">
            <EmptyState icon={Lock} title="Earnings and payouts are managed by the store owner" />
          </CardContent>
        </Card>
      </div>
    );
  }
  return <OwnerPayoutsView />;
}

function OwnerPayoutsView() {
  const storeId = useSellerStoreId();
  const { name, countryCode, currencyCode, currencySymbol, currencyLocale, platformFeePercent, defaultCodEnabled, defaultBankTransferEnabled } =
    usePlatformConfig();
  const currency = { code: currencyCode, symbol: currencySymbol, locale: currencyLocale };
  // Payouts only ever contain money — PayHere is LK-only (see PayoutService.getEligibleOrders'
  // doc comment), so the section is dead weight everywhere else. Stripe is the mirror image:
  // AU-only, and it settles directly to the seller's connected account rather than ever
  // needing a payout run, so showing it outside AU would always be an empty no-op section too.
  const showPayouts = countryCode === "LK";
  const showStripe = countryCode === "AU";
  // Fees owed only exists for cod/bank-transfer orders (the platform never touches that money,
  // so it tracks what the seller owes back) — dead weight on a deployment that doesn't offer
  // either at all, see PlatformSettings' default*Enabled doc comment.
  const showFees = defaultCodEnabled || defaultBankTransferEnabled;

  const { data: payouts, isLoading: isPayoutsLoading } = useQuery({
    queryKey: ["payouts", storeId],
    queryFn: () => payoutsService.listPayoutsByStore(storeId),
    enabled: showPayouts,
  });
  const { data: eligibleOrders } = useQuery({
    queryKey: ["payout-eligible-orders", storeId],
    queryFn: () => payoutsService.getEligibleOrdersForPayout(storeId, 0, 200),
    enabled: showPayouts,
  });
  const { data: settings } = useQuery({
    queryKey: ["store-settings", storeId],
    queryFn: () => storesService.getStoreSettings(storeId),
    enabled: showPayouts,
  });
  const { data: feeCollections, isLoading: isFeeCollectionsLoading } = useQuery({
    queryKey: ["fee-collections", storeId],
    queryFn: () => payoutsService.listFeeCollectionsByStore(storeId),
    enabled: showFees,
  });
  const { data: feeEligibleOrders } = useQuery({
    queryKey: ["fee-collection-eligible-orders", storeId],
    queryFn: () => payoutsService.getEligibleOrdersForFeeCollection(storeId, 0, 200),
    enabled: showFees,
  });
  const { data: stripeSettlements, isLoading: isStripeLoading } = useQuery({
    queryKey: ["stripe-settlements", storeId],
    queryFn: () => ordersService.listStripeSettlementsByStore(storeId),
    enabled: showStripe,
  });

  const availableAmount = (eligibleOrders?.content ?? []).reduce((sum, o) => sum + (o.subtotal - o.platformFee), 0);
  const scheduledAmount = (payouts?.content ?? [])
    .filter((p) => p.status === "scheduled")
    .reduce((sum, p) => sum + p.net, 0);
  const paidAmount = (payouts?.content ?? []).filter((p) => p.status === "paid").reduce((sum, p) => sum + p.net, 0);

  const feeOwedAmount = (feeEligibleOrders?.content ?? []).reduce((sum, o) => sum + o.platformFee, 0);
  const feePendingAmount = (feeCollections?.content ?? [])
    .filter((f) => f.status === "pending")
    .reduce((sum, f) => sum + f.platformFee, 0);
  const feeCollectedAmount = (feeCollections?.content ?? [])
    .filter((f) => f.status === "collected")
    .reduce((sum, f) => sum + f.platformFee, 0);

  const stripeGross = (stripeSettlements?.content ?? []).reduce((sum, o) => sum + o.total, 0);
  const stripeFees = (stripeSettlements?.content ?? []).reduce((sum, o) => sum + o.platformFee, 0);
  const stripeNet = stripeGross - stripeFees;

  return (
    <div className="max-w-6xl space-y-10">
      <div>
        <h1 className="text-2xl font-bold">
          {showPayouts ? "Payouts & fees" : showFees ? "Earnings & fees" : "Earnings"}
        </h1>
        <p className="text-muted-foreground text-sm">
          How money moves depends on the payment method a buyer used — each view below shows which
          direction money is moving for that method.
        </p>
      </div>

      {/* Payouts — PayHere, platform owes seller. LK-only: PayHere is unreachable at checkout
          everywhere else, so this section would always be empty outside LK. */}
      {showPayouts ? (
        <section className="space-y-4">
          <div>
            <h2 className="text-lg font-semibold">Payouts</h2>
            <p className="text-muted-foreground text-sm">
              {name} holds funds from PayHere orders and releases your share in a scheduled payout
              once an order is delivered.
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
                  <h3 className="font-semibold">Payout account</h3>
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
              <h3 className="mb-1 font-semibold">Payout history</h3>
              <p className="text-muted-foreground mb-4 text-xs">
                Payout runs are created and released by {name}, not requested by you — this is a
                read-only ledger of what&apos;s been scheduled and paid.
              </p>
              {isPayoutsLoading ? (
                <div className="divide-y">
                  <TableRowSkeleton columns={5} />
                  <TableRowSkeleton columns={5} />
                </div>
              ) : !payouts || payouts.content.length === 0 ? (
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
                      {payouts.content.map((payout) => (
                        <tr key={payout.id} className="border-b last:border-0">
                          <td className="px-6 py-3 font-medium">{payout.id}</td>
                          <td className="text-muted-foreground px-6 py-3">{formatDate(payout.createdAt)}</td>
                          <td className="text-muted-foreground px-6 py-3">{payout.orders.length}</td>
                          <td className="px-6 py-3 font-medium">{formatCurrency(payout.net, currency)}</td>
                          <td className="px-6 py-3">
                            <StatusBadge tone={payout.status === "paid" ? "success" : "warning"}>
                              {payout.status === "paid" ? "Paid" : "Scheduled"}
                            </StatusBadge>
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
        </section>
      ) : null}

      {/* Fees owed — COD/bank-transfer, seller owes platform. Dead weight on a deployment that
          doesn't offer either at all, see showFees above. */}
      {showFees ? (
      <section className="space-y-4">
        <div>
          <h2 className="text-lg font-semibold">Fees owed</h2>
          <p className="text-muted-foreground text-sm">
            Cash-on-delivery and bank-transfer orders pay you directly — {name} never touches that
            money, so instead it tracks the transaction fee you owe back for those orders.
          </p>
        </div>
        <div className="grid grid-cols-1 gap-4 sm:grid-cols-3">
          <StatCard label="Owed (not yet invoiced)" value={formatCurrency(feeOwedAmount, currency)} icon={Clock} />
          <StatCard label="Pending collection" value={formatCurrency(feePendingAmount, currency)} icon={ReceiptText} />
          <StatCard label="Collected (all time)" value={formatCurrency(feeCollectedAmount, currency)} icon={Landmark} />
        </div>
        <Card>
          <CardContent>
            <h3 className="mb-1 font-semibold">Fee collection history</h3>
            <p className="text-muted-foreground mb-4 text-xs">
              Batches are created by {name} and marked collected once you&apos;ve paid — this is a
              read-only ledger of what&apos;s owed and what&apos;s been settled.
            </p>
            {isFeeCollectionsLoading ? (
              <div className="divide-y">
                <TableRowSkeleton columns={5} />
                <TableRowSkeleton columns={5} />
              </div>
            ) : !feeCollections || feeCollections.content.length === 0 ? (
              <EmptyState icon={ReceiptText} title="No fee collections yet" />
            ) : (
              <div className="-mx-6 overflow-x-auto">
                <table className="w-full text-sm">
                  <thead>
                    <tr className="text-muted-foreground border-y text-left text-xs">
                      <th className="px-6 py-2 font-medium">Batch</th>
                      <th className="px-6 py-2 font-medium">Created</th>
                      <th className="px-6 py-2 font-medium">Orders</th>
                      <th className="px-6 py-2 font-medium">Fee owed</th>
                      <th className="px-6 py-2 font-medium">Status</th>
                      <th className="px-6 py-2 font-medium">Collected</th>
                    </tr>
                  </thead>
                  <tbody>
                    {feeCollections.content.map((fc) => (
                      <tr key={fc.id} className="border-b last:border-0">
                        <td className="px-6 py-3 font-medium">{fc.id}</td>
                        <td className="text-muted-foreground px-6 py-3">{formatDate(fc.createdAt)}</td>
                        <td className="text-muted-foreground px-6 py-3">{fc.orders.length}</td>
                        <td className="px-6 py-3 font-medium">{formatCurrency(fc.platformFee, currency)}</td>
                        <td className="px-6 py-3">
                          <StatusBadge tone={fc.status === "collected" ? "success" : "warning"}>
                            {fc.status === "collected" ? "Collected" : "Pending"}
                          </StatusBadge>
                        </td>
                        <td className="text-muted-foreground px-6 py-3">
                          {fc.collectedAt ? formatDate(fc.collectedAt) : "—"}
                        </td>
                      </tr>
                    ))}
                  </tbody>
                </table>
              </div>
            )}
          </CardContent>
        </Card>
      </section>
      ) : null}

      {/* Stripe — auto-settled, informational only. AU-only for the mirror-image reason: Stripe is
          unreachable at checkout outside AU, so this would always be empty elsewhere. */}
      {showStripe ? (
        <section className="space-y-4">
          <div>
            <h2 className="text-lg font-semibold">Stripe</h2>
            <p className="text-muted-foreground text-sm">
              Stripe pays you directly and automatically at the moment of sale — {name}&apos;s fee is
              deducted at the same time. Nothing here is ever released or collected; it&apos;s a record
              of what already happened.
            </p>
          </div>
          <div className="grid grid-cols-1 gap-4 sm:grid-cols-3">
            <StatCard label="Processed (all time)" value={formatCurrency(stripeGross, currency)} icon={CreditCard} />
            <StatCard label={`Platform fee (${platformFeePercent}%)`} value={formatCurrency(stripeFees, currency)} icon={ReceiptText} />
            <StatCard label="Paid to you automatically" value={formatCurrency(stripeNet, currency)} icon={Landmark} />
          </div>
          <Card>
            <CardContent>
              <h3 className="mb-1 font-semibold">Stripe settlements</h3>
              {isStripeLoading ? (
                <div className="divide-y">
                  <TableRowSkeleton columns={4} />
                  <TableRowSkeleton columns={4} />
                </div>
              ) : !stripeSettlements || stripeSettlements.content.length === 0 ? (
                <EmptyState icon={CreditCard} title="No Stripe orders yet" />
              ) : (
                <div className="-mx-6 overflow-x-auto">
                  <table className="w-full text-sm">
                    <thead>
                      <tr className="text-muted-foreground border-y text-left text-xs">
                        <th className="px-6 py-2 font-medium">Order</th>
                        <th className="px-6 py-2 font-medium">Total</th>
                        <th className="px-6 py-2 font-medium">Platform fee</th>
                        <th className="px-6 py-2 font-medium">Paid to you</th>
                      </tr>
                    </thead>
                    <tbody>
                      {stripeSettlements.content.map((order) => (
                        <tr key={order.id} className="border-b last:border-0">
                          <td className="px-6 py-3 font-medium">{order.orderNumber}</td>
                          <td className="px-6 py-3">{formatCurrency(order.total, currency)}</td>
                          <td className="text-muted-foreground px-6 py-3">{formatCurrency(order.platformFee, currency)}</td>
                          <td className="px-6 py-3 font-medium">
                            {formatCurrency(order.total - order.platformFee, currency)}
                          </td>
                        </tr>
                      ))}
                    </tbody>
                  </table>
                </div>
              )}
            </CardContent>
          </Card>
        </section>
      ) : null}
    </div>
  );
}
