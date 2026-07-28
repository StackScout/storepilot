"use client";

import { formatCurrency } from "@/lib/currency";
import { usePlatformConfig } from "@/hooks/use-platform-config";
import { cn } from "@/lib/utils";

interface PriceDisplayProps {
  price: number;
  compareAtPrice?: number;
  size?: "sm" | "md" | "lg";
  className?: string;
}

const SIZE_CLASSES: Record<NonNullable<PriceDisplayProps["size"]>, string> = {
  sm: "text-sm",
  md: "text-base",
  lg: "text-2xl",
};

export function PriceDisplay({
  price,
  compareAtPrice,
  size = "md",
  className,
}: PriceDisplayProps) {
  const { currencyCode, currencySymbol, currencyLocale } = usePlatformConfig();
  const currency = { code: currencyCode, symbol: currencySymbol, locale: currencyLocale };
  const hasDiscount = compareAtPrice && compareAtPrice > price;

  return (
    <span className={cn("inline-flex items-baseline gap-2", className)}>
      <span className={cn("font-semibold text-foreground", SIZE_CLASSES[size])}>
        {formatCurrency(price, currency)}
      </span>
      {hasDiscount ? (
        <span className="text-muted-foreground text-sm line-through">
          {formatCurrency(compareAtPrice, currency)}
        </span>
      ) : null}
    </span>
  );
}
