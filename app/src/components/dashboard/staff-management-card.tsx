"use client";

import { useState } from "react";
import { useForm } from "react-hook-form";
import { zodResolver } from "@hookform/resolvers/zod";
import { z } from "zod";
import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import { toast } from "sonner";
import { Loader2, Trash2, UserPlus, Users } from "lucide-react";
import { Button } from "@/components/ui/button";
import { Card, CardContent } from "@/components/ui/card";
import { Input } from "@/components/ui/input";
import { Label } from "@/components/ui/label";
import { StatusBadge } from "@/components/shared/status-badge";
import {
  Dialog,
  DialogContent,
  DialogDescription,
  DialogFooter,
  DialogHeader,
  DialogTitle,
} from "@/components/ui/dialog";
import { EmptyState } from "@/components/shared/empty-state";
import { formatDateTime } from "@/lib/format";
import { storeStaffService } from "@/services";
import type { StoreStaffInvite, StoreStaffMember } from "@/types";

const inviteSchema = z.object({
  name: z.string().min(2, "Enter a full name"),
  email: z.string().email("Enter a valid email"),
});

type InviteFormValues = z.infer<typeof inviteSchema>;

type PendingRemoval = { kind: "member"; item: StoreStaffMember } | { kind: "invite"; item: StoreStaffInvite };

/**
 * Store-owner-only — invite/manage staff who get full operational access
 * (orders, products, bookings, ...) to this store but not financial/
 * sensitive data. See backend StoreStaffMember.kt's doc comment.
 */
