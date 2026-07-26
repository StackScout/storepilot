"use client";

import { Suspense } from "react";
import Link from "next/link";
import { useRouter, useSearchParams } from "next/navigation";
import { useState } from "react";
import { useMutation } from "@tanstack/react-query";
import { Loader2, User } from "lucide-react";
import { Button } from "@/components/ui/button";
import { Input } from "@/components/ui/input";
import { Label } from "@/components/ui/label";
import { Card, CardContent } from "@/components/ui/card";
import { createBuyerSession } from "@/lib/actions/auth";
import { buyersService } from "@/services";

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
  const [email, setEmail] = useState("");

  const mutation = useMutation({
    mutationFn: async () => {
      const buyer = await buyersService.getBuyerByEmail(email);
      if (!buyer) {
        throw new Error("No account found with that email.");
      }
      await createBuyerSession(buyer.id, buyer.name, buyer.email);
    },
    onSuccess: () => router.push(redirectTo),
  });

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
          <form
            onSubmit={(e) => {
              e.preventDefault();
              mutation.mutate();
            }}
            className="space-y-4"
          >
            <div className="space-y-1.5">
              <Label htmlFor="email">Email</Label>
              <Input
                id="email"
                type="email"
                placeholder="you@example.com"
                value={email}
                onChange={(e) => setEmail(e.target.value)}
                required
              />
            </div>
            {mutation.isError ? (
              <p className="text-destructive text-xs">
                {mutation.error instanceof Error ? mutation.error.message : "Something went wrong."}{" "}
                <Link
                  href={`/account/register${redirectTo !== "/account" ? `?redirectTo=${encodeURIComponent(redirectTo)}` : ""}`}
                  className="underline"
                >
                  Create an account instead?
                </Link>
              </p>
            ) : null}
            <p className="text-muted-foreground text-xs">
              This is a demo sign-in — no password required, just your account&apos;s email.
            </p>
            <Button type="submit" size="lg" className="w-full" disabled={mutation.isPending}>
              {mutation.isPending ? <Loader2 className="size-4 animate-spin" /> : null}
              Continue
            </Button>
          </form>
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
