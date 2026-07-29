"use client";

import { Suspense } from "react";
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
import { Card, CardContent } from "@/components/ui/card";
import { authService } from "@/services";

const registerSchema = z.object({
  name: z.string().min(2, "Enter your full name"),
  email: z.string().email("Enter a valid email"),
  password: z.string().min(8, "Password must be at least 8 characters"),
});

type RegisterFormValues = z.infer<typeof registerSchema>;

/**
 * Creates the Cognito account that seller onboarding then attaches a store
 * to. Unlike account/register (buyer), this grants no Cognito group at
 * all — becoming a seller is a separate, later step (see /onboarding) that
 * adds the "seller" group. Buyer and seller are mutually exclusive
 * identities: this account can never become a buyer, and an existing
 * buyer account is refused at /onboarding — see backend
 * AuthController.register()'s doc comment.
 */
export default function SellerRegisterPage() {
  return (
    <Suspense>
      <SellerRegisterForm />
    </Suspense>
  );
}

function SellerRegisterForm() {
  const router = useRouter();
  const searchParams = useSearchParams();
  const redirectTo = searchParams.get("redirectTo") || "/onboarding";
  const queryClient = useQueryClient();

  const {
    register,
    handleSubmit,
    formState: { errors },
  } = useForm<RegisterFormValues>({ resolver: zodResolver(registerSchema) });

  const mutation = useMutation({
    mutationFn: (values: RegisterFormValues) =>
      authService.register(values.name, values.email, values.password, "seller"),
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
          <Store className="size-6" />
        </span>
        <h1 className="text-2xl font-bold">Create your seller account</h1>
        <p className="text-muted-foreground text-sm">
          You&apos;ll set up your store details in the next step.
        </p>
      </div>

      <Card>
        <CardContent>
          <form onSubmit={handleSubmit((values) => mutation.mutate(values))} className="space-y-4">
            <div className="space-y-1.5">
              <Label htmlFor="name">Full name</Label>
              <Input id="name" placeholder="e.g. Kasun Perera" {...register("name")} />
              {errors.name ? <p className="text-destructive text-xs">{errors.name.message}</p> : null}
            </div>
            <div className="space-y-1.5">
              <Label htmlFor="email">Email</Label>
              <Input id="email" type="email" placeholder="you@yourstore.lk" {...register("email")} />
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
        </CardContent>
      </Card>

      <p className="text-muted-foreground mt-6 text-center text-sm">
        Already have an account?{" "}
        <Link href="/login" className="text-primary font-medium underline-offset-4 hover:underline">
          Sign in
        </Link>
      </p>
    </div>
  );
}
