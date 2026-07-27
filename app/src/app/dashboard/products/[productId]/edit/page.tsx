"use client";

import { use } from "react";
import { useRouter } from "next/navigation";
import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import { toast } from "sonner";
import { Loader2, PackageX } from "lucide-react";
import { ProductForm } from "@/components/dashboard/product-form";
import { EmptyState } from "@/components/shared/empty-state";
import { productsService, storesService } from "@/services";
import type { ProductFormInput } from "@/types";

export default function EditProductPage({ params }: { params: Promise<{ productId: string }> }) {
  const { productId } = use(params);
  const router = useRouter();
  const queryClient = useQueryClient();

  const { data: product, isLoading } = useQuery({
    queryKey: ["product", productId],
    queryFn: () => productsService.getProductById(productId),
  });

  const { data: settings } = useQuery({
    queryKey: ["store-settings", product?.storeId],
    queryFn: () => storesService.getStoreSettings(product!.storeId),
    enabled: !!product,
  });

  const mutation = useMutation({
    mutationFn: ({ input, images }: { input: ProductFormInput; images: File[] }) =>
      productsService.updateProduct(productId, input, images),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ["products"] });
      queryClient.invalidateQueries({ queryKey: ["product", productId] });
      toast.success("Product updated");
      router.push("/dashboard/products");
    },
    onError: () => toast.error("Couldn't update product. Please try again."),
  });

  if (isLoading) {
    return (
      <div className="flex justify-center py-24">
        <Loader2 className="text-muted-foreground size-6 animate-spin" />
      </div>
    );
  }

  if (!product) {
    return <EmptyState icon={PackageX} title="Product not found" />;
  }

  return (
    <div className="space-y-6">
      <div>
        <h1 className="text-2xl font-bold">Edit product</h1>
        <p className="text-muted-foreground text-sm">{product.name}</p>
      </div>
      <ProductForm
        initialProduct={product}
        onSubmit={(input, images) => mutation.mutate({ input, images })}
        isSubmitting={mutation.isPending}
        submitLabel="Save changes"
        stockManagementEnabled={settings?.stockManagementEnabled ?? true}
      />
    </div>
  );
}
