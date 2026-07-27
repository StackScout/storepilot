import { apiClient } from "@/lib/api-client";
import type { AdminNotification, AdminNotificationSummary } from "@/types";

/** GET /admin/notifications */
export async function listAdminNotifications(): Promise<AdminNotification[]> {
  return apiClient.get<AdminNotification[]>("/api/admin/notifications");
}

/** GET /admin/notifications/summary */
export async function getAdminNotificationSummary(): Promise<AdminNotificationSummary> {
  return apiClient.get<AdminNotificationSummary>("/api/admin/notifications/summary");
}

/** PATCH /admin/notifications/:id/read */
export async function markAdminNotificationRead(id: string): Promise<AdminNotification> {
  return apiClient.patch<AdminNotification>(`/api/admin/notifications/${id}/read`);
}

/** PATCH /admin/notifications/read-all */
export async function markAllAdminNotificationsRead(): Promise<void> {
  await apiClient.patch<void>("/api/admin/notifications/read-all");
}
