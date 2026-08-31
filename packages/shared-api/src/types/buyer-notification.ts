export type BuyerNotificationType = "order" | "booking" | "conversation";

export interface BuyerNotification {
  id: string;
  type: BuyerNotificationType;
  title: string;
  body: string;
  entityId?: string | null;
  read: boolean;
  createdAt: string;
}

export interface BuyerNotificationSummary {
  unreadCount: number;
}
