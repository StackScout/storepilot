"use client";

import { Minus, Plus } from "lucide-react";
import { Button } from "@/components/ui/button";

interface QuantityStepperProps {
  quantity: number;
  onChange: (quantity: number) => void;
  max?: number;
  min?: number;
  size?: "sm" | "default";
}

export function QuantityStepper({
  quantity,
  onChange,
  max = 99,
  min = 1,
  size = "default",
}: QuantityStepperProps) {
  const btnSize = size === "sm" ? "size-7" : "size-9";

  return (
    <div className="inline-flex items-center rounded-md border">
      <Button
        type="button"
        variant="ghost"
        size="icon"
        className={btnSize}
        disabled={quantity <= min}
        onClick={() => onChange(Math.max(min, quantity - 1))}
        aria-label="Decrease quantity"
      >
        <Minus className="size-3.5" />
      </Button>
      <span className="min-w-8 text-center text-sm tabular-nums">{quantity}</span>
      <Button
        type="button"
        variant="ghost"
        size="icon"
        className={btnSize}
        disabled={quantity >= max}
        onClick={() => onChange(Math.min(max, quantity + 1))}
        aria-label="Increase quantity"
      >
        <Plus className="size-3.5" />
      </Button>
    </div>
  );
}
