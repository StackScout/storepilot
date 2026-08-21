"use client";

import { useState } from "react";
import { useRouter } from "next/navigation";
import { Search, Loader2 } from "lucide-react";
import { Button } from "@/components/ui/button";
import { Input } from "@/components/ui/input";
import { Label } from "@/components/ui/label";
import { Card, CardContent } from "@/components/ui/card";
import { ordersService } from "@/services";
import { ApiRequestError } from "@/lib/api-client";
import { usePlatformConfig } from "@/hooks/use-platform-config";

export default function TrackOrderPage() {
  const router = useRouter();
  const { countryCode } = usePlatformConfig();
  const isSriLanka = countryCode === "LK";
  const [orderNumber, setOrderNumber] = useState("");
  const [phone, setPhone] = useState("");
  const [code, setCode] = useState("");
  const [step, setStep] = useState<"details" | "code">("details");
  const [isLoading, setIsLoading] = useState(false);
  const [error, setError] = useState<string | null>(null);

  async function handleRequestCode(e: React.FormEvent) {
    e.preventDefault();
    setError(null);
    setIsLoading(true);
    try {
      await ordersService.requestOrderLookupCode(orderNumber, phone);
      setStep("code");
    } catch {
      setError("Something went wrong. Please try again.");
    } finally {
      setIsLoading(false);
    }
  }

  async function handleVerifyCode(e: React.FormEvent) {
    e.preventDefault();
    setError(null);
    setIsLoading(true);
    try {
      const order = await ordersService.verifyOrderLookupCode(orderNumber, phone, code);
      router.push(`/orders/${order.id}`);
    } catch (err) {
      setError(
        err instanceof ApiRequestError && err.status === 400
          ? "That code is incorrect or has expired."
          : "We couldn't find an order with that number and phone. Double-check and try again.",
      );
    } finally {
      setIsLoading(false);
    }
  }

  return (
    <div className="mx-auto max-w-md px-4 py-16 sm:px-6">
      <div className="space-y-1 text-center">
        <h1 className="text-2xl font-bold">Track your order</h1>
        <p className="text-muted-foreground text-sm">
          {step === "details"
            ? "Enter your order number and the phone number used at checkout."
            : "Enter the 6-digit code we emailed to the order's contact email."}
        </p>
      </div>

      <Card className="mt-6">
        <CardContent>
          {step === "details" ? (
            <form onSubmit={handleRequestCode} className="space-y-4">
              <div className="space-y-1.5">
                <Label htmlFor="orderNumber">Order number</Label>
                <Input
                  id="orderNumber"
                  placeholder={`e.g. ${countryCode}-20260722-1001`}
                  value={orderNumber}
                  onChange={(e) => setOrderNumber(e.target.value)}
                  required
                />
              </div>
              <div className="space-y-1.5">
                <Label htmlFor="phone">Phone number</Label>
                <Input
                  id="phone"
                  placeholder={isSriLanka ? "07X XXX XXXX" : "04XX XXX XXX"}
                  value={phone}
                  onChange={(e) => setPhone(e.target.value)}
                  required
                />
              </div>
              {error ? <p className="text-destructive text-sm">{error}</p> : null}
              <Button type="submit" className="w-full" disabled={isLoading}>
                {isLoading ? <Loader2 className="size-4 animate-spin" /> : <Search className="size-4" />}
                Send code
              </Button>
            </form>
          ) : (
            <form onSubmit={handleVerifyCode} className="space-y-4">
              <div className="space-y-1.5">
                <Label htmlFor="code">Verification code</Label>
                <Input
                  id="code"
                  inputMode="numeric"
                  placeholder="6-digit code"
                  value={code}
                  onChange={(e) => setCode(e.target.value)}
                  required
                  autoFocus
                />
              </div>
              {error ? <p className="text-destructive text-sm">{error}</p> : null}
              <Button type="submit" className="w-full" disabled={isLoading}>
                {isLoading ? <Loader2 className="size-4 animate-spin" /> : <Search className="size-4" />}
                Track order
              </Button>
              <Button
                type="button"
                variant="ghost"
                className="w-full"
                disabled={isLoading}
                onClick={() => {
                  setStep("details");
                  setCode("");
                  setError(null);
                }}
              >
                Back
              </Button>
            </form>
          )}
        </CardContent>
      </Card>
    </div>
  );
}
