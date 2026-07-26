"use client";

import { useState } from "react";
import { usePathname } from "next/navigation";
import { Menu } from "lucide-react";
import { Button } from "@/components/ui/button";
import { Sheet, SheetContent, SheetTitle } from "@/components/ui/sheet";
import { DashboardSidebarContent } from "@/components/dashboard/dashboard-sidebar";

export function DashboardMobileNav({ sellerEmail }: { sellerEmail?: string }) {
  const [open, setOpen] = useState(false);
  const pathname = usePathname();
  const [lastPathname, setLastPathname] = useState(pathname);

  // Close the sheet on navigation. Setting state during render (rather than
  // in an effect) is the pattern React recommends for "reset state when a
  // prop changes" — it avoids an extra commit/flash of the open sheet.
  if (pathname !== lastPathname) {
    setLastPathname(pathname);
    setOpen(false);
  }

  return (
    <Sheet open={open} onOpenChange={setOpen}>
      <SheetContent side="left" className="w-72 p-0">
        <SheetTitle className="sr-only">Dashboard navigation</SheetTitle>
        <DashboardSidebarContent sellerEmail={sellerEmail} />
      </SheetContent>
      <Button
        variant="outline"
        size="icon"
        className="lg:hidden"
        aria-label="Open dashboard menu"
        onClick={() => setOpen(true)}
      >
        <Menu className="size-4.5" />
      </Button>
    </Sheet>
  );
}
