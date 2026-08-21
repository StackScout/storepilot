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
import { Select, SelectContent, SelectItem, SelectTrigger, SelectValue } from "@/components/ui/select";
import { ImageUploader } from "@/components/dashboard/image-uploader";
import { getCategoryLabel } from "@/mock/categories";
import { usePlatformConfig } from "@/hooks/use-platform-config";
import type { BookableService, BookableServiceFormInput, ServiceStatus, StoreCategory } from "@/types";

const serviceFormSchema = z.object({
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
  durationMinutes: z.number().int().positive("Enter how long this service takes"),
  bufferMinutes: z.number().int().min(0, "Buffer can't be negative"),
  status: z.enum(["active", "draft"]),
});

interface ServiceFormProps {
  initialService?: BookableService;
  onSubmit: (input: BookableServiceFormInput, images: File[]) => void;
  isSubmitting: boolean;
  submitLabel?: string;
  /** A service's category is locked to its store's own approved category — same rule as ProductForm. */
  storeCategory: StoreCategory;
}

export function ServiceForm({
  initialService,
  onSubmit,
  isSubmitting,
  submitLabel = "Save service",
  storeCategory,
}: ServiceFormProps) {
  const {
    register,
    handleSubmit,
    watch,
    setValue,
    formState: { errors },
  } = useForm<z.infer<typeof serviceFormSchema>>({
    resolver: zodResolver(serviceFormSchema),
    defaultValues: {
      name: initialService?.name ?? "",
      description: initialService?.description ?? "",
      category: storeCategory,
      // Cents on the wire, dollars in the form — same conversion as ProductForm.
      price: initialService ? initialService.price / 100 : undefined,
      durationMinutes: initialService?.durationMinutes ?? 30,
      bufferMinutes: initialService?.bufferMinutes ?? 0,
      status: initialService?.status ?? "active",
    },
  });

  const status = watch("status");
  const { currencyCode } = usePlatformConfig();

  const [images, setImages] = useState<File[]>([]);
  const [imagesError, setImagesError] = useState<string | undefined>(undefined);

  function submit(values: z.infer<typeof serviceFormSchema>) {
    const hasExistingImages = (initialService?.images.length ?? 0) > 0;
    if (images.length === 0 && !hasExistingImages) {
      setImagesError("Add at least one image");
      return;
    }
    setImagesError(undefined);
    onSubmit({ ...values, price: Math.round(values.price * 100) }, images);
  }

  return (
    <form onSubmit={handleSubmit(submit)} className="max-w-2xl space-y-6">
      <Card>
        <CardContent className="space-y-4">
          <h2 className="font-semibold">Service details</h2>

          <ImageUploader
            files={images}
            onChange={setImages}
            existingImages={initialService?.images}
            error={imagesError}
          />

          <div className="space-y-1.5">
            <Label htmlFor="name">Service name</Label>
            <Input id="name" placeholder="e.g. Classic Haircut" {...register("name")} />
            {errors.name ? <p className="text-destructive text-xs">{errors.name.message}</p> : null}
          </div>

          <div className="space-y-1.5">
            <Label htmlFor="description">Description</Label>
            <Textarea
              id="description"
              rows={4}
              placeholder="Describe what's included in this service..."
              {...register("description")}
            />
            {errors.description ? (
              <p className="text-destructive text-xs">{errors.description.message}</p>
            ) : null}
          </div>

          <div className="space-y-1.5">
            <Label htmlFor="category">Category</Label>
            <Input id="category" value={getCategoryLabel(storeCategory)} disabled />
            <p className="text-muted-foreground text-xs">Fixed to your store&apos;s approved category.</p>
          </div>
        </CardContent>
      </Card>

      <Card>
        <CardContent className="space-y-4">
          <h2 className="font-semibold">Pricing & scheduling</h2>
          <div className="grid gap-4 sm:grid-cols-3">
            <div className="space-y-1.5">
              <Label htmlFor="price">Price ({currencyCode})</Label>
              <Input id="price" type="number" step="0.01" {...register("price", { valueAsNumber: true })} />
              {errors.price ? <p className="text-destructive text-xs">{errors.price.message}</p> : null}
            </div>
            <div className="space-y-1.5">
              <Label htmlFor="durationMinutes">Duration (minutes)</Label>
              <Input
                id="durationMinutes"
                type="number"
                step="5"
                {...register("durationMinutes", { valueAsNumber: true })}
              />
              {errors.durationMinutes ? (
                <p className="text-destructive text-xs">{errors.durationMinutes.message}</p>
              ) : null}
            </div>
            <div className="space-y-1.5">
              <Label htmlFor="bufferMinutes">Buffer after (minutes)</Label>
              <Input
                id="bufferMinutes"
                type="number"
                step="5"
                {...register("bufferMinutes", { valueAsNumber: true })}
              />
              <p className="text-muted-foreground text-xs">Gap before the next slot opens.</p>
            </div>
          </div>

          <div className="space-y-1.5">
            <Label htmlFor="status">Status</Label>
            <Select value={status} onValueChange={(v) => setValue("status", v as ServiceStatus)}>
              <SelectTrigger id="status" className="w-full sm:w-48">
                <SelectValue>{(v: ServiceStatus) => (v === "draft" ? "Draft" : "Active")}</SelectValue>
              </SelectTrigger>
              <SelectContent>
                <SelectItem value="active">Active</SelectItem>
                <SelectItem value="draft">Draft</SelectItem>
              </SelectContent>
            </Select>
            <p className="text-muted-foreground text-xs">
              Only active services are bookable and shown on your storefront.
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
