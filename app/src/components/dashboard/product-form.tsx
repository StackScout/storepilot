"use client";

import { useForm } from "react-hook-form";
import { zodResolver } from "@hookform/resolvers/zod";
import { z } from "zod";
import { Loader2 } from "lucide-react";
import { Button } from "@/components/ui/button";
import { Input } from "@/components/ui/input";
import { Textarea } from "@/components/ui/textarea";
import { Label } from "@/components/ui/label";
import { Card, CardContent } from "@/components/ui/card";
import { Select, SelectContent, SelectItem, SelectTrigger, SelectValue } from "@/components/ui/select";
import { ImageUploader } from "@/components/dashboard/image-uploader";
import { CATEGORIES } from "@/mock/categories";
import type { Product, ProductFormInput } from "@/types";

const productFormSchema = z.object({
  name: z.string().min(3, "Name must be at least 3 characters"),
  description: z.string().min(10, "Add a short description (min 10 characters)"),
  category: z.enum([
    "fashion",
    "food-beverage",
    "beauty",
    "handicrafts",
    "electronics",
    "home-living",
    "jewelry",
    "grocery",
  ]),
  priceLkr: z.number().positive("Enter a valid price"),
  compareAtPriceLkr: z.union([z.number().positive(), z.nan()]).optional(),
  stockQuantity: z.number().int().min(0, "Stock can't be negative"),
  sku: z.string().min(2, "Enter a SKU"),
  status: z.enum(["active", "draft", "out-of-stock"]),
  imageUrl: z.string().min(1, "Add a product image"),
});

interface ProductFormProps {
  initialProduct?: Product;
  onSubmit: (input: ProductFormInput) => void;
  isSubmitting: boolean;
  submitLabel?: string;
}

export function ProductForm({
  initialProduct,
  onSubmit,
  isSubmitting,
  submitLabel = "Save product",
}: ProductFormProps) {
  const {
    register,
    handleSubmit,
    watch,
    setValue,
    formState: { errors },
  } = useForm<z.infer<typeof productFormSchema>>({
    resolver: zodResolver(productFormSchema),
    defaultValues: {
      name: initialProduct?.name ?? "",
      description: initialProduct?.description ?? "",
      category: initialProduct?.category ?? "fashion",
      priceLkr: initialProduct?.priceLkr ?? undefined,
      compareAtPriceLkr: initialProduct?.compareAtPriceLkr ?? undefined,
      stockQuantity: initialProduct?.stockQuantity ?? 0,
      sku: initialProduct?.sku ?? "",
      status: initialProduct?.status ?? "active",
      imageUrl: initialProduct?.images[0]?.url ?? "",
    },
  });

  const category = watch("category");
  const status = watch("status");
  const imageUrl = watch("imageUrl");

  function submit(values: z.infer<typeof productFormSchema>) {
    onSubmit({
      ...values,
      compareAtPriceLkr:
        values.compareAtPriceLkr && !Number.isNaN(values.compareAtPriceLkr)
          ? values.compareAtPriceLkr
          : undefined,
    });
  }

  return (
    <form onSubmit={handleSubmit(submit)} className="max-w-2xl space-y-6">
      <Card>
        <CardContent className="space-y-4">
          <h2 className="font-semibold">Product details</h2>

          <ImageUploader
            value={imageUrl}
            onChange={(url) => setValue("imageUrl", url, { shouldValidate: true })}
            error={errors.imageUrl?.message}
          />

          <div className="space-y-1.5">
            <Label htmlFor="name">Product name</Label>
            <Input id="name" placeholder="e.g. Ceylon Cinnamon Sticks (100g)" {...register("name")} />
            {errors.name ? <p className="text-destructive text-xs">{errors.name.message}</p> : null}
          </div>

          <div className="space-y-1.5">
            <Label htmlFor="description">Description</Label>
            <Textarea
              id="description"
              rows={4}
              placeholder="Describe your product..."
              {...register("description")}
            />
            {errors.description ? (
              <p className="text-destructive text-xs">{errors.description.message}</p>
            ) : null}
          </div>

          <div className="grid gap-4 sm:grid-cols-2">
            <div className="space-y-1.5">
              <Label htmlFor="category">Category</Label>
              <Select
                value={category}
                onValueChange={(v) => setValue("category", v as ProductFormInput["category"])}
              >
                <SelectTrigger id="category" className="w-full">
                  <SelectValue />
                </SelectTrigger>
                <SelectContent>
                  {CATEGORIES.map((c) => (
                    <SelectItem key={c.value} value={c.value}>
                      {c.label}
                    </SelectItem>
                  ))}
                </SelectContent>
              </Select>
            </div>
            <div className="space-y-1.5">
              <Label htmlFor="sku">SKU</Label>
              <Input id="sku" placeholder="e.g. CSC-CIN-100" {...register("sku")} />
              {errors.sku ? <p className="text-destructive text-xs">{errors.sku.message}</p> : null}
            </div>
          </div>
        </CardContent>
      </Card>

      <Card>
        <CardContent className="space-y-4">
          <h2 className="font-semibold">Pricing & inventory</h2>
          <div className="grid gap-4 sm:grid-cols-3">
            <div className="space-y-1.5">
              <Label htmlFor="priceLkr">Price (LKR)</Label>
              <Input
                id="priceLkr"
                type="number"
                step="1"
                {...register("priceLkr", { valueAsNumber: true })}
              />
              {errors.priceLkr ? (
                <p className="text-destructive text-xs">{errors.priceLkr.message}</p>
              ) : null}
            </div>
            <div className="space-y-1.5">
              <Label htmlFor="compareAtPriceLkr">Compare-at price</Label>
              <Input
                id="compareAtPriceLkr"
                type="number"
                step="1"
                placeholder="Optional"
                {...register("compareAtPriceLkr", { valueAsNumber: true })}
              />
            </div>
            <div className="space-y-1.5">
              <Label htmlFor="stockQuantity">Stock quantity</Label>
              <Input
                id="stockQuantity"
                type="number"
                step="1"
                {...register("stockQuantity", { valueAsNumber: true })}
              />
              {errors.stockQuantity ? (
                <p className="text-destructive text-xs">{errors.stockQuantity.message}</p>
              ) : null}
            </div>
          </div>

          <div className="space-y-1.5">
            <Label htmlFor="status">Status</Label>
            <Select value={status} onValueChange={(v) => setValue("status", v as ProductFormInput["status"])}>
              <SelectTrigger id="status" className="w-full sm:w-48">
                <SelectValue />
              </SelectTrigger>
              <SelectContent>
                <SelectItem value="active">Active</SelectItem>
                <SelectItem value="draft">Draft</SelectItem>
              </SelectContent>
            </Select>
            <p className="text-muted-foreground text-xs">
              Products automatically show as &quot;Out of stock&quot; when quantity reaches 0.
            </p>
          </div>
        </CardContent>
      </Card>

      <div className="flex justify-end gap-3">
        <Button type="submit" disabled={isSubmitting}>
          {isSubmitting ? <Loader2 className="size-4 animate-spin" /> : null}
          {submitLabel}
        </Button>
      </div>
    </form>
  );
}
