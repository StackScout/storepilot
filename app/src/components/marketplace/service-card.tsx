import Image from "next/image";
import Link from "next/link";
import { Clock } from "lucide-react";
import { PriceDisplay } from "@/components/shared/price-display";
import type { BookableService } from "@/types";

export function ServiceCard({ service, priority = false }: { service: BookableService; priority?: boolean }) {
  return (
    <Link
      href={`/stores/${service.storeSlug}/services/${service.slug}`}
      className="group focus-visible:ring-ring block rounded-lg outline-none focus-visible:ring-2"
    >
      <div className="bg-muted relative aspect-square overflow-hidden rounded-lg">
        <Image
          src={service.images[0]?.url}
          alt={service.images[0]?.alt ?? service.name}
          fill
          priority={priority}
          sizes="(max-width: 640px) 50vw, (max-width: 1024px) 33vw, 25vw"
          className="object-cover transition-transform duration-300 group-hover:scale-105"
        />
        <span className="bg-background/90 text-foreground absolute top-2 left-2 flex items-center gap-1 rounded-full px-2 py-0.5 text-xs font-medium">
          <Clock className="size-3" /> {service.durationMinutes} min
        </span>
      </div>
      <div className="mt-2.5 space-y-1">
        <p className="text-muted-foreground truncate text-xs">{service.storeName}</p>
        <h3 className="line-clamp-2 text-sm leading-snug font-medium">{service.name}</h3>
        <PriceDisplay price={service.price} size="sm" />
      </div>
    </Link>
  );
}
