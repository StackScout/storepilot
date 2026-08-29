"use client";

import Link from "next/link";
import { useState } from "react";
import { useForm } from "react-hook-form";
import { zodResolver } from "@hookform/resolvers/zod";
import { z } from "zod";
import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import { toast } from "sonner";
import { Loader2, Star } from "lucide-react";
import { Button } from "@/components/ui/button";
import { Textarea } from "@/components/ui/textarea";
import { Label } from "@/components/ui/label";
import { Separator } from "@/components/ui/separator";
import { RatingStars } from "@/components/shared/rating-stars";
import { useAuthSession } from "@/hooks/use-auth-session";
import { formatDate } from "@/lib/format";
import { cn } from "@/lib/utils";
import { reviewsService } from "@/services";
import type { Review } from "@/types";

const reviewSchema = z.object({
  rating: z.number().min(1, "Select a rating").max(5),
  comment: z.string().max(2000, "Review is too long").optional(),
});

type ReviewFormValues = z.infer<typeof reviewSchema>;

interface ReviewsSectionProps {
  kind: "product" | "store";
  targetId: string;
}

/**
 * Shared by the product detail page and the store page — "verified
 * purchase" enforcement (must have a delivered order, or for a store
 * review a delivered order or completed booking there) happens entirely
 * server-side in ReviewService; this component just surfaces whatever
 * 403/409 comes back rather than trying to predict eligibility client-side.
 */
export function ReviewsSection({ kind, targetId }: ReviewsSectionProps) {
  const { session } = useAuthSession();
  const queryClient = useQueryClient();
  const queryKey = ["reviews", kind, targetId];

  const { data: reviews } = useQuery({
    queryKey,
    queryFn: () => (kind === "product" ? reviewsService.listProductReviews(targetId) : reviewsService.listStoreReviews(targetId)),
  });

  const {
    register,
    handleSubmit,
    watch,
    setValue,
    reset,
    formState: { errors },
  } = useForm<ReviewFormValues>({
    resolver: zodResolver(reviewSchema),
    defaultValues: { rating: 0, comment: "" },
  });
  const rating = watch("rating");
  const [hoverRating, setHoverRating] = useState(0);

  const mutation = useMutation({
    mutationFn: (values: ReviewFormValues) =>
      kind === "product"
        ? reviewsService.createProductReview(targetId, values)
        : reviewsService.createStoreReview(targetId, values),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey });
      toast.success("Review submitted");
      reset({ rating: 0, comment: "" });
    },
    onError: (error: Error) => toast.error(error.message || "Couldn't submit this review"),
  });

  return (
    <section className="space-y-4">
      <h2 className="text-lg font-semibold">Reviews</h2>

      {session.signedIn && session.role === "buyer" ? (
        <form
          onSubmit={handleSubmit((values) => mutation.mutate(values))}
          className="space-y-3 rounded-lg border p-4"
        >
          <div className="space-y-1.5">
            <Label>Your rating</Label>
            <div className="flex items-center gap-1" onMouseLeave={() => setHoverRating(0)}>
              {[1, 2, 3, 4, 5].map((value) => (
                <button
                  key={value}
                  type="button"
                  onClick={() => setValue("rating", value, { shouldValidate: true })}
                  onMouseEnter={() => setHoverRating(value)}
                  aria-label={`${value} star${value === 1 ? "" : "s"}`}
                >
                  <Star
                    size={22}
                    className={cn(
                      value <= (hoverRating || rating) ? "fill-primary text-primary" : "fill-muted text-muted",
                    )}
                  />
                </button>
              ))}
            </div>
            {errors.rating ? <p className="text-destructive text-xs">{errors.rating.message}</p> : null}
          </div>
          <div className="space-y-1.5">
            <Label htmlFor="review-comment">Comment (optional)</Label>
            <Textarea id="review-comment" rows={3} {...register("comment")} />
            {errors.comment ? <p className="text-destructive text-xs">{errors.comment.message}</p> : null}
          </div>
          <Button type="submit" size="sm" disabled={mutation.isPending}>
            {mutation.isPending ? <Loader2 className="size-4 animate-spin" /> : null}
            Submit review
          </Button>
        </form>
      ) : (
        <p className="text-muted-foreground text-sm">
          <Link href="/login" className="text-primary underline">
            Sign in
          </Link>{" "}
          to leave a review — only buyers with a completed order or booking can review.
        </p>
      )}

      {reviews && reviews.content.length > 0 ? (
        <ul className="divide-y">
          {reviews.content.map((review: Review) => (
            <li key={review.id} className="space-y-1 py-4 first:pt-0">
              <div className="flex items-center justify-between gap-3">
                <span className="text-sm font-medium">{review.buyerName}</span>
                <span className="text-muted-foreground text-xs">{formatDate(review.createdAt)}</span>
              </div>
              <RatingStars rating={review.rating} />
              {review.comment ? <p className="text-muted-foreground text-sm leading-relaxed">{review.comment}</p> : null}
            </li>
          ))}
        </ul>
      ) : (
        <>
          <Separator />
          <p className="text-muted-foreground py-4 text-sm">No reviews yet.</p>
        </>
      )}
    </section>
  );
}
