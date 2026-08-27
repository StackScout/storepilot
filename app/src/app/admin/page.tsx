"use client";

import Link from "next/link";
import { useQuery } from "@tanstack/react-query";
import { ClipboardCheck, History, Wallet, ArrowRight } from "lucide-react";
import { Card, CardContent } from "@/components/ui/card";
import { Button } from "@/components/ui/button";
import { formatCurrency } from "@/lib/currency";
import { formatDateTime } from "@/lib/format";
import { usePlatformConfig } from "@/hooks/use-platform-config";
import { queryKeys } from "@/lib/query-keys";
import { storesService, adminService } from "@/services";

export default function AdminOverviewPage() {
  const { currencyCode, currencySymbol, currencyLocale } = usePlatformConfig();
  const currency = { code: currencyCode, symbol: currencySymbol, locale: currencyLocale };

  const { data: pendingStores } = useQuery({
    queryKey: queryKeys.admin.pendingStoresCount(),
    queryFn: () => storesService.adminListStores("pending"),
  });

  const { data: summary } = useQuery({
    queryKey: queryKeys.admin.accountingSummary(),
    queryFn: () => adminService.getAccountingSummary(),
  });

  const { data: recentActivity } = useQuery({
    queryKey: queryKeys.admin.auditLog(0, 5),
    queryFn: () => adminService.listAuditLog({ page: 0, size: 5 }),
  });

  return (
    <div className="space-y-6">
      <div>
        <h1 className="text-2xl font-bold">Overview</h1>
        <p className="text-muted-foreground text-sm">Platform-wide status at a glance.</p>
      </div>

      <div className="grid gap-4 sm:grid-cols-3">
        <Card>
          <CardContent className="space-y-2">
            <div className="text-muted-foreground flex items-center gap-2 text-sm">
              <ClipboardCheck className="size-4" /> Pending applications
            </div>
            <p className="text-2xl font-bold">{pendingStores?.totalElements ?? "—"}</p>
            <Button render={<Link href="/admin/stores" />} variant="ghost" size="sm" className="-ml-2">
              Review <ArrowRight className="size-3.5" />
            </Button>
          </CardContent>
        </Card>

        <Card>
          <CardContent className="space-y-2">
            <div className="text-muted-foreground flex items-center gap-2 text-sm">
              <Wallet className="size-4" /> Payouts scheduled
            </div>
            <p className="text-2xl font-bold">
              {summary ? formatCurrency(summary.payoutsScheduledTotal, currency) : "—"}
            </p>
            <Button render={<Link href="/admin/accounting" />} variant="ghost" size="sm" className="-ml-2">
              Open accounting <ArrowRight className="size-3.5" />
            </Button>
          </CardContent>
        </Card>

        <Card>
          <CardContent className="space-y-2">
            <div className="text-muted-foreground flex items-center gap-2 text-sm">
              <Wallet className="size-4" /> Fees pending collection
            </div>
            <p className="text-2xl font-bold">
              {summary ? formatCurrency(summary.feeCollectionsPendingTotal, currency) : "—"}
            </p>
            <Button render={<Link href="/admin/accounting" />} variant="ghost" size="sm" className="-ml-2">
              Open accounting <ArrowRight className="size-3.5" />
            </Button>
          </CardContent>
        </Card>
      </div>

      <Card>
        <CardContent className="space-y-4">
          <div className="flex items-center justify-between">
            <h2 className="flex items-center gap-2 font-semibold">
              <History className="size-4" /> Recent activity
            </h2>
            <Button render={<Link href="/admin/audit-log" />} variant="ghost" size="sm">
              View full audit log <ArrowRight className="size-3.5" />
            </Button>
          </div>
          {!recentActivity || recentActivity.content.length === 0 ? (
            <p className="text-muted-foreground text-sm">No recorded actions yet.</p>
          ) : (
            <ul className="divide-y">
              {recentActivity.content.map((entry) => (
                <li key={entry.id} className="space-y-0.5 py-2.5 text-sm">
                  <p>{entry.description}</p>
                  <p className="text-muted-foreground text-xs">
                    {entry.actorEmail} · {formatDateTime(entry.createdAt)}
                  </p>
                </li>
              ))}
            </ul>
          )}
        </CardContent>
      </Card>
    </div>
  );
}
