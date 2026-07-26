import { Logo } from "@/components/shared/logo";

export default function OnboardingLayout({ children }: { children: React.ReactNode }) {
  return (
    <div className="flex min-h-screen flex-col">
      <header className="border-b px-4 py-4 sm:px-6">
        <Logo />
      </header>
      <main className="flex-1">{children}</main>
    </div>
  );
}
