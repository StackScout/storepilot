"use client";

import { useEffect, useMemo } from "react";
import Image from "next/image";
import { ImageOff, Upload, X } from "lucide-react";
import { Input } from "@/components/ui/input";
import { Button } from "@/components/ui/button";
import { Label } from "@/components/ui/label";
import type { ProductImage } from "@/types";

interface ImageUploaderProps {
  /** Newly selected files, not yet uploaded. */
  files: File[];
  onChange: (files: File[]) => void;
  /** Existing product images (edit mode only) — shown until the seller picks new files, which replace the whole set. */
  existingImages?: ProductImage[];
  error?: string;
}

/**
 * Multi-file product image uploader — the backend stores whatever is
 * selected here as the product's full image set (see products.service.ts's
 * createProduct/updateProduct: selecting new files replaces existing ones
 * entirely, never merges).
 */
export function ImageUploader({ files, onChange, existingImages = [], error }: ImageUploaderProps) {
  const objectUrls = useMemo(() => files.map((f) => URL.createObjectURL(f)), [files]);
  useEffect(() => {
    return () => objectUrls.forEach((url) => URL.revokeObjectURL(url));
  }, [objectUrls]);

  const previews = files.length > 0 ? objectUrls : existingImages.map((img) => img.url);

  function handleFilesSelected(fileList: FileList | null) {
    if (!fileList || fileList.length === 0) return;
    onChange(Array.from(fileList));
  }

  function removeFile(index: number) {
    onChange(files.filter((_, i) => i !== index));
  }

  return (
    <div className="space-y-2">
      <Label htmlFor="product-images">Product images</Label>
      <div className="flex flex-wrap gap-3">
        {previews.length > 0 ? (
          previews.map((src, i) => (
            <div key={src + i} className="bg-muted relative size-20 shrink-0 overflow-hidden rounded-md border">
              <Image src={src} alt="Product preview" fill sizes="80px" className="object-cover" />
              {files.length > 0 ? (
                <button
                  type="button"
                  onClick={() => removeFile(i)}
                  className="absolute top-0.5 right-0.5 rounded-full bg-black/60 p-0.5 text-white"
                  aria-label="Remove image"
                >
                  <X className="size-3" />
                </button>
              ) : null}
            </div>
          ))
        ) : (
          <div className="bg-muted text-muted-foreground flex size-20 shrink-0 items-center justify-center rounded-md border">
            <ImageOff className="size-5" />
          </div>
        )}
      </div>
      <div>
        <Input
          id="product-images"
          type="file"
          accept="image/jpeg,image/png,image/webp"
          multiple
          className="hidden"
          onChange={(e) => handleFilesSelected(e.target.files)}
        />
        <Button
          type="button"
          variant="outline"
          size="sm"
          onClick={() => document.getElementById("product-images")?.click()}
        >
          <Upload className="size-3.5" />
          {existingImages.length > 0 ? "Replace images" : "Upload images"}
        </Button>
      </div>
      {error ? <p className="text-destructive text-xs">{error}</p> : null}
    </div>
  );
}
