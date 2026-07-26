import { Badge } from "@/components/ui/badge";
import { Logo } from "@/components/shared/logo";

export default function AdminLayout({ children }: { children: React.ReactNode }) {
  return (
    <div className="flex min-h-screen flex-col">
      <header className="flex items-center justify-between border-b px-4 py-4 sm:px-6">
        <Logo href="/admin" />
        <Badge variant="outline">Internal tool — no auth in this demo</Badge>
      </header>
      <main className="flex-1 bg-muted/20">{children}</main>
    </div>
  );
}
