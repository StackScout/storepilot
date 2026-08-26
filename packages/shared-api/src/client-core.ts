/**
 * Shared pieces of the HTTP layer for both frontends (React Native/Expo
 * mobile app and the Next.js web app) talking to the same Spring Boot
 * backend: the error type/shape and response-body parsing, which are
 * byte-for-byte identical between the two apps.
 *
 * Deliberately NOT shared here: the actual fetch-with-401-retry
 * orchestration. The two apps use genuinely different auth transports (web:
 * httpOnly cookies via credentials:"include"; mobile: a Bearer token from
 * expo-secure-store) *and* genuinely different rules for which calls skip
 * the retry — mobile distinguishes call-by-call via an explicit `skipAuth`
 * flag (e.g. GET /api/auth/session still retries after a refresh, but
 * POST /api/auth/login never does, even though both are under /api/auth/*),
 * while web decides purely from the URL path. Forcing both through one
 * generic orchestration risks silently changing that behavior in one app
 * without an easy way to verify it — each app keeps its own small
 * `fetchWithRefresh`-equivalent, just built on the shared error/parsing
 * primitives below.
 */

export type ApiErrorBody = {
  error: {
    code: string;
    message: string;
    fields?: Record<string, string> | null;
  };
};

export class ApiError extends Error {
  constructor(
    public status: number,
    public code: string,
    message: string,
    public fields?: Record<string, string> | null,
  ) {
    super(message);
    this.name = "ApiError";
  }
}

/** Parses a non-ok Response into an ApiError, matching the backend's { error: { code, message, fields } } shape. Falls back to a generic message if the body isn't JSON (e.g. a proxy-level 502). */
export async function toApiError(res: Response): Promise<ApiError> {
  try {
    const body = (await res.json()) as ApiErrorBody;
    return new ApiError(
      res.status,
      body.error?.code ?? "UNKNOWN_ERROR",
      body.error?.message ?? `Request failed with status ${res.status}`,
      body.error?.fields,
    );
  } catch {
    return new ApiError(res.status, "UNKNOWN_ERROR", `Request failed with status ${res.status}`);
  }
}

/** Parses a successful Response body: 204 -> undefined, empty body -> undefined, otherwise JSON. */
export async function parseBody<T>(res: Response): Promise<T> {
  if (res.status === 204) return undefined as T;
  const text = await res.text();
  return (text ? JSON.parse(text) : undefined) as T;
}
