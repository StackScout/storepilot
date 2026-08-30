export type SellerNotificationType = "order" | "booking" | "product" | "conversation" | "payout";

export interface SellerNotification {
  id: string;
  type: SellerNotificationType;
  title: string;
  body: string;
  entityId?: string | null;
  read: boolean;
  createdAt: string;
}

export interface SellerNotificationSummary {
  unreadCount: number;
}
