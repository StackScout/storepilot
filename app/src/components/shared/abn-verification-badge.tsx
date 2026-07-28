"use client";

import { useEffect, useState } from "react";
import { useQuery } from "@tanstack/react-query";
import { CheckCircle2, Loader2, TriangleAlert } from "lucide-react";
import { abnService } from "@/services";

const DEBOUNCE_MS = 500;

/** ABR's own check-digit algorithm expects exactly 11 digits — a shorter value is just a still-typing ABN, not an error. */
function digitCount(abn: string): number {
  return abn.replace(/\D/g, "").length;
}

/**
 * Live ABR "ABN Lookup" result for whatever ABN is currently typed —
 * debounced, and only fires once 11 digits are present (matches ABN
 * length, avoids spamming the backend on every keystroke). Renders nothing
 * until then, and also renders nothing when the backend has no ABR GUID
 * configured yet ("not-configured") — this is expected pre-launch, not an
 * error to surface to every seller/admin who looks at this field.
 */
export function AbnVerificationBadge({ abn }: { abn: string | undefined }) {
  const [debouncedAbn, setDebouncedAbn] = useState(abn ?? "");

  useEffect(() => {
    const timer = setTimeout(() => setDebouncedAbn(abn ?? ""), DEBOUNCE_MS);
    return () => clearTimeout(timer);
  }, [abn]);

  const ready = digitCount(debouncedAbn) === 11;

  const { data, isFetching } = useQuery({
    queryKey: ["abn-lookup", debouncedAbn],
    queryFn: () => abnService.lookupAbn(debouncedAbn),
    enabled: ready,
    staleTime: 5 * 60_000,
  });

  if (!ready) return null;
  if (isFetching) {
    return (
      <p className="text-muted-foreground flex items-center gap-1 text-xs">
        <Loader2 className="size-3 animate-spin" /> Checking ABN...
      </p>
    );
  }
  if (!data || data.status === "not-configured") return null;

  if (data.status === "found") {
    const isActive = !data.abnStatus || data.abnStatus.toLowerCase() === "active";
    return (
      <p
        className={
          isActive
            ? "flex items-center gap-1 text-xs text-emerald-600 dark:text-emerald-400"
            : "flex items-center gap-1 text-xs text-amber-600 dark:text-amber-400"
        }
      >
        {isActive ? <CheckCircle2 className="size-3 shrink-0" /> : <TriangleAlert className="size-3 shrink-0" />}
        Registered to {data.entityName}
        {data.abnStatus ? ` — ${data.abnStatus}` : ""}
      </p>
    );
  }

  if (data.status === "invalid-format") {
    return (
      <p className="text-destructive flex items-center gap-1 text-xs">
        <TriangleAlert className="size-3 shrink-0" /> That doesn&apos;t look like a valid ABN — check the digits.
      </p>
    );
  }

  if (data.status === "not-found") {
    return (
      <p className="flex items-center gap-1 text-xs text-amber-600 dark:text-amber-400">
        <TriangleAlert className="size-3 shrink-0" /> No matching ABN found in the register — double-check the
        number.
      </p>
    );
  }

  // "error" — a transient ABR/network issue, not the seller's/admin's fault. Stay quiet rather than alarm them.
  return null;
}
