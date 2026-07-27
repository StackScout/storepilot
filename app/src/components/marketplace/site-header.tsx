"use client";

import Link from "next/link";
import { User } from "lucide-react";
import { Button } from "@/components/ui/button";
import { Logo } from "@/components/shared/logo";
import { SearchBar } from "@/components/marketplace/search-bar";
import { CartDrawer } from "@/components/marketplace/cart-drawer";
import { MobileNav } from "@/components/marketplace/mobile-nav";
import { AccountMenu } from "@/components/marketplace/account-menu";
import { useBuyerAccountLink } from "@/hooks/use-buyer-account-link";

export function SiteHeader() {
  const { buyerName } = useBuyerAccountLink();
  return (
    <header className="bg-background/95 sticky top-0 z-40 border-b backdrop-blur">
      <div className="mx-auto flex max-w-7xl flex-col gap-3 px-4 py-3 sm:px-6 lg:px-8">
        <div className="flex items-center gap-3">
          <MobileNav />
          <Logo className="shrink-0" />
          <SearchBar className="hidden flex-1 sm:block" />
          <div className="ml-auto flex items-center gap-2">
            <Button render={<Link href="/track-order" />} variant="ghost" className="hidden md:inline-flex">
              Track order
            </Button>
            <Button render={<Link href="/onboarding" />} variant="outline" className="hidden md:inline-flex">
              Sell on IslandCart
            </Button>
            {buyerName ? (
              <AccountMenu buyerName={buyerName} />
            ) : (
              <Button render={<Link href="/account/login" />} variant="ghost" size="icon" aria-label="Sign in">
                <User className="size-4.5" />
              </Button>
            )}
            <CartDrawer />
          </div>
        </div>
        <SearchBar className="sm:hidden" />
      </div>
    </header>
  );
}
