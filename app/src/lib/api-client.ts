/**
 * Thin fetch wrapper around the Spring Boot backend (see ../../../backend).
 * Every function in src/services/*.service.ts goes through this instead of
 * mockDb now — this is the one file that knows the backend's base URL and
 * error shape (see docs/api-contracts.md#error-response-convention-recommended).
 */

const API_BASE_URL = process.env.NEXT_PUBLIC_API_URL ?? "http://localhost:8080";

interface ApiErrorBody {
  error: {
    code: string;
    message: string;
    fields?: Record<string, string>;
  };
}

export class ApiRequestError extends Error {
  status: number;
  code?: string;
  fields?: Record<string, string>;

  constructor(message: string, status: number, code?: string, fields?: Record<string, string>) {
    super(message);
    this.name = "ApiRequestError";
    this.status = status;
    this.code = code;
    this.fields = fields;
  }
}

async function toApiError(res: Response): Promise<ApiRequestError> {
  try {
    const body = (await res.json()) as ApiErrorBody;
    return new ApiRequestError(
      body.error?.message ?? `Request failed with status ${res.status}`,
      res.status,
      body.error?.code,
      body.error?.fields,
    );
  } catch {
    return new ApiRequestError(`Request failed with status ${res.status}`, res.status);
  }
}

async function parseBody<T>(res: Response): Promise<T> {
  if (res.status === 204) return undefined as T;
  const text = await res.text();
  return (text ? JSON.parse(text) : undefined) as T;
}

async function request<T>(path: string, init?: RequestInit): Promise<T> {
  const res = await fetch(`${API_BASE_URL}${path}`, {
    ...init,
    headers: { "Content-Type": "application/json", ...init?.headers },
  });
  if (!res.ok) throw await toApiError(res);
  return parseBody<T>(res);
}

/** For GET endpoints where the mock's contract is "return null if not found" — treats a 404 as null instead of throwing. */
async function getOrNull<T>(path: string): Promise<T | null> {
  const res = await fetch(`${API_BASE_URL}${path}`);
  if (res.status === 404) return null;
  if (!res.ok) throw await toApiError(res);
  return parseBody<T | null>(res);
}

/** For file uploads — no Content-Type header, so the browser sets the multipart boundary itself. */
async function postForm<T>(path: string, formData: FormData): Promise<T> {
  const res = await fetch(`${API_BASE_URL}${path}`, { method: "POST", body: formData });
  if (!res.ok) throw await toApiError(res);
  return parseBody<T>(res);
}

export const apiClient = {
  get: <T>(path: string) => request<T>(path),
  getOrNull: <T>(path: string) => getOrNull<T>(path),
  post: <T>(path: string, body?: unknown) =>
    request<T>(path, { method: "POST", body: body !== undefined ? JSON.stringify(body) : undefined }),
  postForm: <T>(path: string, formData: FormData) => postForm<T>(path, formData),
  patch: <T>(path: string, body?: unknown) =>
    request<T>(path, { method: "PATCH", body: body !== undefined ? JSON.stringify(body) : undefined }),
  delete: <T>(path: string) => request<T>(path, { method: "DELETE" }),
};

/** Prefixes a backend-relative path (e.g. a stored receipt URL) with the API base URL. */
export function toApiUrl(path: string): string {
  return `${API_BASE_URL}${path}`;
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
