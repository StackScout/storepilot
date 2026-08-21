import { apiClient, resolveAssetUrl, toQueryString } from "@/lib/api-client";
import type {
  PageResponse,
  Store,
  StoreApplicationInput,
  StoreCategory,
  StoreProfileInput,
  FollowStatus,
  StorePublicSettings,
  StoreSettings,
  StoreStats,
  StoreVerificationChangeRequest,
  StoreVerificationChangeRequestStatus,
  StoreVerificationStatus,
  VerificationChangeRequestInput,
} from "@/types";

/** logoUrl/bannerUrl may be relative (local FileStorageService) or already absolute (S3 presigned), or null if never uploaded — normalize once here. */
function normalizeStore(store: Store): Store {
  return {
    ...store,
    logoUrl: store.logoUrl ? resolveAssetUrl(store.logoUrl) : store.logoUrl,
    bannerUrl: store.bannerUrl ? resolveAssetUrl(store.bannerUrl) : store.bannerUrl,
  };
}

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
  const page = await apiClient.get<PageResponse<Store>>(`/api/stores${qs}`);
  return { ...page, content: page.content.map(normalizeStore) };
}

/** GET /stores/:slug — public storefront page: active stores only (backend-enforced). */
export async function getStoreBySlug(slug: string): Promise<Store | null> {
  const store = await apiClient.getOrNull<Store>(`/api/stores/${slug}`);
  return store ? normalizeStore(store) : null;
}

/** GET /stores/id/:id — internal lookup, not gated by verification status. */
export async function getStoreById(id: string): Promise<Store | null> {
  const store = await apiClient.getOrNull<Store>(`/api/stores/id/${id}`);
  return store ? normalizeStore(store) : null;
}

/** GET /stores/:id/settings — owner-only; full details including bank/contact/verification PII. */
export async function getStoreSettings(storeId: string): Promise<StoreSettings | null> {
  const settings = await apiClient.getOrNull<StoreSettings>(`/api/stores/${storeId}/settings`);
  return settings ? normalizeStoreSettings(settings) : null;
}

/** GET /stores/:id/public-settings — no auth required; buyer-safe subset for checkout/order pages. */
export async function getPublicStoreSettings(storeId: string): Promise<StorePublicSettings | null> {
  return apiClient.getOrNull<StorePublicSettings>(`/api/stores/${storeId}/public-settings`);
}

/** GET /stores/:id/stats — owner-only dashboard trend cards, rolling 7-day window vs the 7 days before it. */
export async function getStoreStats(storeId: string): Promise<StoreStats> {
  return apiClient.get<StoreStats>(`/api/stores/${storeId}/stats`);
}

/** GET /stores/:id/follow — public; reports false for a signed-out visitor. */
export async function getFollowStatus(storeId: string): Promise<FollowStatus> {
  return apiClient.get<FollowStatus>(`/api/stores/${storeId}/follow`);
}

/** POST /stores/:id/follow — requires a signed-in buyer. Idempotent. */
export async function followStore(storeId: string): Promise<FollowStatus> {
  return apiClient.post<FollowStatus>(`/api/stores/${storeId}/follow`);
}

/** DELETE /stores/:id/follow — requires a signed-in buyer. Idempotent. */
export async function unfollowStore(storeId: string): Promise<void> {
  await apiClient.delete<void>(`/api/stores/${storeId}/follow`);
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
  const store = await apiClient.patch<Store>(`/api/stores/${storeId}/profile`, patch);
  return normalizeStore(store);
}

/** POST /stores/:id/logo — upload/replace the store logo. */
export async function uploadStoreLogo(storeId: string, file: File): Promise<Store> {
  const formData = new FormData();
  formData.append("file", file);
  const store = await apiClient.postForm<Store>(`/api/stores/${storeId}/logo`, formData);
  return normalizeStore(store);
}

