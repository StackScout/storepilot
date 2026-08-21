"use client";

import { useState } from "react";
import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import { toast } from "sonner";
import { ClipboardCheck, FileEdit, History, Store as StoreIcon } from "lucide-react";
import { Button } from "@/components/ui/button";
import { Card, CardContent } from "@/components/ui/card";
import { Badge } from "@/components/ui/badge";
import { StatusBadge, type StatusTone } from "@/components/shared/status-badge";
import { Textarea } from "@/components/ui/textarea";
import { Tabs, TabsContent, TabsList, TabsTrigger } from "@/components/ui/tabs";
import {
  Select,
  SelectContent,
  SelectItem,
  SelectTrigger,
  SelectValue,
} from "@/components/ui/select";
import {
  Dialog,
  DialogContent,
  DialogDescription,
  DialogFooter,
  DialogHeader,
  DialogTitle,
} from "@/components/ui/dialog";
import { StoreDetailCard } from "@/components/admin/store-detail-card";
import { EmptyState } from "@/components/shared/empty-state";
import { TableRowSkeleton } from "@/components/shared/loading-skeletons";
import { formatDateTime } from "@/lib/format";
import { usePlatformConfig } from "@/hooks/use-platform-config";
import { storesService, adminService } from "@/services";
import type { Store, StoreSettings, StoreVerificationChangeRequest, StoreVerificationStatus } from "@/types";

interface StoreWithSettings {
  store: Store;
  settings: StoreSettings | null;
}

type StatusFilter = StoreVerificationStatus | "all";

const STATUS_FILTERS: { value: StatusFilter; label: string }[] = [
  { value: "all", label: "All statuses" },
  { value: "pending", label: "Pending" },
  { value: "active", label: "Active" },
  { value: "rejected", label: "Rejected" },
];

/** Every AuditAction whose targetType is "store" — see backend AuditAction.kt — covers what the "Decision history" tab can show. */
const DECISION_LABELS: Record<string, string> = {
  store_approved: "Approved",
  store_rejected: "Rejected",
  store_settings_updated: "Settings updated",
  store_verification_change_requested: "Change requested",
  store_verification_change_approved: "Change approved",
  store_verification_change_rejected: "Change rejected",
};

const DECISION_TONES: Record<string, StatusTone> = {
  store_approved: "success",
  store_verification_change_approved: "success",
  store_rejected: "danger",
  store_verification_change_rejected: "danger",
  store_verification_change_requested: "warning",
};

