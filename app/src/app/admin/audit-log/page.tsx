"use client";

import { useState } from "react";
import { useQuery } from "@tanstack/react-query";
import { ChevronLeft, ChevronRight, History } from "lucide-react";
import { Card, CardContent } from "@/components/ui/card";
import { Badge } from "@/components/ui/badge";
import { Button } from "@/components/ui/button";
import { Select, SelectContent, SelectItem, SelectTrigger, SelectValue } from "@/components/ui/select";
import { EmptyState } from "@/components/shared/empty-state";
import { TableRowSkeleton } from "@/components/shared/loading-skeletons";
import { formatDateTime } from "@/lib/format";
import { adminService } from "@/services";

const ACTIONS = [
  { value: "all", label: "All actions" },
  { value: "store_approved", label: "Store approved" },
  { value: "store_rejected", label: "Store rejected" },
  { value: "admin_invited", label: "Admin invited" },
  { value: "payout_marked_paid", label: "Payout marked paid" },
  { value: "fee_collection_marked_collected", label: "Fee collection marked collected" },
];

const ACTION_LABELS = Object.fromEntries(ACTIONS.map((a) => [a.value, a.label]));

export default function AdminAuditLogPage() {
  const [action, setAction] = useState("all");
  const [page, setPage] = useState(0);

  const { data, isLoading } = useQuery({
    queryKey: ["admin-audit-log", { action, page }],
    queryFn: () => adminService.listAuditLog({ action: action === "all" ? undefined : action, page, size: 25 }),
  });

  return (
    <div className="space-y-6">
      <div className="flex flex-wrap items-start justify-between gap-4">
        <div>
          <h1 className="text-2xl font-bold">Audit log</h1>
          <p className="text-muted-foreground text-sm">
            Every recorded admin action, for audit purposes — write-once, never edited.
          </p>
        </div>
        <Select
          value={action}
          onValueChange={(v) => {
            setAction(v as string);
            setPage(0);
          }}
        >
          <SelectTrigger className="w-56">
            <SelectValue />
          </SelectTrigger>
          <SelectContent>
            {ACTIONS.map((a) => (
              <SelectItem key={a.value} value={a.value}>
                {a.label}
              </SelectItem>
            ))}
          </SelectContent>
        </Select>
      </div>

      <Card>
        <CardContent className="p-0">
          {isLoading ? (
            <div className="space-y-2 p-6">
              <TableRowSkeleton columns={3} />
              <TableRowSkeleton columns={3} />
              <TableRowSkeleton columns={3} />
            </div>
          ) : !data || data.content.length === 0 ? (
            <EmptyState icon={History} title="No actions recorded yet" />
          ) : (
            <ul className="divide-y">
              {data.content.map((entry) => (
                <li key={entry.id} className="space-y-1 p-4">
                  <div className="flex flex-wrap items-center gap-2">
                    <Badge variant="secondary">{ACTION_LABELS[entry.action] ?? entry.action}</Badge>
                    <p className="text-sm">{entry.description}</p>
                  </div>
                  <p className="text-muted-foreground text-xs">
                    {entry.actorEmail} · {formatDateTime(entry.createdAt)}
                  </p>
                </li>
              ))}
            </ul>
          )}
        </CardContent>
      </Card>

      {data && data.totalPages > 1 ? (
        <div className="flex items-center justify-between">
          <p className="text-muted-foreground text-xs">
            Page {data.page + 1} of {data.totalPages}
          </p>
          <div className="flex gap-2">
            <Button
              variant="outline"
              size="sm"
              disabled={page === 0}
              onClick={() => setPage((p) => Math.max(0, p - 1))}
            >
              <ChevronLeft className="size-3.5" /> Previous
            </Button>
            <Button
              variant="outline"
              size="sm"
              disabled={page + 1 >= data.totalPages}
              onClick={() => setPage((p) => p + 1)}
            >
              Next <ChevronRight className="size-3.5" />
            </Button>
          </div>
        </div>
      ) : null}
    </div>
  );
}
