"use client";

import Link from "next/link";
import { usePathname, useRouter } from "next/navigation";
import { useQueryClient } from "@tanstack/react-query";
import { LayoutDashboard, ClipboardCheck, Wallet, Users, History, Bell, LogOut, ShieldCheck } from "lucide-react";
import { cn } from "@/lib/utils";
import { Logo } from "@/components/shared/logo";
import { Button } from "@/components/ui/button";
import { useAuthSession } from "@/hooks/use-auth-session";
import { authService } from "@/services";

const NAV_ITEMS = [
  { href: "/admin", label: "Overview", icon: LayoutDashboard },
  { href: "/admin/stores", label: "Store approvals", icon: ClipboardCheck },
  { href: "/admin/accounting", label: "Accounting", icon: Wallet },
  { href: "/admin/admins", label: "Admins", icon: Users },
  { href: "/admin/audit-log", label: "Audit log", icon: History },
  { href: "/admin/notifications", label: "Notifications", icon: Bell },
];

export function AdminSidebarContent() {
  const pathname = usePathname();
  const router = useRouter();
  const queryClient = useQueryClient();
  const { session } = useAuthSession();

  async function handleSignOut() {
    await authService.logout();
    queryClient.clear();
    router.push("/admin/login");
  }

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

      <div className="space-y-3 border-t p-4">
        <div className="flex items-center gap-2.5">
          <span className="bg-muted flex size-8 items-center justify-center rounded-full">
            <ShieldCheck className="size-4" />
          </span>
          <div className="min-w-0">
            <p className="truncate text-sm font-medium">Platform admin</p>
            {session.email ? <p className="text-muted-foreground truncate text-xs">{session.email}</p> : null}
          </div>
        </div>
        <Button type="button" variant="outline" size="sm" className="w-full" onClick={handleSignOut}>
          <LogOut className="size-3.5" /> Sign out
        </Button>
      </div>
    </div>
  );
}
