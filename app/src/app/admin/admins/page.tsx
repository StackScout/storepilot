"use client";

import { useState } from "react";
import { useForm } from "react-hook-form";
import { zodResolver } from "@hookform/resolvers/zod";
import { z } from "zod";
import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import { toast } from "sonner";
import { Loader2, TriangleAlert, UserPlus, Users } from "lucide-react";
import { Button } from "@/components/ui/button";
import { Card, CardContent } from "@/components/ui/card";
import { Input } from "@/components/ui/input";
import { Label } from "@/components/ui/label";
import { Checkbox } from "@/components/ui/checkbox";
import {
  Dialog,
  DialogContent,
  DialogDescription,
  DialogFooter,
  DialogHeader,
  DialogTitle,
} from "@/components/ui/dialog";
import { EmptyState } from "@/components/shared/empty-state";
import { TableRowSkeleton } from "@/components/shared/loading-skeletons";
import { formatDateTime } from "@/lib/format";
import { adminService } from "@/services";

const inviteSchema = z
  .object({
    name: z.string().min(2, "Enter a full name"),
    email: z.string().email("Enter a valid email"),
    password: z.string().min(8, "Password must be at least 8 characters"),
    confirmPassword: z.string().min(1, "Confirm the password"),
  })
  .refine((data) => data.password === data.confirmPassword, {
    message: "Passwords do not match",
    path: ["confirmPassword"],
  });

type InviteFormValues = z.infer<typeof inviteSchema>;

export default function AdminAdminsPage() {
  const queryClient = useQueryClient();
  const [dialogOpen, setDialogOpen] = useState(false);
  const [showPassword, setShowPassword] = useState(false);

  const {
    data: admins,
    isLoading,
    isError,
  } = useQuery({
    queryKey: ["admin-admins"],
    queryFn: () => adminService.listAdmins(),
  });

  const {
    register,
    handleSubmit,
    reset,
    formState: { errors },
  } = useForm<InviteFormValues>({ resolver: zodResolver(inviteSchema) });

  const inviteMutation = useMutation({
    mutationFn: (values: InviteFormValues) => adminService.inviteAdmin(values.name, values.email, values.password),
    onSuccess: (result) => {
      queryClient.invalidateQueries({ queryKey: ["admin-admins"] });
      toast.success(`Invited ${result.name} — share the password with them directly.`);
      setDialogOpen(false);
      reset();
      setShowPassword(false);
    },
    onError: (error: Error) => toast.error(error.message || "Couldn't invite this admin"),
  });

  return (
    <div className="space-y-6">
      <div className="flex items-start justify-between gap-4">
        <div>
          <h1 className="text-2xl font-bold">Admins</h1>
          <p className="text-muted-foreground text-sm">
            Everyone with platform-admin access. Invite sets a password directly — share it with the
            new admin yourself.
          </p>
        </div>
        <Button type="button" onClick={() => setDialogOpen(true)}>
          <UserPlus className="size-3.5" /> Invite admin
        </Button>
      </div>

      <Card>
        <CardContent className="p-0">
          {isLoading ? (
            <div className="space-y-2 p-6">
              <TableRowSkeleton columns={3} />
              <TableRowSkeleton columns={3} />
            </div>
          ) : isError ? (
            <EmptyState
              icon={TriangleAlert}
              title="Couldn't load admins"
              description="Something went wrong fetching the admin list. Try refreshing the page."
            />
          ) : !admins || admins.length === 0 ? (
            <EmptyState icon={Users} title="No admins found" />
          ) : (
            <div className="overflow-x-auto">
              <table className="w-full text-sm">
                <thead>
                  <tr className="text-muted-foreground border-b text-left text-xs">
                    <th className="px-6 py-3 font-medium">Name</th>
                    <th className="px-6 py-3 font-medium">Email</th>
                    <th className="px-6 py-3 font-medium">Invited</th>
                  </tr>
                </thead>
                <tbody>
                  {admins.map((admin) => (
                    <tr key={admin.email} className="border-b last:border-0">
                      <td className="px-6 py-3 font-medium">{admin.name}</td>
                      <td className="text-muted-foreground px-6 py-3">{admin.email}</td>
                      <td className="text-muted-foreground px-6 py-3">{formatDateTime(admin.invitedAt)}</td>
                    </tr>
                  ))}
                </tbody>
              </table>
            </div>
          )}
        </CardContent>
      </Card>

      <Dialog
        open={dialogOpen}
        onOpenChange={(open) => {
          setDialogOpen(open);
          if (!open) {
            reset();
            setShowPassword(false);
          }
        }}
      >
        <DialogContent>
          <DialogHeader>
            <DialogTitle>Invite a new admin</DialogTitle>
            <DialogDescription>
              Set an initial password and share it with them directly — there&apos;s no invite-link
              flow, this creates a ready-to-use account immediately.
            </DialogDescription>
          </DialogHeader>
          <form onSubmit={handleSubmit((values) => inviteMutation.mutate(values))} className="space-y-4">
            <div className="space-y-1.5">
              <Label htmlFor="name">Full name</Label>
              <Input id="name" placeholder="e.g. Priya Fernando" {...register("name")} />
              {errors.name ? <p className="text-destructive text-xs">{errors.name.message}</p> : null}
            </div>
            <div className="space-y-1.5">
              <Label htmlFor="email">Email</Label>
              <Input id="email" type="email" placeholder="admin@storepilot.com.au" {...register("email")} />
              {errors.email ? <p className="text-destructive text-xs">{errors.email.message}</p> : null}
            </div>
            <div className="space-y-1.5">
              <Label htmlFor="password">Password</Label>
              <Input id="password" type={showPassword ? "text" : "password"} {...register("password")} />
              {errors.password ? <p className="text-destructive text-xs">{errors.password.message}</p> : null}
            </div>
            <div className="space-y-1.5">
              <Label htmlFor="confirmPassword">Confirm password</Label>
              <Input
                id="confirmPassword"
                type={showPassword ? "text" : "password"}
                {...register("confirmPassword")}
              />
              {errors.confirmPassword ? (
                <p className="text-destructive text-xs">{errors.confirmPassword.message}</p>
              ) : null}
            </div>
            <label className="flex items-center gap-2 text-sm">
              <Checkbox checked={showPassword} onCheckedChange={(checked) => setShowPassword(checked === true)} />
              <span className="text-muted-foreground">Show password</span>
            </label>
            <DialogFooter>
              <Button type="button" variant="outline" onClick={() => setDialogOpen(false)}>
                Cancel
              </Button>
              <Button type="submit" disabled={inviteMutation.isPending}>
                {inviteMutation.isPending ? <Loader2 className="size-4 animate-spin" /> : null}
                Invite admin
              </Button>
            </DialogFooter>
          </form>
        </DialogContent>
      </Dialog>
    </div>
  );
}
