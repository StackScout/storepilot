export type SenderType = "buyer" | "seller";

/** Mirrors backend Conversation. One thread per (store, buyer) pair. unreadCount is scoped to whichever side is calling — never both counts exposed to either party. */
export interface Conversation {
  id: string;
  storeId: string;
  storeName: string;
  storeSlug: string;
  buyerId: string;
  buyerName: string;
  lastMessageAt?: string;
  unreadCount: number;
  createdAt: string;
}

export interface Message {
  id: string;
  conversationId: string;
  senderType: SenderType;
  body: string;
  createdAt: string;
}
