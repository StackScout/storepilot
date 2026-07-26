"use client";

import Image from "next/image";
import { ImageOff, Shuffle } from "lucide-react";
import { Input } from "@/components/ui/input";
import { Button } from "@/components/ui/button";
import { Label } from "@/components/ui/label";

interface ImageUploaderProps {
  value: string;
  onChange: (url: string) => void;
  error?: string;
}

/**
 * MVP placeholder for a real upload flow (S3/Cloudinary + resumable upload).
 * Sellers paste an image URL for now; the preview and validation contract
 * are already in place for when a file-upload endpoint exists.
 */
export function ImageUploader({ value, onChange, error }: ImageUploaderProps) {
  function useSampleImage() {
    onChange(`https://picsum.photos/seed/product-${Date.now()}/700/700`);
  }

  return (
    <div className="space-y-2">
      <Label htmlFor="product-image">Product image URL</Label>
      <div className="flex gap-3">
        <div className="bg-muted relative size-20 shrink-0 overflow-hidden rounded-md border">
          {value ? (
            <Image src={value} alt="Product preview" fill sizes="80px" className="object-cover" />
          ) : (
            <div className="text-muted-foreground flex h-full items-center justify-center">
              <ImageOff className="size-5" />
            </div>
          )}
        </div>
        <div className="flex-1 space-y-2">
          <Input
            id="product-image"
            value={value}
            onChange={(e) => onChange(e.target.value)}
            placeholder="https://..."
            aria-invalid={!!error}
          />
          <Button type="button" variant="outline" size="sm" onClick={useSampleImage}>
            <Shuffle className="size-3.5" />
            Use a sample image
          </Button>
        </div>
      </div>
      {error ? <p className="text-destructive text-xs">{error}</p> : null}
    </div>
  );
}
