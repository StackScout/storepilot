"use client";

import { useEffect, useRef, useState } from "react";
import Link from "next/link";
import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import { toast } from "sonner";
import { ArrowLeft, Loader2, Send } from "lucide-react";
import { Button, buttonVariants } from "@/components/ui/button";
import { Card, CardContent } from "@/components/ui/card";
import { formatDateTime } from "@/lib/format";
import { cn } from "@/lib/utils";
import { messagingService } from "@/services";
import type { SenderType } from "@/types";

/**
 * Renders one conversation thread — reused by both the buyer (/account/messages/:id)
 * and seller (/dashboard/messages/:id) pages, which only differ in [viewerSide]
 * (which side's messages are right-aligned as "own") and [backHref]. Live
 * delivery is polling, not SSE — a conversation is private (unlike order/
 * booking status pages' public "ID is proof enough" model), and the existing
 * useLiveStatus EventSource doesn't send credentials cross-origin, so
 * polling avoided that whole problem for a nice-to-have feature.
 */
export function MessageThread({
  conversationId,
  viewerSide,
  backHref,
  counterpartLabel,
}: {
  conversationId: string;
  viewerSide: SenderType;
  backHref: string;
  counterpartLabel: string;
}) {
  const queryClient = useQueryClient();
  const [body, setBody] = useState("");
  const bottomRef = useRef<HTMLDivElement>(null);

  const { data: messages, isLoading } = useQuery({
    queryKey: ["messages", conversationId],
    queryFn: () => messagingService.listMessages(conversationId),
    refetchInterval: 4000,
  });

  useEffect(() => {
    bottomRef.current?.scrollIntoView({ block: "end" });
  }, [messages?.content.length]);

  const mutation = useMutation({
    mutationFn: (text: string) => messagingService.sendMessage(conversationId, text),
    onSuccess: () => {
      setBody("");
      queryClient.invalidateQueries({ queryKey: ["messages", conversationId] });
      queryClient.invalidateQueries({ queryKey: ["conversations"] });
    },
    onError: () => toast.error("Couldn't send your message. Please try again."),
  });

  function handleSubmit(e: React.FormEvent) {
    e.preventDefault();
    const trimmed = body.trim();
    if (!trimmed) return;
    mutation.mutate(trimmed);
  }

  return (
    <div className="mx-auto flex h-[calc(100vh-8rem)] max-w-2xl flex-col gap-3">
      <Link href={backHref} className={cn(buttonVariants({ variant: "ghost", size: "sm" }), "w-fit")}>
        <ArrowLeft className="size-3.5" /> Back to messages
      </Link>
      <h1 className="text-lg font-semibold">{counterpartLabel}</h1>

      <Card className="flex-1 overflow-hidden">
        <CardContent className="flex h-full flex-col gap-3 overflow-y-auto">
          {isLoading ? (
            <p className="text-muted-foreground text-center text-sm">Loading…</p>
          ) : !messages || messages.content.length === 0 ? (
            <p className="text-muted-foreground text-center text-sm">
              No messages yet — say hello!
            </p>
          ) : (
            messages.content.map((message) => {
              const isOwn = message.senderType === viewerSide;
              return (
                <div key={message.id} className={cn("flex", isOwn ? "justify-end" : "justify-start")}>
                  <div
                    className={cn(
                      "max-w-[80%] rounded-lg px-3.5 py-2 text-sm",
                      isOwn ? "bg-primary text-primary-foreground" : "bg-muted",
                    )}
                  >
                    <p className="whitespace-pre-wrap">{message.body}</p>
                    <p className={cn("mt-1 text-[10px]", isOwn ? "text-primary-foreground/70" : "text-muted-foreground")}>
                      {formatDateTime(message.createdAt)}
                    </p>
                  </div>
                </div>
              );
            })
          )}
          <div ref={bottomRef} />
        </CardContent>
      </Card>

      <form onSubmit={handleSubmit} className="flex gap-2">
        <textarea
          value={body}
          onChange={(e) => setBody(e.target.value)}
          onKeyDown={(e) => {
            if (e.key === "Enter" && !e.shiftKey) {
              e.preventDefault();
              handleSubmit(e);
            }
          }}
          placeholder="Type a message…"
          rows={1}
          className="border-input flex-1 resize-none rounded-md border bg-transparent px-3 py-2 text-sm shadow-xs outline-none focus-visible:ring-2 focus-visible:ring-ring/50"
        />
        <Button type="submit" size="icon" disabled={!body.trim() || mutation.isPending}>
          {mutation.isPending ? <Loader2 className="size-4 animate-spin" /> : <Send className="size-4" />}
        </Button>
      </form>
    </div>
  );
}
