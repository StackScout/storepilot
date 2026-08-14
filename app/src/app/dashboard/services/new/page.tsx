"use client";

import { useRouter } from "next/navigation";
import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import { toast } from "sonner";
import { Loader2 } from "lucide-react";
import { ServiceForm } from "@/components/dashboard/service-form";
import { useSellerStoreId } from "@/hooks/use-seller-store";
import { bookableServicesService, storesService } from "@/services";
import type { BookableServiceFormInput } from "@/types";

export default function NewServicePage() {
  const router = useRouter();
  const queryClient = useQueryClient();
  const storeId = useSellerStoreId();

  const { data: store, isLoading } = useQuery({
    queryKey: ["store", storeId],
    queryFn: () => storesService.getStoreById(storeId),
    staleTime: 0,
  });

  const mutation = useMutation({
    mutationFn: ({ input, images }: { input: BookableServiceFormInput; images: File[] }) => {
      if (!store) throw new Error("Store not loaded yet");
      return bookableServicesService.createService(store.id, input, images);
    },
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ["bookable-services"] });
      toast.success("Service created");
      router.push("/dashboard/services");
    },
    onError: () => toast.error("Couldn't create service. Please try again."),
  });

  if (isLoading || !store) {
    return (
      <div className="flex justify-center py-24">
        <Loader2 className="text-muted-foreground size-6 animate-spin" />
      </div>
    );
  }

  return (
    <div className="space-y-6">
      <div>
        <h1 className="text-2xl font-bold">New service</h1>
        <p className="text-muted-foreground text-sm">Add a new bookable service to {store.name}.</p>
      </div>
      <ServiceForm
        onSubmit={(input, images) => mutation.mutate({ input, images })}
        isSubmitting={mutation.isPending}
        submitLabel="Create service"
        storeCategory={store.category}
      />
    </div>
  );
}
