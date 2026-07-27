import Link from "next/link";
import { LogOut } from "lucide-react";
import { Button } from "@/components/ui/button";
import { DashboardSidebarContent } from "@/components/dashboard/dashboard-sidebar";
import { DashboardMobileNav } from "@/components/dashboard/dashboard-mobile-nav";
import { PendingVerificationBanner } from "@/components/dashboard/pending-verification-banner";
import { SellerStoreProvider } from "@/hooks/use-seller-store";

export default function DashboardLayout({ children }: { children: React.ReactNode }) {
  return (
    <SellerStoreProvider>
      <div className="bg-muted/20 flex min-h-screen">
        <aside className="bg-background hidden w-64 shrink-0 border-r lg:block">
          <DashboardSidebarContent />
        </aside>

        <div className="flex min-w-0 flex-1 flex-col">
          <header className="bg-background/95 sticky top-0 z-30 flex items-center gap-3 border-b px-4 py-3 backdrop-blur lg:px-8">
            <DashboardMobileNav />
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
