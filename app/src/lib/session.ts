import "server-only";
import { cookies } from "next/headers";

export const SESSION_COOKIE = "islandcart_session";
const SESSION_MAX_AGE = 60 * 60 * 24 * 7; // 7 days

export type SessionPayload =
  | { role: "seller"; storeId: string; email: string }
  | { role: "buyer"; buyerId: string; name: string; email: string };

/**
 * Mock session store: the payload is plain base64 JSON in the cookie, not
 * signed or encrypted. That's fine for this demo (one mock seller, no real
 * user data) — before this ever touches real accounts, swap this for a
 * signed/encrypted session (e.g. via `jose`) as described in the Next.js
 * authentication guide.
 */
export async function createSession(payload: SessionPayload): Promise<void> {
  const cookieStore = await cookies();
  const value = Buffer.from(JSON.stringify(payload)).toString("base64url");
  cookieStore.set(SESSION_COOKIE, value, {
    httpOnly: true,
    sameSite: "lax",
    path: "/",
    maxAge: SESSION_MAX_AGE,
  });
}

export async function getSession(): Promise<SessionPayload | null> {
  const cookieStore = await cookies();
  const raw = cookieStore.get(SESSION_COOKIE)?.value;
  if (!raw) return null;
  try {
    return JSON.parse(Buffer.from(raw, "base64url").toString("utf-8")) as SessionPayload;
  } catch {
    return null;
  }
}

export async function deleteSession(): Promise<void> {
  const cookieStore = await cookies();
  cookieStore.delete(SESSION_COOKIE);
}
