"use client";

import { useQuery } from "@tanstack/react-query";
import { CalendarX } from "lucide-react";
import { ServiceCard } from "@/components/marketplace/service-card";
import { EmptyState } from "@/components/shared/empty-state";
import { queryKeys } from "@/lib/query-keys";
import { bookableServicesService } from "@/services";
import type { BookableService } from "@/types";

/** Mirrors StoreProductGrid exactly — server-seeded initial data, reconciled client-side. */
export function StoreServiceGrid({
  storeId,
  initialServices,
}: {
  storeId: string;
  initialServices: BookableService[];
}) {
  const { data: services } = useQuery({
    queryKey: queryKeys.bookableServices.byStore(storeId),
    queryFn: async () => (await bookableServicesService.listServicesByStore(storeId)).content,
    initialData: initialServices,
    staleTime: 0,
  });

  if (services.length === 0) {
    return (
      <EmptyState icon={CalendarX} title="No bookable services yet" description="This store hasn't listed any services." />
    );
  }

  return (
    <div className="grid grid-cols-2 gap-4 sm:grid-cols-3 lg:grid-cols-4">
      {services.map((service) => (
        <ServiceCard key={service.id} service={service} />
      ))}
    </div>
  );
}
