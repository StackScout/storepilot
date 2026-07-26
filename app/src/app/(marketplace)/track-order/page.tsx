"use client";

import { useState } from "react";
import { useRouter } from "next/navigation";
import { Search, Loader2 } from "lucide-react";
import { Button } from "@/components/ui/button";
import { Input } from "@/components/ui/input";
import { Label } from "@/components/ui/label";
import { Card, CardContent } from "@/components/ui/card";
import { ordersService } from "@/services";

export default function TrackOrderPage() {
  const router = useRouter();
  const [orderNumber, setOrderNumber] = useState("");
  const [phone, setPhone] = useState("");
  const [isLoading, setIsLoading] = useState(false);
  const [error, setError] = useState<string | null>(null);

  async function handleSubmit(e: React.FormEvent) {
    e.preventDefault();
    setError(null);
    setIsLoading(true);
    const order = await ordersService.findOrderByNumberAndPhone(orderNumber, phone);
    setIsLoading(false);
    if (!order) {
      setError("We couldn't find an order with that number and phone. Double-check and try again.");
      return;
    }
    router.push(`/orders/${order.id}`);
  }

  return (
    <div className="mx-auto max-w-md px-4 py-16 sm:px-6">
      <div className="space-y-1 text-center">
        <h1 className="text-2xl font-bold">Track your order</h1>
        <p className="text-muted-foreground text-sm">
          Enter your order number and the phone number used at checkout.
        </p>
      </div>

      <Card className="mt-6">
        <CardContent>
          <form onSubmit={handleSubmit} className="space-y-4">
            <div className="space-y-1.5">
              <Label htmlFor="orderNumber">Order number</Label>
              <Input
                id="orderNumber"
                placeholder="e.g. SL-20260722-1001"
                value={orderNumber}
                onChange={(e) => setOrderNumber(e.target.value)}
                required
              />
            </div>
            <div className="space-y-1.5">
              <Label htmlFor="phone">Phone number</Label>
              <Input
                id="phone"
                placeholder="07X XXX XXXX"
                value={phone}
                onChange={(e) => setPhone(e.target.value)}
                required
              />
            </div>
            {error ? <p className="text-destructive text-sm">{error}</p> : null}
            <Button type="submit" className="w-full" disabled={isLoading}>
              {isLoading ? <Loader2 className="size-4 animate-spin" /> : <Search className="size-4" />}
              Track order
            </Button>
          </form>
        </CardContent>
      </Card>
    </div>
  );
}
