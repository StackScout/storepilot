"use client";

import { useState } from "react";
import { usePathname } from "next/navigation";
import { Menu } from "lucide-react";
import { Button } from "@/components/ui/button";
import { Sheet, SheetContent, SheetTitle } from "@/components/ui/sheet";
import { AdminSidebarContent } from "@/components/admin/admin-sidebar";

export function AdminMobileNav() {
  const [open, setOpen] = useState(false);
  const pathname = usePathname();
  const [lastPathname, setLastPathname] = useState(pathname);

  // Close the sheet on navigation — see DashboardMobileNav's identical pattern.
  if (pathname !== lastPathname) {
    setLastPathname(pathname);
    setOpen(false);
  }

  return (
    <Sheet open={open} onOpenChange={setOpen}>
      <SheetContent side="left" className="w-72 p-0">
        <SheetTitle className="sr-only">Admin navigation</SheetTitle>
        <AdminSidebarContent />
      </SheetContent>
      <Button
        variant="outline"
        size="icon"
        className="lg:hidden"
        aria-label="Open admin menu"
        onClick={() => setOpen(true)}
      >
        <Menu className="size-4.5" />
      </Button>
    </Sheet>
  );
}
