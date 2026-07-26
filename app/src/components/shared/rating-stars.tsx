import { Star } from "lucide-react";
import { cn } from "@/lib/utils";

interface RatingStarsProps {
  rating: number;
  reviewCount?: number;
  size?: number;
  className?: string;
}

export function RatingStars({ rating, reviewCount, size = 14, className }: RatingStarsProps) {
  return (
    <span className={cn("inline-flex items-center gap-1", className)}>
      <span className="inline-flex items-center gap-0.5">
        {Array.from({ length: 5 }).map((_, i) => (
          <Star
            key={i}
            size={size}
            className={cn(
              i < Math.round(rating)
                ? "fill-primary text-primary"
                : "fill-muted text-muted",
            )}
          />
        ))}
      </span>
      <span className="text-muted-foreground text-xs">
        {rating.toFixed(1)}
        {typeof reviewCount === "number" ? ` (${reviewCount})` : ""}
      </span>
    </span>
  );
}
