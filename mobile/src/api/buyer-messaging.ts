import type { ConversationResponse } from '@/api/types';
import { apiFetch } from '@/lib/api-client';

/** getConversation/listMessages/sendMessage are identical for either side — reused directly from api/messaging.ts. Only the buyer-specific entry points live here. */

export function getOrCreateConversation(storeId: string): Promise<ConversationResponse> {
  return apiFetch<ConversationResponse>(`/api/stores/${storeId}/conversations`, { method: 'POST' });
}

/** GET /api/me/conversations — buyer-scoped list, newest activity first. */
export function listMyConversations(): Promise<ConversationResponse[]> {
  return apiFetch<ConversationResponse[]>('/api/me/conversations');
}
