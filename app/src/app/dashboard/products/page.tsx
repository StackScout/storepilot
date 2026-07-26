"use client";

import { useState } from "react";
import Image from "next/image";
import Link from "next/link";
import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import { toast } from "sonner";
import { Package, Pencil, Plus, Trash2 } from "lucide-react";
import { Button } from "@/components/ui/button";
import { Card, CardContent } from "@/components/ui/card";
import { Badge } from "@/components/ui/badge";
import {
  Dialog,
  DialogContent,
  DialogDescription,
  DialogFooter,
  DialogHeader,
  DialogTitle,
} from "@/components/ui/dialog";
import { EmptyState } from "@/components/shared/empty-state";
import { TableRowSkeleton } from "@/components/shared/loading-skeletons";
import { PriceDisplay } from "@/components/shared/price-display";
import { formatLkr } from "@/lib/currency";
import { useSellerStoreId } from "@/hooks/use-seller-store";
import { productsService } from "@/services";
import type { Product } from "@/types";

export default function DashboardProductsPage() {
  const queryClient = useQueryClient();
  const storeId = useSellerStoreId();
  const [productToDelete, setProductToDelete] = useState<Product | null>(null);

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
                        <PriceDisplay priceLkr={product.priceLkr} size="sm" />
                      </td>
                      <td className="px-6 py-3">{product.stockQuantity}</td>
                      <td className="px-6 py-3">
                        <Badge
                          variant={product.status === "active" ? "secondary" : "outline"}
                          className={
                            product.status === "out-of-stock"
                              ? "border-red-200 text-red-700 dark:text-red-400"
                              : ""
                          }
                        >
                          {product.status === "out-of-stock" ? "Out of stock" : product.status}
                        </Badge>
                      </td>
                      <td className="px-6 py-3">
                        <div className="flex items-center gap-1">
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
              This will permanently remove &quot;{productToDelete?.name}&quot; ({formatLkr(productToDelete?.priceLkr ?? 0)}) from your store. This can&apos;t be undone.
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
