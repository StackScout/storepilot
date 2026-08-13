"use client";

import { useRouter } from "next/navigation";
import { useQueryClient } from "@tanstack/react-query";
import { authService } from "@/services";

/**
 * Shared sign-out action: clears the auth cookies, wipes all cached
 * queries (session, buyer profile, orders, etc.), and redirects to
 * [redirectTo] — each account type's own sign-in page, since buyer/seller/
 * admin are separate login flows (see AuthController.register's doc
 * comment on why they're mutually exclusive accounts).
 */
export function useSignOut(redirectTo: string = "/account/login") {
  const router = useRouter();
  const queryClient = useQueryClient();

  return async function signOut() {
    await authService.logout();
    queryClient.clear();
    router.push(redirectTo);
  };
}
