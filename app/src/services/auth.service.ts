import { apiClient } from "@/lib/api-client";

export interface AuthSession {
  signedIn: boolean;
  role?: "buyer" | "seller" | "admin";
  email?: string;
  name?: string;
}

/**
 * The only place in the frontend that talks to the backend's Cognito-backed
 * auth endpoints. Every call here sets/clears httpOnly cookies as a side
 * effect of the backend's response — there is no local session to manage.
 */

/**
 * POST /api/auth/register — creates a real Cognito account and signs in
 * immediately. `accountType` must match which page is calling this
 * ("buyer" for account/register, "seller" for register) — buyer and
 * seller are mutually exclusive identities, see backend
 * AuthController.register()'s doc comment. A "seller" registration gets
 * no Cognito group at all until /onboarding grants "seller".
 */
export async function register(
  name: string,
  email: string,
  password: string,
  accountType: "buyer" | "seller",
): Promise<AuthSession> {
  return apiClient.post<AuthSession>("/api/auth/register", { name, email, password, accountType });
}

/** POST /api/auth/login */
export async function login(email: string, password: string): Promise<AuthSession> {
  return apiClient.post<AuthSession>("/api/auth/login", { email, password });
}

/**
 * POST /api/auth/refresh — reissues the access-token cookie from the
 * refresh-token cookie. Needed right after an action that changes Cognito
 * group membership (seller onboarding) since the old access token's
 * cognito:groups claim won't reflect it until a fresh token is issued.
 */
export async function refresh(): Promise<AuthSession> {
  return apiClient.post<AuthSession>("/api/auth/refresh");
}

/** POST /api/auth/logout — clears both cookies (and best-effort revokes the Cognito session server-side). */
export async function logout(): Promise<void> {
  await apiClient.post<AuthSession>("/api/auth/logout");
}

/** GET /api/auth/session — the only way to learn "am I signed in, and as what" (the tokens themselves are httpOnly). */
export async function getSession(): Promise<AuthSession> {
  return apiClient.get<AuthSession>("/api/auth/session");
}
