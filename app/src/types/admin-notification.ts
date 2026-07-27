export type AdminNotificationType = "bank-details-changed";

export interface AdminNotification {
  id: string;
  type: AdminNotificationType;
  message: string;
  storeId?: string;
  read: boolean;
  createdAt: string;
}

export interface AdminNotificationSummary {
  unreadCount: number;
}
