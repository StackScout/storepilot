"use client";

import { useQuery, useQueryClient } from "@tanstack/react-query";
import { Bell, Check } from "lucide-react";
import { Card, CardContent } from "@/components/ui/card";
import { Button } from "@/components/ui/button";
import { Badge } from "@/components/ui/badge";
import { EmptyState } from "@/components/shared/empty-state";
import { formatDateTime } from "@/lib/format";
import { cn } from "@/lib/utils";
import { adminNotificationsService } from "@/services";

export default function AdminNotificationsPage() {
  const queryClient = useQueryClient();

  const { data: notifications, isLoading } = useQuery({
    queryKey: ["admin-notifications"],
    queryFn: () => adminNotificationsService.listAdminNotifications(),
  });

  async function handleMarkRead(id: string) {
    await adminNotificationsService.markAdminNotificationRead(id);
    queryClient.invalidateQueries({ queryKey: ["admin-notifications"] });
  }

  return (
    <div className="mx-auto max-w-3xl px-4 py-8 sm:px-6">
      <div className="mb-6">
        <h1 className="text-2xl font-bold">Notifications</h1>
        <p className="text-muted-foreground text-sm">
          Activity that needs an admin&apos;s attention — currently just payout bank-detail changes.
        </p>
      </div>

      <Card>
        <CardContent className="p-0">
          {isLoading ? (
            <p className="text-muted-foreground p-6 text-center text-sm">Loading…</p>
          ) : !notifications || notifications.length === 0 ? (
            <EmptyState icon={Bell} title="No notifications yet" description="Nothing to review right now." />
          ) : (
            <ul className="divide-y">
              {notifications.map((n) => (
                <li
                  key={n.id}
                  className={cn("flex items-start justify-between gap-4 p-4", !n.read && "bg-primary/5")}
                >
                  <div className="space-y-1">
                    <div className="flex items-center gap-2">
                      {!n.read ? <Badge className="border-0 px-1.5 py-0 text-[10px]">New</Badge> : null}
                      <p className="text-sm">{n.message}</p>
                    </div>
                    <p className="text-muted-foreground text-xs">{formatDateTime(n.createdAt)}</p>
                  </div>
                  {!n.read ? (
                    <Button size="sm" variant="ghost" onClick={() => handleMarkRead(n.id)}>
                      <Check className="size-3.5" /> Mark read
                    </Button>
                  ) : null}
                </li>
              ))}
            </ul>
          )}
        </CardContent>
      </Card>
    </div>
  );
}
