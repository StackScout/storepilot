import { apiFetch } from '@/lib/api-client';
import type { AbnLookupResult } from '@/api/types';

/** GET /api/abn-lookup/:abn — public, no auth needed (see backend SecurityConfig). */
export function lookupAbn(abn: string): Promise<AbnLookupResult> {
  return apiFetch<AbnLookupResult>(`/api/abn-lookup/${encodeURIComponent(abn)}`, { skipAuth: true });
}
