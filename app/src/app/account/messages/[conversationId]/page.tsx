"use client";

import { use } from "react";
import { useQuery } from "@tanstack/react-query";
import { MessageThread } from "@/components/shared/message-thread";
import { messagingService } from "@/services";

export default function AccountMessageThreadPage({
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
    <div className="px-4 py-8 sm:px-6">
      <MessageThread
        conversationId={conversationId}
        viewerSide="buyer"
        backHref="/account/messages"
        counterpartLabel={conversation?.storeName ?? "Conversation"}
      />
    </div>
  );
}
