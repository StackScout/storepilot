import Link from "next/link";
import { notFound } from "next/navigation";
import type { Metadata } from "next";
import { ChevronRight, Clock, MapPin } from "lucide-react";
import { PriceDisplay } from "@/components/shared/price-display";
import { ProductGallery } from "@/components/marketplace/product-gallery";
import { ServiceBookingForm } from "@/components/marketplace/service-booking-form";
import { CopyLinkButton } from "@/components/shared/copy-link-button";
import { bookableServicesService, storesService } from "@/services";

interface ServicePageProps {
  params: Promise<{ slug: string; serviceSlug: string }>;
}

export async function generateMetadata({ params }: ServicePageProps): Promise<Metadata> {
  const { slug, serviceSlug } = await params;
  const service = await bookableServicesService.getServiceBySlug(slug, serviceSlug);
  return { title: service ? service.name : "Service not found" };
}

export default async function ServicePage({ params }: ServicePageProps) {
  const { slug, serviceSlug } = await params;
  const service = await bookableServicesService.getServiceBySlug(slug, serviceSlug);
  if (!service) notFound();

  const store = await storesService.getStoreBySlug(slug);
  if (!store) notFound();

  return (
    <div className="mx-auto max-w-7xl px-4 py-6 sm:px-6 lg:px-8">
      <nav className="text-muted-foreground mb-4 flex items-center gap-1 text-xs">
        <Link href="/" className="hover:text-foreground">
          Home
        </Link>
        <ChevronRight className="size-3" />
        <Link href={`/stores/${slug}`} className="hover:text-foreground">
          {service.storeName}
        </Link>
        <ChevronRight className="size-3" />
        <span className="text-foreground line-clamp-1">{service.name}</span>
      </nav>

      <div className="grid gap-8 lg:grid-cols-2">
        <div className="space-y-6">
          <ProductGallery images={service.images} productName={service.name} />

          <div className="space-y-5">
            <div className="space-y-2">
              <Link href={`/stores/${slug}`} className="text-primary flex items-center gap-1 text-sm font-medium">
                {service.storeName}
              </Link>
              <div className="flex items-start justify-between gap-3">
                <h1 className="text-2xl font-bold text-balance">{service.name}</h1>
                <CopyLinkButton path={`/stores/${slug}/services/${service.slug}`} className="shrink-0" />
              </div>
              <div className="flex flex-wrap items-center gap-3">
                <span className="text-muted-foreground flex items-center gap-1 text-xs">
                  <Clock className="size-3.5" /> {service.durationMinutes} minutes
                </span>
                <span className="text-muted-foreground flex items-center gap-1 text-xs">
                  <MapPin className="size-3.5" /> {store.address.city}
                </span>
              </div>
            </div>

            <PriceDisplay price={service.price} size="lg" />

            <div className="space-y-1.5 border-t pt-5">
              <h2 className="text-sm font-semibold">Description</h2>
              <p className="text-muted-foreground text-sm leading-relaxed">{service.description}</p>
            </div>
          </div>
        </div>

        <div>
          <ServiceBookingForm service={service} store={store} />
        </div>
      </div>
    </div>
  );
}
