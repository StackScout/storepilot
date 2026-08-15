"use client";

import Link from "next/link";
import { usePathname } from "next/navigation";
import { LayoutDashboard, ClipboardCheck, Wallet, Users, History, Bell, ShieldCheck, Tag } from "lucide-react";
import { cn } from "@/lib/utils";
import { Logo } from "@/components/shared/logo";
import { ThemeToggle } from "@/components/shared/theme-toggle";

const NAV_ITEMS = [
  { href: "/admin", label: "Overview", icon: LayoutDashboard },
  { href: "/admin/stores", label: "Store approvals", icon: ClipboardCheck },
  { href: "/admin/accounting", label: "Accounting", icon: Wallet },
  { href: "/admin/coupons", label: "Coupons", icon: Tag },
  { href: "/admin/admins", label: "Admins", icon: Users },
  { href: "/admin/audit-log", label: "Audit log", icon: History },
  { href: "/admin/notifications", label: "Notifications", icon: Bell },
  { href: "/admin/settings", label: "Your security", icon: ShieldCheck },
];

export function AdminSidebarContent() {
  const pathname = usePathname();

  return (
    <div className="flex h-full flex-col">
      <div className="p-4">
        <Logo href="/admin" />
      </div>

      <nav className="flex-1 space-y-1 px-2">
        {NAV_ITEMS.map((item) => {
          const isActive = item.href === "/admin" ? pathname === item.href : pathname.startsWith(item.href);
          return (
            <Link
              key={item.href}
              href={item.href}
              className={cn(
                "flex items-center gap-2.5 rounded-md px-3 py-2 text-sm font-medium transition-colors",
                isActive
                  ? "bg-primary/10 text-primary"
                  : "text-muted-foreground hover:bg-accent hover:text-foreground",
              )}
            >
              <item.icon className="size-4" />
              {item.label}
            </Link>
          );
        })}
      </nav>

      <div className="flex items-center justify-between border-t p-4">
        <span className="text-muted-foreground text-xs font-medium">Theme</span>
        <ThemeToggle className="-mr-2 size-8" />
      </div>
    </div>
  );
}
