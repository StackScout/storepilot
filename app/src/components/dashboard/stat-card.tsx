import type { LucideIcon } from "lucide-react";
import { Card, CardContent } from "@/components/ui/card";
import { cn } from "@/lib/utils";

interface StatCardProps {
  label: string;
  value: string;
  icon: LucideIcon;
  trend?: string;
  trendDirection?: "up" | "down";
}

export function StatCard({ label, value, icon: Icon, trend, trendDirection }: StatCardProps) {
  return (
    <Card>
      <CardContent className="flex items-start justify-between gap-3">
        <div className="space-y-1">
          <p className="text-muted-foreground text-sm">{label}</p>
          <p className="text-2xl font-semibold tabular-nums">{value}</p>
          {trend ? (
            <p
              className={cn(
                "text-xs font-medium",
                trendDirection === "down" ? "text-red-600" : "text-emerald-600",
              )}
            >
              {trend}
            </p>
          ) : null}
        </div>
        <span className="bg-primary/10 text-primary flex size-9 shrink-0 items-center justify-center rounded-lg">
          <Icon className="size-4.5" />
        </span>
      </CardContent>
    </Card>
  );
}
