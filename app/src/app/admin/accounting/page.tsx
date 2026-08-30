"use client";

import { useState } from "react";
import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import { toast } from "sonner";
import { CreditCard, Landmark, ReceiptText, RotateCcw, Wallet } from "lucide-react";
import { Button } from "@/components/ui/button";
import { Card, CardContent } from "@/components/ui/card";
import { StatusBadge } from "@/components/shared/status-badge";
import { Input } from "@/components/ui/input";
import { Label } from "@/components/ui/label";
import { Tabs, TabsContent, TabsList, TabsTrigger } from "@/components/ui/tabs";
import {
  Dialog,
  DialogContent,
  DialogDescription,
  DialogFooter,
  DialogHeader,
  DialogTitle,
} from "@/components/ui/dialog";
import { EmptyState } from "@/components/shared/empty-state";
import { TableRowSkeleton } from "@/components/shared/loading-skeletons";
import { formatCurrency } from "@/lib/currency";
import { formatDateTime, returnReasonLabel, returnStatusLabel } from "@/lib/format";
import { cn } from "@/lib/utils";
import { usePlatformConfig } from "@/hooks/use-platform-config";
import { queryKeys } from "@/lib/query-keys";
import { storesService, payoutsService, ordersService, adminService, returnsService } from "@/services";
import type { Store } from "@/types";

interface EligibleStore {
  store: Store;
  eligibleNet: number;
  eligibleCount: number;
}

interface EligibleFeeStore {
  store: Store;
  eligibleFee: number;
  eligibleCount: number;
}