export function StaffManagementCard({ storeId }: { storeId: string }) {
  const queryClient = useQueryClient();
  const [dialogOpen, setDialogOpen] = useState(false);
  const [pendingRemoval, setPendingRemoval] = useState<PendingRemoval | null>(null);

  const staffQuery = useQuery({
    queryKey: ["store", storeId, "staff"],
    queryFn: () => storeStaffService.listStaff(storeId),
  });
  const invitesQuery = useQuery({
    queryKey: ["store", storeId, "staff-invites"],
    queryFn: () => storeStaffService.listPendingInvites(storeId),
  });

  const {
    register,
    handleSubmit,
    reset,
    formState: { errors },
  } = useForm<InviteFormValues>({ resolver: zodResolver(inviteSchema) });

  const inviteMutation = useMutation({
    mutationFn: (values: InviteFormValues) => storeStaffService.inviteStaff(storeId, values),
    onSuccess: (invite) => {
      queryClient.invalidateQueries({ queryKey: ["store", storeId, "staff-invites"] });
      toast.success(`Invited ${invite.name} — they'll get an email to set up their account.`);
      setDialogOpen(false);
      reset();
    },
    onError: (error: Error) => toast.error(error.message || "Couldn't send this invite"),
  });

  const removeMutation = useMutation({
    mutationFn: () =>
      pendingRemoval?.kind === "member"
        ? storeStaffService.removeStaff(storeId, pendingRemoval.item.id)
        : storeStaffService.revokeInvite(storeId, pendingRemoval!.item.id),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ["store", storeId, "staff"] });
      queryClient.invalidateQueries({ queryKey: ["store", storeId, "staff-invites"] });
      toast.success(pendingRemoval?.kind === "member" ? "Removed from your store" : "Invite revoked");
      setPendingRemoval(null);
    },
    onError: () => toast.error("Couldn't complete this action — please try again."),
  });

  const hasAnyone = (staffQuery.data?.length ?? 0) > 0 || (invitesQuery.data?.length ?? 0) > 0;

  return (
    <Card>
      <CardContent className="space-y-4">
        <div className="flex items-start justify-between gap-4">
          <div>
            <h2 className="font-semibold">Staff</h2>
            <p className="text-muted-foreground text-sm">
              Give someone access to run orders, products, and bookings for your store — without
              sharing your own login or exposing revenue, payouts, or bank details.
            </p>
          </div>
          <Button type="button" size="sm" onClick={() => setDialogOpen(true)}>
            <UserPlus className="size-3.5" /> Invite staff
          </Button>
        </div>

        {!hasAnyone && !staffQuery.isLoading && !invitesQuery.isLoading ? (
          <EmptyState icon={Users} title="No staff yet" description="Invite someone to help run your store." />
        ) : (
          <div className="divide-y">
            {(staffQuery.data ?? []).map((member) => (
              <div key={member.id} className="flex items-center justify-between gap-3 py-3">
                <div>
                  <p className="text-sm font-medium">{member.name}</p>
                  <p className="text-muted-foreground text-xs">
                    {member.email} · joined {formatDateTime(member.joinedAt)}
                  </p>
                </div>
                <Button
                  type="button"
                  variant="ghost"
                  size="icon"
                  className="text-destructive size-8"
                  onClick={() => setPendingRemoval({ kind: "member", item: member })}
                >
                  <Trash2 className="size-3.5" />
                </Button>
              </div>
            ))}
            {(invitesQuery.data ?? []).map((invite) => (
              <div key={invite.id} className="flex items-center justify-between gap-3 py-3">
                <div>
                  <p className="flex items-center gap-2 text-sm font-medium">
                    {invite.name}
                    <StatusBadge tone="warning">Pending</StatusBadge>
                  </p>
                  <p className="text-muted-foreground text-xs">
                    {invite.email} · invited {formatDateTime(invite.invitedAt)}
                  </p>
                </div>
                <Button
                  type="button"
                  variant="ghost"
                  size="icon"
                  className="text-destructive size-8"
                  onClick={() => setPendingRemoval({ kind: "invite", item: invite })}
                >
                  <Trash2 className="size-3.5" />
                </Button>
              </div>
            ))}
          </div>
        )}
      </CardContent>

      <Dialog
        open={dialogOpen}
        onOpenChange={(open) => {
          setDialogOpen(open);
          if (!open) reset();
        }}
      >
        <DialogContent>
          <DialogHeader>
            <DialogTitle>Invite staff</DialogTitle>
            <DialogDescription>
              They&apos;ll get an email with a link to set up their own account — no password to share.
            </DialogDescription>
          </DialogHeader>
          <form onSubmit={handleSubmit((values) => inviteMutation.mutate(values))} className="space-y-4">
            <div className="space-y-1.5">
              <Label htmlFor="staff-name">Full name</Label>
              <Input id="staff-name" placeholder="e.g. Priya Fernando" {...register("name")} />
              {errors.name ? <p className="text-destructive text-xs">{errors.name.message}</p> : null}
            </div>
            <div className="space-y-1.5">
              <Label htmlFor="staff-email">Email</Label>
              <Input id="staff-email" type="email" placeholder="staff@example.com" {...register("email")} />
              {errors.email ? <p className="text-destructive text-xs">{errors.email.message}</p> : null}
            </div>
            <DialogFooter>
              <Button type="button" variant="outline" onClick={() => setDialogOpen(false)}>
                Cancel
              </Button>
              <Button type="submit" disabled={inviteMutation.isPending}>
                {inviteMutation.isPending ? <Loader2 className="size-4 animate-spin" /> : null}
                Send invite
              </Button>
            </DialogFooter>
          </form>
        </DialogContent>
      </Dialog>

      <Dialog open={!!pendingRemoval} onOpenChange={(open) => !open && setPendingRemoval(null)}>
        <DialogContent>
          <DialogHeader>
            <DialogTitle>{pendingRemoval?.kind === "member" ? "Remove staff member?" : "Revoke invite?"}</DialogTitle>
            <DialogDescription>
              {pendingRemoval?.kind === "member"
                ? `"${pendingRemoval.item.name}" will lose access to this store immediately.`
                : `The invite sent to "${pendingRemoval?.item.email}" will no longer work.`}
            </DialogDescription>
          </DialogHeader>
          <DialogFooter>
            <Button type="button" variant="outline" onClick={() => setPendingRemoval(null)}>
              Cancel
            </Button>
            <Button type="button" variant="destructive" disabled={removeMutation.isPending} onClick={() => removeMutation.mutate()}>
              {removeMutation.isPending ? <Loader2 className="size-4 animate-spin" /> : null}
              {pendingRemoval?.kind === "member" ? "Remove" : "Revoke"}
            </Button>
          </DialogFooter>
        </DialogContent>
      </Dialog>
    </Card>
  );
}
