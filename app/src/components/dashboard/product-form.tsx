"use client";

import { useState } from "react";
import { useForm } from "react-hook-form";
import { zodResolver } from "@hookform/resolvers/zod";
import { z } from "zod";
import { Loader2 } from "lucide-react";
import { Button } from "@/components/ui/button";
import { Input } from "@/components/ui/input";
import { Textarea } from "@/components/ui/textarea";
import { Label } from "@/components/ui/label";
import { Card, CardContent } from "@/components/ui/card";
import { Checkbox } from "@/components/ui/checkbox";
import { Select, SelectContent, SelectItem, SelectTrigger, SelectValue } from "@/components/ui/select";
import { ImageUploader } from "@/components/dashboard/image-uploader";
import { cn } from "@/lib/utils";
import { getCategoryLabel } from "@/mock/categories";
import { usePlatformConfig } from "@/hooks/use-platform-config";
import type { Product, ProductFormInput, StoreCategory } from "@/types";

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
  price: z.number().positive("Enter a valid price"),
  compareAtPrice: z.union([z.number().positive(), z.nan()]).optional(),
  stockQuantity: z.number().int().min(0, "Stock can't be negative"),
  trackStock: z.boolean(),
  sku: z.string().optional(),
  status: z.enum(["active", "draft", "out-of-stock"]),
});

interface ProductFormProps {
  initialProduct?: Product;
  onSubmit: (input: ProductFormInput, images: File[]) => void;
  isSubmitting: boolean;
  submitLabel?: string;
  /** Store-wide switch (StoreSettings.stockManagementEnabled) — when false, the stock UI is hidden entirely and every product is submitted with trackStock=false. */
  stockManagementEnabled: boolean;
  /** A product's category is locked to its store's own approved category (backend ProductService enforces this too) — see task item 40's doc comment on Store.kt. */
  storeCategory: StoreCategory;
}

export function ProductForm({
  initialProduct,
  onSubmit,
  isSubmitting,
  submitLabel = "Save product",
  stockManagementEnabled,
  storeCategory,
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
      category: storeCategory,
      // Product.price/compareAtPrice are cents on the wire — this form
      // collects/displays whole-and-cents dollars, converted back to cents
      // only at submit time (see submit() below).
      price: initialProduct ? initialProduct.price / 100 : undefined,
      compareAtPrice: initialProduct?.compareAtPrice ? initialProduct.compareAtPrice / 100 : undefined,
      stockQuantity: initialProduct?.stockQuantity ?? 0,
      trackStock: initialProduct?.trackStock ?? true,
      sku: initialProduct?.sku ?? "",
      status: initialProduct?.status ?? "active",
    },
  });

  const status = watch("status");
  const trackStock = watch("trackStock");
  const { currencyCode } = usePlatformConfig();

  const [images, setImages] = useState<File[]>([]);
  const [imagesError, setImagesError] = useState<string | undefined>(undefined);

  function submit(values: z.infer<typeof productFormSchema>) {
    const hasExistingImages = (initialProduct?.images.length ?? 0) > 0;
    if (images.length === 0 && !hasExistingImages) {
      setImagesError("Add at least one product image");
      return;
    }
    setImagesError(undefined);
    onSubmit(
      {
        ...values,
        // Convert dollars (what the form collects) to cents (the wire unit) here, once.
        price: Math.round(values.price * 100),
        compareAtPrice:
          values.compareAtPrice && !Number.isNaN(values.compareAtPrice)
            ? Math.round(values.compareAtPrice * 100)
            : undefined,
        trackStock: stockManagementEnabled && values.trackStock,
        stockQuantity: stockManagementEnabled && values.trackStock ? values.stockQuantity : 0,
      },
      images,
    );
  }

  return (
    <form onSubmit={handleSubmit(submit)} className="max-w-2xl space-y-6">
      <Card>
        <CardContent className="space-y-4">
          <h2 className="font-semibold">Product details</h2>

          <ImageUploader
            files={images}
            onChange={setImages}
            existingImages={initialProduct?.images}
            error={imagesError}
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
              <Input id="category" value={getCategoryLabel(storeCategory)} disabled />
              <p className="text-muted-foreground text-xs">Fixed to your store&apos;s approved category.</p>
            </div>
            <div className="space-y-1.5">
              <Label htmlFor="sku">SKU (optional)</Label>
              <Input id="sku" placeholder="e.g. CSC-CIN-100" {...register("sku")} />
              {errors.sku ? <p className="text-destructive text-xs">{errors.sku.message}</p> : null}
            </div>
          </div>
        </CardContent>
      </Card>

      <Card>
        <CardContent className="space-y-4">
          <h2 className="font-semibold">Pricing & inventory</h2>
          <div className={cn("grid gap-4", stockManagementEnabled ? "sm:grid-cols-3" : "sm:grid-cols-2")}>
            <div className="space-y-1.5">
              <Label htmlFor="price">Price ({currencyCode})</Label>
              <Input
                id="price"
                type="number"
                step="0.01"
                {...register("price", { valueAsNumber: true })}
              />
              {errors.price ? (
                <p className="text-destructive text-xs">{errors.price.message}</p>
              ) : null}
            </div>
            <div className="space-y-1.5">
              <Label htmlFor="compareAtPrice">Compare-at price</Label>
              <Input
                id="compareAtPrice"
                type="number"
                step="0.01"
                placeholder="Optional"
                {...register("compareAtPrice", { valueAsNumber: true })}
              />
            </div>
            {stockManagementEnabled ? (
              <div className="space-y-1.5">
                <Label htmlFor="stockQuantity">Stock quantity</Label>
                <Input
                  id="stockQuantity"
                  type="number"
                  step="1"
                  disabled={!trackStock}
                  {...register("stockQuantity", { valueAsNumber: true })}
                />
                {errors.stockQuantity ? (
                  <p className="text-destructive text-xs">{errors.stockQuantity.message}</p>
                ) : null}
              </div>
            ) : null}
          </div>

          {stockManagementEnabled ? (
            <label className="flex items-start gap-3">
              <Checkbox
                checked={trackStock}
                onCheckedChange={(checked) => setValue("trackStock", checked === true)}
              />
              <span>
                <span className="block text-sm font-medium">Track stock for this product</span>
                <span className="text-muted-foreground block text-xs">
                  When off, this product is treated as always available regardless of quantity.
                </span>
              </span>
            </label>
          ) : null}

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
            {stockManagementEnabled && trackStock ? (
              <p className="text-muted-foreground text-xs">
                Products automatically show as &quot;Out of stock&quot; when quantity reaches 0.
              </p>
            ) : null}
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
