"use client";

import { useQuery } from "@tanstack/react-query";

interface BuyerSessionResponse {
  signedIn: boolean;
  name?: string;
}

/**
 * Client-side check for whether a buyer is signed in, used only to decide
 * what the header's account link says/points to. Deliberately a client
 * fetch (via /api/account/session) rather than reading the session cookie
 * in the shared marketplace layout — a Server Component layout calling
 * getSession() would force every page beneath it (home, search, store
 * pages) into dynamic rendering, which would undo their static generation.
 * React Query dedupes this across every component that calls the hook.
 */
export function useBuyerAccountLink() {
  const { data } = useQuery({
    queryKey: ["buyer-account-link"],
    queryFn: async (): Promise<BuyerSessionResponse> => {
      const res = await fetch("/api/account/session");
      return res.json();
    },
    staleTime: 30_000,
  });

  return { buyerName: data?.signedIn ? data.name : undefined };
}
