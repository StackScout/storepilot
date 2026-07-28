"use client";

import { useState } from "react";
import { useRouter } from "next/navigation";
import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import { toast } from "sonner";
import { Check, ClipboardCheck, CreditCard, Landmark, LogOut, MapPin, ReceiptText, X } from "lucide-react";
import { Button } from "@/components/ui/button";
import { Card, CardContent } from "@/components/ui/card";
import { Badge } from "@/components/ui/badge";
import { Textarea } from "@/components/ui/textarea";
import { Input } from "@/components/ui/input";
import { Label } from "@/components/ui/label";
import {
  Dialog,
  DialogContent,
  DialogDescription,
  DialogFooter,
  DialogHeader,
  DialogTitle,
} from "@/components/ui/dialog";
import { AbnVerificationBadge } from "@/components/shared/abn-verification-badge";
import { EmptyState } from "@/components/shared/empty-state";
import { TableRowSkeleton } from "@/components/shared/loading-skeletons";
import { NotificationsBell } from "@/components/admin/notifications-bell";
import { formatCurrency } from "@/lib/currency";
import { formatDateTime } from "@/lib/format";
import { getCategoryLabel } from "@/mock/categories";
import { usePlatformConfig } from "@/hooks/use-platform-config";
import { storesService, payoutsService, ordersService, authService } from "@/services";
import type { Store, StoreSettings } from "@/types";

interface PendingApplication {
  store: Store;
  settings: StoreSettings | null;
}

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

