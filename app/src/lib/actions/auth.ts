"use server";

import { redirect } from "next/navigation";
import { createSession, deleteSession } from "@/lib/session";
import { storesService } from "@/services";

/** The seed store any email signs into via the mock seller login — see docs/features/seller-auth.md. */
const DEMO_SELLER_STORE_SLUG = "ceylon-spice-co";

/** Used by the plain `<form action={signInAsSeller}>` on /login — no client JS required. */
export async function signInAsSeller(formData: FormData): Promise<void> {
  const email = String(formData.get("email") ?? "").trim();
  const redirectTo = String(formData.get("redirectTo") ?? "/dashboard");

  if (!email) {
    redirect(`/login?error=missing-email&redirectTo=${encodeURIComponent(redirectTo)}`);
  }

  // Mock auth: any email signs in as the single demo seller. A real
  // implementation would verify credentials against a user record here.
  // Server Actions run in Node.js, but that's no longer a constraint on
  // reaching store data — storesService now calls the real backend over
  // HTTP, which works from either the server or the browser.
  const demoStore = await storesService.getStoreBySlug(DEMO_SELLER_STORE_SLUG);
  if (!demoStore) {
    redirect(`/login?error=missing-email&redirectTo=${encodeURIComponent(redirectTo)}`);
  }
  await createSession({ role: "seller", storeId: demoStore.id, email });
  redirect(redirectTo || "/dashboard");
}

/**
 * Establishes the session for a newly-onboarded seller, after the client
 * has already created the Store + StoreSettings rows via storesService.
 */
export async function createSellerSession(storeId: string, email: string): Promise<void> {
  await createSession({ role: "seller", storeId, email });
}

export async function signOutSeller(): Promise<void> {
  await deleteSession();
  redirect("/login");
}

/**
 * Establishes the session for a buyer, after the client has already
 * resolved (registered or looked up) a real Buyer row.
 */
export async function createBuyerSession(buyerId: string, name: string, email: string): Promise<void> {
  await createSession({ role: "buyer", buyerId, name, email });
}

export async function signOutBuyer(): Promise<void> {
  await deleteSession();
  redirect("/");
}
