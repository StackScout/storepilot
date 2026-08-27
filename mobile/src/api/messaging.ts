import { apiFetch } from '@/lib/api-client';
import type { ConversationResponse, MessageResponse, PageResponse } from '@/api/types';

/** No pagination UI on the seller dashboard yet — size=200 keeps today's "show everything" behavior. */
export async function listStoreConversations(storeId: string): Promise<ConversationResponse[]> {
  return (await apiFetch<PageResponse<ConversationResponse>>(`/api/stores/${storeId}/conversations?size=200`)).content;
}

export function getConversation(id: string): Promise<ConversationResponse> {
  return apiFetch<ConversationResponse>(`/api/conversations/${id}`);
}

/**
 * Fetching messages also marks them read on the caller's side — there is no separate mark-as-read
 * endpoint. No pagination UI yet — size=200 keeps today's "show everything" behavior.
 */
export async function listMessages(conversationId: string): Promise<MessageResponse[]> {
  return (await apiFetch<PageResponse<MessageResponse>>(`/api/conversations/${conversationId}/messages?size=200`)).content;
}

export function sendMessage(conversationId: string, body: string): Promise<MessageResponse> {
  return apiFetch<MessageResponse>(`/api/conversations/${conversationId}/messages`, {
    method: 'POST',
    body: { body },
  });
}
