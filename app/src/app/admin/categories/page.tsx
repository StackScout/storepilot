"use client";

import { useState } from "react";
import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import { toast } from "sonner";
import { LayoutGrid, Trash2 } from "lucide-react";
import { Button } from "@/components/ui/button";
import { Card, CardContent } from "@/components/ui/card";
import { StatusBadge } from "@/components/shared/status-badge";
import { EmptyState } from "@/components/shared/empty-state";
import { TableRowSkeleton } from "@/components/shared/loading-skeletons";
import {
  Dialog,
  DialogContent,
  DialogDescription,
  DialogFooter,
  DialogHeader,
  DialogTitle,
} from "@/components/ui/dialog";
import { CategoryFormDialog } from "@/components/admin/category-form-dialog";
import { queryKeys } from "@/lib/query-keys";
import { categoriesService } from "@/services";
import type { Category } from "@/types";

export default function AdminCategoriesPage() {
  const queryClient = useQueryClient();
  const [categoryToDelete, setCategoryToDelete] = useState<Category | null>(null);

  const { data: categories, isLoading } = useQuery({
    queryKey: queryKeys.categories.admin(),
    queryFn: () => categoriesService.adminListCategories(),
  });

  const sortedCategories = categories ? [...categories].sort((a, b) => a.sortOrder - b.sortOrder) : undefined;

  const deleteMutation = useMutation({
    mutationFn: (id: string) => categoriesService.deleteCategory(id),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: queryKeys.categories.all() });
      queryClient.invalidateQueries({ queryKey: queryKeys.categories.admin() });
      toast.success("Category deleted");
      setCategoryToDelete(null);
    },
    onError: (error: Error) =>
      toast.error(error.message || "Couldn't delete this category — try deactivating it instead."),
  });

  return (
    <div className="space-y-6">
      <div className="flex items-center justify-between">
        <div>
          <h1 className="text-2xl font-bold">Categories</h1>
          <p className="text-muted-foreground text-sm">
            Store, product, and service categories shown across the marketplace. Deactivate a category
            that&apos;s still in use instead of deleting it.
          </p>
        </div>
        <CategoryFormDialog />
      </div>

      <Card>
        <CardContent>
          {isLoading ? (
            <div className="divide-y">
              <TableRowSkeleton columns={5} />
              <TableRowSkeleton columns={5} />
            </div>
          ) : !sortedCategories || sortedCategories.length === 0 ? (
            <EmptyState
              icon={LayoutGrid}
              title="No categories yet"
              description="Add the first category sellers can list their store, product, or service under."
            />
          ) : (
            <div className="-mx-6 overflow-x-auto">
              <table className="w-full text-sm">
                <thead>
                  <tr className="text-muted-foreground border-y text-left text-xs">
                    <th className="px-6 py-2 font-medium">Name</th>
                    <th className="px-6 py-2 font-medium">Wire value</th>
                    <th className="px-6 py-2 font-medium">Icon</th>
                    <th className="px-6 py-2 font-medium">Sort order</th>
                    <th className="px-6 py-2 font-medium">Status</th>
                    <th className="px-6 py-2 font-medium">Actions</th>
                  </tr>
                </thead>
                <tbody>
                  {sortedCategories.map((category) => (
                    <tr key={category.id} className="border-b last:border-0">
                      <td className="px-6 py-3 font-medium">{category.name}</td>
                      <td className="text-muted-foreground px-6 py-3 font-mono text-xs">{category.wireValue}</td>
                      <td className="text-muted-foreground px-6 py-3">{category.icon}</td>
                      <td className="text-muted-foreground px-6 py-3">{category.sortOrder}</td>
                      <td className="px-6 py-3">
                        <StatusBadge tone={category.active ? "success" : "neutral"}>
                          {category.active ? "active" : "inactive"}
                        </StatusBadge>
                      </td>
                      <td className="px-6 py-3">
                        <div className="flex items-center gap-1">
                          <CategoryFormDialog category={category} />
                          <Button
                            type="button"
                            variant="ghost"
                            size="icon"
                            className="text-destructive size-8"
                            onClick={() => setCategoryToDelete(category)}
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

      <Dialog open={!!categoryToDelete} onOpenChange={(open) => !open && setCategoryToDelete(null)}>
        <DialogContent>
          <DialogHeader>
            <DialogTitle>Delete &quot;{categoryToDelete?.name}&quot;?</DialogTitle>
            <DialogDescription>
              This will permanently remove the category. It can&apos;t be undone, and it will be rejected
              if any store, product, or service still uses it — deactivate it instead in that case.
            </DialogDescription>
          </DialogHeader>
          <DialogFooter>
            <Button variant="outline" onClick={() => setCategoryToDelete(null)}>
              Cancel
            </Button>
            <Button
              variant="destructive"
              disabled={deleteMutation.isPending}
              onClick={() => categoryToDelete && deleteMutation.mutate(categoryToDelete.id)}
            >
              Delete
            </Button>
          </DialogFooter>
        </DialogContent>
      </Dialog>
    </div>
  );
}
