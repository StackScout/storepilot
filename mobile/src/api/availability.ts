import { apiFetch } from '@/lib/api-client';
import type { AvailabilityException, AvailabilityExceptionInput, StoreAvailability, WeeklyAvailabilityInput } from '@/api/types';

/** GET /stores/:storeId/availability — weekly template + lead time + exceptions in one call. */
export function getAvailability(storeId: string): Promise<StoreAvailability> {
  return apiFetch<StoreAvailability>(`/api/stores/${storeId}/availability`);
}

/** PUT /stores/:storeId/availability/weekly-rules — always replaces all 7 rows in one call. */
export function upsertWeeklyRules(storeId: string, input: WeeklyAvailabilityInput): Promise<StoreAvailability> {
  return apiFetch<StoreAvailability>(`/api/stores/${storeId}/availability/weekly-rules`, { method: 'PUT', body: input });
}

/** POST /stores/:storeId/availability/exceptions — upserts by date (an existing exception for that date is replaced). */
export function createException(storeId: string, input: AvailabilityExceptionInput): Promise<AvailabilityException> {
  return apiFetch<AvailabilityException>(`/api/stores/${storeId}/availability/exceptions`, { method: 'POST', body: input });
}

/** DELETE /stores/:storeId/availability/exceptions/:exceptionId */
export function deleteException(storeId: string, exceptionId: string): Promise<void> {
  return apiFetch<void>(`/api/stores/${storeId}/availability/exceptions/${exceptionId}`, { method: 'DELETE' });
}
