import { apiClient } from "@/lib/api-client";
import type { Conversation, Message } from "@/types";

/** POST /stores/:storeId/conversations — buyer-only, gets the buyer's existing conversation with this store or creates a new one. */
export async function getOrCreateConversation(storeId: string): Promise<Conversation> {
  return apiClient.post<Conversation>(`/api/stores/${storeId}/conversations`);
}

/** GET /me/conversations — buyer-scoped list, newest activity first. */
export async function listMyConversations(): Promise<Conversation[]> {
  return apiClient.get<Conversation[]>("/api/me/conversations");
}

/** GET /stores/:storeId/conversations — seller-scoped list. */
export async function listStoreConversations(storeId: string): Promise<Conversation[]> {
  return apiClient.get<Conversation[]>(`/api/stores/${storeId}/conversations`);
}

/** GET /conversations/:id */
export async function getConversationById(id: string): Promise<Conversation> {
  return apiClient.get<Conversation>(`/api/conversations/${id}`);
}

/** GET /conversations/:id/messages — also marks every message as read for the calling side. */
export async function listMessages(id: string): Promise<Message[]> {
  return apiClient.get<Message[]>(`/api/conversations/${id}/messages`);
}

/** POST /conversations/:id/messages */
export async function sendMessage(id: string, body: string): Promise<Message> {
  return apiClient.post<Message>(`/api/conversations/${id}/messages`, { body });
}
