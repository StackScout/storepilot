"use client";

import { useState } from "react";
import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import { toast } from "sonner";
import { Check, ClipboardCheck, History, MapPin, X } from "lucide-react";
import { Button } from "@/components/ui/button";
import { Card, CardContent } from "@/components/ui/card";
import { Badge } from "@/components/ui/badge";
import { Textarea } from "@/components/ui/textarea";
import { Tabs, TabsContent, TabsList, TabsTrigger } from "@/components/ui/tabs";
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
import { formatDateTime } from "@/lib/format";
import { getCategoryLabel } from "@/mock/categories";
import { usePlatformConfig } from "@/hooks/use-platform-config";
import { storesService, adminService } from "@/services";
import type { Store, StoreSettings } from "@/types";

interface PendingApplication {
  store: Store;
  settings: StoreSettings | null;
}

export default function AdminStoresPage() {
  const queryClient = useQueryClient();
  const { countryCode } = usePlatformConfig();
  const isSriLanka = countryCode === "LK";
  const [rejectTarget, setRejectTarget] = useState<Store | null>(null);
  const [rejectReason, setRejectReason] = useState("");

  const { data: pendingApplications, isLoading: pendingLoading } = useQuery<PendingApplication[]>({
    queryKey: ["admin-pending-stores"],
    queryFn: async () => {
      const stores = await storesService.adminListStores("pending");
      return Promise.all(
        stores.map(async (store) => ({ store, settings: await storesService.getStoreSettings(store.id) })),
      );
    },
  });

  const { data: decisionHistory, isLoading: historyLoading } = useQuery({
    queryKey: ["admin-store-decision-history"],
    queryFn: () => adminService.listAuditLog({ targetType: "store", size: 50 }),
  });

  const approveMutation = useMutation({
    mutationFn: (storeId: string) => storesService.setStoreVerificationStatus(storeId, "active"),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ["admin-pending-stores"] });
      queryClient.invalidateQueries({ queryKey: ["admin-store-decision-history"] });
      queryClient.invalidateQueries({ queryKey: ["admin-pending-stores-count"] });
      toast.success("Store approved — it's now live on the marketplace");
    },
    onError: () => toast.error("Couldn't approve this store"),
  });

  const rejectMutation = useMutation({
    mutationFn: ({ storeId, reason }: { storeId: string; reason: string }) =>
      storesService.setStoreVerificationStatus(storeId, "rejected", reason),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ["admin-pending-stores"] });
      queryClient.invalidateQueries({ queryKey: ["admin-store-decision-history"] });
      queryClient.invalidateQueries({ queryKey: ["admin-pending-stores-count"] });
      toast.success("Application rejected");
      setRejectTarget(null);
      setRejectReason("");
    },
    onError: () => toast.error("Couldn't reject this application"),
  });

  return (
    <div className="space-y-6">
      <div>
        <h1 className="text-2xl font-bold">Store approvals</h1>
        <p className="text-muted-foreground text-sm">Review new seller applications and see past decisions.</p>
      </div>

      <Tabs defaultValue="pending">
        <TabsList>
          <TabsTrigger value="pending">Pending</TabsTrigger>
          <TabsTrigger value="history">Decision history</TabsTrigger>
        </TabsList>

        <TabsContent value="pending">
          <Card>
            <CardContent className="space-y-4">
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
        </TabsContent>

        <TabsContent value="history">
          <Card>
            <CardContent className="p-0">
              {historyLoading ? (
                <div className="space-y-2 p-6">
                  <TableRowSkeleton columns={2} />
                  <TableRowSkeleton columns={2} />
                </div>
              ) : !decisionHistory || decisionHistory.content.length === 0 ? (
                <EmptyState icon={History} title="No decisions recorded yet" />
              ) : (
                <ul className="divide-y">
                  {decisionHistory.content.map((entry) => (
                    <li key={entry.id} className="flex items-start justify-between gap-4 p-4">
                      <div className="space-y-0.5">
                        <div className="flex items-center gap-2">
                          <Badge
                            className={
                              entry.action === "store_approved"
                                ? "border-0 bg-emerald-100 text-emerald-800 dark:bg-emerald-950 dark:text-emerald-300"
                                : "border-0 bg-red-100 text-red-800 dark:bg-red-950 dark:text-red-300"
                            }
                          >
                            {entry.action === "store_approved" ? "Approved" : "Rejected"}
                          </Badge>
                          <p className="text-sm">{entry.description}</p>
                        </div>
                        <p className="text-muted-foreground text-xs">
                          {entry.actorEmail} · {formatDateTime(entry.createdAt)}
                        </p>
                      </div>
                    </li>
                  ))}
                </ul>
              )}
            </CardContent>
          </Card>
        </TabsContent>
      </Tabs>

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
    </div>
  );
}
