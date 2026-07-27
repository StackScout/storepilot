"use client";

import { useRouter } from "next/navigation";
import { useQueryClient } from "@tanstack/react-query";
import { authService } from "@/services";

/** Shared sign-out action: clears the auth cookies, wipes all cached queries (session, buyer profile, orders, etc.), and sends the buyer back to login. */
export function useSignOut() {
  const router = useRouter();
  const queryClient = useQueryClient();

  return async function signOut() {
    await authService.logout();
    queryClient.clear();
    router.push("/account/login");
  };
}
