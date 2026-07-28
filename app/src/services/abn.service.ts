import { apiClient } from "@/lib/api-client";
import type { AbnLookupResult } from "@/types";

/** GET /abn-lookup/:abn — public, no auth needed (see backend SecurityConfig). */
export async function lookupAbn(abn: string): Promise<AbnLookupResult> {
  return apiClient.get<AbnLookupResult>(`/api/abn-lookup/${encodeURIComponent(abn)}`);
}
