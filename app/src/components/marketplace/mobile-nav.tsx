"use client";

import { useState } from "react";
import Link from "next/link";
import { LogOut, Menu } from "lucide-react";
import { Button, buttonVariants } from "@/components/ui/button";
import { Sheet, SheetContent, SheetHeader, SheetTitle, SheetClose } from "@/components/ui/sheet";
import { Logo } from "@/components/shared/logo";
import { useBuyerAccountLink } from "@/hooks/use-buyer-account-link";
import { useSignOut } from "@/hooks/use-sign-out";

const LINKS = [
  { href: "/", label: "Home" },
  { href: "/search", label: "Browse products" },
  { href: "/track-order", label: "Track an order" },
];

export function MobileNav() {
  const [open, setOpen] = useState(false);
  const { buyerName } = useBuyerAccountLink();
  const signOut = useSignOut();

  return (
    <Sheet open={open} onOpenChange={setOpen}>
      <SheetContent side="left" className="w-72">
        <SheetHeader>
          <SheetTitle className="sr-only">Menu</SheetTitle>
          <Logo />
        </SheetHeader>
        <nav className="flex flex-col px-2">
          {LINKS.map((link) => (
            <SheetClose
              key={link.href}
              render={
                <Link
                  href={link.href}
                  className="hover:bg-accent rounded-md px-3 py-2.5 text-sm font-medium"
                />
              }
            >
              {link.label}
            </SheetClose>
          ))}
          <SheetClose
            key="/account"
            render={
              <Link
                href={buyerName ? "/account" : "/account/login"}
                className="hover:bg-accent rounded-md px-3 py-2.5 text-sm font-medium"
              />
            }
          >
            {buyerName ? `Hi, ${buyerName.split(" ")[0]}` : "Sign in / Register"}
          </SheetClose>
          {buyerName ? (
            <SheetClose
              nativeButton
              render={
                <button
                  type="button"
                  onClick={() => signOut()}
                  className="hover:bg-accent flex items-center gap-2 rounded-md px-3 py-2.5 text-left text-sm font-medium"
                />
              }
            >
              <LogOut className="size-3.5" /> Sign out
            </SheetClose>
          ) : null}
        </nav>
        <div className="mt-auto space-y-2 p-2">
          <SheetClose
            render={<Link href="/onboarding" className={buttonVariants({ className: "w-full" })} />}
          >
            Sell on IslandCart
          </SheetClose>
          <SheetClose
            render={
              <Link
                href="/dashboard"
                className={buttonVariants({ variant: "outline", className: "w-full" })}
              />
            }
          >
            Seller dashboard
          </SheetClose>
        </div>
      </SheetContent>
      <Button
        variant="outline"
        size="icon"
        className="md:hidden"
        aria-label="Open menu"
        onClick={() => setOpen(true)}
      >
        <Menu className="size-4.5" />
      </Button>
    </Sheet>
  );
}