export default function AdminStoresPage() {
  const queryClient = useQueryClient();
  const { countryCode } = usePlatformConfig();
  const isSriLanka = countryCode === "LK";
  const [rejectTarget, setRejectTarget] = useState<Store | null>(null);
  const [rejectReason, setRejectReason] = useState("");
  const [statusFilter, setStatusFilter] = useState<StatusFilter>("all");
  const [rejectChangeRequestTarget, setRejectChangeRequestTarget] = useState<StoreVerificationChangeRequest | null>(
    null,
  );
  const [rejectChangeRequestReason, setRejectChangeRequestReason] = useState("");

  const { data: pendingApplications, isLoading: pendingLoading } = useQuery<StoreWithSettings[]>({
    queryKey: ["admin-pending-stores"],
    queryFn: async () => {
      const stores = await storesService.adminListStores("pending");
      return Promise.all(
        stores.map(async (store) => ({ store, settings: await storesService.adminGetStoreSettings(store.id) })),
      );
    },
  });

  const { data: allStores, isLoading: allStoresLoading } = useQuery<StoreWithSettings[]>({
    queryKey: ["admin-all-stores"],
    queryFn: async () => {
      const stores = await storesService.adminListStores();
      return Promise.all(
        stores.map(async (store) => ({ store, settings: await storesService.adminGetStoreSettings(store.id) })),
      );
    },
  });

  const filteredStores =
    statusFilter === "all"
      ? allStores
      : allStores?.filter(({ store }) => store.verificationStatus === statusFilter);

  const { data: decisionHistory, isLoading: historyLoading } = useQuery({
    queryKey: ["admin-store-decision-history"],
    queryFn: () => adminService.listAuditLog({ targetType: "store", size: 50 }),
  });

  const { data: pendingChangeRequests, isLoading: changeRequestsLoading } = useQuery({
    queryKey: ["admin-pending-verification-change-requests"],
    queryFn: () => storesService.adminListVerificationChangeRequests("pending"),
  });

  const approveMutation = useMutation({
    mutationFn: (storeId: string) => storesService.setStoreVerificationStatus(storeId, "active"),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ["admin-pending-stores"] });
      queryClient.invalidateQueries({ queryKey: ["admin-all-stores"] });
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
      queryClient.invalidateQueries({ queryKey: ["admin-all-stores"] });
      queryClient.invalidateQueries({ queryKey: ["admin-store-decision-history"] });
      queryClient.invalidateQueries({ queryKey: ["admin-pending-stores-count"] });
      toast.success("Application rejected");
      setRejectTarget(null);
      setRejectReason("");
    },
    onError: () => toast.error("Couldn't reject this application"),
  });

  function invalidateChangeRequestQueries() {
    queryClient.invalidateQueries({ queryKey: ["admin-pending-verification-change-requests"] });
    queryClient.invalidateQueries({ queryKey: ["admin-store-decision-history"] });
    queryClient.invalidateQueries({ queryKey: ["admin-all-stores"] });
  }

  const approveChangeRequestMutation = useMutation({
    mutationFn: (id: string) => storesService.adminApproveVerificationChangeRequest(id),
    onSuccess: () => {
      invalidateChangeRequestQueries();
      toast.success("Verification change approved");
    },
    onError: () => toast.error("Couldn't approve this change request"),
  });

  const rejectChangeRequestMutation = useMutation({
    mutationFn: ({ id, reason }: { id: string; reason: string }) =>
      storesService.adminRejectVerificationChangeRequest(id, reason),
    onSuccess: () => {
      invalidateChangeRequestQueries();
      toast.success("Verification change rejected");
      setRejectChangeRequestTarget(null);
      setRejectChangeRequestReason("");
    },
    onError: () => toast.error("Couldn't reject this change request"),
  });

  return (
    <div className="space-y-6">
      <div>
        <h1 className="text-2xl font-bold">Stores</h1>
        <p className="text-muted-foreground text-sm">
          Review new seller applications, browse every registered store, and see past decisions.
        </p>
      </div>

      <Tabs defaultValue="pending">
        <TabsList>
          <TabsTrigger value="pending">Pending</TabsTrigger>
          <TabsTrigger value="all">All stores</TabsTrigger>
          <TabsTrigger value="changes">
            Verification changes
            {pendingChangeRequests && pendingChangeRequests.length > 0 ? (
              <Badge className="ml-1.5 border-0 px-1.5">{pendingChangeRequests.length}</Badge>
            ) : null}
          </TabsTrigger>
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
                    <StoreDetailCard
                      key={store.id}
                      store={store}
                      settings={settings}
                      isSriLanka={isSriLanka}
                      showActions
                      isApproving={approveMutation.isPending}
                      onApprove={(storeId) => approveMutation.mutate(storeId)}
                      onReject={(target) => setRejectTarget(target)}
                    />
                  ))}
                </div>
              )}
            </CardContent>
          </Card>
        </TabsContent>

        <TabsContent value="all">
          <Card>
            <CardContent className="space-y-4">
              <div className="flex items-center justify-between gap-3">
                <p className="text-muted-foreground text-sm">
                  {filteredStores ? `${filteredStores.length} store${filteredStores.length === 1 ? "" : "s"}` : ""}
                </p>
                <Select value={statusFilter} onValueChange={(v) => setStatusFilter(v as StatusFilter)}>
                  <SelectTrigger className="w-44">
                    <SelectValue>
                      {(v: StatusFilter) => STATUS_FILTERS.find((f) => f.value === v)?.label ?? v}
                    </SelectValue>
                  </SelectTrigger>
                  <SelectContent>
                    {STATUS_FILTERS.map((filter) => (
                      <SelectItem key={filter.value} value={filter.value}>
                        {filter.label}
                      </SelectItem>
                    ))}
                  </SelectContent>
                </Select>
              </div>
              {allStoresLoading ? (
                <div className="space-y-2">
                  <TableRowSkeleton columns={3} />
                  <TableRowSkeleton columns={3} />
                </div>
              ) : !filteredStores || filteredStores.length === 0 ? (
                <EmptyState icon={StoreIcon} title="No stores match this filter" />
              ) : (
                <div className="space-y-3">
                  {filteredStores.map(({ store, settings }) => (
                    <StoreDetailCard
                      key={store.id}
                      store={store}
                      settings={settings}
                      isSriLanka={isSriLanka}
                      showActions={store.verificationStatus === "pending"}
                      isApproving={approveMutation.isPending}
                      onApprove={(storeId) => approveMutation.mutate(storeId)}
                      onReject={(target) => setRejectTarget(target)}
                    />
                  ))}
                </div>
              )}
            </CardContent>
          </Card>
        </TabsContent>

        <TabsContent value="changes">
          <Card>
            <CardContent className="space-y-4">
              {changeRequestsLoading ? (
                <div className="space-y-2">
                  <TableRowSkeleton columns={3} />
                  <TableRowSkeleton columns={3} />
                </div>
              ) : !pendingChangeRequests || pendingChangeRequests.length === 0 ? (
                <EmptyState icon={FileEdit} title="No verification changes pending review" />
              ) : (
                <div className="space-y-3">
                  {pendingChangeRequests.map((request) => (
                    <div key={request.id} className="space-y-3 rounded-md border p-4">
                      <div className="flex items-center justify-between gap-3">
                        <p className="font-semibold">{request.storeName}</p>
                        <p className="text-muted-foreground text-xs">{formatDateTime(request.submittedAt)}</p>
                      </div>
                      <dl className="grid grid-cols-2 gap-3 text-sm">
                        {request.sellerType && request.sellerType !== request.currentSellerType ? (
                          <div>
                            <dt className="text-muted-foreground text-xs">Seller type</dt>
                            <dd>
                              <span className="text-muted-foreground line-through">{request.currentSellerType}</span>{" "}
                              → <span className="font-medium">{request.sellerType}</span>
                            </dd>
                          </div>
                        ) : null}
                        {isSriLanka ? (
                          <>
                            {request.nicNumber && request.nicNumber !== request.currentNicNumber ? (
                              <div>
                                <dt className="text-muted-foreground text-xs">NIC number</dt>
                                <dd>
                                  <span className="text-muted-foreground line-through">
                                    {request.currentNicNumber || "—"}
                                  </span>{" "}
                                  → <span className="font-medium">{request.nicNumber}</span>
                                </dd>
                              </div>
                            ) : null}
                            {request.businessRegistrationNumber &&
                            request.businessRegistrationNumber !== request.currentBusinessRegistrationNumber ? (
                              <div>
                                <dt className="text-muted-foreground text-xs">Business reg. no.</dt>
                                <dd>
                                  <span className="text-muted-foreground line-through">
                                    {request.currentBusinessRegistrationNumber || "—"}
                                  </span>{" "}
                                  → <span className="font-medium">{request.businessRegistrationNumber}</span>
                                </dd>
                              </div>
                            ) : null}
                            {request.nicDocumentUrl ? (
                              <div>
                                <dt className="text-muted-foreground text-xs">NIC document</dt>
                                <dd className="flex flex-wrap items-center gap-x-2">
                                  {request.currentNicDocumentUrl ? (
                                    <>
                                      <a
                                        href={request.currentNicDocumentUrl}
                                        target="_blank"
                                        rel="noopener noreferrer"
                                        className="text-muted-foreground underline-offset-4 hover:underline"
                                      >
                                        View current file
                                      </a>
                                      <span className="text-muted-foreground">→</span>
                                    </>
                                  ) : null}
                                  <a
                                    href={request.nicDocumentUrl}
                                    target="_blank"
                                    rel="noopener noreferrer"
                                    className="text-primary font-medium underline-offset-4 hover:underline"
                                  >
                                    View new file
                                  </a>
                                </dd>
                              </div>
                            ) : null}
                            {request.businessRegDocumentUrl ? (
                              <div>
                                <dt className="text-muted-foreground text-xs">Business reg. document</dt>
                                <dd className="flex flex-wrap items-center gap-x-2">
                                  {request.currentBusinessRegDocumentUrl ? (
                                    <>
                                      <a
                                        href={request.currentBusinessRegDocumentUrl}
                                        target="_blank"
                                        rel="noopener noreferrer"
                                        className="text-muted-foreground underline-offset-4 hover:underline"
                                      >
                                        View current file
                                      </a>
                                      <span className="text-muted-foreground">→</span>
                                    </>
                                  ) : null}
                                  <a
                                    href={request.businessRegDocumentUrl}
                                    target="_blank"
                                    rel="noopener noreferrer"
                                    className="text-primary font-medium underline-offset-4 hover:underline"
                                  >
                                    View new file
                                  </a>
                                </dd>
                              </div>
                            ) : null}
                          </>
                        ) : (
                          <>
                            {request.driverLicenceNumber &&
                            request.driverLicenceNumber !== request.currentDriverLicenceNumber ? (
                              <div>
                                <dt className="text-muted-foreground text-xs">Driver&apos;s licence no.</dt>
                                <dd>
                                  <span className="text-muted-foreground line-through">
                                    {request.currentDriverLicenceNumber || "—"}
                                  </span>{" "}
                                  → <span className="font-medium">{request.driverLicenceNumber}</span>
                                </dd>
                              </div>
                            ) : null}
                            {request.abn && request.abn !== request.currentAbn ? (
                              <div>
                                <dt className="text-muted-foreground text-xs">ABN</dt>
                                <dd>
                                  <span className="text-muted-foreground line-through">
                                    {request.currentAbn || "—"}
                                  </span>{" "}
                                  → <span className="font-medium">{request.abn}</span>
                                </dd>
                              </div>
                            ) : null}
                            {request.driverLicenceDocumentUrl ? (
                              <div>
                                <dt className="text-muted-foreground text-xs">Driver&apos;s licence document</dt>
                                <dd className="flex flex-wrap items-center gap-x-2">
                                  {request.currentDriverLicenceDocumentUrl ? (
                                    <>
                                      <a
                                        href={request.currentDriverLicenceDocumentUrl}
                                        target="_blank"
                                        rel="noopener noreferrer"
                                        className="text-muted-foreground underline-offset-4 hover:underline"
                                      >
                                        View current file
                                      </a>
                                      <span className="text-muted-foreground">→</span>
                                    </>
                                  ) : null}
                                  <a
                                    href={request.driverLicenceDocumentUrl}
                                    target="_blank"
                                    rel="noopener noreferrer"
                                    className="text-primary font-medium underline-offset-4 hover:underline"
                                  >
                                    View new file
                                  </a>
                                </dd>
                              </div>
                            ) : null}
                            {request.abnDocumentUrl ? (
                              <div>
                                <dt className="text-muted-foreground text-xs">ABN document</dt>
                                <dd className="flex flex-wrap items-center gap-x-2">
                                  {request.currentAbnDocumentUrl ? (
                                    <>
                                      <a
                                        href={request.currentAbnDocumentUrl}
                                        target="_blank"
                                        rel="noopener noreferrer"
                                        className="text-muted-foreground underline-offset-4 hover:underline"
                                      >
                                        View current file
                                      </a>
                                      <span className="text-muted-foreground">→</span>
                                    </>
                                  ) : null}
                                  <a
                                    href={request.abnDocumentUrl}
                                    target="_blank"
                                    rel="noopener noreferrer"
                                    className="text-primary font-medium underline-offset-4 hover:underline"
                                  >
                                    View new file
                                  </a>
                                </dd>
                              </div>
                            ) : null}
                          </>
                        )}
                      </dl>
                      <div className="flex gap-2">
                        <Button
                          size="sm"
                          disabled={approveChangeRequestMutation.isPending}
                          onClick={() => approveChangeRequestMutation.mutate(request.id)}
                        >
                          Approve
                        </Button>
                        <Button
                          size="sm"
                          variant="outline"
                          onClick={() => setRejectChangeRequestTarget(request)}
                        >
                          Reject
                        </Button>
                      </div>
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
                          <StatusBadge tone={DECISION_TONES[entry.action] ?? "neutral"}>
                            {DECISION_LABELS[entry.action] ?? entry.action}
                          </StatusBadge>
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

      <Dialog
        open={!!rejectChangeRequestTarget}
        onOpenChange={(open) => !open && setRejectChangeRequestTarget(null)}
      >
        <DialogContent>
          <DialogHeader>
            <DialogTitle>Reject this verification change?</DialogTitle>
            <DialogDescription>
              Give a reason — {rejectChangeRequestTarget?.storeName}&apos;s current details stay unchanged.
            </DialogDescription>
          </DialogHeader>
          <Textarea
            rows={3}
            placeholder="e.g. Document doesn't match the number provided"
            value={rejectChangeRequestReason}
            onChange={(e) => setRejectChangeRequestReason(e.target.value)}
          />
          <DialogFooter>
            <Button variant="outline" onClick={() => setRejectChangeRequestTarget(null)}>
              Cancel
            </Button>
            <Button
              variant="destructive"
              disabled={!rejectChangeRequestReason.trim() || rejectChangeRequestMutation.isPending}
              onClick={() =>
                rejectChangeRequestTarget &&
                rejectChangeRequestMutation.mutate({
                  id: rejectChangeRequestTarget.id,
                  reason: rejectChangeRequestReason,
                })
              }
            >
              Reject change
            </Button>
          </DialogFooter>
        </DialogContent>
      </Dialog>
    </div>
  );
}
