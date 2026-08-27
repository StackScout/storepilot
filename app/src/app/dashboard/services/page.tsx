"use client";

import { useState } from "react";
import Image from "next/image";
import Link from "next/link";
import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import { toast } from "sonner";
import { CalendarClock, Pencil, Plus, Trash2 } from "lucide-react";
import { Button } from "@/components/ui/button";
import { Card, CardContent } from "@/components/ui/card";
import { StatusBadge } from "@/components/shared/status-badge";
import {
  Dialog,
  DialogContent,
  DialogDescription,
  DialogFooter,
  DialogHeader,
  DialogTitle,
} from "@/components/ui/dialog";
import { CopyLinkButton } from "@/components/shared/copy-link-button";
import { EmptyState } from "@/components/shared/empty-state";
import { TableRowSkeleton } from "@/components/shared/loading-skeletons";
import { PriceDisplay } from "@/components/shared/price-display";
import { formatCurrency } from "@/lib/currency";
import { queryKeys } from "@/lib/query-keys";
import { useSellerStoreId } from "@/hooks/use-seller-store";
import { usePlatformConfig } from "@/hooks/use-platform-config";
import { bookableServicesService } from "@/services";
import type { BookableService } from "@/types";

export default function DashboardServicesPage() {
  const queryClient = useQueryClient();
  const storeId = useSellerStoreId();
  const [serviceToDelete, setServiceToDelete] = useState<BookableService | null>(null);
  const { currencyCode, currencySymbol, currencyLocale } = usePlatformConfig();
  const currency = { code: currencyCode, symbol: currencySymbol, locale: currencyLocale };

  const { data: services, isLoading } = useQuery({
    queryKey: queryKeys.bookableServices.byStore(storeId),
    queryFn: async () => (await bookableServicesService.listServicesByStore(storeId)).content,
  });

  const deleteMutation = useMutation({
    mutationFn: (id: string) => bookableServicesService.deleteService(id),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: queryKeys.bookableServices.byStore(storeId) });
      toast.success("Service deleted");
      setServiceToDelete(null);
    },
    onError: () =>
      toast.error("Couldn't delete service — cancel or complete any upcoming bookings for it first."),
  });

  return (
    <div className="max-w-6xl space-y-6">
      <div className="flex items-center justify-between">
        <div>
          <h1 className="text-2xl font-bold">Services</h1>
          <p className="text-muted-foreground text-sm">Manage the appointments buyers can book.</p>
        </div>
        <Button render={<Link href="/dashboard/services/new" />}>
          <Plus className="size-4" /> New service
        </Button>
      </div>

      <Card>
        <CardContent>
          {isLoading ? (
            <div className="divide-y">
              <TableRowSkeleton columns={5} />
              <TableRowSkeleton columns={5} />
              <TableRowSkeleton columns={5} />
            </div>
          ) : !services || services.length === 0 ? (
            <EmptyState
              icon={CalendarClock}
              title="No services yet"
              description="Add your first bookable service so buyers can start booking appointments."
              action={
                <Button render={<Link href="/dashboard/services/new" />} size="sm">
                  Add service
                </Button>
              }
            />
          ) : (
            <div className="-mx-6 overflow-x-auto">
              <table className="w-full text-sm">
                <thead>
                  <tr className="text-muted-foreground border-y text-left text-xs">
                    <th className="px-6 py-2 font-medium">Service</th>
                    <th className="px-6 py-2 font-medium">Price</th>
                    <th className="px-6 py-2 font-medium">Duration</th>
                    <th className="px-6 py-2 font-medium">Status</th>
                    <th className="px-6 py-2 font-medium">Actions</th>
                  </tr>
                </thead>
                <tbody>
                  {services.map((service) => (
                    <tr key={service.id} className="border-b last:border-0">
                      <td className="px-6 py-3">
                        <div className="flex items-center gap-3">
                          <div className="bg-muted relative size-10 shrink-0 overflow-hidden rounded-md">
                            <Image
                              src={service.images[0]?.url}
                              alt={service.name}
                              fill
                              sizes="40px"
                              className="object-cover"
                            />
                          </div>
                          <span className="line-clamp-1 font-medium">{service.name}</span>
                        </div>
                      </td>
                      <td className="px-6 py-3">
                        <PriceDisplay price={service.price} size="sm" />
                      </td>
                      <td className="px-6 py-3">{service.durationMinutes} min</td>
                      <td className="px-6 py-3">
                        <StatusBadge tone={service.status === "active" ? "success" : "neutral"}>
                          {service.status}
                        </StatusBadge>
                      </td>
                      <td className="px-6 py-3">
                        <div className="flex items-center gap-1">
                          <CopyLinkButton
                            path={`/stores/${service.storeSlug}/services/${service.slug}`}
                            iconOnly
                          />
                          <Button
                            render={<Link href={`/dashboard/services/${service.id}/edit`} />}
                            variant="ghost"
                            size="icon"
                            className="size-8"
                          >
                            <Pencil className="size-3.5" />
                          </Button>
                          <Button
                            variant="ghost"
                            size="icon"
                            className="text-destructive size-8"
                            onClick={() => setServiceToDelete(service)}
                          >
                            <Trash2 className="size-3.5" />
                          </Button>
                        </div>
                      </td>
                    </tr>
                  ))}
                </tbody>
              </table>
            </div>
          )}
        </CardContent>
      </Card>

      <Dialog open={!!serviceToDelete} onOpenChange={(open) => !open && setServiceToDelete(null)}>
        <DialogContent>
          <DialogHeader>
            <DialogTitle>Delete service?</DialogTitle>
            <DialogDescription>
              This will permanently remove &quot;{serviceToDelete?.name}&quot; (
              {formatCurrency(serviceToDelete?.price ?? 0, currency)}) from your store. This can&apos;t be
              undone.
            </DialogDescription>
          </DialogHeader>
          <DialogFooter>
            <Button variant="outline" onClick={() => setServiceToDelete(null)}>
              Cancel
            </Button>
            <Button
              variant="destructive"
              disabled={deleteMutation.isPending}
              onClick={() => serviceToDelete && deleteMutation.mutate(serviceToDelete.id)}
            >
              Delete
            </Button>
          </DialogFooter>
        </DialogContent>
      </Dialog>
    </div>
  );
}
