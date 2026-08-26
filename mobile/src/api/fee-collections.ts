import { apiFetch } from '@/lib/api-client';
import type { FeeCollectionResponse } from '@/api/types';

export function listStoreFeeCollections(storeId: string): Promise<FeeCollectionResponse[]> {
  return apiFetch<FeeCollectionResponse[]>(`/api/stores/${storeId}/fee-collections`);
}
