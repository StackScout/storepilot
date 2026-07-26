import type { Metadata } from "next";
import { StorePageContent } from "@/components/marketplace/store-page-content";
import { productsService, storesService } from "@/services";

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
  const products = store ? await productsService.listProductsByStore(store.id) : [];

  return <StorePageContent slug={slug} initialStore={store} initialProducts={products} />;
}
