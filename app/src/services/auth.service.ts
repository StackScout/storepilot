import { apiClient } from "@/lib/api-client";

export interface AuthSession {
  signedIn: boolean;
  role?: "buyer" | "seller" | "admin";
  email?: string;
  name?: string;
  /** Set (with signedIn: false) when login() hit a TOTP challenge instead of completing — see verifyMfaChallenge below. */
  mfaRequired?: boolean;
  mfaSession?: string;
}

export interface MfaSetup {
  /** Raw base32 secret — shown as a manual-entry fallback alongside the QR code. */
  secret: string;
  /** otpauth:// URI to render as a QR code (see MfaEnrollDialog). */
  otpauthUri: string;
}

/**
 * The only place in the frontend that talks to the backend's Cognito-backed
 * auth endpoints. Every call here sets/clears httpOnly cookies as a side
 * effect of the backend's response — there is no local session to manage.
 */

export interface RegisterResult {
  email: string;
  name: string;
}

/**
 * POST /api/auth/register — creates a real Cognito account, but does NOT
 * sign in: the account starts email-unverified, and this only triggers a
 * verification code email (see verifyEmail below). `accountType` must
 * match which page is calling this ("buyer" for account/register, "seller"
 * for register) — buyer and seller are mutually exclusive identities, see
 * backend AuthController.register()'s doc comment. A "seller" registration
 * gets no Cognito group at all until /onboarding grants "seller".
 */
export async function register(
  name: string,
  email: string,
  password: string,
  accountType: "buyer" | "seller",
): Promise<RegisterResult> {
  return apiClient.post<RegisterResult>("/api/auth/register", { name, email, password, accountType });
}

/**
 * POST /api/auth/verify-email — confirms the code emailed by register()/
 * resendVerificationCode() and marks the account verified. Doesn't sign in
 * by itself; callers pair this with login() using the password they still
 * hold in memory (never persisted, never sent to this endpoint).
 */
export async function verifyEmail(email: string, code: string): Promise<void> {
  await apiClient.post<void>("/api/auth/verify-email", { email, code });
}

/** POST /api/auth/resend-verification-code */
export async function resendVerificationCode(email: string): Promise<void> {
  await apiClient.post<void>("/api/auth/resend-verification-code", { email });
}

/**
 * POST /api/auth/login — throws ApiRequestError with code
 * "EMAIL_NOT_VERIFIED" (see api-client.ts) if the account exists but hasn't
 * completed email verification yet. If the account has TOTP MFA enrolled,
 * this returns { signedIn: false, mfaRequired: true, mfaSession } instead
 * of a completed session — callers must prompt for a code and call
 * verifyMfaChallenge() with it to actually finish signing in.
 */
export async function login(email: string, password: string): Promise<AuthSession> {
  return apiClient.post<AuthSession>("/api/auth/login", { email, password });
}

/** POST /api/auth/mfa/challenge — completes a login that returned mfaRequired: true. */
export async function verifyMfaChallenge(email: string, session: string, code: string): Promise<AuthSession> {
  return apiClient.post<AuthSession>("/api/auth/mfa/challenge", { email, session, code });
}

/** GET /api/auth/mfa/status — whether the signed-in caller currently has TOTP MFA enabled. */
export async function getMfaStatus(): Promise<{ enabled: boolean }> {
  return apiClient.get<{ enabled: boolean }>("/api/auth/mfa/status");
}

/** POST /api/auth/mfa/setup — starts (or restarts) TOTP enrollment for the signed-in caller; nothing is enabled until verifyMfaSetup() below succeeds. */
export async function setupMfa(): Promise<MfaSetup> {
  return apiClient.post<MfaSetup>("/api/auth/mfa/setup");
}

/** POST /api/auth/mfa/verify — confirms the code from an authenticator app matches the secret from setupMfa(), then actually turns TOTP on. */
export async function verifyMfaSetup(code: string): Promise<void> {
  await apiClient.post<void>("/api/auth/mfa/verify", { code });
}

/** POST /api/auth/mfa/disable — turns TOTP back off for the signed-in caller. */
export async function disableMfa(): Promise<void> {
  await apiClient.post<void>("/api/auth/mfa/disable");
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

/**
 * POST /api/staff/accept-invite — redeems a store-owner-issued invite
 * token, creating the Cognito user + Seller + StoreStaffMember row and
 * signing the new staff member straight in. Unlike register(), there's no
 * separate verify-email step — the invite link itself already proved the
 * invitee controls that inbox.
 */
export async function acceptStaffInvite(token: string, password: string, name?: string): Promise<AuthSession> {
  return apiClient.post<AuthSession>("/api/staff/accept-invite", { token, password, name });
}
