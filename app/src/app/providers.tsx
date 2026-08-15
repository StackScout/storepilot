"use client";

import { useState } from "react";
import { QueryClient, QueryClientProvider } from "@tanstack/react-query";
import { ThemeProvider } from "next-themes";
import { Toaster } from "@/components/ui/sonner";
import { PlatformConfigProvider } from "@/hooks/use-platform-config";
import type { PlatformConfig } from "@/lib/platform-config";

export function Providers({ config, children }: { config: PlatformConfig; children: React.ReactNode }) {
  const [queryClient] = useState(
    () =>
      new QueryClient({
        defaultOptions: {
          queries: {
            staleTime: 30_000,
            refetchOnWindowFocus: false,
          },
        },
      }),
  );

  return (
    <ThemeProvider attribute="class" defaultTheme="system" enableSystem disableTransitionOnChange>
      <QueryClientProvider client={queryClient}>
        <PlatformConfigProvider config={config}>
          {children}
          <Toaster position="top-center" richColors />
        </PlatformConfigProvider>
      </QueryClientProvider>
    </ThemeProvider>
  );
}
