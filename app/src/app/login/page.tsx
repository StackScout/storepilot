import Link from "next/link";
import { Store } from "lucide-react";
import { Button } from "@/components/ui/button";
import { Input } from "@/components/ui/input";
import { Label } from "@/components/ui/label";
import { Card, CardContent } from "@/components/ui/card";
import { signInAsSeller } from "@/lib/actions/auth";

interface LoginPageProps {
  searchParams: Promise<{ redirectTo?: string; error?: string }>;
}

export default async function LoginPage({ searchParams }: LoginPageProps) {
  const { redirectTo, error } = await searchParams;

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
          <form action={signInAsSeller} className="space-y-4">
            <input type="hidden" name="redirectTo" value={redirectTo ?? "/dashboard"} />
            <div className="space-y-1.5">
              <Label htmlFor="email">Email</Label>
              <Input id="email" name="email" type="email" placeholder="you@yourstore.lk" required />
            </div>
            {error === "missing-email" ? (
              <p className="text-destructive text-xs">Enter your email to continue.</p>
            ) : null}
            <p className="text-muted-foreground text-xs">
              This is a demo sign-in — any email signs you in as the sample seller account.
            </p>
            <Button type="submit" size="lg" className="w-full">
              Continue
            </Button>
          </form>
        </CardContent>
      </Card>

      <p className="text-muted-foreground mt-6 text-center text-sm">
        New seller?{" "}
        <Link href="/onboarding" className="text-primary font-medium underline-offset-4 hover:underline">
          Create your store
        </Link>
      </p>
    </div>
  );
}
