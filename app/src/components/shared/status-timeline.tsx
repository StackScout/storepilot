import { CheckCircle2, Circle } from "lucide-react";
import { formatDateTime } from "@/lib/format";

interface TimelineEntry {
  label: string;
  timestamp: string;
  note?: string;
}

/** Vertical status-history stepper shared by order/booking detail pages, buyer and seller. Latest entry gets a filled check, earlier ones a hollow circle. */
export function StatusTimeline({ entries }: { entries: TimelineEntry[] }) {
  return (
    <ol className="space-y-4">
      {entries.map((entry, i) => (
        <li key={i} className="flex gap-3">
          <span className="mt-0.5">
            {i === entries.length - 1 ? (
              <CheckCircle2 className="text-primary size-4" />
            ) : (
              <Circle className="text-muted-foreground size-4" />
            )}
          </span>
          <div>
            <p className="text-sm font-medium">{entry.label}</p>
            <p className="text-muted-foreground text-xs">{formatDateTime(entry.timestamp)}</p>
            {entry.note ? <p className="text-muted-foreground text-xs">{entry.note}</p> : null}
          </div>
        </li>
      ))}
    </ol>
  );
}
