import { ApiError, parseBody, toApiError } from '@storepilot/shared-api';

import { tokenStorage } from '@/lib/secure-storage';
import { useAuthStore } from '@/store/auth-store';
import type { AuthSessionResponse } from '@/api/types';

export { ApiError };

const BASE_URL = process.env.EXPO_PUBLIC_API_BASE_URL;

if (!BASE_URL) {
  throw new Error('EXPO_PUBLIC_API_BASE_URL is not set — copy .env.example to .env');
}

/** De-dupes concurrent 401s into a single in-flight refresh call rather than each retrying its own refresh. */
let refreshPromise: Promise<string | null> | null = null;

async function refreshAccessToken(): Promise<string | null> {
  if (refreshPromise) return refreshPromise;

  refreshPromise = (async () => {
    const refreshToken = await tokenStorage.getRefreshToken();
    if (!refreshToken) return null;

    try {
      const res = await fetch(`${BASE_URL}/api/auth/refresh`, {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({ refreshToken }),
      });
      if (!res.ok) return null;
      const body: AuthSessionResponse = await res.json();
      if (!body.accessToken) return null;
      await tokenStorage.setAccessToken(body.accessToken);
      return body.accessToken;
    } catch {
      return null;
    }
  })();

  try {
    return await refreshPromise;
  } finally {
    refreshPromise = null;
  }
}

type RequestOptions = Omit<RequestInit, 'body'> & {
  body?: unknown;
  /** Set true for the refresh call itself and login/register — skips attaching a (possibly stale) bearer header and the 401-retry loop, since those calls don't hold a session yet. */
  skipAuth?: boolean;
};

/**
 * Thin fetch wrapper: attaches the bearer token captured at login/refresh
 * (see AuthController.completeLogin/refresh on the backend — this is the
 * header-based counterpart to the web app's httpOnly-cookie transport),
 * retries exactly once on a 401 after a token refresh, and throws a typed
 * ApiError matching the backend's { error: { code, message, fields } }
 * shape on any other failure.
 */
export async function apiFetch<T>(path: string, options: RequestOptions = {}): Promise<T> {
  const { skipAuth, body, headers, ...rest } = options;

  const doFetch = async (): Promise<Response> => {
    const finalHeaders: Record<string, string> = {
      'Content-Type': 'application/json',
      ...(headers as Record<string, string> | undefined),
    };
    if (!skipAuth) {
      const accessToken = await tokenStorage.getAccessToken();
      if (accessToken) finalHeaders.Authorization = `Bearer ${accessToken}`;
    }
    return fetch(`${BASE_URL}${path}`, {
      ...rest,
      headers: finalHeaders,
      body: body !== undefined ? JSON.stringify(body) : undefined,
    });
  };

  let res = await doFetch();

  if (res.status === 401 && !skipAuth) {
    const newAccessToken = await refreshAccessToken();
    if (newAccessToken) {
      res = await doFetch();
    } else {
      await useAuthStore.getState().signOut();
    }
  }

  if (!res.ok) throw await toApiError(res);
  return parseBody<T>(res);
}

/** Prefixes a backend-relative path (e.g. a stored image path) with the API base URL — mirrors the web app's toApiUrl. */
export function toApiUrl(path: string): string {
  return `${BASE_URL}${path}`;
}

/** Safe to call on a value that might already be absolute — product images can be either a backend-relative path (FileStorageService, local dev) or an already-full URL (S3 presigned, or a picsum.photos seed placeholder). Mirrors the web app's resolveAssetUrl. */
export function resolveAssetUrl(url: string): string {
  return url.startsWith('http') ? url : toApiUrl(url);
}

/** Builds a query string from an object, skipping undefined/empty values — mirrors the web app's toQueryString. */
export function toQueryString(params: Record<string, string | number | undefined>): string {
  const usp = new URLSearchParams();
  for (const [key, value] of Object.entries(params)) {
    if (value !== undefined && value !== '') usp.set(key, String(value));
  }
  const qs = usp.toString();
  return qs ? `?${qs}` : '';
}

/** For multipart/form-data requests (product/image uploads) — the browser/RN runtime sets the correct boundary header itself, so Content-Type must NOT be set manually here. */
export async function apiFetchForm<T>(path: string, form: FormData, method: 'POST' | 'PATCH' = 'POST'): Promise<T> {
  const doFetch = async (): Promise<Response> => {
    const accessToken = await tokenStorage.getAccessToken();
    const headers: Record<string, string> = {};
    if (accessToken) headers.Authorization = `Bearer ${accessToken}`;
    return fetch(`${BASE_URL}${path}`, { method, headers, body: form });
  };

  let res = await doFetch();
  if (res.status === 401) {
    const newAccessToken = await refreshAccessToken();
    if (newAccessToken) {
      res = await doFetch();
    } else {
      await useAuthStore.getState().signOut();
    }
  }

  if (!res.ok) throw await toApiError(res);
  return parseBody<T>(res);
}
