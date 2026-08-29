import type { ConversationResponse, PageResponse } from '@/api/types';
import { apiFetch } from '@/lib/api-client';

/** getConversation/listMessages/sendMessage are identical for either side — reused directly from api/messaging.ts. Only the buyer-specific entry points live here. */

export function getOrCreateConversation(storeId: string): Promise<ConversationResponse> {
  return apiFetch<ConversationResponse>(`/api/stores/${storeId}/conversations`, { method: 'POST' });
}

/** GET /api/me/conversations — buyer-scoped list, newest activity first. */
export async function listMyConversations(): Promise<ConversationResponse[]> {
  return (await apiFetch<PageResponse<ConversationResponse>>('/api/me/conversations?size=200')).content;
}
