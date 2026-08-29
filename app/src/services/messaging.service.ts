import { apiClient, toQueryString } from "@/lib/api-client";
import type { Conversation, Message, PageResponse } from "@/types";

/** POST /stores/:storeId/conversations — buyer-only, gets the buyer's existing conversation with this store or creates a new one. */
export async function getOrCreateConversation(storeId: string): Promise<Conversation> {
  return apiClient.post<Conversation>(`/api/stores/${storeId}/conversations`);
}

/** GET /me/conversations — buyer-scoped list, newest activity first. Paginated server-side. */
export async function listMyConversations(page = 0, size = 20): Promise<PageResponse<Conversation>> {
  const qs = toQueryString({ page, size });
  return apiClient.get<PageResponse<Conversation>>(`/api/me/conversations${qs}`);
}

/** GET /stores/:storeId/conversations — seller-scoped list. Paginated server-side. */
export async function listStoreConversations(storeId: string, page = 0, size = 20): Promise<PageResponse<Conversation>> {
  const qs = toQueryString({ page, size });
  return apiClient.get<PageResponse<Conversation>>(`/api/stores/${storeId}/conversations${qs}`);
}

/** GET /conversations/:id */
export async function getConversationById(id: string): Promise<Conversation> {
  return apiClient.get<Conversation>(`/api/conversations/${id}`);
}

/**
 * GET /conversations/:id/messages — also marks every message as read for
 * the calling side. Paginated server-side (oldest-first); defaults to a
 * generous page size since a chat thread is normally shown in full rather
 * than paged through.
 */
export async function listMessages(id: string, page = 0, size = 200): Promise<PageResponse<Message>> {
  const qs = toQueryString({ page, size });
  return apiClient.get<PageResponse<Message>>(`/api/conversations/${id}/messages${qs}`);
}

/** POST /conversations/:id/messages */
export async function sendMessage(id: string, body: string): Promise<Message> {
  return apiClient.post<Message>(`/api/conversations/${id}/messages`, { body });
}
