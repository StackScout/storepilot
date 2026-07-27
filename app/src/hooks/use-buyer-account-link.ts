"use client";

import { useAuthSession } from "./use-auth-session";

/**
 * Client-side check for whether a buyer is signed in, used only to decide
 * what the header's account link says/points to. Deliberately a client
 * fetch (via useAuthSession, hitting the backend's GET /api/auth/session)
 * rather than reading the session in the shared marketplace layout — a
 * Server Component layout resolving auth there would force every page
 * beneath it (home, search, store pages) into dynamic rendering, which
 * would undo their static generation.
 */
export function useBuyerAccountLink() {
  const { session } = useAuthSession();
  return { buyerName: session.signedIn && session.role === "buyer" ? session.name : undefined };
}
