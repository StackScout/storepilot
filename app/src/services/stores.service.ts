import { apiClient, resolveAssetUrl, toQueryString } from "@/lib/api-client";
import type {
  PageResponse,
  Store,
  StoreApplicationInput,
  StoreCategory,
  StoreProfileInput,
  StoreSettings,
  StoreVerificationStatus,
} from "@/types";

/** Uploaded document URLs may be relative (local FileStorageService) or already absolute (S3 presigned) — normalize once here. */
function normalizeStoreSettings(settings: StoreSettings): StoreSettings {
  return {
    ...settings,
    driverLicenceDocumentUrl: settings.driverLicenceDocumentUrl
      ? resolveAssetUrl(settings.driverLicenceDocumentUrl)
      : settings.driverLicenceDocumentUrl,
    abnDocumentUrl: settings.abnDocumentUrl ? resolveAssetUrl(settings.abnDocumentUrl) : settings.abnDocumentUrl,
    nicDocumentUrl: settings.nicDocumentUrl ? resolveAssetUrl(settings.nicDocumentUrl) : settings.nicDocumentUrl,
    businessRegDocumentUrl: settings.businessRegDocumentUrl
      ? resolveAssetUrl(settings.businessRegDocumentUrl)
      : settings.businessRegDocumentUrl,
  };
}

export interface StoreQueryParams {
  category?: StoreCategory;
  query?: string;
  page?: number;
  size?: number;
}

const DEFAULT_PAGE_SIZE = 24;

/**
 * GET /stores — public marketplace listing: active stores only
 * (backend-enforced), sorted by rating server-side. Filtering and
 * pagination both happen in the SQL query — see backend StoreService#search.
 */
export async function listStores(params: StoreQueryParams = {}): Promise<PageResponse<Store>> {
  const qs = toQueryString({
    category: params.category,
    query: params.query,
    page: params.page ?? 0,
    size: params.size ?? DEFAULT_PAGE_SIZE,
  });
  return apiClient.get<PageResponse<Store>>(`/api/stores${qs}`);
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
  const settings = await apiClient.getOrNull<StoreSettings>(`/api/stores/${storeId}/settings`);
  return settings ? normalizeStoreSettings(settings) : null;
}

/** PATCH /stores/:id/settings — upsert, same as the backend service. */
export async function updateStoreSettings(
  storeId: string,
  patch: Partial<Omit<StoreSettings, "storeId">>,
): Promise<StoreSettings> {
  const settings = await apiClient.patch<StoreSettings>(`/api/stores/${storeId}/settings`, patch);
  return normalizeStoreSettings(settings);
}

/** PATCH /stores/:id/profile — seller-editable public social links. */
export async function updateStoreProfile(storeId: string, patch: StoreProfileInput): Promise<Store> {
  return apiClient.patch<Store>(`/api/stores/${storeId}/profile`, patch);
}

/** POST /stores/:id/driver-licence-document — upload/replace the seller's driver's licence proof. */
export async function uploadDriverLicenceDocument(storeId: string, file: File): Promise<StoreSettings> {
  const formData = new FormData();
  formData.append("file", file);
  const settings = await apiClient.postForm<StoreSettings>(`/api/stores/${storeId}/driver-licence-document`, formData);
  return normalizeStoreSettings(settings);
}

/** POST /stores/:id/abn-document — upload/replace the seller's ABN registration proof. */
export async function uploadAbnDocument(storeId: string, file: File): Promise<StoreSettings> {
  const formData = new FormData();
  formData.append("file", file);
  const settings = await apiClient.postForm<StoreSettings>(`/api/stores/${storeId}/abn-document`, formData);
  return normalizeStoreSettings(settings);
}

/** POST /stores/:id/nic-document — upload/replace the seller's NIC proof (Sri Lanka deployments only). */
export async function uploadNicDocument(storeId: string, file: File): Promise<StoreSettings> {
  const formData = new FormData();
  formData.append("file", file);
  const settings = await apiClient.postForm<StoreSettings>(`/api/stores/${storeId}/nic-document`, formData);
  return normalizeStoreSettings(settings);
}

/** POST /stores/:id/business-reg-document — upload/replace the seller's business registration proof (Sri Lanka deployments only). */
export async function uploadBusinessRegDocument(storeId: string, file: File): Promise<StoreSettings> {
  const formData = new FormData();
  formData.append("file", file);
  const settings = await apiClient.postForm<StoreSettings>(`/api/stores/${storeId}/business-reg-document`, formData);
  return normalizeStoreSettings(settings);
}

/** POST /stores/:id/stripe-connect/onboard — start or resume Stripe Connect onboarding; redirect the browser to the returned URL. */
export async function startStripeConnectOnboarding(storeId: string): Promise<{ onboardingUrl: string }> {
  return apiClient.post<{ onboardingUrl: string }>(`/api/stores/${storeId}/stripe-connect/onboard`);
}

/** POST /stores (seller onboarding) — creates a new store in "pending" verification status. Requires a signed-in account (any Cognito user); this call is what grants the seller role. */
export async function createStore(input: StoreApplicationInput): Promise<Store> {
  return apiClient.post<Store>("/api/stores", input);
}

/** GET /api/me/store — the signed-in seller's own store, or null if they haven't onboarded yet. */
export async function getMyStore(): Promise<Store | null> {
  return apiClient.getOrNull<Store>("/api/me/store");
}

// --- Admin (requires the admin Cognito role) ---

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
