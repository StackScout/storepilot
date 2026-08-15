"use client";

import Link from "next/link";
import { useQuery } from "@tanstack/react-query";
import { Ban, CalendarClock, DollarSign, Repeat, Sparkles, UserX } from "lucide-react";
import { Button } from "@/components/ui/button";
import { Card, CardContent } from "@/components/ui/card";
import { StatCard } from "@/components/dashboard/stat-card";
import { EmptyState } from "@/components/shared/empty-state";
import { PriceDisplay } from "@/components/shared/price-display";
import { useSellerStoreId } from "@/hooks/use-seller-store";
import { billingService, bookingsService } from "@/services";

export default function DashboardAnalyticsPage() {
  const storeId = useSellerStoreId();

  const { data: planInfo, isLoading: isPlanLoading } = useQuery({
    queryKey: ["seller-plan"],
    queryFn: () => billingService.getMyPlan(),
  });
  const isPro = planInfo?.plan === "pro";

  const { data: analytics, isLoading: isAnalyticsLoading } = useQuery({
    queryKey: ["booking-analytics", storeId],
    queryFn: () => bookingsService.getBookingAnalytics(storeId),
    enabled: isPro,
  });

  if (isPlanLoading) return null;

  if (!isPro) {
    return (
      <div className="max-w-2xl">
        <Card>
          <CardContent className="flex flex-col items-center gap-3 py-12 text-center">
            <span className="bg-primary/10 text-primary flex size-12 items-center justify-center rounded-full">
              <Sparkles className="size-6" />
            </span>
            <h1 className="text-xl font-bold">Booking analytics is a Pro feature</h1>
            <p className="text-muted-foreground max-w-sm text-sm">
              Upgrade to Pro to see revenue, no-show rate, top services, and repeat-customer trends for your bookings.
            </p>
            <Button render={<Link href="/dashboard/settings" />}>
              <Sparkles className="size-3.5" /> Upgrade to Pro
            </Button>
          </CardContent>
        </Card>
      </div>
    );
  }

  return (
    <div className="max-w-4xl space-y-6">
      <div>
        <h1 className="text-2xl font-bold">Booking analytics</h1>
        <p className="text-muted-foreground text-sm">Revenue and performance trends for your bookings.</p>
      </div>

      {isAnalyticsLoading || !analytics ? (
        <p className="text-muted-foreground text-sm">Loading…</p>
      ) : analytics.totalBookings === 0 ? (
        <Card>
          <CardContent>
            <EmptyState
              icon={CalendarClock}
              title="No bookings yet"
              description="Analytics will show up here once buyers start booking your services."
            />
          </CardContent>
        </Card>
      ) : (
        <>
          <div className="grid grid-cols-2 gap-4 sm:grid-cols-4">
            <StatCard label="Total bookings" value={String(analytics.totalBookings)} icon={CalendarClock} />
            <StatCard label="Revenue" value={`$${(analytics.totalRevenue / 100).toFixed(2)}`} icon={DollarSign} />
            <StatCard label="No-show rate" value={`${analytics.noShowRate}%`} icon={UserX} />
            <StatCard label="Repeat customers" value={`${analytics.repeatBuyerRate}%`} icon={Repeat} />
          </div>

          <Card>
            <CardContent className="space-y-4">
              <h2 className="font-semibold">Top services</h2>
              {analytics.topServices.length === 0 ? (
                <p className="text-muted-foreground text-sm">No completed, paid bookings yet.</p>
              ) : (
                <div className="divide-y">
                  {analytics.topServices.map((service) => (
                    <div key={service.serviceName} className="flex items-center justify-between gap-3 py-3 first:pt-0 last:pb-0">
                      <div>
                        <p className="text-sm font-medium">{service.serviceName}</p>
                        <p className="text-muted-foreground text-xs">{service.bookingCount} bookings</p>
                      </div>
                      <PriceDisplay price={service.revenue} size="sm" />
                    </div>
                  ))}
                </div>
              )}
            </CardContent>
          </Card>

          <Card>
            <CardContent className="flex items-center gap-3">
              <span className="bg-danger/10 text-danger-foreground flex size-9 shrink-0 items-center justify-center rounded-lg">
                <Ban className="size-4.5" />
              </span>
              <p className="text-sm">
                <span className="font-medium">{analytics.cancelledBookings} cancelled</span>
                <span className="text-muted-foreground"> and </span>
                <span className="font-medium">{analytics.noShowBookings} no-show</span>
                <span className="text-muted-foreground"> bookings out of {analytics.totalBookings} total.</span>
              </p>
            </CardContent>
          </Card>
        </>
      )}
    </div>
  );
}
