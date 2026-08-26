/**
 * Thin fetch wrapper around the Spring Boot backend (see ../../../backend).
 * Every function in src/services/*.service.ts goes through this instead of
 * mockDb now — this is the one file that knows the backend's base URL and
 * error shape (see docs/api-contracts.md#error-response-convention-recommended).
 *
 * `credentials: "include"` on every call is required for the backend's
 * httpOnly auth cookies (see backend's CookieBearerTokenResolver) to be
 * sent at all — in production this is same-origin via the Caddy reverse
 * proxy, so it's a no-op there, but local dev is cross-origin
 * (localhost:3000 -> localhost:8080) and needs it explicitly.
 */

import { ApiError, parseBody, toApiError } from "@storepilot/shared-api";

const API_BASE_URL = process.env.NEXT_PUBLIC_API_URL ?? "http://localhost:8080";

export { ApiError as ApiRequestError };

/**
 * A 401 usually just means the short-lived access-token cookie expired —
 * silently refresh it once (via the refresh-token cookie) and retry, rather
 * than bouncing the user out of whatever they were doing. Never recurses:
 * refresh itself, and the /api/auth/* endpoints in general, skip this
 * entirely (a failed login attempt is a real 401, not an expired session).
 */
let refreshInFlight: Promise<boolean> | null = null;

async function tryRefresh(): Promise<boolean> {
  if (!refreshInFlight) {
    refreshInFlight = fetch(`${API_BASE_URL}/api/auth/refresh`, { method: "POST", credentials: "include" })
      .then((res) => res.ok)
      .catch(() => false)
      .finally(() => {
        refreshInFlight = null;
      });
  }
  return refreshInFlight;
}

async function fetchWithRefresh(path: string, init: RequestInit): Promise<Response> {
  const res = await fetch(`${API_BASE_URL}${path}`, { ...init, credentials: "include" });
  if (res.status !== 401 || path.startsWith("/api/auth/")) return res;
  const refreshed = await tryRefresh();
  if (!refreshed) return res;
  return fetch(`${API_BASE_URL}${path}`, { ...init, credentials: "include" });
}

async function request<T>(path: string, init?: RequestInit): Promise<T> {
  const res = await fetchWithRefresh(path, {
    ...init,
    headers: { "Content-Type": "application/json", ...init?.headers },
  });
  if (!res.ok) throw await toApiError(res);
  return parseBody<T>(res);
}

/** For GET endpoints where the mock's contract is "return null if not found" — treats a 404 as null instead of throwing. */
async function getOrNull<T>(path: string): Promise<T | null> {
  const res = await fetchWithRefresh(path, {});
  if (res.status === 404) return null;
  if (!res.ok) throw await toApiError(res);
  return parseBody<T | null>(res);
}

/** For file uploads — no Content-Type header, so the browser sets the multipart boundary itself. */
async function postForm<T>(path: string, formData: FormData): Promise<T> {
  const res = await fetchWithRefresh(path, { method: "POST", body: formData });
  if (!res.ok) throw await toApiError(res);
  return parseBody<T>(res);
}

/** Same as postForm but PATCH — used where a multipart update replaces (rather than creates) a resource. */
async function patchForm<T>(path: string, formData: FormData): Promise<T> {
  const res = await fetchWithRefresh(path, { method: "PATCH", body: formData });
  if (!res.ok) throw await toApiError(res);
  return parseBody<T>(res);
}

export const apiClient = {
  get: <T>(path: string) => request<T>(path),
  getOrNull: <T>(path: string) => getOrNull<T>(path),
  post: <T>(path: string, body?: unknown) =>
    request<T>(path, { method: "POST", body: body !== undefined ? JSON.stringify(body) : undefined }),
  postForm: <T>(path: string, formData: FormData) => postForm<T>(path, formData),
  patchForm: <T>(path: string, formData: FormData) => patchForm<T>(path, formData),
  patch: <T>(path: string, body?: unknown) =>
    request<T>(path, { method: "PATCH", body: body !== undefined ? JSON.stringify(body) : undefined }),
  put: <T>(path: string, body?: unknown) =>
    request<T>(path, { method: "PUT", body: body !== undefined ? JSON.stringify(body) : undefined }),
  delete: <T>(path: string) => request<T>(path, { method: "DELETE" }),
};

/** Prefixes a backend-relative path (e.g. a stored receipt URL) with the API base URL. */
export function toApiUrl(path: string): string {
  return `${API_BASE_URL}${path}`;
}

/**
 * Same idea as toApiUrl, but safe to call on a value that might already be
 * absolute — product images and uploaded documents can be either a
 * backend-relative path (FileStorageService, local dev) or an already-full
 * URL (S3 presigned URL under the aws profile, or a picsum.photos seed-data
 * placeholder that never went through upload at all).
 */
export function resolveAssetUrl(url: string): string {
  return url.startsWith("http") ? url : toApiUrl(url);
}

/** Builds a query string from an object, skipping undefined/empty values. */
export function toQueryString(params: Record<string, string | number | undefined>): string {
  const usp = new URLSearchParams();
  for (const [key, value] of Object.entries(params)) {
    if (value !== undefined && value !== "") usp.set(key, String(value));
  }
  const qs = usp.toString();
  return qs ? `?${qs}` : "";
}
