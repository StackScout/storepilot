"use client";

import { useState } from "react";
import Image from "next/image";
import Link from "next/link";
import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import { toast } from "sonner";
import { Package, Pencil, Plus, Trash2 } from "lucide-react";
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
import { useSellerStoreId } from "@/hooks/use-seller-store";
import { usePlatformConfig } from "@/hooks/use-platform-config";
import { productsService } from "@/services";
import type { Product } from "@/types";

export default function DashboardProductsPage() {
  const queryClient = useQueryClient();
  const storeId = useSellerStoreId();
  const [productToDelete, setProductToDelete] = useState<Product | null>(null);
  const { currencyCode, currencySymbol, currencyLocale } = usePlatformConfig();
  const currency = { code: currencyCode, symbol: currencySymbol, locale: currencyLocale };

  const { data: products, isLoading } = useQuery({
    queryKey: ["products", "store", storeId],
    queryFn: () => productsService.listProductsByStore(storeId),
  });

  const deleteMutation = useMutation({
    mutationFn: (id: string) => productsService.deleteProduct(id),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ["products"] });
      toast.success("Product deleted");
      setProductToDelete(null);
    },
    onError: () => toast.error("Couldn't delete product"),
  });

  return (
    <div className="max-w-6xl space-y-6">
      <div className="flex items-center justify-between">
        <div>
          <h1 className="text-2xl font-bold">Products</h1>
          <p className="text-muted-foreground text-sm">Manage your catalog and stock levels.</p>
        </div>
        <Button render={<Link href="/dashboard/products/new" />}>
          <Plus className="size-4" /> New product
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
          ) : !products || products.length === 0 ? (
            <EmptyState
              icon={Package}
              title="No products yet"
              description="Add your first product to start selling."
              action={
                <Button render={<Link href="/dashboard/products/new" />} size="sm">
                  Add product
                </Button>
              }
            />
          ) : (
            <div className="-mx-6 overflow-x-auto">
              <table className="w-full text-sm">
                <thead>
                  <tr className="text-muted-foreground border-y text-left text-xs">
                    <th className="px-6 py-2 font-medium">Product</th>
                    <th className="px-6 py-2 font-medium">Price</th>
                    <th className="px-6 py-2 font-medium">Stock</th>
                    <th className="px-6 py-2 font-medium">Status</th>
                    <th className="px-6 py-2 font-medium">Actions</th>
                  </tr>
                </thead>
                <tbody>
                  {products.map((product) => (
                    <tr key={product.id} className="border-b last:border-0">
                      <td className="px-6 py-3">
                        <div className="flex items-center gap-3">
                          <div className="bg-muted relative size-10 shrink-0 overflow-hidden rounded-md">
                            <Image
                              src={product.images[0]?.url}
                              alt={product.name}
                              fill
                              sizes="40px"
                              className="object-cover"
                            />
                          </div>
                          <span className="line-clamp-1 font-medium">{product.name}</span>
                        </div>
                      </td>
                      <td className="px-6 py-3">
                        <PriceDisplay price={product.price} size="sm" />
                      </td>
                      <td className="px-6 py-3">
                        {product.trackStock ? product.stockQuantity : "—"}
                      </td>
                      <td className="px-6 py-3">
                        <StatusBadge
                          tone={
                            product.status === "active"
                              ? "success"
                              : product.status === "out-of-stock"
                                ? "danger"
                                : "neutral"
                          }
                        >
                          {product.status === "out-of-stock" ? "Out of stock" : product.status}
                        </StatusBadge>
                      </td>
                      <td className="px-6 py-3">
                        <div className="flex items-center gap-1">
                          <CopyLinkButton
                            path={`/stores/${product.storeSlug}/products/${product.slug}`}
                            iconOnly
                          />
                          <Button
                            render={<Link href={`/dashboard/products/${product.id}/edit`} />}
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
                            onClick={() => setProductToDelete(product)}
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

      <Dialog open={!!productToDelete} onOpenChange={(open) => !open && setProductToDelete(null)}>
        <DialogContent>
          <DialogHeader>
            <DialogTitle>Delete product?</DialogTitle>
            <DialogDescription>
              This will permanently remove &quot;{productToDelete?.name}&quot; ({formatCurrency(productToDelete?.price ?? 0, currency)}) from your store. This can&apos;t be undone.
            </DialogDescription>
          </DialogHeader>
          <DialogFooter>
            <Button variant="outline" onClick={() => setProductToDelete(null)}>
              Cancel
            </Button>
            <Button
              variant="destructive"
              disabled={deleteMutation.isPending}
              onClick={() => productToDelete && deleteMutation.mutate(productToDelete.id)}
            >
              Delete
            </Button>
          </DialogFooter>
        </DialogContent>
      </Dialog>
    </div>
  );
}
