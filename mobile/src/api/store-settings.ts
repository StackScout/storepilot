import { ApiError, apiFetch, apiFetchForm } from '@/lib/api-client';
import type { StoreProfileInput, StoreResponse, StoreSettingsInput, StoreSettingsResponse } from '@/api/types';

function blankStoreSettings(storeId: string): StoreSettingsResponse {
  return {
    storeId,
    contactEmail: '',
    contactPhone: '',
    bankAccountName: '',
    bankAccountNumber: '',
    bankName: '',
    transactionFeePercent: 0,
    codEnabled: false,
    onlinePaymentEnabled: false,
    bankTransferEnabled: false,
    sellerType: 'individual',
    driverLicenceNumber: undefined,
    abn: undefined,
    nicNumber: undefined,
    businessRegistrationNumber: undefined,
    rejectionReason: undefined,
    driverLicenceDocumentUrl: undefined,
    abnDocumentUrl: undefined,
    nicDocumentUrl: undefined,
    businessRegDocumentUrl: undefined,
    stockManagementEnabled: false,
    pickupEnabled: false,
    stripeAccountId: undefined,
    stripeChargesEnabled: false,
    stripePayoutsEnabled: false,
    stripeEnabled: false,
    bookingsEnabled: false,
    gstRegistered: false,
  };
}

/** GET returns a bare 404 (no JSON body) when the store has no settings row yet — the PATCH endpoint upserts, so start from blank defaults. */
export async function getStoreSettings(storeId: string): Promise<StoreSettingsResponse> {
  try {
    return await apiFetch<StoreSettingsResponse>(`/api/stores/${storeId}/settings`);
  } catch (e) {
    if (e instanceof ApiError && e.status === 404) return blankStoreSettings(storeId);
    throw e;
  }
}

export function updateStoreSettings(storeId: string, input: StoreSettingsInput): Promise<StoreSettingsResponse> {
  return apiFetch<StoreSettingsResponse>(`/api/stores/${storeId}/settings`, { method: 'PATCH', body: input });
}

/** platform=mobile makes the backend hand back the app's own deep-link scheme as the return/refresh URL instead of the web app's settings page — see store.tsx's use of openAuthSessionAsync. */
export function startStripeConnectOnboarding(storeId: string): Promise<{ onboardingUrl: string }> {
  return apiFetch<{ onboardingUrl: string }>(`/api/stores/${storeId}/stripe-connect/onboard?platform=mobile`, { method: 'POST' });
}

export function refreshStripeConnectStatus(storeId: string): Promise<void> {
  return apiFetch<void>(`/api/stores/${storeId}/stripe-connect/refresh`, { method: 'POST' });
}

export function updateStoreProfile(storeId: string, input: StoreProfileInput): Promise<StoreResponse> {
  return apiFetch<StoreResponse>(`/api/stores/${storeId}/profile`, { method: 'PATCH', body: input });
}

/** Local file uri -> real Blob with a forced content type — see buildProductForm's comment in products.ts for why. */
async function uploadSingleFile<T>(path: string, uri: string, filename: string, mimeType: string): Promise<T> {
  const rawBlob = await (await fetch(uri)).blob();
  const blob = rawBlob.slice(0, rawBlob.size, mimeType);
  const form = new FormData();
  form.append('file', blob, filename);
  return apiFetchForm<T>(path, form, 'POST');
}

export function uploadStoreLogo(storeId: string, uri: string): Promise<StoreResponse> {
  return uploadSingleFile<StoreResponse>(`/api/stores/${storeId}/logo`, uri, 'logo.jpg', 'image/jpeg');
}

export function uploadStoreBanner(storeId: string, uri: string): Promise<StoreResponse> {
  return uploadSingleFile<StoreResponse>(`/api/stores/${storeId}/banner`, uri, 'banner.jpg', 'image/jpeg');
}

export function uploadDriverLicenceDocument(storeId: string, uri: string): Promise<StoreSettingsResponse> {
  return uploadSingleFile<StoreSettingsResponse>(`/api/stores/${storeId}/driver-licence-document`, uri, 'driver-licence.jpg', 'image/jpeg');
}

export function uploadAbnDocument(storeId: string, uri: string): Promise<StoreSettingsResponse> {
  return uploadSingleFile<StoreSettingsResponse>(`/api/stores/${storeId}/abn-document`, uri, 'abn.jpg', 'image/jpeg');
}

export function uploadNicDocument(storeId: string, uri: string): Promise<StoreSettingsResponse> {
  return uploadSingleFile<StoreSettingsResponse>(`/api/stores/${storeId}/nic-document`, uri, 'nic.jpg', 'image/jpeg');
}

export function uploadBusinessRegDocument(storeId: string, uri: string): Promise<StoreSettingsResponse> {
  return uploadSingleFile<StoreSettingsResponse>(`/api/stores/${storeId}/business-reg-document`, uri, 'business-reg.jpg', 'image/jpeg');
}
