"use client";

import Link from "next/link";
import { useRouter } from "next/navigation";
import { useMutation } from "@tanstack/react-query";
import { toast } from "sonner";
import { Loader2, Mail } from "lucide-react";
import { Button } from "@/components/ui/button";
import { useAuthSession } from "@/hooks/use-auth-session";
import { messagingService } from "@/services";

/** Starts (or resumes) the buyer's one conversation with this store, then navigates to the thread — see Conversation's one-per-(store,buyer) doc comment. */
export function MessageSellerButton({ storeId }: { storeId: string }) {
  const router = useRouter();
  const { session } = useAuthSession();

  const mutation = useMutation({
    mutationFn: () => messagingService.getOrCreateConversation(storeId),
    onSuccess: (conversation) => router.push(`/account/messages/${conversation.id}`),
    onError: () => toast.error("Couldn't start a conversation. Please try again."),
  });

  if (!session.signedIn || session.role !== "buyer") {
    return (
      <Button variant="outline" size="lg" render={<Link href="/account/login" />}>
        <Mail className="size-4" /> Message seller
      </Button>
    );
  }

  return (
    <Button type="button" variant="outline" size="lg" disabled={mutation.isPending} onClick={() => mutation.mutate()}>
      {mutation.isPending ? <Loader2 className="size-4 animate-spin" /> : <Mail className="size-4" />}
      Message seller
    </Button>
  );
}
