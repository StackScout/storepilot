"use client";

import { useState } from "react";
import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import { toast } from "sonner";
import { ClipboardCheck, History, Store as StoreIcon } from "lucide-react";
import { Button } from "@/components/ui/button";
import { Card, CardContent } from "@/components/ui/card";
import { Badge } from "@/components/ui/badge";
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
import type { Store, StoreSettings, StoreVerificationStatus } from "@/types";

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

export default function AdminStoresPage() {
  const queryClient = useQueryClient();
  const { countryCode } = usePlatformConfig();
  const isSriLanka = countryCode === "LK";
  const [rejectTarget, setRejectTarget] = useState<Store | null>(null);
  const [rejectReason, setRejectReason] = useState("");
  const [statusFilter, setStatusFilter] = useState<StatusFilter>("all");

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
                    <SelectValue />
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
