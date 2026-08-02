"use client";

import { useState } from "react";
import Image from "next/image";
import { ChevronLeft, ChevronRight } from "lucide-react";
import { cn } from "@/lib/utils";
import type { ProductImage } from "@/types";

/** Amazon-style product image display: a large active image with prev/next arrows, backed by a thumbnail rail. images[0] is always the primary image (see backend ProductImage.sortOrder's doc comment). */
export function ProductGallery({ images, productName }: { images: ProductImage[]; productName: string }) {
  const [activeIndex, setActiveIndex] = useState(0);
  const active = images[activeIndex] ?? images[0];

  function show(index: number) {
    setActiveIndex((index + images.length) % images.length);
  }

  return (
    <div className="space-y-3">
      <div className="bg-muted group relative aspect-square overflow-hidden rounded-xl">
        <Image
          key={active?.id ?? activeIndex}
          src={active?.url}
          alt={active?.alt ?? productName}
          fill
          priority
          sizes="(max-width: 1024px) 100vw, 50vw"
          className="object-cover"
        />
        {images.length > 1 ? (
          <>
            <button
              type="button"
              onClick={() => show(activeIndex - 1)}
              aria-label="Previous image"
              className="absolute top-1/2 left-2 flex size-8 -translate-y-1/2 items-center justify-center rounded-full bg-white/80 opacity-0 shadow transition-opacity group-hover:opacity-100 hover:bg-white sm:opacity-100"
            >
              <ChevronLeft className="size-4" />
            </button>
            <button
              type="button"
              onClick={() => show(activeIndex + 1)}
              aria-label="Next image"
              className="absolute top-1/2 right-2 flex size-8 -translate-y-1/2 items-center justify-center rounded-full bg-white/80 opacity-0 shadow transition-opacity group-hover:opacity-100 hover:bg-white sm:opacity-100"
            >
              <ChevronRight className="size-4" />
            </button>
            <span className="absolute right-2 bottom-2 rounded-full bg-black/60 px-2 py-0.5 text-xs text-white">
              {activeIndex + 1} / {images.length}
            </span>
          </>
        ) : null}
      </div>

      {images.length > 1 ? (
        <div className="flex gap-2 overflow-x-auto pb-1">
          {images.map((image, i) => (
            <button
              key={image.id}
              type="button"
              onClick={() => setActiveIndex(i)}
              aria-label={`Show image ${i + 1}`}
              aria-current={i === activeIndex}
              className={cn(
                "bg-muted relative size-16 shrink-0 overflow-hidden rounded-md border-2",
                i === activeIndex ? "border-primary" : "border-transparent",
              )}
            >
              <Image src={image.url} alt={image.alt} fill sizes="64px" className="object-cover" />
            </button>
          ))}
        </div>
      ) : null}
    </div>
  );
}
