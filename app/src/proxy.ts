import { NextResponse, type NextRequest } from "next/server";
import { SESSION_COOKIE } from "@/lib/session";

/**
 * Optimistic auth check for the seller dashboard and buyer account area,
 * following the Next.js pattern (proxy reads the cookie only — no DB — real
 * authorization still happens server-side wherever data is fetched).
 */
function readRole(request: NextRequest): string | null {
  const raw = request.cookies.get(SESSION_COOKIE)?.value;
  if (!raw) return null;
  try {
    const payload = JSON.parse(Buffer.from(raw, "base64url").toString("utf-8"));
    return typeof payload?.role === "string" ? payload.role : null;
  } catch {
    return null;
  }
}

export function proxy(request: NextRequest) {
  const { pathname } = request.nextUrl;
  const role = readRole(request);
  const isSeller = role === "seller";
  const isBuyer = role === "buyer";
  const isAccountAuthPage = pathname === "/account/login" || pathname === "/account/register";

  if (pathname.startsWith("/dashboard") && !isSeller) {
    const loginUrl = new URL("/login", request.url);
    loginUrl.searchParams.set("redirectTo", pathname);
    return NextResponse.redirect(loginUrl);
  }

  if (pathname === "/login" && isSeller) {
    return NextResponse.redirect(new URL("/dashboard", request.url));
  }

  if (pathname.startsWith("/account") && !isAccountAuthPage && !isBuyer) {
    const loginUrl = new URL("/account/login", request.url);
    loginUrl.searchParams.set("redirectTo", pathname);
    return NextResponse.redirect(loginUrl);
  }

  if (isAccountAuthPage && isBuyer) {
    return NextResponse.redirect(new URL("/account", request.url));
  }

  return NextResponse.next();
}

export const config = {
  matcher: ["/dashboard/:path*", "/login", "/account/:path*"],
};
