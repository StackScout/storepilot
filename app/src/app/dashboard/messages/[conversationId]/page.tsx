"use client";

import { use } from "react";
import { useQuery } from "@tanstack/react-query";
import { MessageThread } from "@/components/shared/message-thread";
import { messagingService } from "@/services";

export default function DashboardMessageThreadPage({
  params,
}: {
  params: Promise<{ conversationId: string }>;
}) {
  const { conversationId } = use(params);

  const { data: conversation } = useQuery({
    queryKey: ["conversation", conversationId],
    queryFn: () => messagingService.getConversationById(conversationId),
  });

  return (
    <MessageThread
      conversationId={conversationId}
      viewerSide="seller"
      backHref="/dashboard/messages"
      counterpartLabel={conversation?.buyerName ?? "Conversation"}
    />
  );
}
