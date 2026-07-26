import { getSession } from "@/lib/session";
import { AccountView } from "./account-view";

export default async function AccountPage() {
  const session = await getSession();
  // Defensive fallback only — proxy.ts already redirects unauthenticated
  // visitors before this page ever renders.
  if (session?.role !== "buyer") return null;

  return <AccountView buyerId={session.buyerId} name={session.name} email={session.email} />;
}
