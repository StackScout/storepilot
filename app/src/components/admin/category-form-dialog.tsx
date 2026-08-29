"use client";

import { useState } from "react";
import { useForm } from "react-hook-form";
import { zodResolver } from "@hookform/resolvers/zod";
import { z } from "zod";
import { useMutation, useQueryClient } from "@tanstack/react-query";
import { toast } from "sonner";
import { Loader2, Pencil, Plus } from "lucide-react";
import { Button } from "@/components/ui/button";
import { Input } from "@/components/ui/input";
import { Label } from "@/components/ui/label";
import { Checkbox } from "@/components/ui/checkbox";
import {
  Dialog,
  DialogContent,
  DialogFooter,
  DialogHeader,
  DialogTitle,
  DialogTrigger,
} from "@/components/ui/dialog";
import { queryKeys } from "@/lib/query-keys";
import { categoriesService } from "@/services";
import type { Category, CategoryFormInput } from "@/types";

const categorySchema = z.object({
  name: z.string().min(2, "Enter a category name"),
  wireValue: z
    .string()
    .min(2, "Enter a wire value")
    .regex(/^[a-z0-9-]+$/, "Lowercase letters, numbers, and hyphens only"),
  icon: z.string().min(1, "Enter an icon name"),
  sortOrder: z.number().int().min(0),
  active: z.boolean(),
});

type CategoryFormValues = z.infer<typeof categorySchema>;

const EMPTY_VALUES: CategoryFormValues = {
  name: "",
  wireValue: "",
  icon: "tag",
  sortOrder: 0,
  active: true,
};

function toFormValues(category: Category): CategoryFormValues {
  return {
    name: category.name,
    wireValue: category.wireValue,
    icon: category.icon,
    sortOrder: category.sortOrder,
    active: category.active,
  };
}

function toInput(values: CategoryFormValues): CategoryFormInput {
  return {
    name: values.name.trim(),
    wireValue: values.wireValue.trim(),
    icon: values.icon.trim(),
    sortOrder: values.sortOrder,
    active: values.active,
  };
}

/** Create or edit a category — [category] present means edit, absent means create. Mirrors CouponFormDialog's shape. */
export function CategoryFormDialog({ category }: { category?: Category }) {
  const [open, setOpen] = useState(false);
  const queryClient = useQueryClient();
  const initialValues = category ? toFormValues(category) : EMPTY_VALUES;

  const {
    register,
    handleSubmit,
    watch,
    setValue,
    reset,
    formState: { errors },
  } = useForm<CategoryFormValues>({
    resolver: zodResolver(categorySchema),
    defaultValues: initialValues,
  });

  const active = watch("active");

  const mutation = useMutation({
    mutationFn: (values: CategoryFormValues) => {
      const input = toInput(values);
      return category ? categoriesService.updateCategory(category.id, input) : categoriesService.createCategory(input);
    },
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: queryKeys.categories.all() });
      queryClient.invalidateQueries({ queryKey: queryKeys.categories.admin() });
      toast.success(category ? "Category updated" : "Category created");
      setOpen(false);
    },
    onError: (error: Error) => toast.error(error.message || "Couldn't save this category. Please try again."),
  });

  return (
    <Dialog
      open={open}
      onOpenChange={(next) => {
        setOpen(next);
        if (next) reset(initialValues);
      }}
    >
      <DialogTrigger
        render={
          <Button
            type="button"
            variant={category ? "ghost" : "default"}
            size={category ? "icon" : "default"}
            className={category ? "size-8" : undefined}
          />
        }
      >
        {category ? (
          <Pencil className="size-3.5" />
        ) : (
          <>
            <Plus className="size-4" /> New category
          </>
        )}
      </DialogTrigger>
      <DialogContent className="sm:max-w-md">
        <DialogHeader>
          <DialogTitle>{category ? "Edit category" : "New category"}</DialogTitle>
        </DialogHeader>
        <form onSubmit={handleSubmit((values) => mutation.mutate(values))} className="space-y-3">
          <div className="space-y-1.5">
            <Label htmlFor="categoryName">Name</Label>
            <Input id="categoryName" placeholder="e.g. Fashion" {...register("name")} />
            {errors.name ? <p className="text-destructive text-xs">{errors.name.message}</p> : null}
          </div>

          <div className="space-y-1.5">
            <Label htmlFor="categoryWireValue">Wire value</Label>
            <Input id="categoryWireValue" placeholder="e.g. fashion" {...register("wireValue")} />
            <p className="text-muted-foreground text-xs">
              Stored on every store/product/service using this category — avoid changing it once in use.
            </p>
            {errors.wireValue ? <p className="text-destructive text-xs">{errors.wireValue.message}</p> : null}
          </div>

          <div className="grid grid-cols-2 gap-3">
            <div className="space-y-1.5">
              <Label htmlFor="categoryIcon">Icon name</Label>
              <Input id="categoryIcon" placeholder="e.g. shirt" {...register("icon")} />
              {errors.icon ? <p className="text-destructive text-xs">{errors.icon.message}</p> : null}
            </div>
            <div className="space-y-1.5">
              <Label htmlFor="categorySortOrder">Sort order</Label>
              <Input id="categorySortOrder" type="number" step="1" {...register("sortOrder", { valueAsNumber: true })} />
              {errors.sortOrder ? <p className="text-destructive text-xs">{errors.sortOrder.message}</p> : null}
            </div>
          </div>

          <Label htmlFor="categoryActive" className="flex cursor-pointer items-center gap-2 pt-1">
            <Checkbox id="categoryActive" checked={active} onCheckedChange={(c) => setValue("active", c === true)} />
            Active
          </Label>

          <DialogFooter>
            <Button type="button" variant="outline" onClick={() => setOpen(false)}>
              Cancel
            </Button>
            <Button type="submit" disabled={mutation.isPending}>
              {mutation.isPending ? <Loader2 className="size-4 animate-spin" /> : null}
              {category ? "Save" : "Create"}
            </Button>
          </DialogFooter>
        </form>
      </DialogContent>
    </Dialog>
  );
}
