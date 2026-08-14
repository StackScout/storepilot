import type { Metadata } from "next";
import { StorePageContent } from "@/components/marketplace/store-page-content";
import { bookableServicesService, productsService, storesService } from "@/services";

interface StorePageProps {
  params: Promise<{ slug: string }>;
}

export async function generateMetadata({ params }: StorePageProps): Promise<Metadata> {
  const { slug } = await params;
  const store = await storesService.getStoreBySlug(slug);
  return { title: store ? store.name : "Store not found" };
}

export default async function StorePage({ params }: StorePageProps) {
  const { slug } = await params;
  const store = await storesService.getStoreBySlug(slug);
  const [products, services, publicSettings] = store
    ? await Promise.all([
        productsService.listProductsByStore(store.id),
        bookableServicesService.listServicesByStore(store.id),
        storesService.getPublicStoreSettings(store.id),
      ])
    : [[], [], null];

  return (
    <StorePageContent
      slug={slug}
      initialStore={store}
      initialProducts={products}
      initialServices={services}
      bookingsEnabled={publicSettings?.bookingsEnabled ?? false}
    />
  );
}
