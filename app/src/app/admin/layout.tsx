import { Logo } from "@/components/shared/logo";

/** Gated by proxy.ts (requires the admin Cognito role, except /admin/login itself). */
export default function AdminLayout({ children }: { children: React.ReactNode }) {
  return (
    <div className="flex min-h-screen flex-col">
      <header className="flex items-center justify-between border-b px-4 py-4 sm:px-6">
        <Logo href="/admin" />
      </header>
      <main className="flex-1 bg-muted/20">{children}</main>
    </div>
  );
}
