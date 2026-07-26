import { NextResponse } from "next/server";
import { getSession } from "@/lib/session";

/**
 * Lets client components (the site header's account link, in particular)
 * know whether a buyer is signed in, without forcing every page under the
 * shared marketplace layout into dynamic rendering — reading the session
 * cookie in a shared Server Component layout would otherwise opt every
 * page beneath it (home, search, store pages) out of static generation.
 * A tiny route handler keeps that cost local to the one client-side fetch
 * that actually needs it.
 */
export async function GET() {
  const session = await getSession();
  if (session?.role !== "buyer") {
    return NextResponse.json({ signedIn: false });
  }
  return NextResponse.json({ signedIn: true, name: session.name });
}
