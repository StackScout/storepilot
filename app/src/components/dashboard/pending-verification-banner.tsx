"use client";

import { useQuery } from "@tanstack/react-query";
import { Clock, TriangleAlert } from "lucide-react";
import { Card, CardContent } from "@/components/ui/card";
import { useSellerStoreId } from "@/hooks/use-seller-store";
import { storesService } from "@/services";

export function PendingVerificationBanner() {
  const storeId = useSellerStoreId();
  const { data: store } = useQuery({
    queryKey: ["store", storeId],
    queryFn: () => storesService.getStoreById(storeId),
    staleTime: 0,
  });

  if (!store || store.verificationStatus === "active") return null;

  if (store.verificationStatus === "rejected") {
    return (
      <Card className="border-danger bg-danger/60">
        <CardContent className="flex items-start gap-3">
          <TriangleAlert className="text-danger-foreground mt-0.5 size-4 shrink-0" />
          <div>
            <p className="text-sm font-medium">Your store application was not approved</p>
            <p className="text-muted-foreground text-sm">
              You can update your store settings and contact support to request another review.
            </p>
          </div>
        </CardContent>
      </Card>
    );
  }

  return (
    <Card className="border-warning bg-warning/60">
      <CardContent className="flex items-start gap-3">
        <Clock className="text-warning-foreground mt-0.5 size-4 shrink-0" />
        <div>
          <p className="text-sm font-medium">Your store is pending verification</p>
          <p className="text-muted-foreground text-sm">
            You can set up products and settings now, but your storefront won&apos;t be visible to
            buyers until our team reviews your application — typically within 1–3 business days.
          </p>
        </div>
      </CardContent>
    </Card>
  );
}
