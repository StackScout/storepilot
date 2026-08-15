"use client";

import Link from "next/link";
import { useQuery } from "@tanstack/react-query";
import { Mail } from "lucide-react";
import { Card, CardContent } from "@/components/ui/card";
import { EmptyState } from "@/components/shared/empty-state";
import { TableRowSkeleton } from "@/components/shared/loading-skeletons";
import { StatusBadge } from "@/components/shared/status-badge";
import { timeAgo } from "@/lib/format";
import { messagingService } from "@/services";

export default function AccountMessagesPage() {
  const { data: conversations, isLoading } = useQuery({
    queryKey: ["conversations", "me"],
    queryFn: () => messagingService.listMyConversations(),
    refetchInterval: 10000,
  });

  return (
    <div className="mx-auto max-w-2xl space-y-6 px-4 py-8 sm:px-6">
      <h1 className="text-2xl font-bold">Messages</h1>

      <Card>
        <CardContent>
          {isLoading ? (
            <div className="space-y-2">
              <TableRowSkeleton columns={1} />
              <TableRowSkeleton columns={1} />
            </div>
          ) : !conversations || conversations.length === 0 ? (
            <EmptyState
              icon={Mail}
              title="No conversations yet"
              description="Message a seller from their store page and it'll show up here."
            />
          ) : (
            <div className="divide-y">
              {conversations.map((conversation) => (
                <Link
                  key={conversation.id}
                  href={`/account/messages/${conversation.id}`}
                  className="hover:bg-accent/50 -mx-4 flex items-center justify-between gap-3 px-4 py-3"
                >
                  <div className="min-w-0">
                    <p className="line-clamp-1 text-sm font-medium">{conversation.storeName}</p>
                    <p className="text-muted-foreground text-xs">
                      {conversation.lastMessageAt ? timeAgo(conversation.lastMessageAt) : "No messages yet"}
                    </p>
                  </div>
                  {conversation.unreadCount > 0 ? (
                    <StatusBadge tone="info" className="shrink-0">
                      {conversation.unreadCount} new
                    </StatusBadge>
                  ) : null}
                </Link>
              ))}
            </div>
          )}
        </CardContent>
      </Card>
    </div>
  );
}
