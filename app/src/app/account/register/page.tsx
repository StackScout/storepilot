"use client";

import { Suspense } from "react";
import Link from "next/link";
import { useRouter, useSearchParams } from "next/navigation";
import { useForm } from "react-hook-form";
import { zodResolver } from "@hookform/resolvers/zod";
import { z } from "zod";
import { useMutation, useQueryClient } from "@tanstack/react-query";
import { toast } from "sonner";
import { Loader2, UserPlus } from "lucide-react";
import { Button } from "@/components/ui/button";
import { Input } from "@/components/ui/input";
import { Label } from "@/components/ui/label";
import { Card, CardContent } from "@/components/ui/card";
import { GoogleSignInButton } from "@/components/shared/google-sign-in-button";
import { authService } from "@/services";

const registerSchema = z.object({
  name: z.string().min(2, "Enter your full name"),
  email: z.string().email("Enter a valid email"),
  password: z.string().min(8, "Password must be at least 8 characters"),
});

type RegisterFormValues = z.infer<typeof registerSchema>;

export default function BuyerRegisterPage() {
  return (
    <Suspense>
      <BuyerRegisterForm />
    </Suspense>
  );
}

function BuyerRegisterForm() {
  const router = useRouter();
  const searchParams = useSearchParams();
  const redirectTo = searchParams.get("redirectTo") || "/account";
  const queryClient = useQueryClient();

  const {
    register,
    handleSubmit,
    formState: { errors },
  } = useForm<RegisterFormValues>({ resolver: zodResolver(registerSchema) });

  const mutation = useMutation({
    mutationFn: (values: RegisterFormValues) =>
      authService.register(values.name, values.email, values.password, "buyer"),
    onSuccess: () => {
      toast.success("Account created!");
      queryClient.clear();
      router.push(redirectTo);
      router.refresh();
    },
    onError: (error: Error) => toast.error(error.message || "Something went wrong. Please try again."),
  });

  return (
    <div className="mx-auto max-w-sm px-4 py-16 sm:px-6">
      <div className="mb-6 space-y-2 text-center">
        <span className="bg-primary/10 text-primary mx-auto flex size-12 items-center justify-center rounded-full">
          <UserPlus className="size-6" />
        </span>
        <h1 className="text-2xl font-bold">Create your account</h1>
        <p className="text-muted-foreground text-sm">
          Save your address and see your order history across visits.
        </p>
      </div>

      <Card>
        <CardContent>
          <form
            onSubmit={handleSubmit((values) => mutation.mutate(values))}
            className="space-y-4"
          >
            <div className="space-y-1.5">
              <Label htmlFor="name">Full name</Label>
              <Input id="name" placeholder="e.g. Tharindu Silva" {...register("name")} />
              {errors.name ? <p className="text-destructive text-xs">{errors.name.message}</p> : null}
            </div>
            <div className="space-y-1.5">
              <Label htmlFor="email">Email</Label>
              <Input id="email" type="email" placeholder="you@example.com" {...register("email")} />
              {errors.email ? <p className="text-destructive text-xs">{errors.email.message}</p> : null}
            </div>
            <div className="space-y-1.5">
              <Label htmlFor="password">Password</Label>
              <Input id="password" type="password" {...register("password")} />
              {errors.password ? (
                <p className="text-destructive text-xs">{errors.password.message}</p>
              ) : null}
            </div>
            <Button type="submit" size="lg" className="w-full" disabled={mutation.isPending}>
              {mutation.isPending ? <Loader2 className="size-4 animate-spin" /> : null}
              Create account
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
        Already have an account?{" "}
        <Link
          href={`/account/login${redirectTo !== "/account" ? `?redirectTo=${encodeURIComponent(redirectTo)}` : ""}`}
          className="text-primary font-medium underline-offset-4 hover:underline"
        >
          Sign in
        </Link>
      </p>
    </div>
  );
}
