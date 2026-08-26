import { apiFetch } from '@/lib/api-client';
import type { ConversationResponse, MessageResponse } from '@/api/types';

export function listStoreConversations(storeId: string): Promise<ConversationResponse[]> {
  return apiFetch<ConversationResponse[]>(`/api/stores/${storeId}/conversations`);
}

export function getConversation(id: string): Promise<ConversationResponse> {
  return apiFetch<ConversationResponse>(`/api/conversations/${id}`);
}

/** Fetching messages also marks them read on the caller's side — there is no separate mark-as-read endpoint. */
export function listMessages(conversationId: string): Promise<MessageResponse[]> {
  return apiFetch<MessageResponse[]>(`/api/conversations/${conversationId}/messages`);
}

export function sendMessage(conversationId: string, body: string): Promise<MessageResponse> {
  return apiFetch<MessageResponse>(`/api/conversations/${conversationId}/messages`, {
    method: 'POST',
    body: { body },
  });
}
