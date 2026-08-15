"use client";

import { use } from "react";
import { useRouter } from "next/navigation";
import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import { toast } from "sonner";
import { CalendarX, Loader2 } from "lucide-react";
import { ServiceForm } from "@/components/dashboard/service-form";
import { ServiceAvailabilityOverrideCard } from "@/components/dashboard/service-availability-override-card";
import { EmptyState } from "@/components/shared/empty-state";
import { bookableServicesService } from "@/services";
import type { BookableServiceFormInput } from "@/types";

export default function EditServicePage({ params }: { params: Promise<{ serviceId: string }> }) {
  const { serviceId } = use(params);
  const router = useRouter();
  const queryClient = useQueryClient();

  const { data: service, isLoading } = useQuery({
    queryKey: ["bookable-service", serviceId],
    queryFn: () => bookableServicesService.getServiceById(serviceId),
  });

  const mutation = useMutation({
    mutationFn: ({ input, images }: { input: BookableServiceFormInput; images: File[] }) =>
      bookableServicesService.updateService(serviceId, input, images),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ["bookable-services"] });
      queryClient.invalidateQueries({ queryKey: ["bookable-service", serviceId] });
      toast.success("Service updated");
      router.push("/dashboard/services");
    },
    onError: () => toast.error("Couldn't update service. Please try again."),
  });

  if (isLoading) {
    return (
      <div className="flex justify-center py-24">
        <Loader2 className="text-muted-foreground size-6 animate-spin" />
      </div>
    );
  }

  if (!service) {
    return <EmptyState icon={CalendarX} title="Service not found" />;
  }

  return (
    <div className="space-y-6">
      <div>
        <h1 className="text-2xl font-bold">Edit service</h1>
        <p className="text-muted-foreground text-sm">{service.name}</p>
      </div>
      <ServiceForm
        initialService={service}
        onSubmit={(input, images) => mutation.mutate({ input, images })}
        isSubmitting={mutation.isPending}
        submitLabel="Save changes"
        storeCategory={service.category}
      />
      <ServiceAvailabilityOverrideCard storeId={service.storeId} serviceId={serviceId} />
    </div>
  );
}
