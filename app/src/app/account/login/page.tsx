"use client";

import { Suspense, useEffect, useState } from "react";
import Link from "next/link";
import { useRouter, useSearchParams } from "next/navigation";
import { useForm } from "react-hook-form";
import { zodResolver } from "@hookform/resolvers/zod";
import { z } from "zod";
import { useMutation, useQueryClient } from "@tanstack/react-query";
import { toast } from "sonner";
import { Loader2, User } from "lucide-react";
import { Button } from "@/components/ui/button";
import { Input } from "@/components/ui/input";
import { Label } from "@/components/ui/label";
import { Checkbox } from "@/components/ui/checkbox";
import { Card, CardContent } from "@/components/ui/card";
import { GoogleSignInButton } from "@/components/shared/google-sign-in-button";
import { EmailVerificationForm } from "@/components/shared/email-verification-form";
import { authService } from "@/services";
import { ApiRequestError } from "@/lib/api-client";
import type { AuthSession } from "@/services/auth.service";

const loginSchema = z.object({
  email: z.string().email("Enter a valid email"),
  password: z.string().min(1, "Enter your password"),
});

type LoginFormValues = z.infer<typeof loginSchema>;

export default function BuyerLoginPage() {
  return (
    <Suspense>
      <BuyerLoginForm />
    </Suspense>
  );
}

function BuyerLoginForm() {
  const router = useRouter();
  const searchParams = useSearchParams();
  const redirectTo = searchParams.get("redirectTo") || "/account";
  const queryClient = useQueryClient();
  const [showPassword, setShowPassword] = useState(false);
  const [pendingVerification, setPendingVerification] = useState<{ email: string; password: string } | null>(null);

  // AuthController.googleCallback redirects here with this on failure
  // (e.g. the Google popup was cancelled, or the code exchange failed).
  useEffect(() => {
    if (searchParams.get("error") === "google_auth_failed") {
      toast.error("Google sign-in didn't work. Please try again.");
    }
  }, [searchParams]);

  const {
    register,
    handleSubmit,
    formState: { errors },
  } = useForm<LoginFormValues>({ resolver: zodResolver(loginSchema) });

  const handleSession = (session: AuthSession) => {
    // authService.login() authenticates against Cognito regardless of
    // which login page called it — it has no notion of "buyer login" vs
    // "seller login". Without this check, a seller-only account would
    // authenticate successfully here, then get silently bounced back to
    // this page by proxy.ts's role gate on /account/**, with no
    // indication of what happened.
    if (session.role !== "buyer") {
      void authService.logout();
      toast.error("This account isn't registered as a buyer. Try the seller or admin sign-in instead.");
      return;
    }
    queryClient.clear();
    router.push(redirectTo);
    router.refresh();
  };

  const mutation = useMutation({
    mutationFn: (values: LoginFormValues) => authService.login(values.email, values.password),
    onSuccess: handleSession,
    onError: (error: Error, variables) => {
      if (error instanceof ApiRequestError && error.code === "EMAIL_NOT_VERIFIED") {
        setPendingVerification({ email: variables.email, password: variables.password });
      }
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
          <User className="size-6" />
        </span>
        <h1 className="text-2xl font-bold">Sign in</h1>
        <p className="text-muted-foreground text-sm">
          Sign in to see your order history and saved address.
        </p>
      </div>

      <Card>
        <CardContent>
          <form onSubmit={handleSubmit((values) => mutation.mutate(values))} className="space-y-4">
            <div className="space-y-1.5">
              <Label htmlFor="email">Email</Label>
              <Input id="email" type="email" placeholder="you@example.com" {...register("email")} />
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
            {mutation.isError ? (
              <p className="text-destructive text-xs">
                {mutation.error instanceof Error ? mutation.error.message : "Something went wrong."}
              </p>
            ) : null}
            <Button type="submit" size="lg" className="w-full" disabled={mutation.isPending}>
              {mutation.isPending ? <Loader2 className="size-4 animate-spin" /> : null}
              Sign in
            </Button>
          </form>

          <div className="my-4 flex items-center gap-3">
            <div className="bg-border h-px flex-1" />
            <span className="text-muted-foreground text-xs">or</span>
            <div className="bg-border h-px flex-1" />
          </div>

          <GoogleSignInButton />
        </CardContent>
      </Card>

      <p className="text-muted-foreground mt-6 text-center text-sm">
        New here?{" "}
        <Link
          href={`/account/register${redirectTo !== "/account" ? `?redirectTo=${encodeURIComponent(redirectTo)}` : ""}`}
          className="text-primary font-medium underline-offset-4 hover:underline"
        >
          Create an account
        </Link>{" "}
        or{" "}
        <Link href="/search" className="text-primary font-medium underline-offset-4 hover:underline">
          continue as a guest
        </Link>
        .
      </p>
    </div>
  );
}
