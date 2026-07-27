"use client";

import { useQuery } from "@tanstack/react-query";
import { authService } from "@/services";
import type { AuthSession } from "@/services/auth.service";

/**
 * The one place that asks "am I signed in, and as what" — the auth cookies
 * are httpOnly, so this is the only way any client component can learn its
 * own auth state. React Query dedupes this across every component that
 * calls the hook.
 */
export function useAuthSession() {
  const { data, isLoading } = useQuery<AuthSession>({
    queryKey: ["auth-session"],
    queryFn: () => authService.getSession(),
    staleTime: 30_000,
  });

  return { session: data ?? { signedIn: false }, isLoading };
}
