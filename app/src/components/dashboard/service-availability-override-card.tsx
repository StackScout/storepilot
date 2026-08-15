"use client";

import { useState } from "react";
import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import { toast } from "sonner";
import { Loader2 } from "lucide-react";
import { Button } from "@/components/ui/button";
import { Card, CardContent } from "@/components/ui/card";
import { Input } from "@/components/ui/input";
import { Checkbox } from "@/components/ui/checkbox";
import { availabilityService } from "@/services";
import type { WeeklyAvailabilityRule } from "@/types";

const DAY_LABELS = ["Monday", "Tuesday", "Wednesday", "Thursday", "Friday", "Saturday", "Sunday"];

function defaultRules(): WeeklyAvailabilityRule[] {
  return DAY_LABELS.map((_, i) => ({
    dayOfWeek: i + 1,
    isOpen: i < 5,
    openTime: i < 5 ? "09:00" : undefined,
    closeTime: i < 5 ? "17:00" : undefined,
  }));
}

/**
 * Lets a seller give one service its own weekly hours instead of inheriting
 * the store's default template (see dashboard/availability/page.tsx for the
 * store-wide editor this mirrors) — exceptions (holidays, one-off closures)
 * always stay store-wide regardless, so they're not editable here.
 */
export function ServiceAvailabilityOverrideCard({ storeId, serviceId }: { storeId: string; serviceId: string }) {
  const queryClient = useQueryClient();
  const queryKey = ["service-availability-override", storeId, serviceId];

  const { data: override, isLoading } = useQuery({
    queryKey,
    queryFn: () => availabilityService.getServiceAvailabilityOverride(storeId, serviceId),
  });

  const [enabled, setEnabled] = useState(false);
  const [rules, setRules] = useState<WeeklyAvailabilityRule[]>(defaultRules());

  // Same during-render sync pattern as dashboard/availability/page.tsx.
  const [synced, setSynced] = useState(override);
  if (override !== synced) {
    setSynced(override);
    if (override) {
      setEnabled(override.hasCustomAvailability);
      if (override.weeklyRules.length === 7) {
        setRules([...override.weeklyRules].sort((a, b) => a.dayOfWeek - b.dayOfWeek));
      }
    }
  }

  const mutation = useMutation({
    mutationFn: async () => {
      if (!enabled) {
        await availabilityService.disableServiceAvailabilityOverride(storeId, serviceId);
        return;
      }
      for (const rule of rules) {
        if (rule.isOpen && (!rule.openTime || !rule.closeTime || rule.openTime >= rule.closeTime)) {
          throw new Error(`${DAY_LABELS[rule.dayOfWeek - 1]} needs a valid opening time before its closing time`);
        }
      }
      await availabilityService.upsertServiceAvailabilityOverride(storeId, serviceId, { rules });
    },
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey });
      toast.success(enabled ? "Custom hours saved" : "Reverted to the store's default hours");
    },
    onError: (error) => toast.error(error instanceof Error ? error.message : "Couldn't save availability"),
  });

  function updateRule(dayOfWeek: number, patch: Partial<WeeklyAvailabilityRule>) {
    setRules((prev) => prev.map((r) => (r.dayOfWeek === dayOfWeek ? { ...r, ...patch } : r)));
  }

  if (isLoading) {
    return null;
  }

  return (
    <Card>
      <CardContent className="space-y-4">
        <div>
          <h2 className="font-semibold">Availability</h2>
          <p className="text-muted-foreground text-xs">
            By default this service follows the store&apos;s weekly hours. Give it its own hours if it
            needs to be bookable at different times.
          </p>
        </div>

        <label className="flex items-center gap-2.5">
          <Checkbox checked={enabled} onCheckedChange={(checked) => setEnabled(checked === true)} />
          <span className="text-sm font-medium">Use custom hours for this service</span>
        </label>

        {enabled ? (
          <div className="divide-y border-t pt-2">
            {rules.map((rule) => (
              <div key={rule.dayOfWeek} className="flex flex-wrap items-center gap-3 py-3">
                <label className="flex w-36 shrink-0 items-center gap-2.5">
                  <Checkbox
                    checked={rule.isOpen}
                    onCheckedChange={(checked) => updateRule(rule.dayOfWeek, { isOpen: checked === true })}
                  />
                  <span className="text-sm font-medium">{DAY_LABELS[rule.dayOfWeek - 1]}</span>
                </label>
                {rule.isOpen ? (
                  <div className="flex items-center gap-2">
                    <Input
                      type="time"
                      className="w-32"
                      value={rule.openTime ?? ""}
                      onChange={(e) => updateRule(rule.dayOfWeek, { openTime: e.target.value })}
                    />
                    <span className="text-muted-foreground text-sm">to</span>
                    <Input
                      type="time"
                      className="w-32"
                      value={rule.closeTime ?? ""}
                      onChange={(e) => updateRule(rule.dayOfWeek, { closeTime: e.target.value })}
                    />
                  </div>
                ) : (
                  <span className="text-muted-foreground text-sm">Closed</span>
                )}
              </div>
            ))}
          </div>
        ) : null}

        <div className="flex justify-end">
          <Button onClick={() => mutation.mutate()} disabled={mutation.isPending} variant="outline">
            {mutation.isPending ? <Loader2 className="size-4 animate-spin" /> : null}
            Save availability
          </Button>
        </div>
      </CardContent>
    </Card>
  );
}
