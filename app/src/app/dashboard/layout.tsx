import Link from "next/link";
import { LogOut } from "lucide-react";
import { Button } from "@/components/ui/button";
import { DashboardSidebarContent } from "@/components/dashboard/dashboard-sidebar";
import { DashboardMobileNav } from "@/components/dashboard/dashboard-mobile-nav";
import { PendingVerificationBanner } from "@/components/dashboard/pending-verification-banner";
import { getSession } from "@/lib/session";
import { SellerStoreProvider } from "@/hooks/use-seller-store";

export default async function DashboardLayout({ children }: { children: React.ReactNode }) {
  const session = await getSession();
  const sellerSession = session?.role === "seller" ? session : null;
  // Empty-string fallback only — proxy.ts already redirects unauthenticated
  // visitors before this layout ever renders, so sellerSession is always
  // set here in practice.
  const storeId = sellerSession?.storeId ?? "";

  return (
    <SellerStoreProvider storeId={storeId}>
      <div className="bg-muted/20 flex min-h-screen">
        <aside className="bg-background hidden w-64 shrink-0 border-r lg:block">
          <DashboardSidebarContent sellerEmail={sellerSession?.email} />
        </aside>

        <div className="flex min-w-0 flex-1 flex-col">
          <header className="bg-background/95 sticky top-0 z-30 flex items-center gap-3 border-b px-4 py-3 backdrop-blur lg:px-8">
            <DashboardMobileNav sellerEmail={sellerSession?.email} />
            <span className="font-semibold lg:hidden">Seller dashboard</span>
            <Button render={<Link href="/" />} variant="ghost" size="sm" className="ml-auto">
              <LogOut className="size-3.5" /> Exit to marketplace
            </Button>
          </header>
          <main className="flex-1 space-y-4 p-4 lg:p-8">
            <PendingVerificationBanner />
            {children}
          </main>
        </div>
      </div>
    </SellerStoreProvider>
  );
}
