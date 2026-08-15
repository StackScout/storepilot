import { apiClient, toQueryString } from "@/lib/api-client";
import type {
  AvailabilityException,
  AvailabilityExceptionInput,
  DayAvailability,
  ServiceAvailabilityOverride,
  ServiceAvailabilityOverrideInput,
  StoreAvailability,
  WeeklyAvailabilityInput,
} from "@/types";

/** GET /stores/:storeId/availability — weekly template + lead time + exceptions in one call. */
export async function getAvailability(storeId: string): Promise<StoreAvailability> {
  return apiClient.get<StoreAvailability>(`/api/stores/${storeId}/availability`);
}

/** PUT /stores/:storeId/availability/weekly-rules — always replaces all 7 rows in one call. */
export async function upsertWeeklyRules(storeId: string, input: WeeklyAvailabilityInput): Promise<StoreAvailability> {
  return apiClient.put<StoreAvailability>(`/api/stores/${storeId}/availability/weekly-rules`, input);
}

/** POST /stores/:storeId/availability/exceptions — upserts by date (an existing exception for that date is replaced). */
export async function createException(storeId: string, input: AvailabilityExceptionInput): Promise<AvailabilityException> {
  return apiClient.post<AvailabilityException>(`/api/stores/${storeId}/availability/exceptions`, input);
}

/** DELETE /stores/:storeId/availability/exceptions/:exceptionId */
export async function deleteException(storeId: string, exceptionId: string): Promise<void> {
  await apiClient.delete<void>(`/api/stores/${storeId}/availability/exceptions/${exceptionId}`);
}

/**
 * GET /stores/:storeId/bookable-services/:serviceId/availability?from=&to= —
 * computed on read, no slot-materialization job. `from`/`to` are ISO date
 * strings (yyyy-MM-dd); omitted, the backend defaults to "today through the
 * next 30 days".
 */
export async function getSlots(storeId: string, serviceId: string, from?: string, to?: string): Promise<DayAvailability[]> {
  const qs = toQueryString({ from, to });
  return apiClient.get<DayAvailability[]>(`/api/stores/${storeId}/bookable-services/${serviceId}/availability${qs}`);
}

/** GET /stores/:storeId/bookable-services/:serviceId/availability-override — public. */
export async function getServiceAvailabilityOverride(storeId: string, serviceId: string): Promise<ServiceAvailabilityOverride> {
  return apiClient.get<ServiceAvailabilityOverride>(`/api/stores/${storeId}/bookable-services/${serviceId}/availability-override`);
}

/** PUT /stores/:storeId/bookable-services/:serviceId/availability-override — replaces all 7 rows and enables the override. */
export async function upsertServiceAvailabilityOverride(
  storeId: string,
  serviceId: string,
  input: ServiceAvailabilityOverrideInput,
): Promise<ServiceAvailabilityOverride> {
  return apiClient.put<ServiceAvailabilityOverride>(`/api/stores/${storeId}/bookable-services/${serviceId}/availability-override`, input);
}

/** DELETE /stores/:storeId/bookable-services/:serviceId/availability-override — reverts to inheriting the store's default weekly template. */
export async function disableServiceAvailabilityOverride(storeId: string, serviceId: string): Promise<void> {
  await apiClient.delete<void>(`/api/stores/${storeId}/bookable-services/${serviceId}/availability-override`);
}
