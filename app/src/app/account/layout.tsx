import Link from "next/link";
import { Logo } from "@/components/shared/logo";

export default function AccountLayout({ children }: { children: React.ReactNode }) {
  return (
    <div className="flex min-h-screen flex-col">
      <header className="flex items-center justify-between border-b px-4 py-4 sm:px-6">
        <Logo />
        <Link href="/" className="text-muted-foreground text-sm hover:underline">
          Back to marketplace
        </Link>
      </header>
      <main className="flex-1">{children}</main>
    </div>
  );
}
