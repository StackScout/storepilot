import { apiClient, toQueryString } from "@/lib/api-client";
import type {
  Store,
  StoreApplicationInput,
  StoreCategory,
  StoreSettings,
  StoreVerificationStatus,
} from "@/types";

export interface StoreQueryParams {
  category?: StoreCategory;
  query?: string;
  limit?: number;
}

/** GET /stores — public marketplace listing: active stores only (backend-enforced). */
export async function listStores(params: StoreQueryParams = {}): Promise<Store[]> {
  const qs = toQueryString({ category: params.category, query: params.query });
  const results = await apiClient.get<Store[]>(`/api/stores${qs}`);
  return params.limit ? results.slice(0, params.limit) : results;
}

/** GET /stores/:slug — public storefront page: active stores only (backend-enforced). */
export async function getStoreBySlug(slug: string): Promise<Store | null> {
  return apiClient.getOrNull<Store>(`/api/stores/${slug}`);
}

/** GET /stores/id/:id — internal lookup, not gated by verification status. */
export async function getStoreById(id: string): Promise<Store | null> {
  return apiClient.getOrNull<Store>(`/api/stores/id/${id}`);
}

/** GET /stores/:id/settings */
export async function getStoreSettings(storeId: string): Promise<StoreSettings | null> {
  return apiClient.getOrNull<StoreSettings>(`/api/stores/${storeId}/settings`);
}

/** PATCH /stores/:id/settings — upsert, same as the backend service. */
export async function updateStoreSettings(
  storeId: string,
  patch: Partial<Omit<StoreSettings, "storeId">>,
): Promise<StoreSettings> {
  return apiClient.patch<StoreSettings>(`/api/stores/${storeId}/settings`, patch);
}

/** POST /stores (seller onboarding) — creates a new store in "pending" verification status. */
export async function createStore(input: StoreApplicationInput): Promise<Store> {
  return apiClient.post<Store>("/api/stores", input);
}

// --- Admin (mock, unauthenticated — see src/app/admin) ---

/** GET /admin/stores?status= */
export async function adminListStores(status?: StoreVerificationStatus): Promise<Store[]> {
  const qs = toQueryString({ status });
  return apiClient.get<Store[]>(`/api/admin/stores${qs}`);
}

/** PATCH /admin/stores/:id/verification — approve/reject a store. */
export async function setStoreVerificationStatus(
  storeId: string,
  status: StoreVerificationStatus,
  rejectionReason?: string,
): Promise<Store> {
  return apiClient.patch<Store>(`/api/admin/stores/${storeId}/verification`, {
    status,
    rejectionReason,
  });
}
