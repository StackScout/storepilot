"use client";

import { Suspense, useState } from "react";
import { useRouter, useSearchParams } from "next/navigation";
import { useForm } from "react-hook-form";
import { zodResolver } from "@hookform/resolvers/zod";
import { z } from "zod";
import { useMutation, useQueryClient } from "@tanstack/react-query";
import { toast } from "sonner";
import { Loader2, ShieldCheck } from "lucide-react";
import { Button } from "@/components/ui/button";
import { Input } from "@/components/ui/input";
import { Label } from "@/components/ui/label";
import { Checkbox } from "@/components/ui/checkbox";
import { Card, CardContent } from "@/components/ui/card";
import { MfaChallengeForm } from "@/components/shared/mfa-challenge-form";
import { authService } from "@/services";
import type { AuthSession } from "@/services/auth.service";

const loginSchema = z.object({
  email: z.string().email("Enter a valid email"),
  password: z.string().min(1, "Enter your password"),
});

type LoginFormValues = z.infer<typeof loginSchema>;

/** Admin accounts are never self-registered — Cognito group membership is granted out-of-band, so there's no /admin/register. */
export default function AdminLoginPage() {
  return (
    <Suspense>
      <AdminLoginForm />
    </Suspense>
  );
}

function AdminLoginForm() {
  const router = useRouter();
  const searchParams = useSearchParams();
  const redirectTo = searchParams.get("redirectTo") || "/admin";
  const queryClient = useQueryClient();
  const [showPassword, setShowPassword] = useState(false);
  const [pendingMfa, setPendingMfa] = useState<{ email: string; session: string } | null>(null);

  const {
    register,
    handleSubmit,
    formState: { errors },
  } = useForm<LoginFormValues>({ resolver: zodResolver(loginSchema) });

  const completeSession = (session: AuthSession) => {
    // See the equivalent check in account/login/page.tsx — authService.login()
    // is role-agnostic, so a non-admin account would otherwise authenticate
    // here and then get silently bounced back by proxy.ts's /admin gate.
    if (session.role !== "admin") {
      void authService.logout();
      toast.error("This account isn't registered as an admin.");
      return;
    }
    queryClient.clear();
    router.push(redirectTo);
    router.refresh();
  };

  const mutation = useMutation({
    mutationFn: (values: LoginFormValues) => authService.login(values.email, values.password),
    onSuccess: (session, variables) => {
      if (session.mfaRequired && session.mfaSession) {
        setPendingMfa({ email: variables.email, session: session.mfaSession });
        return;
      }
      completeSession(session);
    },
    onError: (error: Error) => toast.error(error.message || "Invalid email or password"),
  });

  if (pendingMfa) {
    return (
      <div className="mx-auto max-w-sm px-4 py-16 sm:px-6">
        <Card>
          <CardContent>
            <MfaChallengeForm email={pendingMfa.email} session={pendingMfa.session} onVerified={completeSession} />
          </CardContent>
        </Card>
      </div>
    );
  }

  return (
    <div className="mx-auto max-w-sm px-4 py-16 sm:px-6">
      <div className="mb-6 space-y-2 text-center">
        <span className="bg-primary/10 text-primary mx-auto flex size-12 items-center justify-center rounded-full">
          <ShieldCheck className="size-6" />
        </span>
        <h1 className="text-2xl font-bold">Admin sign in</h1>
        <p className="text-muted-foreground text-sm">Platform administration — restricted access.</p>
      </div>

      <Card>
        <CardContent>
          <form onSubmit={handleSubmit((values) => mutation.mutate(values))} className="space-y-4">
            <div className="space-y-1.5">
              <Label htmlFor="email">Email</Label>
              <Input id="email" type="email" placeholder="you@storepilot.lk" {...register("email")} />
              {errors.email ? <p className="text-destructive text-xs">{errors.email.message}</p> : null}
            </div>
            <div className="space-y-1.5">
              <Label htmlFor="password">Password</Label>
              <Input id="password" type={showPassword ? "text" : "password"} {...register("password")} />
              {errors.password ? (
                <p className="text-destructive text-xs">{errors.password.message}</p>
              ) : null}
            </div>
            <label className="flex items-center gap-2 text-sm">
              <Checkbox checked={showPassword} onCheckedChange={(checked) => setShowPassword(checked === true)} />
              <span className="text-muted-foreground">Show password</span>
            </label>
            <Button type="submit" size="lg" className="w-full" disabled={mutation.isPending}>
              {mutation.isPending ? <Loader2 className="size-4 animate-spin" /> : null}
              Sign in
            </Button>
          </form>
        </CardContent>
      </Card>
    </div>
  );
}
