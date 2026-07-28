import Link from "next/link";
import { Logo } from "@/components/shared/logo";
import { getPlatformConfig } from "@/lib/platform-config";

export async function SiteFooter() {
  const config = await getPlatformConfig();
  return (
    <footer className="bg-muted/40 mt-16 border-t">
      <div className="mx-auto grid max-w-7xl gap-8 px-4 py-10 sm:grid-cols-2 sm:px-6 lg:grid-cols-4 lg:px-8">
        <div className="space-y-3 sm:col-span-2 lg:col-span-1">
          <Logo />
          <p className="text-muted-foreground max-w-xs text-sm">{config.tagline}</p>
        </div>
        <div className="space-y-2 text-sm">
          <p className="font-medium">Shop</p>
          <Link href="/search" className="text-muted-foreground hover:text-foreground block">
            Browse products
          </Link>
          <Link href="/track-order" className="text-muted-foreground hover:text-foreground block">
            Track an order
          </Link>
        </div>
        <div className="space-y-2 text-sm">
          <p className="font-medium">Sell</p>
          <Link href="/onboarding" className="text-muted-foreground hover:text-foreground block">
            Start selling
          </Link>
          <Link href="/dashboard" className="text-muted-foreground hover:text-foreground block">
            Seller dashboard
          </Link>
        </div>
        <div className="space-y-2 text-sm">
          <p className="font-medium">Company</p>
          <span className="text-muted-foreground block">{config.companyLocation}</span>
          <span className="text-muted-foreground block">{config.supportEmail}</span>
        </div>
      </div>
      <div className="text-muted-foreground border-t px-4 py-4 text-center text-xs sm:px-6 lg:px-8">
        © {new Date().getFullYear()} {config.name}. All rights reserved.
      </div>
    </footer>
  );
}
