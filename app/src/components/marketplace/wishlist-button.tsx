"use client";

import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import { toast } from "sonner";
import { Heart } from "lucide-react";
import { Button } from "@/components/ui/button";
import { useAuthSession } from "@/hooks/use-auth-session";
import { cn } from "@/lib/utils";
import { productsService } from "@/services";

/**
 * A heart-icon toggle used both as a floating overlay on ProductCard (whole
 * card is a <Link>, so this must stopPropagation/preventDefault to avoid
 * triggering navigation) and, with `overlay={false}`, as a bordered icon
 * button inline on the product detail page (matching CopyLinkButton's
 * iconOnly variant).
 */
export function WishlistButton({
  productId,
  className,
  overlay = true,
}: {
  productId: string;
  className?: string;
  overlay?: boolean;
}) {
  const { session } = useAuthSession();
  const queryClient = useQueryClient();
  const queryKey = ["wishlist-status", productId];

  const { data } = useQuery({
    queryKey,
    queryFn: () => productsService.getWishlistStatus(productId),
  });

  const mutation = useMutation({
    mutationFn: async () => {
      if (data?.wishlisted) {
        await productsService.removeFromWishlist(productId);
      } else {
        await productsService.addToWishlist(productId);
      }
    },
    onSuccess: () => {
      queryClient.setQueryData(queryKey, { wishlisted: !data?.wishlisted });
      queryClient.invalidateQueries({ queryKey: ["wishlist"] });
    },
    onError: () => toast.error("Couldn't update your wishlist. Please try again."),
  });

  const wishlisted = data?.wishlisted ?? false;

  function handleClick(e: React.MouseEvent) {
    e.preventDefault();
    e.stopPropagation();
    if (!session.signedIn || session.role !== "buyer") {
      toast.error("Sign in as a buyer to save items to your wishlist.");
      return;
    }
    mutation.mutate();
  }

  const icon = <Heart className={cn("size-4", wishlisted ? "fill-primary text-primary" : "")} />;
  const label = wishlisted ? "Remove from wishlist" : "Save to wishlist";

  if (overlay) {
    return (
      <button
        type="button"
        aria-label={label}
        disabled={mutation.isPending}
        onClick={handleClick}
        className={cn(
          "bg-background/90 hover:bg-background flex size-8 items-center justify-center rounded-full shadow-sm transition-colors",
          className,
        )}
      >
        {icon}
      </button>
    );
  }

  return (
    <Button
      type="button"
      variant="outline"
      size="icon"
      className={cn("size-8", className)}
      disabled={mutation.isPending}
      onClick={handleClick}
      aria-label={label}
    >
      {icon}
    </Button>
  );
}
