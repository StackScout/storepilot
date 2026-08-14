"use client";

import { useState } from "react";
import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import { toast } from "sonner";
import { CalendarDays, Loader2, Plus, Trash2 } from "lucide-react";
import { Button } from "@/components/ui/button";
import { Card, CardContent } from "@/components/ui/card";
import { Input } from "@/components/ui/input";
import { Label } from "@/components/ui/label";
import { Checkbox } from "@/components/ui/checkbox";
import { EmptyState } from "@/components/shared/empty-state";
import { useSellerStoreId } from "@/hooks/use-seller-store";
import { availabilityService } from "@/services";
import type { AvailabilityExceptionInput, WeeklyAvailabilityRule } from "@/types";

const DAY_LABELS = ["Monday", "Tuesday", "Wednesday", "Thursday", "Friday", "Saturday", "Sunday"];

function defaultRules(): WeeklyAvailabilityRule[] {
  return DAY_LABELS.map((_, i) => ({
    dayOfWeek: i + 1,
    isOpen: i < 5,
    openTime: i < 5 ? "09:00" : undefined,
    closeTime: i < 5 ? "17:00" : undefined,
  }));
}

export default function DashboardAvailabilityPage() {
  const queryClient = useQueryClient();
  const storeId = useSellerStoreId();

  const { data: availability, isLoading } = useQuery({
    queryKey: ["availability", storeId],
    queryFn: () => availabilityService.getAvailability(storeId),
  });

  const [rules, setRules] = useState<WeeklyAvailabilityRule[]>(defaultRules());
  const [leadTimeMinutes, setLeadTimeMinutes] = useState(120);

  // Adjusting local editable state from freshly-loaded query data, once per
  // load — done during render (React's documented pattern for this) rather
  // than in an effect, which would cause an extra commit.
  const [syncedAvailability, setSyncedAvailability] = useState(availability);
  if (availability !== syncedAvailability) {
    setSyncedAvailability(availability);
    if (availability) {
      if (availability.weeklyRules.length === 7) {
        setRules([...availability.weeklyRules].sort((a, b) => a.dayOfWeek - b.dayOfWeek));
      }
      setLeadTimeMinutes(availability.leadTimeMinutes);
    }
  }

  const weeklyMutation = useMutation({
    mutationFn: () => {
      for (const rule of rules) {
        if (rule.isOpen && (!rule.openTime || !rule.closeTime || rule.openTime >= rule.closeTime)) {
          throw new Error(`${DAY_LABELS[rule.dayOfWeek - 1]} needs a valid opening time before its closing time`);
        }
      }
      return availabilityService.upsertWeeklyRules(storeId, { rules, leadTimeMinutes });
    },
    onSuccess: (data) => {
      queryClient.setQueryData(["availability", storeId], data);
      toast.success("Weekly hours saved");
    },
    onError: (error) => toast.error(error instanceof Error ? error.message : "Couldn't save weekly hours"),
  });

  const [newException, setNewException] = useState<AvailabilityExceptionInput>({
    date: "",
    isOpen: false,
  });

  const createExceptionMutation = useMutation({
    mutationFn: (input: AvailabilityExceptionInput) => availabilityService.createException(storeId, input),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ["availability", storeId] });
      setNewException({ date: "", isOpen: false });
      toast.success("Exception added");
    },
    onError: () => toast.error("Couldn't add exception"),
  });

  const deleteExceptionMutation = useMutation({
    mutationFn: (exceptionId: string) => availabilityService.deleteException(storeId, exceptionId),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ["availability", storeId] });
      toast.success("Exception removed");
    },
    onError: () => toast.error("Couldn't remove exception"),
  });

  function updateRule(dayOfWeek: number, patch: Partial<WeeklyAvailabilityRule>) {
    setRules((prev) => prev.map((r) => (r.dayOfWeek === dayOfWeek ? { ...r, ...patch } : r)));
  }

  if (isLoading) {
    return (
      <div className="flex justify-center py-24">
        <Loader2 className="text-muted-foreground size-6 animate-spin" />
      </div>
    );
  }

  return (
    <div className="max-w-3xl space-y-6">
      <div>
        <h1 className="text-2xl font-bold">Availability</h1>
        <p className="text-muted-foreground text-sm">
          Set your weekly open hours and any one-off closures or special openings — buyers can only book
          slots inside these windows.
        </p>
      </div>

      <Card>
        <CardContent className="space-y-4">
          <h2 className="font-semibold">Weekly hours</h2>
          <div className="divide-y">
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

          <div className="space-y-1.5 border-t pt-4">
            <Label htmlFor="leadTimeMinutes">Lead time (minutes)</Label>
            <Input
              id="leadTimeMinutes"
              type="number"
              step="15"
              min="0"
              className="w-40"
              value={leadTimeMinutes}
              onChange={(e) => setLeadTimeMinutes(Number(e.target.value))}
            />
            <p className="text-muted-foreground text-xs">
              How much notice you need before a booking — also used as the cutoff for buyers cancelling a
              booking close to its start time.
            </p>
          </div>

          <div className="flex justify-end">
            <Button onClick={() => weeklyMutation.mutate()} disabled={weeklyMutation.isPending}>
              {weeklyMutation.isPending ? <Loader2 className="size-4 animate-spin" /> : null}
              Save weekly hours
            </Button>
          </div>
        </CardContent>
      </Card>

      <Card>
        <CardContent className="space-y-4">
          <h2 className="font-semibold">Exceptions</h2>
          <p className="text-muted-foreground text-xs">
            Holidays, one-off closures, or a special opening on a normally-closed day.
          </p>

          {!availability || availability.exceptions.length === 0 ? (
            <EmptyState icon={CalendarDays} title="No exceptions yet" />
          ) : (
            <ul className="divide-y">
              {availability.exceptions.map((exception) => (
                <li key={exception.id} className="flex items-center justify-between gap-3 py-2.5">
                  <div>
                    <p className="text-sm font-medium">
                      {exception.date}{" "}
                      <span className="text-muted-foreground font-normal">
                        {exception.isOpen
                          ? `— open ${exception.openTime}–${exception.closeTime}`
                          : "— closed"}
                      </span>
                    </p>
                    {exception.note ? (
                      <p className="text-muted-foreground text-xs">{exception.note}</p>
                    ) : null}
                  </div>
                  <Button
                    variant="ghost"
                    size="icon"
                    className="text-destructive size-8"
                    onClick={() => deleteExceptionMutation.mutate(exception.id)}
                  >
                    <Trash2 className="size-3.5" />
                  </Button>
                </li>
              ))}
            </ul>
          )}

          <div className="space-y-3 border-t pt-4">
            <div className="grid gap-3 sm:grid-cols-4">
              <div className="space-y-1.5">
                <Label htmlFor="exceptionDate">Date</Label>
                <Input
                  id="exceptionDate"
                  type="date"
                  value={newException.date}
                  onChange={(e) => setNewException((prev) => ({ ...prev, date: e.target.value }))}
                />
              </div>
              <label className="flex items-center gap-2.5 pt-6">
                <Checkbox
                  checked={newException.isOpen}
                  onCheckedChange={(checked) =>
                    setNewException((prev) => ({ ...prev, isOpen: checked === true }))
                  }
                />
                <span className="text-sm">Special opening</span>
              </label>
              {newException.isOpen ? (
                <>
                  <div className="space-y-1.5">
                    <Label htmlFor="exceptionOpenTime">Open</Label>
                    <Input
                      id="exceptionOpenTime"
                      type="time"
                      value={newException.openTime ?? ""}
                      onChange={(e) => setNewException((prev) => ({ ...prev, openTime: e.target.value }))}
                    />
                  </div>
                  <div className="space-y-1.5">
                    <Label htmlFor="exceptionCloseTime">Close</Label>
                    <Input
                      id="exceptionCloseTime"
                      type="time"
                      value={newException.closeTime ?? ""}
                      onChange={(e) => setNewException((prev) => ({ ...prev, closeTime: e.target.value }))}
                    />
                  </div>
                </>
              ) : null}
            </div>
            <div className="space-y-1.5">
              <Label htmlFor="exceptionNote">Note (optional)</Label>
              <Input
                id="exceptionNote"
                placeholder="e.g. Closed for public holiday"
                value={newException.note ?? ""}
                onChange={(e) => setNewException((prev) => ({ ...prev, note: e.target.value }))}
              />
            </div>
            <div className="flex justify-end">
              <Button
                variant="outline"
                disabled={
                  !newException.date ||
                  (newException.isOpen && (!newException.openTime || !newException.closeTime)) ||
                  createExceptionMutation.isPending
                }
                onClick={() => createExceptionMutation.mutate(newException)}
              >
                <Plus className="size-4" /> Add exception
              </Button>
            </div>
          </div>
        </CardContent>
      </Card>
    </div>
  );
}
