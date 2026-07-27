"use client";

import Link from "next/link";
import { useQuery, useQueryClient } from "@tanstack/react-query";
import { Bell } from "lucide-react";
import {
  DropdownMenu,
  DropdownMenuContent,
  DropdownMenuGroup,
  DropdownMenuItem,
  DropdownMenuLabel,
  DropdownMenuSeparator,
  DropdownMenuTrigger,
} from "@/components/ui/dropdown-menu";
import { Button } from "@/components/ui/button";
import { Badge } from "@/components/ui/badge";
import { timeAgo } from "@/lib/format";
import { adminNotificationsService } from "@/services";

export function NotificationsBell() {
  const queryClient = useQueryClient();

  const { data: notifications } = useQuery({
    queryKey: ["admin-notifications"],
    queryFn: () => adminNotificationsService.listAdminNotifications(),
    refetchInterval: 60_000,
  });

  const unreadCount = notifications?.filter((n) => !n.read).length ?? 0;
  const recent = notifications?.slice(0, 8) ?? [];

  async function handleOpenChange(open: boolean) {
    if (open || unreadCount === 0) return;
    await adminNotificationsService.markAllAdminNotificationsRead();
    queryClient.invalidateQueries({ queryKey: ["admin-notifications"] });
  }

  return (
    <DropdownMenu onOpenChange={handleOpenChange}>
      <DropdownMenuTrigger
        render={
          <Button variant="ghost" size="icon" className="relative">
            <Bell className="size-5" />
            {unreadCount > 0 ? (
              <Badge className="absolute -top-1 -right-1 h-4 min-w-4 justify-center rounded-full border-0 px-1 text-[10px]">
                {unreadCount}
              </Badge>
            ) : null}
          </Button>
        }
      />
      <DropdownMenuContent align="end" className="w-80">
        <DropdownMenuGroup>
          <DropdownMenuLabel>Notifications</DropdownMenuLabel>
        </DropdownMenuGroup>
        <DropdownMenuSeparator />
        {recent.length === 0 ? (
          <p className="text-muted-foreground px-2 py-3 text-center text-sm">No notifications yet</p>
        ) : (
          recent.map((n) => (
            <DropdownMenuItem key={n.id} className="flex-col items-start gap-0.5 whitespace-normal">
              <span className="text-sm">{n.message}</span>
              <span className="text-muted-foreground text-xs">{timeAgo(n.createdAt)}</span>
            </DropdownMenuItem>
          ))
        )}
        {notifications && notifications.length > 8 ? (
          <>
            <DropdownMenuSeparator />
            <DropdownMenuItem render={<Link href="/admin/notifications" />}>
              View all notifications
            </DropdownMenuItem>
          </>
        ) : null}
      </DropdownMenuContent>
    </DropdownMenu>
  );
}