export default function AdminAccountingPage() {
  const queryClient = useQueryClient();
  const { countryCode, currencyCode, currencySymbol, currencyLocale, defaultCodEnabled, defaultBankTransferEnabled } = usePlatformConfig();
  const currency = { code: currencyCode, symbol: currencySymbol, locale: currencyLocale };
  // Payouts only ever contain PayHere-funded money (LK-only) — see PayoutService.getEligibleOrders'
  // doc comment. Outside LK this tab would always be empty, so hide it rather than show dead UI.
  const showPayouts = countryCode === "LK";
  // Fee collections only ever contain cod/bank-transfer orders (the platform never touches that
  // money) — dead weight on a deployment that doesn't offer either at all, see PlatformSettings'
  // default*Enabled doc comment.
  const showFees = defaultCodEnabled || defaultBankTransferEnabled;
  const [payoutToMarkPaid, setPayoutToMarkPaid] = useState<string | null>(null);
  const [bankReference, setBankReference] = useState("");
  const [feeCollectionToMarkCollected, setFeeCollectionToMarkCollected] = useState<string | null>(null);
  const [collectionReference, setCollectionReference] = useState("");
  const [returnToMarkRefunded, setReturnToMarkRefunded] = useState<string | null>(null);
  const [returnRefundReference, setReturnRefundReference] = useState("");

  const { data: summary } = useQuery({
    queryKey: queryKeys.admin.accountingSummary(),
    queryFn: () => adminService.getAccountingSummary(),
  });

  const { data: eligibleStores, isLoading: eligibleLoading } = useQuery<EligibleStore[]>({
    queryKey: ["admin-eligible-stores"],
    enabled: showPayouts,
    queryFn: async () => {
      const stores = await storesService.adminListStores("active");
      const enriched = await Promise.all(
        stores.content.map(async (store) => {
          // A payout batch bundles every eligible order AND booking for a
          // store into one run — see PayoutSourceRef's doc comment.
          const [orders, bookings] = await Promise.all([
            payoutsService.adminGetEligibleOrdersForPayout(store.id, 0, 200),
            payoutsService.adminGetEligibleBookingsForPayout(store.id, 0, 200),
          ]);
          return {
            store,
            eligibleCount: orders.content.length + bookings.content.length,
            eligibleNet:
              orders.content.reduce((sum, o) => sum + (o.subtotal - o.platformFee), 0) +
              bookings.content.reduce((sum, b) => sum + (b.servicePrice - b.platformFee), 0),
          };
        }),
      );
      return enriched.filter((s) => s.eligibleCount > 0);
    },
  });

  const { data: allPayouts, isLoading: payoutsLoading } = useQuery({
    queryKey: ["admin-payouts"],
    queryFn: () => payoutsService.adminListPayouts(),
    enabled: showPayouts,
  });

  const { data: eligibleFeeStores, isLoading: eligibleFeeLoading } = useQuery<EligibleFeeStore[]>({
    queryKey: ["admin-eligible-fee-stores"],
    enabled: showFees,
    queryFn: async () => {
      const stores = await storesService.adminListStores("active");
      const enriched = await Promise.all(
        stores.content.map(async (store) => {
          const [orders, bookings] = await Promise.all([
            payoutsService.adminGetEligibleOrdersForFeeCollection(store.id, 0, 200),
            payoutsService.adminGetEligibleBookingsForFeeCollection(store.id, 0, 200),
          ]);
          return {
            store,
            eligibleCount: orders.content.length + bookings.content.length,
            eligibleFee:
              orders.content.reduce((sum, o) => sum + o.platformFee, 0) +
              bookings.content.reduce((sum, b) => sum + b.platformFee, 0),
          };
        }),
      );
      return enriched.filter((s) => s.eligibleCount > 0);
    },
  });

  const { data: allFeeCollections, isLoading: feeCollectionsLoading } = useQuery({
    queryKey: ["admin-fee-collections"],
    queryFn: () => payoutsService.adminListFeeCollections(),
    enabled: showFees,
  });

  const { data: stripeSettlements, isLoading: stripeSettlementsLoading } = useQuery({
    queryKey: ["admin-stripe-settlements"],
    queryFn: () => ordersService.adminListStripeSettlements(),
  });

  const { data: allReturns, isLoading: returnsLoading } = useQuery({
    queryKey: ["admin-returns"],
    queryFn: () => returnsService.adminListReturns(),
  });

  function invalidateAccounting() {
    queryClient.invalidateQueries({ queryKey: queryKeys.admin.accountingSummary() });
  }

  const createPayoutMutation = useMutation({
    mutationFn: (storeId: string) => payoutsService.createPayout(storeId),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ["admin-eligible-stores"] });
      queryClient.invalidateQueries({ queryKey: ["admin-payouts"] });
      invalidateAccounting();
      toast.success("Payout batch created");
    },
    onError: () => toast.error("Couldn't create payout batch"),
  });

  const markPaidMutation = useMutation({
    mutationFn: ({ payoutId, reference }: { payoutId: string; reference: string }) =>
      payoutsService.markPayoutPaid(payoutId, reference || undefined),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ["admin-payouts"] });
      invalidateAccounting();
      toast.success("Payout marked as paid");
      setPayoutToMarkPaid(null);
      setBankReference("");
    },
    onError: () => toast.error("Couldn't update payout"),
  });

  const createFeeCollectionMutation = useMutation({
    mutationFn: (storeId: string) => payoutsService.createFeeCollection(storeId),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ["admin-eligible-fee-stores"] });
      queryClient.invalidateQueries({ queryKey: ["admin-fee-collections"] });
      invalidateAccounting();
      toast.success("Fee collection batch created");
    },
    onError: () => toast.error("Couldn't create fee collection batch"),
  });

  const markCollectedMutation = useMutation({
    mutationFn: ({ feeCollectionId, reference }: { feeCollectionId: string; reference: string }) =>
      payoutsService.markFeeCollectionCollected(feeCollectionId, reference || undefined),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ["admin-fee-collections"] });
      invalidateAccounting();
      toast.success("Fee collection marked as collected");
      setFeeCollectionToMarkCollected(null);
      setCollectionReference("");
    },
    onError: () => toast.error("Couldn't update fee collection"),
  });

  const markReturnRefundedMutation = useMutation({
    mutationFn: ({ returnId, reference }: { returnId: string; reference: string }) =>
      returnsService.adminMarkReturnRefunded(returnId, reference || undefined),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ["admin-returns"] });
      toast.success("Return marked as refunded");
      setReturnToMarkRefunded(null);
      setReturnRefundReference("");
    },
    onError: (err) => toast.error(err instanceof Error ? err.message : "Couldn't update the return"),
  });

  return (
    <div className="space-y-6">
      <div>
        <h1 className="text-2xl font-bold">Accounting</h1>
        <p className="text-muted-foreground text-sm">
          {showPayouts ? "Payouts, fee collections, and Stripe settlements." : "Fee collections and Stripe settlements."}
        </p>
      </div>

      <div className={cn("grid gap-4", showPayouts ? "sm:grid-cols-3" : "sm:grid-cols-2")}>
        {showPayouts ? (
          <Card>
            <CardContent className="space-y-1">
              <p className="text-muted-foreground text-xs">Payouts scheduled / paid</p>
              <p className="text-lg font-semibold">
                {summary ? formatCurrency(summary.payoutsScheduledTotal, currency) : "—"}
                <span className="text-muted-foreground text-sm font-normal">
                  {" "}
                  / {summary ? formatCurrency(summary.payoutsPaidTotal, currency) : "—"}
                </span>
              </p>
            </CardContent>
          </Card>
        ) : null}
        <Card>
          <CardContent className="space-y-1">
            <p className="text-muted-foreground text-xs">Fees pending / collected</p>
            <p className="text-lg font-semibold">
              {summary ? formatCurrency(summary.feeCollectionsPendingTotal, currency) : "—"}
              <span className="text-muted-foreground text-sm font-normal">
                {" "}
                / {summary ? formatCurrency(summary.feeCollectionsCollectedTotal, currency) : "—"}
              </span>
            </p>
          </CardContent>
        </Card>
        <Card>
          <CardContent className="space-y-1">
            <p className="text-muted-foreground text-xs">Stripe settled / platform fee</p>
            <p className="text-lg font-semibold">
              {summary ? formatCurrency(summary.stripeSettledTotal, currency) : "—"}
              <span className="text-muted-foreground text-sm font-normal">
                {" "}
                / {summary ? formatCurrency(summary.stripePlatformFeeTotal, currency) : "—"}
              </span>
            </p>
          </CardContent>
        </Card>
      </div>

      <Tabs defaultValue={showPayouts ? "payouts" : "stripe"}>
        <div className="overflow-x-auto">
          <TabsList>
            {showPayouts ? (
              <TabsTrigger value="payouts">
                <Wallet className="size-3.5" /> Payouts
              </TabsTrigger>
            ) : null}
            {showFees ? (
              <TabsTrigger value="fee-collections">
                <ReceiptText className="size-3.5" /> Fee collections
              </TabsTrigger>
            ) : null}
            <TabsTrigger value="stripe">
              <CreditCard className="size-3.5" /> Stripe settlements
            </TabsTrigger>
            <TabsTrigger value="returns">
              <RotateCcw className="size-3.5" /> Returns
            </TabsTrigger>
          </TabsList>
        </div>

        {showPayouts ? (
          <TabsContent value="payouts">
            <Card>
              <CardContent className="space-y-4">
                <p className="text-muted-foreground text-xs">
                  Stores with delivered orders or completed bookings, paid via PayHere, not yet included
                  in a payout batch.
                </p>
                {eligibleLoading ? (
                  <TableRowSkeleton columns={3} />
                ) : !eligibleStores || eligibleStores.length === 0 ? (
                  <EmptyState icon={Landmark} title="Nothing eligible for payout right now" />
                ) : (
                  <div className="space-y-2">
                    {eligibleStores.map(({ store, eligibleCount, eligibleNet }) => (
                      <div
                        key={store.id}
                        className="flex flex-wrap items-center justify-between gap-3 rounded-lg border p-3"
                      >
                        <div>
                          <p className="text-sm font-medium">{store.name}</p>
                          <p className="text-muted-foreground text-xs">
                            {eligibleCount} order{eligibleCount === 1 ? "" : "s"} ·{" "}
                            {formatCurrency(eligibleNet, currency)} net
                          </p>
                        </div>
                        <Button
                          size="sm"
                          disabled={createPayoutMutation.isPending}
                          onClick={() => createPayoutMutation.mutate(store.id)}
                        >
                          Create payout batch
                        </Button>
                      </div>
                    ))}
                  </div>
                )}

                <div className="border-t pt-4">
                  <h3 className="mb-2 text-sm font-semibold">All payouts</h3>
                  {payoutsLoading ? (
                    <TableRowSkeleton columns={5} />
                  ) : !allPayouts || allPayouts.content.length === 0 ? (
                    <p className="text-muted-foreground text-sm">No payouts created yet.</p>
                  ) : (
                    <div className="-mx-6 overflow-x-auto">
                      <table className="w-full text-sm">
                        <thead>
                          <tr className="text-muted-foreground border-y text-left text-xs">
                            <th className="px-6 py-2 font-medium">Store</th>
                            <th className="px-6 py-2 font-medium">Created</th>
                            <th className="px-6 py-2 font-medium">Net</th>
                            <th className="px-6 py-2 font-medium">Status</th>
                            <th className="px-6 py-2 font-medium">Action</th>
                          </tr>
                        </thead>
                        <tbody>
                          {allPayouts.content.map((payout) => (
                            <tr key={payout.id} className="border-b last:border-0">
                              <td className="px-6 py-3 font-medium">{payout.storeName}</td>
                              <td className="text-muted-foreground px-6 py-3">
                                {formatDateTime(payout.createdAt)}
                              </td>
                              <td className="px-6 py-3">{formatCurrency(payout.net, currency)}</td>
                              <td className="px-6 py-3">
                                <StatusBadge tone={payout.status === "paid" ? "success" : "warning"}>
                                  {payout.status === "paid" ? "Paid" : "Scheduled"}
                                </StatusBadge>
                              </td>
                              <td className="px-6 py-3">
                                {payout.status === "scheduled" ? (
                                  <Button size="sm" variant="outline" onClick={() => setPayoutToMarkPaid(payout.id)}>
                                    Mark as paid
                                  </Button>
                                ) : (
                                  <span className="text-muted-foreground text-xs">
                                    {payout.bankReference ?? "—"}
                                  </span>
                                )}
                              </td>
                            </tr>
                          ))}
                        </tbody>
                      </table>
                    </div>
                  )}
                </div>
              </CardContent>
            </Card>
          </TabsContent>
        ) : null}

        {showFees ? (
        <TabsContent value="fee-collections">
          <Card>
            <CardContent className="space-y-4">
              <p className="text-muted-foreground text-xs">
                COD/bank-transfer orders and &quot;pay at venue&quot;/bank-transfer bookings pay the
                seller directly, so these stores owe the platform its transaction fee back — not the
                other way around, unlike payouts.
              </p>
              {eligibleFeeLoading ? (
                <TableRowSkeleton columns={3} />
              ) : !eligibleFeeStores || eligibleFeeStores.length === 0 ? (
                <EmptyState icon={ReceiptText} title="Nothing owed right now" />
              ) : (
                <div className="space-y-2">
                  {eligibleFeeStores.map(({ store, eligibleCount, eligibleFee }) => (
                    <div
                      key={store.id}
                      className="flex flex-wrap items-center justify-between gap-3 rounded-lg border p-3"
                    >
                      <div>
                        <p className="text-sm font-medium">{store.name}</p>
                        <p className="text-muted-foreground text-xs">
                          {eligibleCount} order{eligibleCount === 1 ? "" : "s"} ·{" "}
                          {formatCurrency(eligibleFee, currency)} owed
                        </p>
                      </div>
                      <Button
                        size="sm"
                        disabled={createFeeCollectionMutation.isPending}
                        onClick={() => createFeeCollectionMutation.mutate(store.id)}
                      >
                        Create fee collection batch
                      </Button>
                    </div>
                  ))}
                </div>
              )}

              <div className="border-t pt-4">
                <h3 className="mb-2 text-sm font-semibold">All fee collections</h3>
                {feeCollectionsLoading ? (
                  <TableRowSkeleton columns={5} />
                ) : !allFeeCollections || allFeeCollections.content.length === 0 ? (
                  <p className="text-muted-foreground text-sm">No fee collections created yet.</p>
                ) : (
                  <div className="-mx-6 overflow-x-auto">
                    <table className="w-full text-sm">
                      <thead>
                        <tr className="text-muted-foreground border-y text-left text-xs">
                          <th className="px-6 py-2 font-medium">Store</th>
                          <th className="px-6 py-2 font-medium">Created</th>
                          <th className="px-6 py-2 font-medium">Fee owed</th>
                          <th className="px-6 py-2 font-medium">Status</th>
                          <th className="px-6 py-2 font-medium">Action</th>
                        </tr>
                      </thead>
                      <tbody>
                        {allFeeCollections.content.map((fc) => (
                          <tr key={fc.id} className="border-b last:border-0">
                            <td className="px-6 py-3 font-medium">{fc.storeName}</td>
                            <td className="text-muted-foreground px-6 py-3">
                              {formatDateTime(fc.createdAt)}
                            </td>
                            <td className="px-6 py-3">{formatCurrency(fc.platformFee, currency)}</td>
                            <td className="px-6 py-3">
                              <StatusBadge tone={fc.status === "collected" ? "success" : "warning"}>
                                {fc.status === "collected" ? "Collected" : "Pending"}
                              </StatusBadge>
                            </td>
                            <td className="px-6 py-3">
                              {fc.status === "pending" ? (
                                <Button
                                  size="sm"
                                  variant="outline"
                                  onClick={() => setFeeCollectionToMarkCollected(fc.id)}
                                >
                                  Mark as collected
                                </Button>
                              ) : (
                                <span className="text-muted-foreground text-xs">{fc.reference ?? "—"}</span>
                              )}
                            </td>
                          </tr>
                        ))}
                      </tbody>
                    </table>
                  </div>
                )}
              </div>
            </CardContent>
          </Card>
        </TabsContent>
        ) : null}

        <TabsContent value="stripe">
          <Card>
            <CardContent className="space-y-4">
              <p className="text-muted-foreground text-xs">
                Stripe pays sellers directly and automatically at the moment of sale — read-only, never
                a batch to release or collect.
              </p>
              {stripeSettlementsLoading ? (
                <TableRowSkeleton columns={4} />
              ) : !stripeSettlements || stripeSettlements.content.length === 0 ? (
                <EmptyState icon={CreditCard} title="No Stripe orders yet" />
              ) : (
                <div className="-mx-6 overflow-x-auto">
                  <table className="w-full text-sm">
                    <thead>
                      <tr className="text-muted-foreground border-y text-left text-xs">
                        <th className="px-6 py-2 font-medium">Store</th>
                        <th className="px-6 py-2 font-medium">Order</th>
                        <th className="px-6 py-2 font-medium">Total</th>
                        <th className="px-6 py-2 font-medium">Platform fee</th>
                      </tr>
                    </thead>
                    <tbody>
                      {stripeSettlements.content.map((order) => (
                        <tr key={order.id} className="border-b last:border-0">
                          <td className="px-6 py-3 font-medium">{order.storeName}</td>
                          <td className="text-muted-foreground px-6 py-3">{order.orderNumber}</td>
                          <td className="px-6 py-3">{formatCurrency(order.total, currency)}</td>
                          <td className="text-muted-foreground px-6 py-3">
                            {formatCurrency(order.platformFee, currency)}
                          </td>
                        </tr>
                      ))}
                    </tbody>
                  </table>
                </div>
              )}
            </CardContent>
          </Card>
        </TabsContent>

        <TabsContent value="returns">
          <Card>
            <CardContent className="space-y-4">
              <p className="text-muted-foreground text-xs">
                {showPayouts
                  ? "Buyer return requests across every store. PayHere refunds need admin confirmation here — the platform's own merchant account is the one holding that money. Every other payment method is self-attested by the seller on the order page."
                  : "Buyer return requests across every store, self-attested by the seller on the order page."}
              </p>
              {returnsLoading ? (
                <TableRowSkeleton columns={5} />
              ) : !allReturns || allReturns.content.length === 0 ? (
                <EmptyState icon={RotateCcw} title="No returns yet" />
              ) : (
                <div className="-mx-6 overflow-x-auto">
                  <table className="w-full text-sm">
                    <thead>
                      <tr className="text-muted-foreground border-y text-left text-xs">
                        <th className="px-6 py-2 font-medium">Store</th>
                        <th className="px-6 py-2 font-medium">Order</th>
                        <th className="px-6 py-2 font-medium">Reason</th>
                        <th className="px-6 py-2 font-medium">Payment</th>
                        <th className="px-6 py-2 font-medium">Status</th>
                        <th className="px-6 py-2 font-medium">Action</th>
                      </tr>
                    </thead>
                    <tbody>
                      {allReturns.content.map((r) => (
                        <tr key={r.id} className="border-b last:border-0">
                          <td className="px-6 py-3 font-medium">{r.storeName}</td>
                          <td className="text-muted-foreground px-6 py-3">{r.orderNumber}</td>
                          <td className="px-6 py-3">
                            {returnReasonLabel(r.reasonCategory)}
                            {r.settlementReconciliationNote ? (
                              <p className="text-warning-foreground mt-1 text-xs">{r.settlementReconciliationNote}</p>
                            ) : null}
                          </td>
                          <td className="text-muted-foreground px-6 py-3 capitalize">{r.paymentMethod}</td>
                          <td className="px-6 py-3">
                            <StatusBadge tone={r.status === "refunded" ? "success" : r.status === "rejected" ? "danger" : "warning"}>
                              {returnStatusLabel(r.status)}
                            </StatusBadge>
                          </td>
                          <td className="px-6 py-3">
                            {r.status === "refund-pending" && r.paymentMethod === "payhere" ? (
                              <Button size="sm" variant="outline" onClick={() => setReturnToMarkRefunded(r.id)}>
                                Mark refunded
                              </Button>
                            ) : r.status === "refunded" ? (
                              <span className="text-muted-foreground text-xs">{r.refundReference ?? "—"}</span>
                            ) : null}
                          </td>
                        </tr>
                      ))}
                    </tbody>
                  </table>
                </div>
              )}
            </CardContent>
          </Card>
        </TabsContent>
      </Tabs>

      <Dialog open={!!payoutToMarkPaid} onOpenChange={(open) => !open && setPayoutToMarkPaid(null)}>
        <DialogContent>
          <DialogHeader>
            <DialogTitle>Mark payout as paid</DialogTitle>
            <DialogDescription>
              Record the bank transfer reference once you&apos;ve actually sent the funds.
            </DialogDescription>
          </DialogHeader>
          <div className="space-y-1.5">
            <Label htmlFor="bankReference">Bank reference (optional)</Label>
            <Input
              id="bankReference"
              placeholder="e.g. CBC-TRF-88214"
              value={bankReference}
              onChange={(e) => setBankReference(e.target.value)}
            />
          </div>
          <DialogFooter>
            <Button variant="outline" onClick={() => setPayoutToMarkPaid(null)}>
              Cancel
            </Button>
            <Button
              disabled={markPaidMutation.isPending}
              onClick={() =>
                payoutToMarkPaid &&
                markPaidMutation.mutate({ payoutId: payoutToMarkPaid, reference: bankReference })
              }
            >
              Confirm paid
            </Button>
          </DialogFooter>
        </DialogContent>
      </Dialog>

      <Dialog
        open={!!feeCollectionToMarkCollected}
        onOpenChange={(open) => !open && setFeeCollectionToMarkCollected(null)}
      >
        <DialogContent>
          <DialogHeader>
            <DialogTitle>Mark fee collection as collected</DialogTitle>
            <DialogDescription>
              Record a reference once the seller has actually paid the platform this fee.
            </DialogDescription>
          </DialogHeader>
          <div className="space-y-1.5">
            <Label htmlFor="collectionReference">Reference (optional)</Label>
            <Input
              id="collectionReference"
              placeholder="e.g. INV-88214"
              value={collectionReference}
              onChange={(e) => setCollectionReference(e.target.value)}
            />
          </div>
          <DialogFooter>
            <Button variant="outline" onClick={() => setFeeCollectionToMarkCollected(null)}>
              Cancel
            </Button>
            <Button
              disabled={markCollectedMutation.isPending}
              onClick={() =>
                feeCollectionToMarkCollected &&
                markCollectedMutation.mutate({
                  feeCollectionId: feeCollectionToMarkCollected,
                  reference: collectionReference,
                })
              }
            >
              Confirm collected
            </Button>
          </DialogFooter>
        </DialogContent>
      </Dialog>

      <Dialog open={!!returnToMarkRefunded} onOpenChange={(open) => !open && setReturnToMarkRefunded(null)}>
        <DialogContent>
          <DialogHeader>
            <DialogTitle>Mark return as refunded</DialogTitle>
            <DialogDescription>
              Record a reference once the PayHere refund has actually been sent to the buyer.
            </DialogDescription>
          </DialogHeader>
          <div className="space-y-1.5">
            <Label htmlFor="returnRefundReference">Reference (optional)</Label>
            <Input
              id="returnRefundReference"
              placeholder="e.g. payhere-refund-88214"
              value={returnRefundReference}
              onChange={(e) => setReturnRefundReference(e.target.value)}
            />
          </div>
          <DialogFooter>
            <Button variant="outline" onClick={() => setReturnToMarkRefunded(null)}>
              Cancel
            </Button>
            <Button
              disabled={markReturnRefundedMutation.isPending}
              onClick={() =>
                returnToMarkRefunded &&
                markReturnRefundedMutation.mutate({ returnId: returnToMarkRefunded, reference: returnRefundReference })
              }
            >
              Confirm refunded
            </Button>
          </DialogFooter>
        </DialogContent>
      </Dialog>
    </div>
  );
}
