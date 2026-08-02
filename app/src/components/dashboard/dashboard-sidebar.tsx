"use client";

import Link from "next/link";
import { usePathname, useRouter } from "next/navigation";
import { useQuery, useQueryClient } from "@tanstack/react-query";
import {
  LayoutDashboard,
  Package,
  ClipboardList,
  Wallet,
  Settings,
  Store,
  ExternalLink,
  LogOut,
  Sparkles,
} from "lucide-react";
import { cn } from "@/lib/utils";
import { Logo } from "@/components/shared/logo";
import { Button } from "@/components/ui/button";
import { Badge } from "@/components/ui/badge";
import { useSellerStoreId } from "@/hooks/use-seller-store";
import { useAuthSession } from "@/hooks/use-auth-session";
import { storesService, authService, billingService } from "@/services";

const NAV_ITEMS = [
  { href: "/dashboard", label: "Overview", icon: LayoutDashboard },
  { href: "/dashboard/products", label: "Products", icon: Package },
  { href: "/dashboard/orders", label: "Orders", icon: ClipboardList },
  { href: "/dashboard/payouts", label: "Payouts", icon: Wallet },
  { href: "/dashboard/settings", label: "Store settings", icon: Settings },
];

export function DashboardSidebarContent() {
  const pathname = usePathname();
  const router = useRouter();
  const queryClient = useQueryClient();
  const storeId = useSellerStoreId();
  const { session } = useAuthSession();
  const { data: store } = useQuery({
    queryKey: ["store", storeId],
    queryFn: () => storesService.getStoreById(storeId),
    staleTime: 0,
  });
  const { data: planInfo } = useQuery({
    queryKey: ["seller-plan"],
    queryFn: () => billingService.getMyPlan(),
  });
  const isPro = planInfo?.plan === "pro";

  async function handleSignOut() {
    await authService.logout();
    queryClient.clear();
    router.push("/login");
  }

  return (
    <div className="flex h-full flex-col">
      <div className="p-4">
        <Logo href="/dashboard" />
      </div>

      <nav className="flex-1 space-y-1 px-2">
        {NAV_ITEMS.map((item) => {
          const isActive =
            item.href === "/dashboard" ? pathname === item.href : pathname.startsWith(item.href);
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
            <Store className="size-4" />
          </span>
          <div className="min-w-0 flex-1">
            <p className="truncate text-sm font-medium">{store?.name ?? "Your store"}</p>
            <p className="text-muted-foreground text-xs">{store?.address.city ?? ""}</p>
          </div>
          {isPro ? (
            <Badge className="border-0 bg-amber-100 text-amber-800 shrink-0 dark:bg-amber-950 dark:text-amber-300">
              <Sparkles className="size-3" /> Pro
            </Badge>
          ) : (
            <Link
              href="/dashboard/settings"
              className="text-primary shrink-0 text-xs font-medium underline-offset-4 hover:underline"
            >
              Upgrade
            </Link>
          )}
        </div>
        {store?.verificationStatus === "active" ? (
          <Link
            href={`/stores/${store.slug}`}
            className="text-muted-foreground hover:text-foreground flex items-center gap-1.5 text-xs font-medium"
          >
            View storefront <ExternalLink className="size-3" />
          </Link>
        ) : (
          <p className="text-muted-foreground text-xs">Storefront hidden until approved</p>
        )}
        {session.email ? (
          <p className="text-muted-foreground truncate text-xs">Signed in as {session.email}</p>
        ) : null}
        <Button type="button" variant="outline" size="sm" className="w-full" onClick={handleSignOut}>
          <LogOut className="size-3.5" /> Sign out
        </Button>
      </div>
    </div>
  );
}
