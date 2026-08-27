import AppTabs from '@/components/app-tabs';
import BuyerTabs from '@/components/buyer-tabs';
import { useAuthStore } from '@/store/auth-store';

/** Buyer browsing (and the cart) never require sign-in — only a signed-in seller gets the seller dashboard; everyone else (guest or signed-in buyer) gets the buyer marketplace tabs, matching the web app's public-marketplace-plus-gated-actions model. */
export default function AppLayout() {
  const isHydrated = useAuthStore((s) => s.isHydrated);
  const isSignedIn = useAuthStore((s) => s.isSignedIn);
  const role = useAuthStore((s) => s.role);

  if (!isHydrated) return null;
  return isSignedIn && role === 'seller' ? <AppTabs /> : <BuyerTabs />;
}
