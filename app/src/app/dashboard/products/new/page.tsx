"use client";

import { useRouter } from "next/navigation";
import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import { toast } from "sonner";
import { Loader2 } from "lucide-react";
import { ProductForm } from "@/components/dashboard/product-form";
import { useSellerStoreId } from "@/hooks/use-seller-store";
import { productsService, storesService } from "@/services";
import type { ProductFormInput } from "@/types";

export default function NewProductPage() {
  const router = useRouter();
  const queryClient = useQueryClient();
  const storeId = useSellerStoreId();

  const { data: store, isLoading } = useQuery({
    queryKey: ["store", storeId],
    queryFn: () => storesService.getStoreById(storeId),
    staleTime: 0,
  });

  const mutation = useMutation({
    mutationFn: (input: ProductFormInput) => {
      if (!store) throw new Error("Store not loaded yet");
      return productsService.createProduct(store.id, store.name, store.slug, input);
    },
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ["products"] });
      toast.success("Product created");
      router.push("/dashboard/products");
    },
    onError: () => toast.error("Couldn't create product. Please try again."),
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
        <h1 className="text-2xl font-bold">New product</h1>
        <p className="text-muted-foreground text-sm">Add a new product to {store.name}.</p>
      </div>
      <ProductForm
        onSubmit={(input) => mutation.mutate(input)}
        isSubmitting={mutation.isPending}
        submitLabel="Create product"
      />
    </div>
  );
}
