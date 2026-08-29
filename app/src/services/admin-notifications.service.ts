import { apiClient, toQueryString } from "@/lib/api-client";
import type { AdminNotification, AdminNotificationSummary, PageResponse } from "@/types";

/** GET /admin/notifications — paginated server-side. */
export async function listAdminNotifications(page = 0, size = 20): Promise<PageResponse<AdminNotification>> {
  const qs = toQueryString({ page, size });
  return apiClient.get<PageResponse<AdminNotification>>(`/api/admin/notifications${qs}`);
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
