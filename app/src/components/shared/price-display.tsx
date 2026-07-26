import { formatLkr } from "@/lib/currency";
import { cn } from "@/lib/utils";

interface PriceDisplayProps {
  priceLkr: number;
  compareAtPriceLkr?: number;
  size?: "sm" | "md" | "lg";
  className?: string;
}

const SIZE_CLASSES: Record<NonNullable<PriceDisplayProps["size"]>, string> = {
  sm: "text-sm",
  md: "text-base",
  lg: "text-2xl",
};

export function PriceDisplay({
  priceLkr,
  compareAtPriceLkr,
  size = "md",
  className,
}: PriceDisplayProps) {
  const hasDiscount = compareAtPriceLkr && compareAtPriceLkr > priceLkr;

  return (
    <span className={cn("inline-flex items-baseline gap-2", className)}>
      <span className={cn("font-semibold text-foreground", SIZE_CLASSES[size])}>
        {formatLkr(priceLkr)}
      </span>
      {hasDiscount ? (
        <span className="text-muted-foreground text-sm line-through">
          {formatLkr(compareAtPriceLkr)}
        </span>
      ) : null}
    </span>
  );
}