export default function AdminPage() {
  const router = useRouter();
  const queryClient = useQueryClient();
  const { currencyCode, currencySymbol, currencyLocale, countryCode } = usePlatformConfig();
  const isSriLanka = countryCode === "LK";
  const currency = { code: currencyCode, symbol: currencySymbol, locale: currencyLocale };
  const [rejectTarget, setRejectTarget] = useState<Store | null>(null);
  const [rejectReason, setRejectReason] = useState("");
  const [payoutToMarkPaid, setPayoutToMarkPaid] = useState<string | null>(null);
  const [bankReference, setBankReference] = useState("");
  const [feeCollectionToMarkCollected, setFeeCollectionToMarkCollected] = useState<string | null>(null);
  const [collectionReference, setCollectionReference] = useState("");

  async function handleSignOut() {
    await authService.logout();
    queryClient.clear();
    router.push("/admin/login");
  }

  const { data: pendingApplications, isLoading: pendingLoading } = useQuery<PendingApplication[]>({
    queryKey: ["admin-pending-stores"],
    queryFn: async () => {
      const stores = await storesService.adminListStores("pending");
      return Promise.all(
        stores.map(async (store) => ({ store, settings: await storesService.getStoreSettings(store.id) })),
      );
    },
  });

  const { data: eligibleStores, isLoading: eligibleLoading } = useQuery<EligibleStore[]>({
    queryKey: ["admin-eligible-stores"],
    queryFn: async () => {
      const stores = await storesService.adminListStores("active");
      const enriched = await Promise.all(
        stores.map(async (store) => {
          const orders = await payoutsService.getEligibleOrdersForPayout(store.id);
          return {
            store,
            eligibleCount: orders.length,
            eligibleNet: orders.reduce((sum, o) => sum + (o.subtotal - o.platformFee), 0),
          };
        }),
      );
      return enriched.filter((s) => s.eligibleCount > 0);
    },
  });

  const { data: allPayouts, isLoading: payoutsLoading } = useQuery({
    queryKey: ["admin-payouts"],
    queryFn: () => payoutsService.adminListPayouts(),
  });

  const { data: eligibleFeeStores, isLoading: eligibleFeeLoading } = useQuery<EligibleFeeStore[]>({
    queryKey: ["admin-eligible-fee-stores"],
    queryFn: async () => {
      const stores = await storesService.adminListStores("active");
      const enriched = await Promise.all(
        stores.map(async (store) => {
          const orders = await payoutsService.getEligibleOrdersForFeeCollection(store.id);
          return {
            store,
            eligibleCount: orders.length,
            eligibleFee: orders.reduce((sum, o) => sum + o.platformFee, 0),
          };
        }),
      );
      return enriched.filter((s) => s.eligibleCount > 0);
    },
  });

  const { data: allFeeCollections, isLoading: feeCollectionsLoading } = useQuery({
    queryKey: ["admin-fee-collections"],
    queryFn: () => payoutsService.adminListFeeCollections(),
  });

  const { data: stripeSettlements, isLoading: stripeSettlementsLoading } = useQuery({
    queryKey: ["admin-stripe-settlements"],
    queryFn: () => ordersService.adminListStripeSettlements(),
  });

  const approveMutation = useMutation({
    mutationFn: (storeId: string) => storesService.setStoreVerificationStatus(storeId, "active"),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ["admin-pending-stores"] });
      queryClient.invalidateQueries({ queryKey: ["admin-eligible-stores"] });
      toast.success("Store approved — it's now live on the marketplace");
    },
    onError: () => toast.error("Couldn't approve this store"),
  });

  const rejectMutation = useMutation({
    mutationFn: ({ storeId, reason }: { storeId: string; reason: string }) =>
      storesService.setStoreVerificationStatus(storeId, "rejected", reason),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ["admin-pending-stores"] });
      toast.success("Application rejected");
      setRejectTarget(null);
      setRejectReason("");
    },
    onError: () => toast.error("Couldn't reject this application"),
  });

  const createPayoutMutation = useMutation({
    mutationFn: (storeId: string) => payoutsService.createPayout(storeId),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ["admin-eligible-stores"] });
      queryClient.invalidateQueries({ queryKey: ["admin-payouts"] });
      toast.success("Payout batch created");
    },
    onError: () => toast.error("Couldn't create payout batch"),
  });

  const markPaidMutation = useMutation({
    mutationFn: ({ payoutId, reference }: { payoutId: string; reference: string }) =>
      payoutsService.markPayoutPaid(payoutId, reference || undefined),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ["admin-payouts"] });
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
      toast.success("Fee collection batch created");
    },
    onError: () => toast.error("Couldn't create fee collection batch"),
  });

  const markCollectedMutation = useMutation({
    mutationFn: ({ feeCollectionId, reference }: { feeCollectionId: string; reference: string }) =>
      payoutsService.markFeeCollectionCollected(feeCollectionId, reference || undefined),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ["admin-fee-collections"] });
      toast.success("Fee collection marked as collected");
      setFeeCollectionToMarkCollected(null);
      setCollectionReference("");
    },
    onError: () => toast.error("Couldn't update fee collection"),
  });

  return (
    <div className="mx-auto max-w-5xl space-y-8 p-4 sm:p-8">
      <div className="flex items-start justify-between gap-4">
        <div>
          <h1 className="text-2xl font-bold">Platform admin</h1>
          <p className="text-muted-foreground text-sm">
            Seller verification and payout runs.
          </p>
        </div>
        <div className="flex items-center gap-2">
          <NotificationsBell />
          <Button type="button" variant="outline" size="sm" onClick={handleSignOut}>
            <LogOut className="size-3.5" /> Sign out
          </Button>
        </div>
      </div>

      <Card>
        <CardContent className="space-y-4">
          <h2 className="font-semibold">Pending store applications</h2>
          {pendingLoading ? (
            <div className="space-y-2">
              <TableRowSkeleton columns={3} />
              <TableRowSkeleton columns={3} />
            </div>
          ) : !pendingApplications || pendingApplications.length === 0 ? (
            <EmptyState icon={ClipboardCheck} title="No pending applications" />
          ) : (
            <div className="space-y-3">
              {pendingApplications.map(({ store, settings }) => (
                <div key={store.id} className="rounded-lg border p-4">
                  <div className="flex flex-wrap items-start justify-between gap-3">
                    <div>
                      <div className="flex items-center gap-2">
                        <p className="font-medium">{store.name}</p>
                        <Badge variant="secondary">{getCategoryLabel(store.category)}</Badge>
                      </div>
                      <p className="text-muted-foreground flex items-center gap-1 text-xs">
                        <MapPin className="size-3" /> {store.address.city}, {store.address.state}
                      </p>
                    </div>
                    <div className="flex gap-2">
                      <Button
                        size="sm"
                        variant="outline"
                        className="text-destructive"
                        onClick={() => setRejectTarget(store)}
                      >
                        <X className="size-3.5" /> Reject
                      </Button>
                      <Button
                        size="sm"
                        disabled={approveMutation.isPending}
                        onClick={() => approveMutation.mutate(store.id)}
                      >
                        <Check className="size-3.5" /> Approve
                      </Button>
                    </div>
                  </div>
                  <dl className="mt-3 grid grid-cols-2 gap-x-4 gap-y-1 border-t pt-3 text-xs sm:grid-cols-4">
                    <div>
                      <dt className="text-muted-foreground">Seller type</dt>
                      <dd className="font-medium capitalize">{settings?.sellerType ?? "—"}</dd>
                    </div>
                    <div>
                      <dt className="text-muted-foreground">
                        {isSriLanka ? "NIC no." : "Driver's licence no."}
                      </dt>
                      <dd className="font-medium">
                        {(isSriLanka ? settings?.nicNumber : settings?.driverLicenceNumber) ?? "—"}
                      </dd>
                    </div>
                    <div>
                      <dt className="text-muted-foreground">{isSriLanka ? "Business reg. no." : "ABN"}</dt>
                      <dd className="font-medium">
                        {(isSriLanka ? settings?.businessRegistrationNumber : settings?.abn) ?? "—"}
                      </dd>
                      {!isSriLanka && settings?.abn ? <AbnVerificationBadge abn={settings.abn} /> : null}
                    </div>
                    <div>
                      <dt className="text-muted-foreground">Bank account</dt>
                      <dd className="font-medium">
                        {settings ? `${settings.bankName} · ${settings.bankAccountNumber}` : "—"}
                      </dd>
                    </div>
                    <div>
                      <dt className="text-muted-foreground">
                        {isSriLanka ? "NIC document" : "Driver's licence document"}
                      </dt>
                      <dd className="font-medium">
                        {(isSriLanka ? settings?.nicDocumentUrl : settings?.driverLicenceDocumentUrl) ? (
                          <a
                            href={(isSriLanka ? settings?.nicDocumentUrl : settings?.driverLicenceDocumentUrl)!}
                            target="_blank"
                            rel="noopener noreferrer"
                            className="text-primary underline-offset-4 hover:underline"
                          >
                            View file
                          </a>
                        ) : (
                          "—"
                        )}
                      </dd>
                    </div>
                    <div>
                      <dt className="text-muted-foreground">
                        {isSriLanka ? "Business reg. document" : "ABN document"}
                      </dt>
                      <dd className="font-medium">
                        {(isSriLanka ? settings?.businessRegDocumentUrl : settings?.abnDocumentUrl) ? (
                          <a
                            href={(isSriLanka ? settings?.businessRegDocumentUrl : settings?.abnDocumentUrl)!}
                            target="_blank"
                            rel="noopener noreferrer"
                            className="text-primary underline-offset-4 hover:underline"
                          >
                            View file
                          </a>
                        ) : (
                          "—"
                        )}
                      </dd>
                    </div>
                  </dl>
                </div>
              ))}
            </div>
          )}
        </CardContent>
      </Card>

      <Card>
        <CardContent className="space-y-4">
          <h2 className="font-semibold">Payout runs</h2>
          <p className="text-muted-foreground text-xs">
            Stores with delivered, paid orders not yet included in a payout batch.
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
                      {eligibleCount} order{eligibleCount === 1 ? "" : "s"} · {formatCurrency(eligibleNet, currency)} net
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
            ) : !allPayouts || allPayouts.length === 0 ? (
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
                    {allPayouts.map((payout) => (
                      <tr key={payout.id} className="border-b last:border-0">
                        <td className="px-6 py-3 font-medium">{payout.storeName}</td>
                        <td className="text-muted-foreground px-6 py-3">
                          {formatDateTime(payout.createdAt)}
                        </td>
                        <td className="px-6 py-3">{formatCurrency(payout.net, currency)}</td>
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

      <Card>
        <CardContent className="space-y-4">
          <h2 className="font-semibold">Fee collection runs</h2>
          <p className="text-muted-foreground text-xs">
            COD/bank-transfer orders pay the seller directly, so these stores owe the platform its
            transaction fee back — not the other way around, unlike payout runs above.
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
                      {eligibleCount} order{eligibleCount === 1 ? "" : "s"} · {formatCurrency(eligibleFee, currency)} owed
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
            ) : !allFeeCollections || allFeeCollections.length === 0 ? (
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
                    {allFeeCollections.map((fc) => (
                      <tr key={fc.id} className="border-b last:border-0">
                        <td className="px-6 py-3 font-medium">{fc.storeName}</td>
                        <td className="text-muted-foreground px-6 py-3">
                          {formatDateTime(fc.createdAt)}
                        </td>
                        <td className="px-6 py-3">{formatCurrency(fc.platformFee, currency)}</td>
                        <td className="px-6 py-3">
                          <Badge
                            className={
                              fc.status === "collected"
                                ? "border-0 bg-emerald-100 text-emerald-800 dark:bg-emerald-950 dark:text-emerald-300"
                                : "border-0 bg-amber-100 text-amber-800 dark:bg-amber-950 dark:text-amber-300"
                            }
                          >
                            {fc.status === "collected" ? "Collected" : "Pending"}
                          </Badge>
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

      <Card>
        <CardContent className="space-y-4">
          <h2 className="font-semibold">Stripe settlements</h2>
          <p className="text-muted-foreground text-xs">
            Stripe pays sellers directly and automatically at the moment of sale — read-only, never
            a batch to release or collect.
          </p>
          {stripeSettlementsLoading ? (
            <TableRowSkeleton columns={4} />
          ) : !stripeSettlements || stripeSettlements.length === 0 ? (
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
                  {stripeSettlements.map((order) => (
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

      <Dialog open={!!rejectTarget} onOpenChange={(open) => !open && setRejectTarget(null)}>
        <DialogContent>
          <DialogHeader>
            <DialogTitle>Reject &quot;{rejectTarget?.name}&quot;?</DialogTitle>
            <DialogDescription>
              Give a reason — the seller will see this on their dashboard.
            </DialogDescription>
          </DialogHeader>
          <Textarea
            rows={3}
            placeholder={
              isSriLanka
                ? "e.g. NIC number couldn't be verified"
                : "e.g. Driver's licence number couldn't be verified"
            }
            value={rejectReason}
            onChange={(e) => setRejectReason(e.target.value)}
          />
          <DialogFooter>
            <Button variant="outline" onClick={() => setRejectTarget(null)}>
              Cancel
            </Button>
            <Button
              variant="destructive"
              disabled={!rejectReason.trim() || rejectMutation.isPending}
              onClick={() =>
                rejectTarget && rejectMutation.mutate({ storeId: rejectTarget.id, reason: rejectReason })
              }
            >
              Reject application
            </Button>
          </DialogFooter>
        </DialogContent>
      </Dialog>

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
    </div>
  );
}
