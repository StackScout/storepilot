"use client";

import { Suspense, useState } from "react";
import Link from "next/link";
import { useRouter, useSearchParams } from "next/navigation";
import { useForm } from "react-hook-form";
import { zodResolver } from "@hookform/resolvers/zod";
import { z } from "zod";
import { useMutation, useQueryClient } from "@tanstack/react-query";
import { toast } from "sonner";
import { Loader2, Store } from "lucide-react";
import { Button } from "@/components/ui/button";
import { Input } from "@/components/ui/input";
import { Label } from "@/components/ui/label";
import { Checkbox } from "@/components/ui/checkbox";
import { Card, CardContent } from "@/components/ui/card";
import { EmailVerificationForm } from "@/components/shared/email-verification-form";
import { authService } from "@/services";
import { ApiRequestError } from "@/lib/api-client";
import type { AuthSession } from "@/services/auth.service";

const loginSchema = z.object({
  email: z.string().email("Enter a valid email"),
  password: z.string().min(1, "Enter your password"),
});

type LoginFormValues = z.infer<typeof loginSchema>;

export default function LoginPage() {
  return (
    <Suspense>
      <SellerLoginForm />
    </Suspense>
  );
}

function SellerLoginForm() {
  const router = useRouter();
  const searchParams = useSearchParams();
  const redirectTo = searchParams.get("redirectTo") || "/dashboard";
  const queryClient = useQueryClient();
  const [showPassword, setShowPassword] = useState(false);
  const [pendingVerification, setPendingVerification] = useState<{ email: string; password: string } | null>(null);

  const {
    register,
    handleSubmit,
    formState: { errors },
  } = useForm<LoginFormValues>({ resolver: zodResolver(loginSchema) });

  const handleSession = (session: AuthSession) => {
    // See the equivalent check in account/login/page.tsx — authService.login()
    // is role-agnostic, so a non-seller account would otherwise authenticate
    // here and then get silently bounced back by proxy.ts's /dashboard gate.
    // A groupless role (register() no longer grants "buyer" to a seller
    // registration — see backend AuthController's doc comment) means they
    // registered but never finished onboarding — resume it instead of
    // rejecting. "buyer" is also accepted here, but only as backward
    // compatibility for accounts created before this fix that still hold
    // both groups; new registrations can never reach this state.
    if (session.role && session.role !== "seller" && session.role !== "buyer") {
      void authService.logout();
      toast.error("This account isn't registered as a seller. Try the buyer or admin sign-in instead.");
      return;
    }
    // React Query's own cache (auth-session, buyer/store lookups, ...) is
    // separate from Next's router cache — router.refresh() alone won't
    // clear a "signed out" result some component cached before login.
    queryClient.clear();
    if (session.role !== "seller") {
      toast.message("Let's finish setting up your store.");
      router.push("/onboarding");
    } else {
      router.push(redirectTo);
    }
    router.refresh();
  };

  const mutation = useMutation({
    mutationFn: (values: LoginFormValues) => authService.login(values.email, values.password),
    onSuccess: handleSession,
    onError: (error: Error, variables) => {
      if (error instanceof ApiRequestError && error.code === "EMAIL_NOT_VERIFIED") {
        setPendingVerification({ email: variables.email, password: variables.password });
        return;
      }
      toast.error(error.message || "Invalid email or password");
    },
  });

  if (pendingVerification) {
    return (
      <div className="mx-auto max-w-sm px-4 py-16 sm:px-6">
        <Card>
          <CardContent>
            <EmailVerificationForm
              email={pendingVerification.email}
              password={pendingVerification.password}
              autoSend
              onVerified={handleSession}
            />
          </CardContent>
        </Card>
      </div>
    );
  }

  return (
    <div className="mx-auto max-w-sm px-4 py-16 sm:px-6">
      <div className="mb-6 space-y-2 text-center">
        <span className="bg-primary/10 text-primary mx-auto flex size-12 items-center justify-center rounded-full">
          <Store className="size-6" />
        </span>
        <h1 className="text-2xl font-bold">Seller sign in</h1>
        <p className="text-muted-foreground text-sm">
          Sign in to manage your store, products and orders.
        </p>
      </div>

      <Card>
        <CardContent>
          <form onSubmit={handleSubmit((values) => mutation.mutate(values))} className="space-y-4">
            <div className="space-y-1.5">
              <Label htmlFor="email">Email</Label>
              <Input id="email" type="email" placeholder="you@yourstore.lk" {...register("email")} />
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

      <p className="text-muted-foreground mt-6 text-center text-sm">
        New seller?{" "}
        <Link href="/register" className="text-primary font-medium underline-offset-4 hover:underline">
          Create your account
        </Link>
      </p>
    </div>
  );
}
