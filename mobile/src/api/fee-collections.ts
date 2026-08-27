import { apiFetch } from '@/lib/api-client';
import type { FeeCollectionResponse, PageResponse } from '@/api/types';

/** No pagination UI on the seller dashboard yet — size=200 keeps today's "show everything" behavior. */
export async function listStoreFeeCollections(storeId: string): Promise<FeeCollectionResponse[]> {
  return (await apiFetch<PageResponse<FeeCollectionResponse>>(`/api/stores/${storeId}/fee-collections?size=200`)).content;
}