/** POST /stores/:id/banner — upload/replace the store banner. */
export async function uploadStoreBanner(storeId: string, file: File): Promise<Store> {
  const formData = new FormData();
  formData.append("file", file);
  const store = await apiClient.postForm<Store>(`/api/stores/${storeId}/banner`, formData);
  return normalizeStore(store);
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

/**
 * POST /stores/:id/stripe-connect/refresh — pulls the connected account's
 * live status directly from Stripe, the same way the `account.updated`
 * webhook normally would. Exists as a fallback for when that webhook is
 * misconfigured or drops an event — see backend StripeConnectService's doc
 * comment.
 */
export async function refreshStripeConnectStatus(storeId: string): Promise<void> {
  await apiClient.post<void>(`/api/stores/${storeId}/stripe-connect/refresh`);
}

/** POST /stores (seller onboarding) — creates a new store in "pending" verification status. Requires a signed-in account (any Cognito user); this call is what grants the seller role. */
export async function createStore(input: StoreApplicationInput): Promise<Store> {
  const store = await apiClient.post<Store>("/api/stores", input);
  return normalizeStore(store);
}

/** GET /api/me/store — the signed-in seller's own store, or null if they haven't onboarded yet. */
export async function getMyStore(): Promise<Store | null> {
  const store = await apiClient.getOrNull<Store>("/api/me/store");
  return store ? normalizeStore(store) : null;
}

/**
 * POST /stores/:id/close — permanent, seller-initiated. Blocked (409, with
 * a specific reason in the error message) unless every in-flight order,
 * booking, fee collection, and payout for the store has been resolved.
 * Precondition for seller account deletion — see seller-account.service.ts.
 */
export async function closeStore(storeId: string): Promise<Store> {
  const store = await apiClient.post<Store>(`/api/stores/${storeId}/close`);
  return normalizeStore(store);
}

// --- Admin (requires the admin Cognito role) ---

/** GET /admin/stores?status= */
export async function adminListStores(status?: StoreVerificationStatus): Promise<Store[]> {
  const qs = toQueryString({ status });
  const stores = await apiClient.get<Store[]>(`/api/admin/stores${qs}`);
  return stores.map(normalizeStore);
}

/** GET /admin/stores/:id/settings — full verification/bank details for any store, regardless of who owns it. */
export async function adminGetStoreSettings(storeId: string): Promise<StoreSettings | null> {
  const settings = await apiClient.getOrNull<StoreSettings>(`/api/admin/stores/${storeId}/settings`);
  return settings ? normalizeStoreSettings(settings) : null;
}

/** PATCH /admin/stores/:id/verification — approve/reject a store. */
export async function setStoreVerificationStatus(
  storeId: string,
  status: StoreVerificationStatus,
  rejectionReason?: string,
): Promise<Store> {
  const store = await apiClient.patch<Store>(`/api/admin/stores/${storeId}/verification`, {
    status,
    rejectionReason,
  });
  return normalizeStore(store);
}

/** Document URLs may be relative (local FileStorageService) or already absolute (S3 presigned) — normalize once here, same as normalizeStoreSettings. */
function normalizeChangeRequest(request: StoreVerificationChangeRequest): StoreVerificationChangeRequest {
  return {
    ...request,
    driverLicenceDocumentUrl: request.driverLicenceDocumentUrl
      ? resolveAssetUrl(request.driverLicenceDocumentUrl)
      : request.driverLicenceDocumentUrl,
    abnDocumentUrl: request.abnDocumentUrl ? resolveAssetUrl(request.abnDocumentUrl) : request.abnDocumentUrl,
    nicDocumentUrl: request.nicDocumentUrl ? resolveAssetUrl(request.nicDocumentUrl) : request.nicDocumentUrl,
    businessRegDocumentUrl: request.businessRegDocumentUrl
      ? resolveAssetUrl(request.businessRegDocumentUrl)
      : request.businessRegDocumentUrl,
  };
}

/** GET /stores/:id/verification-change-requests/current — the signed-in seller's own open request for this store, or null. */
export async function getCurrentVerificationChangeRequest(storeId: string): Promise<StoreVerificationChangeRequest | null> {
  const request = await apiClient.getOrNull<StoreVerificationChangeRequest>(
    `/api/stores/${storeId}/verification-change-requests/current`,
  );
  return request ? normalizeChangeRequest(request) : null;
}

/**
 * POST /stores/:id/verification-change-requests — only reachable once the
 * store is already approved (see StoreVerificationChangeRequestService's
 * doc comment); files are optional replacement documents for the fields
 * that changed.
 */
export async function submitVerificationChangeRequest(
  storeId: string,
  input: VerificationChangeRequestInput,
  files?: { driverLicenceDocument?: File; abnDocument?: File; nicDocument?: File; businessRegDocument?: File },
): Promise<StoreVerificationChangeRequest> {
  const formData = new FormData();
  formData.append("data", new Blob([JSON.stringify(input)], { type: "application/json" }));
  if (files?.driverLicenceDocument) formData.append("driverLicenceDocument", files.driverLicenceDocument);
  if (files?.abnDocument) formData.append("abnDocument", files.abnDocument);
  if (files?.nicDocument) formData.append("nicDocument", files.nicDocument);
  if (files?.businessRegDocument) formData.append("businessRegDocument", files.businessRegDocument);
  const request = await apiClient.postForm<StoreVerificationChangeRequest>(
    `/api/stores/${storeId}/verification-change-requests`,
    formData,
  );
  return normalizeChangeRequest(request);
}

/** GET /admin/verification-change-requests?status= — defaults to every request across all stores when status is omitted. */
export async function adminListVerificationChangeRequests(
  status?: StoreVerificationChangeRequestStatus,
): Promise<StoreVerificationChangeRequest[]> {
  const qs = toQueryString({ status });
  const requests = await apiClient.get<StoreVerificationChangeRequest[]>(`/api/admin/verification-change-requests${qs}`);
  return requests.map(normalizeChangeRequest);
}

/** POST /admin/verification-change-requests/:id/approve — applies every proposed field/document onto the store's live settings. */
export async function adminApproveVerificationChangeRequest(id: string): Promise<StoreSettings> {
  const settings = await apiClient.post<StoreSettings>(`/api/admin/verification-change-requests/${id}/approve`);
  return normalizeStoreSettings(settings);
}

/** POST /admin/verification-change-requests/:id/reject */
export async function adminRejectVerificationChangeRequest(
  id: string,
  rejectionReason: string,
): Promise<StoreVerificationChangeRequest> {
  const request = await apiClient.post<StoreVerificationChangeRequest>(`/api/admin/verification-change-requests/${id}/reject`, {
    rejectionReason,
  });
  return normalizeChangeRequest(request);
}
