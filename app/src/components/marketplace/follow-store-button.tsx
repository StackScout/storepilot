"use client";

import Link from "next/link";
import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import { toast } from "sonner";
import { Heart, Loader2 } from "lucide-react";
import { Button } from "@/components/ui/button";
import { useAuthSession } from "@/hooks/use-auth-session";
import { cn } from "@/lib/utils";
import { storesService } from "@/services";

export function FollowStoreButton({ storeId }: { storeId: string }) {
  const { session } = useAuthSession();
  const queryClient = useQueryClient();
  const queryKey = ["follow-status", storeId];

  const { data } = useQuery({
    queryKey,
    queryFn: () => storesService.getFollowStatus(storeId),
  });

  const mutation = useMutation({
    mutationFn: async () => {
      if (data?.following) {
        await storesService.unfollowStore(storeId);
      } else {
        await storesService.followStore(storeId);
      }
    },
    onSuccess: () => {
      queryClient.setQueryData(queryKey, { following: !data?.following });
      queryClient.invalidateQueries({ queryKey: ["store", "slug"] });
    },
    onError: () => toast.error("Couldn't update follow status. Please try again."),
  });

  if (!session.signedIn || session.role !== "buyer") {
    return (
      <Button variant="outline" size="lg" render={<Link href="/login" />}>
        <Heart className="size-4" /> Follow
      </Button>
    );
  }

  const following = data?.following ?? false;

  return (
    <Button
      type="button"
      variant={following ? "default" : "outline"}
      size="lg"
      disabled={mutation.isPending}
      onClick={() => mutation.mutate()}
    >
      {mutation.isPending ? (
        <Loader2 className="size-4 animate-spin" />
      ) : (
        <Heart className={cn("size-4", following ? "fill-current" : "")} />
      )}
      {following ? "Following" : "Follow"}
    </Button>
  );
}
