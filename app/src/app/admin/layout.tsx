"use client";

import { usePathname } from "next/navigation";
import { Logo } from "@/components/shared/logo";
import { AdminSidebarContent } from "@/components/admin/admin-sidebar";
import { AdminMobileNav } from "@/components/admin/admin-mobile-nav";
import { UserAccountMenu } from "@/components/shared/user-account-menu";
import { useAuthSession } from "@/hooks/use-auth-session";

/**
 * Gated by proxy.ts (requires the admin Cognito role, except /admin/login
 * itself — which is why this stays a single shared layout with a pathname
 * check, rather than a route-group split: /admin/login needs to render
 * before there's any session to build a sidebar footer from).
 */
export default function AdminLayout({ children }: { children: React.ReactNode }) {
  const pathname = usePathname();
  const isLoginPage = pathname === "/admin/login";
  const { session } = useAuthSession();

  if (isLoginPage) {
    return (
      <div className="flex min-h-screen flex-col">
        <header className="flex items-center justify-between border-b px-4 py-4 sm:px-6">
          <Logo href="/admin" />
        </header>
        <main className="flex-1 bg-muted/20">{children}</main>
      </div>
    );
  }

  return (
    <div className="bg-muted/20 flex min-h-screen">
      <aside className="bg-background hidden w-64 shrink-0 border-r lg:block">
        <AdminSidebarContent />
      </aside>

      <div className="flex min-w-0 flex-1 flex-col">
        <header className="bg-background/95 sticky top-0 z-30 flex items-center gap-3 border-b px-4 py-3 backdrop-blur lg:px-8">
          <AdminMobileNav />
          <span className="font-semibold lg:hidden">Platform admin</span>
          {session.signedIn ? (
            <UserAccountMenu
              name={session.name ?? "Admin"}
              email={session.email}
              signOutRedirect="/admin/login"
              className="ml-auto"
            />
          ) : null}
        </header>
        <main className="flex-1 space-y-4 p-4 lg:p-8">{children}</main>
      </div>
    </div>
  );
}
