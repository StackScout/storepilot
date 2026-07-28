"use client";

import { createContext, useContext } from "react";
import { useQuery } from "@tanstack/react-query";
import { getStates, type PlatformConfig, type StateOption } from "@/lib/platform-config";

/**
 * Populated once, server-side, by app/layout.tsx (a single cached fetch —
 * see lib/platform-config.ts) and handed down here so every Client
 * Component reads it synchronously via context instead of each doing its
 * own fetch/loading state. Server Components can't consume context, so
 * they call getPlatformConfig() directly instead (see site-footer.tsx,
 * the home page).
 */
const PlatformConfigContext = createContext<PlatformConfig | null>(null);

export function PlatformConfigProvider({
  config,
  children,
}: {
  config: PlatformConfig;
  children: React.ReactNode;
}) {
  return <PlatformConfigContext.Provider value={config}>{children}</PlatformConfigContext.Provider>;
}

export function usePlatformConfig(): PlatformConfig {
  const config = useContext(PlatformConfigContext);
  if (!config) throw new Error("usePlatformConfig must be used within PlatformConfigProvider");
  return config;
}

/** State/province dropdown options for the running platform's own country (see GET /api/states). Rarely changes, so cached indefinitely for the session. */
export function useStates() {
  return useQuery<StateOption[]>({
    queryKey: ["states"],
    queryFn: getStates,
    staleTime: Infinity,
  });
}
