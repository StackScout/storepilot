import { NextResponse, type NextRequest } from "next/server";
import { createRemoteJWKSet, jwtVerify } from "jose";

const ACCESS_TOKEN_COOKIE = "storepilot_access_token";

const region = process.env.NEXT_PUBLIC_COGNITO_REGION;
const userPoolId = process.env.NEXT_PUBLIC_COGNITO_USER_POOL_ID;
const issuer = `https://cognito-idp.${region}.amazonaws.com/${userPoolId}`;
// createRemoteJWKSet caches the JWKS response internally, so this doesn't
// hit the network on every request — safe to call once per module load.
const jwks = createRemoteJWKSet(new URL(`${issuer}/.well-known/jwks.json`));

/**
 * Real JWT verification (signature + expiry + issuer), edge-safe via jose —
 * replaces the old unsigned-base64-cookie decode. Still an "optimistic"
 * check in the sense the Next.js docs describe: real authorization happens
 * again server-side (the Spring backend re-validates the same JWT on every
 * API call) — this is just fast route-gating so a signed-out visitor never
 * even renders the seller/buyer/admin shell.
 */
async function getAuthState(request: NextRequest): Promise<{ groups: string[]; authenticated: boolean }> {
  const token = request.cookies.get(ACCESS_TOKEN_COOKIE)?.value;
  if (!token) return { groups: [], authenticated: false };
  try {
    const { payload } = await jwtVerify(token, jwks, { issuer });
    const groups = payload["cognito:groups"];
    return {
      groups: Array.isArray(groups) ? groups.filter((g): g is string => typeof g === "string") : [],
      authenticated: true,
    };
  } catch {
    // Expired/invalid/malformed token — treat exactly like "signed out"
    // rather than erroring the request.
    return { groups: [], authenticated: false };
  }
}

export async function proxy(request: NextRequest) {
  const { pathname } = request.nextUrl;
  const { groups, authenticated } = await getAuthState(request);
  // Buyer, seller, and admin are mutually exclusive identities (see
  // backend AuthController.register()'s doc comment) — an account only
  // ever holds one of these groups.
  const isSeller = groups.includes("seller");
  const isBuyer = groups.includes("buyer");
  const isAdmin = groups.includes("admin");
  const isAccountAuthPage = pathname === "/account/login" || pathname === "/account/register";
  const isAdminLoginPage = pathname === "/admin/login";

  if (pathname.startsWith("/dashboard") && !isSeller) {
    const loginUrl = new URL("/login", request.url);
    loginUrl.searchParams.set("redirectTo", pathname);
    return NextResponse.redirect(loginUrl);
  }

  if ((pathname === "/login" || pathname === "/register") && isSeller) {
    return NextResponse.redirect(new URL("/dashboard", request.url));
  }

  // Onboarding requires *any* authenticated account — a freshly-registered
  // seller-track account has zero Cognito groups until this step grants
  // "seller", so this must check for a valid token, not group membership.
  if (pathname === "/onboarding" && !authenticated) {
    const loginUrl = new URL("/login", request.url);
    loginUrl.searchParams.set("redirectTo", pathname);
    return NextResponse.redirect(loginUrl);
  }

  if (pathname.startsWith("/account") && !isAccountAuthPage && !isBuyer) {
    const loginUrl = new URL("/account/login", request.url);
    loginUrl.searchParams.set("redirectTo", pathname);
    return NextResponse.redirect(loginUrl);
  }

  if (isAccountAuthPage && isBuyer) {
    return NextResponse.redirect(new URL("/account", request.url));
  }

  if (pathname.startsWith("/admin") && !isAdminLoginPage && !isAdmin) {
    const loginUrl = new URL("/admin/login", request.url);
    loginUrl.searchParams.set("redirectTo", pathname);
    return NextResponse.redirect(loginUrl);
  }

  if (isAdminLoginPage && isAdmin) {
    return NextResponse.redirect(new URL("/admin", request.url));
  }

  return NextResponse.next();
}

export const config = {
  matcher: ["/dashboard/:path*", "/login", "/register", "/onboarding", "/account/:path*", "/admin/:path*"],
};
