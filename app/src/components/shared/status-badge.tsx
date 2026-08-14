import { Badge } from "@/components/ui/badge";
import { cn } from "@/lib/utils";

/**
 * The five semantic status colors (see globals.css's --success/--warning/
 * --danger/--info/--highlight tokens) — every status pill/badge in the app
 * (order status, store verification, payout/fee-collection state, audit-log
 * decisions, Pro-plan badge, stock warnings, ...) should map onto one of
 * these rather than hardcoding its own Tailwind color shade.
 */
export type StatusTone = "success" | "warning" | "danger" | "info" | "highlight" | "neutral";

const TONE_CLASSES: Record<StatusTone, string> = {
  success: "bg-success text-success-foreground",
  warning: "bg-warning text-warning-foreground",
  danger: "bg-danger text-danger-foreground",
  info: "bg-info text-info-foreground",
  highlight: "bg-highlight text-highlight-foreground",
  neutral: "bg-muted text-muted-foreground",
};

export function StatusBadge({
  tone,
  children,
  className,
}: {
  tone: StatusTone;
  children: React.ReactNode;
  className?: string;
}) {
  return <Badge className={cn("border-0 font-medium", TONE_CLASSES[tone], className)}>{children}</Badge>;
}
