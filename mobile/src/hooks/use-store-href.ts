import { useSegments, type Href } from 'expo-router';

/**
 * Store/product/service screens are nested under all four buyer tabs
 * (home/search/cart/account), not a single top-level route, so that pushing
 * into one keeps the current tab's own native tab bar and back-stack instead
 * of covering the whole app full-screen. Building an href for one of these
 * screens needs to target whichever tab the caller is currently in.
 */
const BUYER_TABS = ['home', 'search', 'cart', 'account'] as const;
type BuyerTab = (typeof BUYER_TABS)[number];

function useCurrentBuyerTab(): BuyerTab {
  const segments = useSegments();
  return (segments as string[]).find((s): s is BuyerTab => (BUYER_TABS as readonly string[]).includes(s)) ?? 'home';
}

export function useStoreHrefs() {
  const tab = useCurrentBuyerTab();
  return {
    store: (storeSlug: string): Href => `/${tab}/stores/${storeSlug}` as Href,
    product: (storeSlug: string, productSlug: string): Href => `/${tab}/stores/${storeSlug}/products/${productSlug}` as Href,
    service: (storeSlug: string, serviceSlug: string): Href => `/${tab}/stores/${storeSlug}/services/${serviceSlug}` as Href,
  };
}
