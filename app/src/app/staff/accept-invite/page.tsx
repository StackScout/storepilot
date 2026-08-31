"use client";

import { Suspense } from "react";
import { useRouter, useSearchParams } from "next/navigation";
import { useForm } from "react-hook-form";
import { zodResolver } from "@hookform/resolvers/zod";
import { z } from "zod";
import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import { toast } from "sonner";
import { Loader2, Store, TriangleAlert } from "lucide-react";
import { Button } from "@/components/ui/button";
import { Input } from "@/components/ui/input";
import { Label } from "@/components/ui/label";
import { Card, CardContent } from "@/components/ui/card";
import { EmptyState } from "@/components/shared/empty-state";
import { ApiRequestError } from "@/lib/api-client";
import { authService, storeStaffService } from "@/services";

const acceptInviteSchema = z
  .object({
    name: z.string().min(2, "Enter your full name"),
    password: z.string().min(8, "Password must be at least 8 characters"),
    confirmPassword: z.string().min(1, "Confirm your password"),
  })
  .refine((data) => data.password === data.confirmPassword, {
    message: "Passwords do not match",
    path: ["confirmPassword"],
  });

type AcceptInviteFormValues = z.infer<typeof acceptInviteSchema>;

/**
 * Redeems a store-owner-issued staff invite — creates the invitee's own
 * account (own password, chosen here) and signs them straight in. No
 * verify-email step: the invite link itself already proved they control
 * that inbox, see backend AuthController.acceptStaffInvite's doc comment.
 */
export default function AcceptStaffInvitePage() {
  return (
    <Suspense>
      <AcceptStaffInviteForm />
    </Suspense>
  );
}

function AcceptStaffInviteForm() {
  const router = useRouter();
  const queryClient = useQueryClient();
  const searchParams = useSearchParams();
  const token = searchParams.get("token") ?? "";

  const inviteQuery = useQuery({
    queryKey: ["staff-invite", token],
    queryFn: () => storeStaffService.getInviteDetails(token),
    enabled: !!token,
    retry: false,
  });

  const {
    register,
    handleSubmit,
    formState: { errors },
  } = useForm<AcceptInviteFormValues>({
    resolver: zodResolver(acceptInviteSchema),
    values: inviteQuery.data ? { name: inviteQuery.data.name, password: "", confirmPassword: "" } : undefined,
  });

  const mutation = useMutation({
    mutationFn: (values: AcceptInviteFormValues) => authService.acceptStaffInvite(token, values.password, values.name),
    onSuccess: () => {
      queryClient.clear();
      toast.success("You're in! Redirecting to the dashboard...");
      router.push("/dashboard");
      router.refresh();
    },
    onError: (error: Error) => toast.error(error.message || "Couldn't accept this invite. Please try again."),
  });

  if (!token) {
    return (
      <div className="mx-auto max-w-sm px-4 py-16 sm:px-6">
        <EmptyState icon={TriangleAlert} title="Missing invite link" description="Use the link from your invite email to get here." />
      </div>
    );
  }

  if (inviteQuery.isLoading) {
    return (
      <div className="flex justify-center py-24">
        <Loader2 className="text-muted-foreground size-6 animate-spin" />
      </div>
    );
  }

  if (inviteQuery.isError) {
    const message =
      inviteQuery.error instanceof ApiRequestError ? inviteQuery.error.message : "This invite link is invalid or has expired.";
    return (
      <div className="mx-auto max-w-sm px-4 py-16 sm:px-6">
        <EmptyState icon={TriangleAlert} title="Couldn't use this invite" description={message} />
      </div>
    );
  }

  const invite = inviteQuery.data!;

  return (
    <div className="mx-auto max-w-sm px-4 py-16 sm:px-6">
      <div className="mb-6 space-y-2 text-center">
        <span className="bg-primary/10 text-primary mx-auto flex size-12 items-center justify-center rounded-full">
          <Store className="size-6" />
        </span>
        <h1 className="text-2xl font-bold">Join {invite.storeName}</h1>
        <p className="text-muted-foreground text-sm">
          Set a password for <span className="font-medium">{invite.email}</span> to finish joining.
        </p>
      </div>
      <Card>
        <CardContent>
          <form onSubmit={handleSubmit((values) => mutation.mutate(values))} className="space-y-4">
            <div className="space-y-1.5">
              <Label htmlFor="name">Full name</Label>
              <Input id="name" placeholder="e.g. Priya Fernando" {...register("name")} />
              {errors.name ? <p className="text-destructive text-xs">{errors.name.message}</p> : null}
            </div>
            <div className="space-y-1.5">
              <Label htmlFor="password">Password</Label>
              <Input id="password" type="password" {...register("password")} />
              {errors.password ? <p className="text-destructive text-xs">{errors.password.message}</p> : null}
            </div>
            <div className="space-y-1.5">
              <Label htmlFor="confirmPassword">Confirm password</Label>
              <Input id="confirmPassword" type="password" {...register("confirmPassword")} />
              {errors.confirmPassword ? <p className="text-destructive text-xs">{errors.confirmPassword.message}</p> : null}
            </div>
            <Button type="submit" size="lg" className="w-full" disabled={mutation.isPending}>
              {mutation.isPending ? <Loader2 className="size-4 animate-spin" /> : null}
              Join store
            </Button>
          </form>
        </CardContent>
      </Card>
    </div>
  );
}
